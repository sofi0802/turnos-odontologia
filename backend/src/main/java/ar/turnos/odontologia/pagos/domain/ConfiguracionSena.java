package ar.turnos.odontologia.pagos.domain;

/**
 * Configuración de las señas (RN-SE-01): vienen desactivadas, con el 30 % y 30 minutos. El plazo mínimo de 5 minutos
 * evita que la seña venza antes de que el paciente llegue a Mercado Pago (design D9).
 */
public record ConfiguracionSena(boolean activas, int porcentaje, int plazoMinutos) {

    public static final ConfiguracionSena INICIAL = new ConfiguracionSena(false, 30, 30);

    public ConfiguracionSena {
        if (porcentaje < 1 || porcentaje > 100) {
            throw new DatoInvalidoException("porcentaje", "Escribí un número entero de 1 a 100.");
        }
        if (plazoMinutos < 5 || plazoMinutos > 1440) {
            throw new DatoInvalidoException("plazoMinutos", "Escribí un plazo de 5 a 1440 minutos.");
        }
    }
}
