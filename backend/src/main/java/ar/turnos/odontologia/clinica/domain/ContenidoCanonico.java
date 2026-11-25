package ar.turnos.odontologia.clinica.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Contenido canónico, versión 1, de un consentimiento firmado y su SHA-256 (C-27, RN-CL-10, design D4). Es una función
 * pura: los mismos datos dan siempre los mismos bytes.
 *
 * <pre>
 * consentimiento-informado/v1\n
 * &lt;campo&gt;=&lt;n&gt;:&lt;valor&gt;\n   (una línea por campo, en el orden de los componentes)
 * </pre>
 *
 * <p>{@code <n>} es el largo de {@code <valor>} en bytes UTF-8: así ningún dato se puede correr al campo siguiente
 * sin cambiar el hash, aunque el texto tenga saltos de línea. El instante va en UTC con microsegundos, la precisión de
 * {@code timestamptz}. La revocación no entra: el documento firmado sigue verificando después de revocarlo.
 *
 * @param firmadoEn ya truncado a microsegundos
 * @param ipOrigen  el texto guardado tal cual
 * @param dispositivo el texto guardado tal cual, vacío si no vino
 */
public record ContenidoCanonico(long pacienteId, long plantillaVersionId, String titulo, String texto,
                                String firmanteNombre, String firmanteDni, long profesionalId, Instant firmadoEn,
                                String ipOrigen, String dispositivo, String tipoFirma) {

    public static final int FORMATO = 1;
    public static final String ALGORITMO = "SHA-256";
    private static final String ENCABEZADO = "consentimiento-informado/v1\n";
    private static final DateTimeFormatter INSTANTE =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);

    public ContenidoCanonico {
        Objects.requireNonNull(titulo, "titulo");
        Objects.requireNonNull(texto, "texto");
        Objects.requireNonNull(firmanteNombre, "firmanteNombre");
        Objects.requireNonNull(firmanteDni, "firmanteDni");
        Objects.requireNonNull(firmadoEn, "firmadoEn");
        Objects.requireNonNull(ipOrigen, "ipOrigen");
        Objects.requireNonNull(dispositivo, "dispositivo");
        Objects.requireNonNull(tipoFirma, "tipoFirma");
    }

    /** Los bytes UTF-8 del formato versión 1. */
    public byte[] bytes() {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes(ENCABEZADO.getBytes(StandardCharsets.UTF_8));
        campo(salida, "paciente_id", Long.toString(pacienteId));
        campo(salida, "plantilla_version_id", Long.toString(plantillaVersionId));
        campo(salida, "titulo", titulo);
        campo(salida, "texto", texto);
        campo(salida, "firmante_nombre", firmanteNombre);
        campo(salida, "firmante_dni", firmanteDni);
        campo(salida, "profesional_id", Long.toString(profesionalId));
        campo(salida, "firmado_en", INSTANTE.format(firmadoEn));
        campo(salida, "ip_origen", ipOrigen);
        campo(salida, "dispositivo", dispositivo);
        campo(salida, "tipo_firma", tipoFirma);
        return salida.toByteArray();
    }

    /** SHA-256 de {@link #bytes()} en hexadecimal en minúsculas (64 caracteres). */
    public String sha256() {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(ALGORITMO).digest(bytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void campo(ByteArrayOutputStream salida, String nombre, String valor) {
        byte[] bytes = valor.getBytes(StandardCharsets.UTF_8);
        salida.writeBytes((nombre + "=" + bytes.length + ":").getBytes(StandardCharsets.UTF_8));
        salida.writeBytes(bytes);
        salida.write('\n');
    }
}
