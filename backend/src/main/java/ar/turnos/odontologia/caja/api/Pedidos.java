package ar.turnos.odontologia.caja.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Pedidos de la caja (design D6): importes en pesos con hasta dos decimales, como número o como texto. */
final class Pedidos {

    private Pedidos() {
    }

    record AbrirCaja(
            @NotNull(message = "Es obligatorio.") @DecimalMin(value = "0.00", message = "No puede ser negativo.")
            @Digits(integer = 10, fraction = 2, message = "Tiene hasta dos decimales.") BigDecimal montoInicial) {}

    record CerrarCaja(
            @NotNull(message = "Es obligatorio.") @DecimalMin(value = "0.00", message = "No puede ser negativo.")
            @Digits(integer = 10, fraction = 2, message = "Tiene hasta dos decimales.") BigDecimal efectivoContado,
            @Size(max = 255, message = "Tiene hasta 255 caracteres.") String motivoDiferencia) {}

    record Cobrar(
            @NotNull(message = "Es obligatorio.")
            @DecimalMin(value = "0.01", message = "Tiene que ser mayor que cero.")
            @Digits(integer = 10, fraction = 2, message = "Tiene hasta dos decimales.") BigDecimal importe,
            @NotNull(message = "Es obligatorio.")
            @Pattern(regexp = "EFECTIVO|DEBITO|CREDITO|TRANSFERENCIA", message = "No es un medio de pago válido.")
            String medio) {}

    record Anular(
            @NotBlank(message = "Escribí el motivo de la anulación.")
            @Size(max = 255, message = "Tiene hasta 255 caracteres.") String motivo) {}
}
