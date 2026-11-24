package ar.turnos.odontologia.coberturas.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

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
}
