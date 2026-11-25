package ar.turnos.odontologia.clinica.domain;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Evidencia del pedido de firma (C-27, RN-CL-10, design D7): la IP de origen tal como la ve el servidor y el
 * dispositivo según el {@code User-Agent}. El controller la arma desde el pedido HTTP; el dominio no conoce HTTP. Las
 * dos son contexto, no identifican a la persona, y nunca van a logs ni a mensajes de error (design D11).
 *
 * @param ip          una IPv4 o IPv6 literal, guardada exactamente como se hashea (sin reformatear)
 * @param dispositivo sin caracteres de control, hasta 512 caracteres, vacío si el pedido no lo trae
 */
public record EvidenciaFirma(String ip, String dispositivo) {

    public static final int LARGO_MAXIMO_IP = 45;
    public static final int LARGO_MAXIMO_DISPOSITIVO = 512;

    private static final String OCTETO = "(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final Pattern IPV4 = Pattern.compile(OCTETO + "(\\." + OCTETO + "){3}");
    /** Solo los caracteres de una IPv6 literal, con una zona opcional: nunca un nombre que haya que resolver. */
    private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*(%[0-9A-Za-z._-]+)?");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cc}");

    /**
     * @param ip        el {@code getRemoteAddr()} del pedido; nunca {@code X-Forwarded-For}
     * @param userAgent el encabezado {@code User-Agent}, o {@code null}
     * @throws IllegalArgumentException si la IP no es una dirección literal: la entrega el contenedor, no el usuario,
     *                                  así que es un error interno (el mensaje no la repite)
     */
    public static EvidenciaFirma de(String ip, String userAgent) {
        if (!esIpLiteral(ip)) {
            throw new IllegalArgumentException("La dirección de origen del pedido no es una IP literal");
        }
        return new EvidenciaFirma(ip, dispositivo(userAgent));
    }

    private static boolean esIpLiteral(String ip) {
        if (ip == null || ip.isEmpty() || ip.length() > LARGO_MAXIMO_IP) {
            return false;
        }
        if (IPV4.matcher(ip).matches()) {
            return true;
        }
        if (!IPV6.matcher(ip).matches()) {
            return false;
        }
        try {
            // Con ':' el JDK la interpreta como literal IPv6 y no consulta el DNS.
            return InetAddress.getByName(ip) != null;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static String dispositivo(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        String limpio = CONTROL.matcher(userAgent).replaceAll("");
        if (limpio.codePointCount(0, limpio.length()) <= LARGO_MAXIMO_DISPOSITIVO) {
            return limpio;
        }
        return limpio.substring(0, limpio.offsetByCodePoints(0, LARGO_MAXIMO_DISPOSITIVO));
    }
}
