package ar.turnos.odontologia.caja.application;

import ar.turnos.odontologia.caja.domain.Arqueo;
import ar.turnos.odontologia.caja.domain.MedioPago;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Puerto de las cajas (design D1 y D4). */
public interface Cajas {

    /** Inserta la caja abierta; vacío si ya había una abierta (índice único parcial, sin abortar la transacción). */
    Optional<Long> abrir(LocalDate fecha, BigDecimal montoInicial, String usuario, Instant ahora);

    Optional<CajaFila> abierta();

    /** La caja abierta con {@code FOR SHARE}: un cierre en curso espera y uno posterior la ve cerrada. */
    Optional<CajaFila> abiertaParaCobrar();

    Optional<CajaFila> porId(long id);

    /** La caja con {@code FOR UPDATE}, para cerrarla. */
    Optional<CajaFila> bloquearParaCerrar(long id);

    /** La caja con {@code FOR SHARE}, para anular uno de sus cobros. */
    Optional<CajaFila> bloquearParaAnular(long id);

    void cerrar(long id, String usuario, Instant ahora, Arqueo arqueo);

    /** Las cajas con fecha entre las dos dadas, de la más nueva a la más vieja. */
    List<CajaFila> entre(LocalDate desde, LocalDate hasta);

    /** Total de cobros no anulados por medio de pago, para cada caja pedida. */
    Map<Long, Map<MedioPago, BigDecimal>> totales(List<Long> cajaIds);

    record CajaFila(long id, LocalDate fecha, BigDecimal montoInicial, String abiertaPor, Instant abiertaEn,
                    String cerradaPor, Instant cerradaEn, BigDecimal efectivoContado, BigDecimal diferencia,
                    String motivoDiferencia) {

        public boolean cerrada() {
            return cerradaEn != null;
        }
    }
}
