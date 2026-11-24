package ar.turnos.odontologia.reportes.domain;

/** Período de reporte inválido (RN-RE-03); {@code campo} es el parámetro a corregir. */
public class PeriodoInvalidoException extends RuntimeException {

    private final String campo;

    public PeriodoInvalidoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
