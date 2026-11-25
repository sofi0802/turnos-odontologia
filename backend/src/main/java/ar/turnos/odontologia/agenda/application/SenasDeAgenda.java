package ar.turnos.odontologia.agenda.application;

/**
 * Puerto de las señas que consulta la agenda al cancelar (C-23, RN-SE-06): con la seña pagada, el personal tiene que
 * indicar la causa. Lo implementa el módulo de pagos; la agenda no depende de él.
 */
public interface SenasDeAgenda {

    boolean tieneSenaPagada(Long turnoId);

    /** Sin módulo de pagos: ningún turno tiene seña. */
    SenasDeAgenda NINGUNA = turnoId -> false;
}
