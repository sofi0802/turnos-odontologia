package ar.turnos.odontologia.clinica.domain;

import java.util.Optional;

/**
 * Una plantilla de consentimiento con su última versión (C-27, RN-CL-09). Decide si una edición crea una versión nueva
 * y si se puede firmar sobre una versión concreta. Las versiones anteriores no se modifican nunca (design D5).
 *
 * @param versionActual el número de la última versión
 * @param contenido     el título y el texto de la última versión
 */
public record PlantillaConsentimiento(long id, boolean activa, int versionActual, VersionPlantilla contenido) {

    /**
     * @param versionBase la versión sobre la que trabajó el {@code ADMIN}
     * @return la versión nueva, o vacío si el título y el texto normalizados son iguales a los de la última versión
     * @throws ClinicaException {@code DATOS_INVALIDOS} si falta la versión base o el contenido no es válido, y
     *                          {@code VERSION_DESACTUALIZADA} si la versión base no es la última (design D6)
     */
    public Optional<VersionPlantilla> editar(Integer versionBase, String titulo, String texto) {
        if (versionBase == null) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "Falta la versión sobre la que se edita");
        }
        VersionPlantilla nueva = VersionPlantilla.nueva(titulo, texto);
        if (versionBase != versionActual) {
            throw desactualizada();
        }
        return nueva.equals(contenido) ? Optional.empty() : Optional.of(nueva);
    }

    /**
     * El paciente firma exactamente el texto que se le mostró: tiene que ser la última versión de una plantilla activa
     * (design D2 y D6).
     *
     * @throws ClinicaException {@code DATOS_INVALIDOS} si falta el número, {@code PLANTILLA_INACTIVA} o
     *                          {@code VERSION_DESACTUALIZADA}
     */
    public void exigirFirmable(Integer versionNumero) {
        if (versionNumero == null) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "Falta la versión que se firma");
        }
        if (!activa) {
            throw new ClinicaException(CodigoClinica.PLANTILLA_INACTIVA, "La plantilla está desactivada");
        }
        if (versionNumero != versionActual) {
            throw desactualizada();
        }
    }

    private static ClinicaException desactualizada() {
        return new ClinicaException(CodigoClinica.VERSION_DESACTUALIZADA,
                "La plantilla cambió: hay que volver a cargar la última versión");
    }
}
