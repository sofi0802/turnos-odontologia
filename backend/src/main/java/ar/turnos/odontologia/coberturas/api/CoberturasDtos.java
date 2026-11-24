package ar.turnos.odontologia.coberturas.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Cuerpos de entrada de la API de coberturas. Los límites siguen las columnas de V12 (spec: validación). */
final class CoberturasDtos {

    private CoberturasDtos() {
    }

    /** El tipo se valida con un patrón para responder {@code DATOS_INVALIDOS} con el campo {@code tipo}. */
    record ObraSocialSolicitud(
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 20) String sigla,
            @NotBlank @Pattern(regexp = "OBRA_SOCIAL|PREPAGA") String tipo) {
    }

    record PlanSolicitud(@NotBlank @Size(max = 80) String nombre) {
    }

    record CodigoSolicitud(
            @NotBlank @Size(max = 20) String codigo,
            @NotBlank @Size(max = 200) String descripcion,
            Long prestacionId) {
    }

    /**
     * El número y las fechas se validan en el dominio para responder el código y el campo de la spec sin repetir el
     * valor recibido (RN-OS-07).
     */
    record AfiliacionSolicitud(@NotNull Long planId, String numeroAfiliado, LocalDate vigenteDesde,
                               LocalDate vigenteHasta) {
    }

    /** Corrige solo errores de carga: el plan, el número y el inicio (design D5). */
    record CorreccionSolicitud(@NotNull Long planId, String numeroAfiliado, LocalDate vigenteDesde) {
    }

    record CierreSolicitud(@NotNull LocalDate vigenteHasta) {
    }

    /** El precio se valida en el dominio ({@code Precio}) para responder el campo sin repetir el valor. */
    record ArancelSolicitud(@NotNull BigDecimal precio) {
    }

    /**
     * El porcentaje llega como {@code BigDecimal}: con {@code Integer}, Jackson convertiría {@code 50.5} en {@code 50}
     * en silencio (design D3). {@code @Digits(fraction = 0)} rechaza los decimales.
     */
    record PorcentajeSolicitud(@NotNull @Digits(integer = 3, fraction = 0) @Min(0) @Max(100) BigDecimal porcentaje) {
    }

    /**
     * Pedido de emisión (design D7). No tiene campo de profesional: sale de la sesión, y un {@code profesionalId} que
     * llegue se ignora. La pieza y la cara se validan en el dominio para responder {@code items[i].pieza} o
     * {@code items[i].cara} sin repetir el valor (datos de salud).
     */
    record PresupuestoSolicitud(@NotNull @Size(min = 1, max = 50) List<@NotNull @Valid ItemSolicitud> items) {
    }

    record ItemSolicitud(@NotNull Long prestacionId, Long nomencladorId, Integer pieza, String cara,
                         @NotNull @Min(1) @Max(99) Integer cantidad) {
    }
}
