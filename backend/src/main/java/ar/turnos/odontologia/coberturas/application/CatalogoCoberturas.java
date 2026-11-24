package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.CodigoNomenclador;
import ar.turnos.odontologia.coberturas.domain.ObraSocial;
import ar.turnos.odontologia.coberturas.domain.Plan;
import java.util.List;
import java.util.Optional;

/** Puerto de salida: almacenamiento del catálogo de obras sociales, planes y nomenclador (C-18, design D1). */
public interface CatalogoCoberturas {

    /** Ordenadas por nombre sin distinguir mayúsculas. */
    List<ObraSocial> obrasSociales();

    Optional<ObraSocial> obraSocial(Long id);

    /** Si hay otra obra social con ese nombre sin distinguir mayúsculas; {@code excluirId} es la que se modifica. */
    boolean existeObraSocial(String nombre, Long excluirId);

    /** Inserta si {@code id} es nulo; si no, reemplaza. */
    ObraSocial guardar(ObraSocial obraSocial);

    /** Todos los planes, ordenados por nombre. */
    List<Plan> planes();

    List<Plan> planes(Long obraSocialId);

    /** El plan buscado junto con su obra social: si no coinciden, no existe. */
    Optional<Plan> plan(Long obraSocialId, Long planId);

    Optional<Plan> plan(Long planId);

    boolean existePlan(Long obraSocialId, String nombre, Long excluirId);

    Plan guardar(Plan plan);

    /** Ordenado por código sin distinguir mayúsculas. */
    List<CodigoNomenclador> nomenclador();

    Optional<CodigoNomenclador> codigoNomenclador(Long id);

    /** Si hay otro código igual sin distinguir mayúsculas; {@code excluirId} es el que se modifica. */
    boolean existeCodigo(String codigo, Long excluirId);

    CodigoNomenclador guardar(CodigoNomenclador codigo);
}
