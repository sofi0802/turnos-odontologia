package ar.turnos.odontologia.pagos.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verificación de la firma de una notificación de Mercado Pago (RN-SE-05, design D5), según su documentación: el
 * encabezado {@code x-signature} trae {@code ts} y {@code v1}; {@code v1} es el HMAC-SHA256 en hexadecimal, con la clave
 * secreta, del manifiesto {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}, omitiendo el par que no vino. La
 * comparación es en tiempo constante.
 */
public final class FirmaMercadoPago {

    private FirmaMercadoPago() {
    }

    public static boolean valida(String xSignature, String xRequestId, String dataId, String clave) {
        if (xSignature == null || xSignature.isBlank() || clave == null || clave.isBlank()) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] claveValor = parte.split("=", 2);
            if (claveValor.length != 2) {
                continue;
            }
            String nombre = claveValor[0].trim();
            String valor = claveValor[1].trim();
            if (nombre.equals("ts")) {
                ts = valor;
            } else if (nombre.equals("v1")) {
                v1 = valor;
            }
        }
        if (ts == null || ts.isEmpty() || v1 == null || v1.isEmpty()) {
            return false;
        }
        byte[] esperado = hmac(manifiesto(dataId, xRequestId, ts), clave).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(esperado, v1.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    /** El encabezado {@code x-signature} que mandaría Mercado Pago; lo usan los tests y la verificación manual. */
    public static String firmar(String dataId, String xRequestId, String ts, String clave) {
        return "ts=" + ts + ",v1=" + hmac(manifiesto(dataId, xRequestId, ts), clave);
    }

    static String manifiesto(String dataId, String xRequestId, String ts) {
        StringBuilder texto = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            texto.append("id:").append(dataId).append(';');
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            texto.append("request-id:").append(xRequestId).append(';');
        }
        if (ts != null && !ts.isBlank()) {
            texto.append("ts:").append(ts).append(';');
        }
        return texto.toString();
    }

    private static String hmac(String mensaje, String clave) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no está disponible", e);
        }
    }
}
