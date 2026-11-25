package ar.turnos.odontologia.caja.application;

import ar.turnos.odontologia.caja.domain.MedioPago;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Lo que devuelven los casos de uso de la caja (design D9): records, nunca entidades. */
public final class Vistas {

    private Vistas() {
    }

    /** Los campos del cierre son {@code null} mientras la caja está abierta; {@code cobros} solo en la caja abierta. */
    public record CajaVista(long id, LocalDate fecha, BigDecimal montoInicial, String abiertaPor, Instant abiertaEn,
                            String cerradaPor, Instant cerradaEn, Map<MedioPago, BigDecimal> totales,
                            BigDecimal efectivoEsperado, BigDecimal efectivoContado, BigDecimal diferencia,
                            String motivoDiferencia, List<CobroVista> cobros) {}

    /** {@code paciente} es "Apellido, Nombre": la caja la ve solo el personal autorizado (RN-CA-08). */
    public record CobroVista(long id, long cargoId, String paciente, Instant registradoEn, MedioPago medio,
                             BigDecimal importe, String registradoPor, boolean anulado, String anuladoPor,
                             Instant anuladoEn, String motivoAnulacion) {}

    public record PendienteVista(long id, long pacienteId, String paciente, LocalDate fecha, Instant inicio,
                                 String prestacion, BigDecimal copago, BigDecimal pagado, BigDecimal saldo) {}

    public record SinPrecioVista(long turnoId, long pacienteId, String paciente, LocalDate fecha, Instant inicio,
                                 String prestacion) {}

    public record PendientesVista(List<PendienteVista> cargos, List<SinPrecioVista> sinPrecio) {}

    public record CargoVista(long id, LocalDate fecha, Instant inicio, String prestacion, BigDecimal precio,
                             int porcentajeCobertura, BigDecimal cobertura, BigDecimal copago, BigDecimal pagado,
                             BigDecimal saldo, List<CobroVista> cobros) {}

    public record CuentaCorrienteVista(long pacienteId, String paciente, List<CargoVista> cargos,
                                       BigDecimal saldoTotal) {}

    public record CobroRegistrado(CobroVista cobro, BigDecimal saldo) {}
}
