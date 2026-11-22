package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.application.RegistroClinico.Cambio;
import ar.turnos.odontologia.clinica.application.RegistroClinico.RegistroOdontograma;
import ar.turnos.odontologia.clinica.domain.CaraDental;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.EstadoPieza;
import ar.turnos.odontologia.clinica.domain.PiezaDental;
import java.time.Clock;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Odontograma FDI de solo agregado (design D3 y D4). Un pedido es atómico: se valida cada cambio en el dominio antes
 * de guardar alguno, y todos quedan con el profesional de la sesión y el mismo momento.
 */
@Service
@Transactional
public class ServicioOdontograma {

    private final RegistroClinico registro;
    private final Clock reloj;

    public ServicioOdontograma(RegistroClinico registro, Clock reloj) {
        this.registro = registro;
        this.reloj = reloj;
    }

    /**
     * @param profesionalId el profesional vinculado al usuario de la sesión
     * @throws AccessDeniedException si el usuario no está vinculado a un profesional (403, design D5)
     * @throws ClinicaException {@code PIEZA_INVALIDA}, {@code CARA_INVALIDA} o {@code DATOS_INVALIDOS} sin guardar nada
     */
    public List<RegistroOdontograma> registrar(Long pacienteId, List<CambioPedido> pedido, Long profesionalId) {
        if (profesionalId == null) {
            throw new AccessDeniedException("El usuario no está vinculado a un profesional");
        }
        exigirPaciente(pacienteId);
        List<Cambio> cambios = pedido.stream().map(ServicioOdontograma::validar).toList();
        registro.registrarCambios(pacienteId, profesionalId, reloj.instant(), cambios);
        return registro.vigente(pacienteId);
    }

    @Transactional(readOnly = true)
    public List<RegistroOdontograma> vigente(Long pacienteId) {
        exigirPaciente(pacienteId);
        return registro.vigente(pacienteId);
    }

    @Transactional(readOnly = true)
    public List<RegistroOdontograma> historial(Long pacienteId) {
        exigirPaciente(pacienteId);
        return registro.historial(pacienteId);
    }

    private static Cambio validar(CambioPedido cambio) {
        PiezaDental pieza = PiezaDental.de(cambio.pieza());
        return new Cambio(pieza, CaraDental.para(cambio.cara(), pieza), EstadoPieza.de(cambio.estado()));
    }

    private void exigirPaciente(Long pacienteId) {
        if (!registro.existePaciente(pacienteId)) {
            throw ClinicaException.pacienteInexistente();
        }
    }

    /** Un cambio tal como llega en el pedido, todavía sin validar. */
    public record CambioPedido(int pieza, String cara, String estado) {}
}
