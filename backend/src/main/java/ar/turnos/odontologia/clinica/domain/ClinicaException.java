package ar.turnos.odontologia.clinica.domain;

import java.util.Objects;

/**
 * Error del módulo clínico con código estable. El mensaje puede nombrar una pieza o una cara, que no son datos de
 * salud por sí solas, pero nunca el texto de la ficha (design D9).
 */
public class ClinicaException extends RuntimeException {

    private final CodigoClinica codigo;

    public ClinicaException(CodigoClinica codigo, String mensaje) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
    }

    public CodigoClinica codigo() {
        return codigo;
    }

    public static ClinicaException pacienteInexistente() {
        return new ClinicaException(CodigoClinica.RECURSO_NO_ENCONTRADO, "No existe el paciente");
    }
}
