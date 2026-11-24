package ar.turnos.odontologia.coberturas.domain;

/**
 * Códigos estables de los errores del módulo de coberturas (C-18, design D6). Los conflictos con el estado de los
 * datos son {@code 409}, los datos de entrada inválidos {@code 422} y {@code RECURSO_NO_ENCONTRADO} es {@code 404}.
 */
public enum CodigoCobertura {
    OBRA_SOCIAL_EXISTENTE(Estado.CONFLICTO),
    PLAN_EXISTENTE(Estado.CONFLICTO),
    CODIGO_NOMENCLADOR_EXISTENTE(Estado.CONFLICTO),
    VIGENCIA_SUPERPUESTA(Estado.CONFLICTO),
    AFILIADO_DUPLICADO(Estado.CONFLICTO),
    PLAN_INACTIVO(Estado.CONFLICTO),
    OBRA_SOCIAL_INACTIVA(Estado.CONFLICTO),
    AFILIACION_CERRADA(Estado.CONFLICTO),
    // C-19 (design D9): el pedido está bien formado y choca con el estado del catálogo o del presupuesto.
    ARANCEL_INEXISTENTE(Estado.CONFLICTO),
    PRESTACION_INACTIVA(Estado.CONFLICTO),
    PRESUPUESTO_VENCIDO(Estado.CONFLICTO),
    PRESUPUESTO_CERRADO(Estado.CONFLICTO),
    RECURSO_NO_ENCONTRADO(Estado.NO_ENCONTRADO),
    DATOS_INVALIDOS(Estado.INVALIDO),
    VIGENCIA_INVALIDA(Estado.INVALIDO),
    PRESTACION_INEXISTENTE(Estado.INVALIDO),
    CODIGO_NOMENCLADOR_REQUERIDO(Estado.INVALIDO),
    CODIGO_NOMENCLADOR_INVALIDO(Estado.INVALIDO);

    /** La clase de respuesta HTTP, sin depender de Spring en el dominio. */
    public enum Estado { CONFLICTO, NO_ENCONTRADO, INVALIDO }

    private final Estado estado;

    CodigoCobertura(Estado estado) {
        this.estado = estado;
    }

    public Estado estado() {
        return estado;
    }
}
