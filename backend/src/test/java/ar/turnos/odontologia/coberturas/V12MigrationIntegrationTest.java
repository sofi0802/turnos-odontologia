package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Esquema de V12 contra PostgreSQL real (C-18, tarea 1.1): la base repite las reglas de RN-OS-01 a RN-OS-05 (design D2
 * y D3), así una escritura directa tampoco puede dejar vigencias superpuestas ni números de afiliado repetidos.
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest
class V12MigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private long pacienteUno;
    private long pacienteDos;
    private long obraSocial;
    private long planBase;
    private long planIntegral;
    private long prepaga;
    private long planOro;

    @BeforeEach
    void datosFicticios() {
        jdbc.execute("TRUNCATE afiliacion_paciente, nomenclador, plan, obra_social, usuario, turno, paciente, "
                + "prestacion, sillon, profesional RESTART IDENTITY CASCADE");
        pacienteUno = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Uno','99000001') RETURNING id");
        pacienteDos = id("INSERT INTO paciente(nombre, apellido, dni) VALUES ('Paciente','Dos','99000002') RETURNING id");
        obraSocial = id("INSERT INTO obra_social(nombre, sigla, tipo) VALUES ('Obra Social Ejemplo','OSE','OBRA_SOCIAL') RETURNING id");
        planBase = plan(obraSocial, "Plan Base");
        planIntegral = plan(obraSocial, "Plan Integral");
        prepaga = id("INSERT INTO obra_social(nombre, tipo) VALUES ('Prepaga Prueba','PREPAGA') RETURNING id");
        planOro = plan(prepaga, "Plan Oro");
    }

    @Test
    @DisplayName("V12 crea las cuatro tablas del catálogo y las afiliaciones")
    void creaLasCuatroTablas() {
        List<String> tablas = jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name IN "
                + "('obra_social','plan','nomenclador','afiliacion_paciente')", String.class);
        assertThat(tablas).containsExactlyInAnyOrder("obra_social", "plan", "nomenclador", "afiliacion_paciente");
    }

    @Test
    @DisplayName("La base rechaza un nombre de obra social repetido con otras mayúsculas")
    void nombreDeObraSocialRepetido() {
        assertRechaza(() -> jdbc.update("INSERT INTO obra_social(nombre, tipo) VALUES ('OBRA SOCIAL EJEMPLO','OBRA_SOCIAL')"),
                "obra_social_nombre_unico");
    }

    @Test
    @DisplayName("La base rechaza un tipo de obra social inválido")
    void tipoInvalido() {
        assertRechaza(() -> jdbc.update("INSERT INTO obra_social(nombre, tipo) VALUES ('Mutual Prueba','MUTUAL')"),
                "obra_social_tipo_valido");
    }

    @Test
    @DisplayName("La base rechaza un plan repetido en la misma obra social y lo acepta en otra")
    void planRepetido() {
        assertRechaza(() -> plan(obraSocial, "PLAN BASE"), "plan_nombre_unico");
        assertThatCode(() -> plan(prepaga, "Plan Base")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza un código de nomenclador repetido con otras mayúsculas")
    void codigoRepetido() {
        jdbc.update("INSERT INTO nomenclador(codigo, descripcion) VALUES ('N-0101','Consulta')");
        assertRechaza(() -> jdbc.update("INSERT INTO nomenclador(codigo, descripcion) VALUES ('n-0101','Otra')"),
                "nomenclador_codigo_unico");
    }

    @Test
    @DisplayName("La base rechaza vigente_hasta anterior a vigente_desde")
    void vigenciaInvalida() {
        assertRechaza(() -> afiliar(pacienteUno, planBase, "OSE-000001", "2026-06-01", "2026-05-31"),
                "afiliacion_vigencia_valida");
        assertThatCode(() -> afiliar(pacienteUno, planBase, "OSE-000001", "2026-06-01", "2026-06-01"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza un número de afiliado con caracteres no permitidos")
    void numeroInvalido() {
        assertRechaza(() -> afiliar(pacienteUno, planBase, "OSE 0001#", "2026-01-01", null), "afiliacion_numero_valido");
        // 31 caracteres: lo frena el VARCHAR(30) antes que el CHECK.
        assertThatThrownBy(() -> afiliar(pacienteUno, planBase, "A".repeat(31), "2026-01-01", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("La base rechaza una obra social que no es la de su plan")
    void obraSocialDistintaDeLaDelPlan() {
        assertRechaza(() -> jdbc.update("INSERT INTO afiliacion_paciente(paciente_id, plan_id, obra_social_id, "
                + "numero_afiliado, vigente_desde) VALUES (?,?,?,'PP-77',DATE '2026-01-01')",
                pacienteUno, planOro, obraSocial), "afiliacion_plan_fk");
    }

    @Test
    @DisplayName("La base rechaza una superposición escrita directo")
    void superposicionDirecta() {
        afiliar(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
        assertRechaza(() -> afiliar(pacienteUno, planOro, "PP-77", "2026-03-01", null), "afiliacion_sin_superposicion");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM afiliacion_paciente", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("La base acepta vigencias contiguas y rechaza la que comparte un día")
    void vigenciasContiguas() {
        afiliar(pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-06-30");
        assertRechaza(() -> afiliar(pacienteUno, planOro, "PP-77", "2026-06-30", null), "afiliacion_sin_superposicion");
        assertThatCode(() -> afiliar(pacienteUno, planOro, "PP-77", "2026-07-01", null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base acepta las mismas fechas para pacientes distintos")
    void otroPacienteMismasFechas() {
        afiliar(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
        assertThatCode(() -> afiliar(pacienteDos, planBase, "OSE-000002", "2026-01-01", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base rechaza el mismo número en la misma obra social para otro paciente")
    void numeroRepetidoParaOtroPaciente() {
        afiliar(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
        assertRechaza(() -> afiliar(pacienteDos, planIntegral, "ose-000001", "2026-01-01", null),
                "afiliacion_numero_unico_por_obra_social");
    }

    @Test
    @DisplayName("La base acepta el mismo número en otra obra social y el mismo paciente en otro plan de la suya")
    void numeroPermitido() {
        afiliar(pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-06-30");
        assertThatCode(() -> afiliar(pacienteDos, planOro, "OSE-000001", "2026-01-01", null)).doesNotThrowAnyException();
        assertThatCode(() -> afiliar(pacienteUno, planIntegral, "OSE-000001", "2026-07-01", null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("La base no deja borrar un plan con afiliaciones ni una obra social con planes")
    void nadaSeBorra() {
        afiliar(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
        assertRechaza(() -> jdbc.update("DELETE FROM plan WHERE id = ?", planBase), "afiliacion_plan_fk");
        assertRechaza(() -> jdbc.update("DELETE FROM obra_social WHERE id = ?", obraSocial), "plan_obra_social_id_fkey");
    }

    private long plan(long obra, String nombre) {
        return id("INSERT INTO plan(obra_social_id, nombre) VALUES (" + obra + ", '" + nombre + "') RETURNING id");
    }

    private int afiliar(long paciente, long plan, String numero, String desde, String hasta) {
        long obra = jdbc.queryForObject("SELECT obra_social_id FROM plan WHERE id = ?", Long.class, plan);
        return jdbc.update("INSERT INTO afiliacion_paciente(paciente_id, plan_id, obra_social_id, numero_afiliado, "
                + "vigente_desde, vigente_hasta) VALUES (?,?,?,?,CAST(? AS date),CAST(? AS date))",
                paciente, plan, obra, numero, desde, hasta);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static void assertRechaza(Runnable accion, String restriccion) {
        assertThatThrownBy(accion::run).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(restriccion);
    }
}
