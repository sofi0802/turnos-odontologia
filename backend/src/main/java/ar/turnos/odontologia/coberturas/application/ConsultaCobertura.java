package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.Vistas.AfiliacionVista;
import ar.turnos.odontologia.coberturas.application.Vistas.CoberturaVista;
import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobertura de un paciente en una fecha (RN-OS-04, design D7): su afiliación vigente o la atención como particular.
 * "Hoy" es la fecha de Buenos Aires del reloj inyectado. Es la consulta que reutilizan C-19 y C-20. La vigencia de una
 * afiliación no depende de que su plan u obra social sigan activos.
 */
@Service
@Transactional(readOnly = true)
public class ConsultaCobertura {

    private final LecturaCoberturas lectura;
    private final PacientesExistentes pacientes;
    private final Clock reloj;
    private final ZoneId zona;

    public ConsultaCobertura(LecturaCoberturas lectura, PacientesExistentes pacientes, Clock reloj, ZoneId zona) {
        this.lectura = lectura;
        this.pacientes = pacientes;
        this.reloj = reloj;
        this.zona = zona;
    }

    /** La cobertura del paciente en {@code fecha}, o hoy si es nula; un paciente inexistente es {@code 404}. */
    public CoberturaVista de(Long pacienteId, LocalDate fecha) {
        if (!pacientes.existe(pacienteId)) {
            throw CoberturaException.noEncontrado("No existe el paciente");
        }
        return en(pacienteId, fecha == null ? hoy() : fecha);
    }

    /** La cobertura de hoy del paciente de la sesión: el id sale de la sesión, no del pedido. */
    public CoberturaVista deHoy(Long pacienteId) {
        return en(pacienteId, hoy());
    }

    private CoberturaVista en(Long pacienteId, LocalDate fecha) {
        return lectura.vigenteEn(pacienteId, fecha)
                .map(d -> new CoberturaVista(false, AfiliacionVista.de(d, fecha)))
                .orElseGet(() -> new CoberturaVista(true, null));
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj.withZone(zona));
    }
}
