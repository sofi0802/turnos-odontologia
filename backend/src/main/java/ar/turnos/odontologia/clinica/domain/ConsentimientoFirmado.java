package ar.turnos.odontologia.clinica.domain;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Un consentimiento recién firmado en el consultorio, listo para guardar (C-27, RN-CL-10, design D1, D3, D4 y D7). Es
 * una <strong>firma electrónica</strong> (Ley 25.506, art. 5), no una firma digital: no hay certificado. Lleva su propia
 * copia del título y del texto de la versión firmada, la evidencia y el hash del contenido canónico. Una vez guardado no
 * se modifica (design D5).
 *
 * @param firmadoEn el instante del servidor, truncado a microsegundos (la precisión de {@code timestamptz})
 */
public record ConsentimientoFirmado(long pacienteId, long plantillaVersionId, long profesionalId, String titulo,
                                    String texto, String firmanteNombre, String firmanteDni, Instant firmadoEn,
                                    String ipOrigen, String dispositivo) {

    public static final String TIPO_FIRMA = "ELECTRONICA";

    /**
     * @param contenido el título y el texto de la versión firmada, ya normalizados al crearla
     * @param reloj     el reloj del servidor: la fecha nunca viene del pedido
     */
    public static ConsentimientoFirmado firmar(long pacienteId, Firmante firmante, long plantillaVersionId,
                                               VersionPlantilla contenido, long profesionalId,
                                               EvidenciaFirma evidencia, Clock reloj) {
        Objects.requireNonNull(firmante, "firmante");
        Objects.requireNonNull(contenido, "contenido");
        Objects.requireNonNull(evidencia, "evidencia");
        Instant ahora = Instant.now(reloj).truncatedTo(ChronoUnit.MICROS);
        return new ConsentimientoFirmado(pacienteId, plantillaVersionId, profesionalId, contenido.titulo(),
                contenido.texto(), firmante.nombre(), firmante.dni(), ahora, evidencia.ip(), evidencia.dispositivo());
    }

    public ContenidoCanonico contenidoCanonico() {
        return new ContenidoCanonico(pacienteId, plantillaVersionId, titulo, texto, firmanteNombre, firmanteDni,
                profesionalId, firmadoEn, ipOrigen, dispositivo, TIPO_FIRMA);
    }

    /** SHA-256 del contenido canónico versión 1. */
    public String hash() {
        return contenidoCanonico().sha256();
    }

    /**
     * El nombre y el DNI que escribe el paciente, con su aceptación (design D3). El nombre no se compara con el del
     * paciente (PA-46); el DNI sí. Los mensajes de error son fijos: nunca repiten el nombre ni ningún DNI (design D11).
     */
    public record Firmante(String nombre, String dni) {

        public static final int LARGO_MINIMO_NOMBRE = 3;
        public static final int LARGO_MAXIMO_NOMBRE = 200;
        private static final Pattern ESPACIOS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
        private static final Pattern SEPARADORES_DNI = Pattern.compile("[.\\s]", Pattern.UNICODE_CHARACTER_CLASS);
        private static final Pattern DNI = Pattern.compile("[0-9]{7,8}");

        /**
         * @param dniPaciente el DNI registrado del paciente, que se normaliza igual que el escrito
         * @throws ClinicaException {@code DATOS_INVALIDOS} sin aceptación, con un nombre de menos de 3 o más de 200
         *                          caracteres o con un DNI que no queda en 7 u 8 dígitos; {@code FIRMANTE_NO_COINCIDE}
         *                          si el DNI escrito no es el del paciente
         */
        public static Firmante de(String nombre, String dni, Boolean aceptado, String dniPaciente) {
            if (!Boolean.TRUE.equals(aceptado)) {
                throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "Falta la aceptación del consentimiento");
            }
            String elNombre = nombre(nombre);
            int largo = elNombre.codePointCount(0, elNombre.length());
            if (largo < LARGO_MINIMO_NOMBRE || largo > LARGO_MAXIMO_NOMBRE) {
                throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS,
                        "El nombre del firmante es obligatorio y tiene de 3 a 200 caracteres");
            }
            String elDni = dni(dni);
            if (!DNI.matcher(elDni).matches()) {
                throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "El DNI del firmante tiene 7 u 8 dígitos");
            }
            if (!elDni.equals(dni(dniPaciente))) {
                throw new ClinicaException(CodigoClinica.FIRMANTE_NO_COINCIDE,
                        "El DNI del firmante no coincide con el del paciente");
            }
            return new Firmante(elNombre, elDni);
        }

        private static String nombre(String nombre) {
            if (nombre == null) {
                return "";
            }
            String nfc = Normalizer.normalize(nombre, Normalizer.Form.NFC);
            return ESPACIOS.matcher(nfc).replaceAll(" ").strip();
        }

        private static String dni(String dni) {
            return dni == null ? "" : SEPARADORES_DNI.matcher(dni).replaceAll("");
        }
    }
}
