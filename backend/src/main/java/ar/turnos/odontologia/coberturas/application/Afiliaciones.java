package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.Afiliacion;
import ar.turnos.odontologia.coberturas.domain.NumeroAfiliado;
import java.util.List;
import java.util.Optional;

/** Puerto de salida: almacenamiento de las afiliaciones de los pacientes (C-18, design D4). */
public interface Afiliaciones {

    List<Afiliacion> delPaciente(Long pacienteId);

    /** La afiliación buscada junto con su paciente: si no coinciden, no existe. */
    Optional<Afiliacion> delPaciente(Long pacienteId, Long afiliacionId);

    /** Si el número ya lo usa otro paciente en la misma obra social, sin distinguir mayúsculas (RN-OS-03). */
    boolean numeroEnUsoPorOtroPaciente(Long obraSocialId, NumeroAfiliado numero, Long pacienteId);

    /**
     * Inserta si {@code id} es nulo; si no, reemplaza. Escribe en el acto ({@code saveAndFlush}), así la violación de
     * una {@code EXCLUDE} de la base salta dentro del caso de uso.
     */
    Afiliacion guardar(Afiliacion afiliacion);
}
