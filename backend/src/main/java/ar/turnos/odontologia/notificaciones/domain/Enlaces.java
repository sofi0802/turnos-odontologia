package ar.turnos.odontologia.notificaciones.domain;

import java.util.Objects;

/**
 * Los links de los mensajes: la dirección base del frontend más una ruta. Los de cancelar y reprogramar apuntan a las
 * pantallas del paciente y son configurables ({@code NOTIFICACIONES_RUTA_CANCELAR},
 * {@code NOTIFICACIONES_RUTA_REPROGRAMAR}). Nunca llevan el número de reserva, el DNI, el email, el teléfono ni el id
 * del turno: el número de reserva es la credencial para cancelar o reprogramar (RN-AG-07), así que el paciente inicia
 * sesión e ingresa el suyo en la pantalla. Solo el link de confirmar lleva el token de un solo uso.
 */
public record Enlaces(String baseUrl, String rutaCancelar, String rutaReprogramar) {

    public static final String RUTA_CANCELAR = "/mis-turnos/cancelar";
    public static final String RUTA_REPROGRAMAR = "/mis-turnos/reprogramar";
    static final String RUTA_CONFIRMAR = "/confirmar/";

    public Enlaces {
        baseUrl = sinBarraFinal(Objects.requireNonNull(baseUrl, "baseUrl"));
        rutaCancelar = conBarraInicial(Objects.requireNonNull(rutaCancelar, "rutaCancelar"));
        rutaReprogramar = conBarraInicial(Objects.requireNonNull(rutaReprogramar, "rutaReprogramar"));
    }

    /** Con las rutas por defecto. */
    public static Enlaces de(String baseUrl) {
        return new Enlaces(baseUrl, RUTA_CANCELAR, RUTA_REPROGRAMAR);
    }

    public String confirmar(String token) {
        return baseUrl + RUTA_CONFIRMAR + Objects.requireNonNull(token, "token");
    }

    public String cancelar() {
        return baseUrl + rutaCancelar;
    }

    public String reprogramar() {
        return baseUrl + rutaReprogramar;
    }

    private static String sinBarraFinal(String valor) {
        return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor;
    }

    private static String conBarraInicial(String valor) {
        return valor.startsWith("/") ? valor : "/" + valor;
    }
}
