package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.domain.CaraDental;
import ar.turnos.odontologia.clinica.domain.EstadoPieza;
import ar.turnos.odontologia.clinica.domain.PiezaDental;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de salida: almacenamiento de la ficha básica y del odontograma (C-13, design D2 y D3). */
public interface RegistroClinico {

    boolean existePaciente(Long pacienteId);

    Optional<Ficha> ficha(Long pacienteId);

    /** Crea o reemplaza la ficha del paciente. */
    Ficha guardarFicha(Ficha ficha);

    /** Inserta los cambios como registros nuevos, todos con el mismo momento. Nunca modifica ni borra. */
    void registrarCambios(Long pacienteId, Long profesionalId, Instant momento, List<Cambio> cambios);

    /** El último registro de cada pieza y cara. */
    List<RegistroOdontograma> vigente(Long pacienteId);

    /** Todos los registros, del más nuevo al más viejo. */
    List<RegistroOdontograma> historial(Long pacienteId);

    record Ficha(Long pacienteId, String antecedentes, String alergias, String medicacion, String actualizadoPor,
                 Instant actualizadoEn) {}

    /** Un cambio ya validado por el dominio. {@code cara} nula es la pieza completa. */
    record Cambio(PiezaDental pieza, CaraDental cara, EstadoPieza estado) {}

    record RegistroOdontograma(Long id, int pieza, CaraDental cara, EstadoPieza estado, Long profesionalId,
                               String profesional, Instant registradoEn) {}
}
