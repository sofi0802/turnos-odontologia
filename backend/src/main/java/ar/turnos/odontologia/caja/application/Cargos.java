package ar.turnos.odontologia.caja.application;

import ar.turnos.odontologia.caja.domain.CargoCalculado;
import ar.turnos.odontologia.caja.application.Vistas.CargoVista;
import ar.turnos.odontologia.caja.application.Vistas.PendienteVista;
import ar.turnos.odontologia.caja.application.Vistas.SinPrecioVista;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Puerto de los cargos (design D2 y D4). */
public interface Cargos {

    /** Inserta el cargo de un turno; no hace nada si el turno ya tiene uno ({@code ON CONFLICT}). */
    boolean insertarSiNoExiste(long turnoId, long pacienteId, long prestacionId, LocalDate fecha, CargoCalculado cargo,
                               Instant ahora);

    /** El cargo con {@code FOR UPDATE} y lo pagado (cobros no anulados), para cobrarlo. */
    Optional<CargoBloqueado> bloquear(long cargoId);

    List<PendienteVista> pendientes();

    List<SinPrecioVista> sinPrecio();

    /** Los cargos del paciente, del más nuevo al más viejo, sin sus cobros. */
    List<CargoVista> delPaciente(long pacienteId);

    Optional<String> nombrePaciente(long pacienteId);

    record CargoBloqueado(long id, BigDecimal copago, BigDecimal pagado) {

        public BigDecimal saldo() {
            return copago.subtract(pagado);
        }
    }
}
