package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import ar.turnos.odontologia.coberturas.domain.CodigoCobertura;

/**
 * Los errores que se repiten en los casos de uso, con mensajes fijos: nunca incluyen el número de afiliado recibido ni
 * datos de otro paciente (RN-OS-07).
 */
final class CoberturasErrores {

    private CoberturasErrores() {
    }

    static CoberturaException planInexistente() {
        return CoberturaException.noEncontrado("No existe el plan");
    }

    static CoberturaException vigenciaSuperpuesta() {
        return new CoberturaException(CodigoCobertura.VIGENCIA_SUPERPUESTA,
                "La vigencia se superpone con otra afiliación del paciente");
    }

    static CoberturaException afiliadoDuplicado() {
        return new CoberturaException(CodigoCobertura.AFILIADO_DUPLICADO,
                "El número de afiliado ya está registrado en esa obra social");
    }
}
