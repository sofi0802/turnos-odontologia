package ar.turnos.odontologia.reserva.application;

import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.reserva.application.ReservasPaciente.TurnoDelPaciente;
import ar.turnos.odontologia.reserva.domain.CodigoReserva;
import ar.turnos.odontologia.reserva.domain.PlazoModificacion;
import ar.turnos.odontologia.reserva.domain.ReservaRechazadaException;
import java.time.Clock;
import java.util.Locale;

/**
 * Los tres controles que comparten cancelar y reprogramar online, en este orden (design D6): que la reserva exista y
 * sea del paciente de la sesión (404), que siga activa (409) y que falten 24 horas o más (409).
 */
final class ReservaPropia {

    private ReservaPropia() {}

    static TurnoDelPaciente verificar(ReservasPaciente reservas, Clock reloj, Long pacienteId, String numeroReserva) {
        // un número ajeno y uno inexistente son indistinguibles: la búsqueda ya filtra por el paciente de la sesión
        TurnoDelPaciente turno = reservas.buscar(normalizar(numeroReserva), pacienteId)
                .orElseThrow(() -> ReservaRechazadaException.de(CodigoReserva.RESERVA_INEXISTENTE,
                        "No existe una reserva con ese número"));
        if (!turno.estado().esActivo()) {
            throw ReservaRechazadaException.de(CodigoRechazo.TURNO_NO_ACTIVO, "La reserva no está activa");
        }
        if (!PlazoModificacion.permitido(reloj.instant(), turno.inicio())) {
            throw ReservaRechazadaException.de(CodigoReserva.CANCELACION_FUERA_DE_PLAZO,
                    "Faltan menos de 24 horas para el turno: comunicate con el consultorio");
        }
        return turno;
    }

    /** El DNI sale del paciente de la sesión, nunca del pedido: la doble verificación de la agenda se mantiene. */
    static String dniDe(ReservasPaciente reservas, Long pacienteId) {
        return reservas.dni(pacienteId)
                .orElseThrow(() -> new IllegalStateException("La sesión apunta a un paciente que no existe"));
    }

    private static String normalizar(String numeroReserva) {
        return numeroReserva == null ? "" : numeroReserva.trim().toUpperCase(Locale.ROOT);
    }
}
