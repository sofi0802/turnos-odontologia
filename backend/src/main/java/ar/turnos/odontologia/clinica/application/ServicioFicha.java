package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.application.AusenciasPaciente.Ausencia;
import ar.turnos.odontologia.clinica.application.RegistroClinico.Ficha;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ficha básica del paciente (RN-CL-01, design D2): antecedentes, alergias y medicación, que cada registro reemplaza.
 * Firma con el usuario de la sesión. Nada de su contenido va a logs.
 */
@Service
@Transactional
public class ServicioFicha {

    private final RegistroClinico registro;
    private final AusenciasPaciente ausencias;
    private final Clock reloj;

    public ServicioFicha(RegistroClinico registro, AusenciasPaciente ausencias, Clock reloj) {
        this.registro = registro;
        this.ausencias = ausencias;
        this.reloj = reloj;
    }

    /** Un paciente sin ficha devuelve una ficha vacía, sin firma (design D2). */
    @Transactional(readOnly = true)
    public FichaCompleta consultar(Long pacienteId) {
        exigirPaciente(pacienteId);
        Ficha ficha = registro.ficha(pacienteId)
                .orElse(new Ficha(pacienteId, null, null, null, null, null));
        return new FichaCompleta(ficha, ausencias.de(pacienteId));
    }

    public FichaCompleta guardar(Long pacienteId, String antecedentes, String alergias, String medicacion,
                                 String usuario) {
        exigirPaciente(pacienteId);
        registro.guardarFicha(new Ficha(pacienteId, antecedentes, alergias, medicacion, usuario, reloj.instant()));
        return consultar(pacienteId);
    }

    private void exigirPaciente(Long pacienteId) {
        if (!registro.existePaciente(pacienteId)) {
            throw ClinicaException.pacienteInexistente();
        }
    }

    /** La ficha básica y las ausencias del paciente a sus turnos (design D7). */
    public record FichaCompleta(Ficha ficha, List<Ausencia> ausencias) {}
}
