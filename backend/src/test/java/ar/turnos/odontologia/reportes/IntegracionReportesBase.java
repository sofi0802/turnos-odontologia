package ar.turnos.odontologia.reportes;

import ar.turnos.odontologia.clinica.IntegracionClinicaBase.ConRelojAjustable;
import ar.turnos.odontologia.clinica.IntegracionClinicaBase.RelojAjustable;
import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.shared.security.Navegador;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Base de los tests de integración de C-32: PostgreSQL real, sesión y CSRF de verdad y datos ficticios.
 *
 * <ul>
 *   <li>La Dra. Ana Ejemplo con el Sillón 1 como habitual atiende de lunes a viernes de 09:00 a 13:00; el Dr. Bruno
 *       Prueba con el Sillón 2, los lunes de 09:00 a 10:00. El Sillón 3 no es habitual de nadie.</li>
 *   <li>Cuentas: {@code admin}, {@code recepcion}, {@code ana.ejemplo} (odontóloga de la Dra. Ana Ejemplo) y
 *       {@code paciente.uno@example.com}.</li>
 *   <li>Reloj fijo: lunes 2026-12-14 a las 12:00 en Buenos Aires, después de la semana de referencia.</li>
 * </ul>
 */
@Tag("integration")
@Import(ConRelojAjustable.class)
public abstract class IntegracionReportesBase extends IntegracionSeguridadBase {

    protected static final String REPORTES = "/api/v1/reportes";
    protected static final String SEMANA = REPORTES + "?desde=2026-12-07&hasta=2026-12-13";
    protected static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    protected RelojAjustable reloj;

    protected final String clave = contrasenaFicticia();
    protected long sillon1;
    protected long sillon2;
    protected long sillon3;
    protected long ana;
    protected long bruno;
    protected long consulta;
    protected long pacienteUno;

    @BeforeEach
    void cargarDatosDeReportes() {
        jdbc.execute("TRUNCATE horario_trabajo, ausencia_profesional, bloqueo_agenda RESTART IDENTITY CASCADE");
        reloj.ahoraEs(instante("2026-12-14", "12:00"));
        sillon1 = id("INSERT INTO sillon(nombre) VALUES ('Sillón 1') RETURNING id");
        sillon2 = id("INSERT INTO sillon(nombre) VALUES ('Sillón 2') RETURNING id");
        sillon3 = id("INSERT INTO sillon(nombre) VALUES ('Sillón 3') RETURNING id");
        ana = id("INSERT INTO profesional(nombre, apellido, matricula, sillon_habitual_id) "
                + "VALUES ('Ana','Ejemplo','MP-0001',?) RETURNING id", sillon1);
        bruno = id("INSERT INTO profesional(nombre, apellido, matricula, sillon_habitual_id) "
                + "VALUES ('Bruno','Prueba','MP-0002',?) RETURNING id", sillon2);
        consulta = id("INSERT INTO prestacion(nombre, duracion_minutos) VALUES ('Consulta', 30) RETURNING id");
        for (int dia = 1; dia <= 5; dia++) {
            franja(ana, dia, "09:00", "13:00");
        }
        franja(bruno, 1, "09:00", "10:00");
        pacienteUno = crearPacienteConCuenta("Paciente", "Uno", "99000001", "paciente.uno@example.com", clave);
        crearPersonal("admin", "ADMIN", clave);
        crearPersonal("recepcion", "RECEPCIONISTA", clave);
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, profesional_id) VALUES (?,?,'ODONTOLOGO',?)",
                "ana.ejemplo", passwordEncoder.encode(clave), ana);
    }

    protected long id(String sql, Object... argumentos) {
        Long valor = jdbc.queryForObject(sql, Long.class, argumentos);
        return valor == null ? 0 : valor;
    }

    protected void franja(long profesional, int diaSemana, String desde, String hasta) {
        jdbc.update("INSERT INTO horario_trabajo(profesional_id, dia_semana, hora_desde, hora_hasta) VALUES (?,?,?,?)",
                profesional, diaSemana, LocalTime.parse(desde), LocalTime.parse(hasta));
    }

    protected static Instant instante(String fecha, String hora) {
        return LocalDateTime.of(LocalDate.parse(fecha), LocalTime.parse(hora)).atZone(ZONA).toInstant();
    }

    private static OffsetDateTime enZona(String fecha, String hora) {
        return instante(fecha, hora).atZone(ZONA).toOffsetDateTime();
    }

    /** Inserta un turno de Paciente Uno, sin registros de auditoría. */
    protected long turno(long profesional, long sillon, String fecha, String desde, String hasta, String estado,
                         boolean sobreturno) {
        return id("INSERT INTO turno(profesional_id, sillon_id, prestacion_id, paciente_id, inicio, fin, fin_con_margen, "
                        + "estado, es_sobreturno, motivo_sobreturno) VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id",
                profesional, sillon, consulta, pacienteUno, enZona(fecha, desde), enZona(fecha, hasta),
                enZona(fecha, hasta).plusMinutes(10), estado, sobreturno,
                sobreturno ? "Urgencia administrativa de prueba" : null);
    }

    protected void auditar(long turno, String accion, String actor) {
        jdbc.update("INSERT INTO turno_auditoria(turno_id, accion, actor, fecha) VALUES (?,?,?,now())", turno, accion,
                actor);
    }

    protected Navegador como(String usuario) throws Exception {
        return iniciarSesion(usuario, clave);
    }
}
