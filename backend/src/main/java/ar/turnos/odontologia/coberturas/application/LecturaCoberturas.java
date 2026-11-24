package ar.turnos.odontologia.coberturas.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de lectura de las afiliaciones con los datos de su plan y de su obra social (C-18, design D1): un solo
 * {@code JOIN} por consulta en lugar de armar la vista con varios puertos.
 */
public interface LecturaCoberturas {

    /** La afiliación con su plan y su obra social, buscada junto con su paciente. */
    Optional<AfiliacionDetalle> detalle(Long pacienteId, Long afiliacionId);

    /** La afiliación del paciente que contiene la fecha (días inclusivos); como mucho una (RN-OS-05). */
    Optional<AfiliacionDetalle> vigenteEn(Long pacienteId, LocalDate fecha);

    /** Todas las afiliaciones del paciente, de la más reciente a la más antigua por inicio. */
    List<AfiliacionDetalle> historial(Long pacienteId);

    record AfiliacionDetalle(Long id, Long obraSocialId, String obraSocial, String sigla, String tipo, Long planId,
                             String plan, String numeroAfiliado, LocalDate vigenteDesde, LocalDate vigenteHasta) {}
}
