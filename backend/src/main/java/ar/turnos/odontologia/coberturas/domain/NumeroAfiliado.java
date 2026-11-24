package ar.turnos.odontologia.coberturas.domain;

import java.util.regex.Pattern;

/**
 * Número de afiliado (RN-OS-03): de 1 a 30 caracteres entre letras, números, {@code -}, {@code /} y {@code .},
 * después de quitar los espacios de los extremos. Es un dato personal: {@code toString} no lo muestra y el mensaje
 * de error no lo repite (RN-OS-07).
 */
public record NumeroAfiliado(String valor) {

    private static final Pattern FORMATO = Pattern.compile("[A-Za-z0-9./-]{1,30}");

    public NumeroAfiliado {
        if (valor == null || !FORMATO.matcher(valor).matches()) {
            throw invalido();
        }
    }

    /** Quita los espacios de los extremos y valida el formato. */
    public static NumeroAfiliado de(String texto) {
        if (texto == null) {
            throw invalido();
        }
        return new NumeroAfiliado(texto.strip());
    }

    private static CoberturaException invalido() {
        return CoberturaException.datosInvalidos("numeroAfiliado",
                "El número de afiliado debe tener de 1 a 30 caracteres: letras, números, guion, barra o punto");
    }

    @Override
    public String toString() {
        return "NumeroAfiliado[***]";
    }
}
