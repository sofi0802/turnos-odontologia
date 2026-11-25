package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Candidato;
import java.time.Instant;
import java.util.List;

/**
 * Puerto de lectura de los turnos que pueden entrar en una liquidación (C-20, design D4 paso 2). El adaptador solo lee
 * {@code turno}, {@code paciente}, {@code afiliacion_paciente}, {@code plan}, {@code prestacion}, {@code nomenclador},
 * {@code cobertura_plan}, {@code arancel} y las líneas de liquidación: no usa código de la agenda ni de pacientes.
 */
public interface TurnosLiquidables {

    /**
     * Los turnos {@code ATENDIDO} que empezaron antes de {@code finExclusivo} (sin límite inferior), de pacientes con
     * una afiliación a la obra social vigente en la fecha del turno, que no están en una liquidación distinta de
     * {@code liquidacionId} ni quitados a mano de esa liquidación. Ordenados por inicio y por id.
     */
    List<Candidato> candidatos(Long obraSocialId, Instant finExclusivo, Long liquidacionId);
}
