package ar.turnos.odontologia.agenda.listaespera.domain;

/** Códigos estables de los rechazos de la lista de espera (C-15), con su estado HTTP (regla dura 9). */
public enum CodigoListaEspera {
    DATOS_INVALIDOS(422),
    PLAZO_INVALIDO(422),
    YA_EN_LISTA_DE_ESPERA(409),
    SIN_CONSENTIMIENTO(409),
    RECURSO_INACTIVO(409),
    PROFESIONAL_NO_HABILITADO(409),
    INSCRIPCION_NO_ACTIVA(409),
    OFERTA_VENCIDA(409),
    OFERTA_YA_RESPONDIDA(409),
    OFERTA_NO_DISPONIBLE(409),
    RECURSO_NO_ENCONTRADO(404),
    OFERTA_INEXISTENTE(404);

    private final int estadoHttp;

    CodigoListaEspera(int estadoHttp) {
        this.estadoHttp = estadoHttp;
    }

    public int estadoHttp() {
        return estadoHttp;
    }
}
