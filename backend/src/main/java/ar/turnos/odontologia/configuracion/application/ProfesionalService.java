package ar.turnos.odontologia.configuracion.application;

import ar.turnos.odontologia.configuracion.application.Vistas.PrestacionVista;
import ar.turnos.odontologia.configuracion.application.Vistas.ProfesionalVista;
import ar.turnos.odontologia.configuracion.domain.CodigoError;
import ar.turnos.odontologia.configuracion.domain.ConfiguracionException;
import ar.turnos.odontologia.configuracion.infrastructure.PrestacionRepository;
import ar.turnos.odontologia.configuracion.infrastructure.ProfesionalRepository;
import ar.turnos.odontologia.configuracion.infrastructure.SillonRepository;
import ar.turnos.odontologia.shared.persistence.PrestacionEntity;
import ar.turnos.odontologia.shared.persistence.ProfesionalEntity;
import ar.turnos.odontologia.shared.persistence.SillonEntity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ABM de profesionales (US-001), especialidades (RN-AG-15) y sillón habitual (US-018). */
@Service
@Transactional
public class ProfesionalService {

    private static final String RECURSO = "el profesional";

    private final ProfesionalRepository profesionales;
    private final SillonRepository sillones;
    private final PrestacionRepository prestaciones;

    public ProfesionalService(ProfesionalRepository profesionales, SillonRepository sillones,
                              PrestacionRepository prestaciones) {
        this.profesionales = profesionales;
        this.sillones = sillones;
        this.prestaciones = prestaciones;
    }

    @Transactional(readOnly = true)
    public List<ProfesionalVista> listar() {
        return profesionales.findAllByOrderByApellidoAscNombreAsc().stream().map(ProfesionalVista::de).toList();
    }

    @Transactional(readOnly = true)
    public ProfesionalVista obtener(Long id) {
        return ProfesionalVista.de(buscar(id));
    }

    public ProfesionalVista crear(String nombre, String apellido, String matricula) {
        if (profesionales.existsByMatricula(matricula)) {
            throw matriculaDuplicada(matricula);
        }
        return ProfesionalVista.de(profesionales.save(new ProfesionalEntity(nombre, apellido, matricula)));
    }

    public ProfesionalVista actualizar(Long id, String nombre, String apellido, String matricula) {
        ProfesionalEntity profesional = buscar(id);
        if (profesionales.existsByMatriculaAndIdNot(matricula, id)) {
            throw matriculaDuplicada(matricula);
        }
        profesional.actualizar(nombre, apellido, matricula);
        return ProfesionalVista.de(profesional);
    }

    public ProfesionalVista activar(Long id) {
        ProfesionalEntity profesional = buscar(id);
        profesional.activar();
        return ProfesionalVista.de(profesional);
    }

    public ProfesionalVista desactivar(Long id) {
        ProfesionalEntity profesional = buscar(id);
        profesional.desactivar();
        return ProfesionalVista.de(profesional);
    }

    @Transactional(readOnly = true)
    public List<PrestacionVista> especialidades(Long id) {
        return buscar(id).getEspecialidades().stream()
                .sorted(Comparator.comparing(PrestacionEntity::getNombre))
                .map(PrestacionVista::de)
                .toList();
    }

    /** Reemplaza todas las especialidades. Si alguna prestación es inválida, no cambia nada. */
    public List<PrestacionVista> definirEspecialidades(Long id, List<Long> prestacionIds) {
        ProfesionalEntity profesional = buscar(id);
        Set<PrestacionEntity> nuevas = new HashSet<>();
        for (Long prestacionId : prestacionIds) {
            PrestacionEntity prestacion = prestaciones.findById(prestacionId)
                    .orElseThrow(() -> ConfiguracionException.noEncontrado("la prestación", prestacionId));
            if (!prestacion.isActivo()) {
                throw new ConfiguracionException(CodigoError.PRESTACION_INACTIVA,
                        "La prestación " + prestacion.getNombre() + " está inactiva");
            }
            nuevas.add(prestacion);
        }
        profesional.reemplazarEspecialidades(nuevas);
        return especialidades(id);
    }

    /** Asigna un sillón habitual activo, o lo quita si {@code sillonId} es {@code null}. */
    public ProfesionalVista asignarSillonHabitual(Long id, Long sillonId) {
        ProfesionalEntity profesional = buscar(id);
        SillonEntity sillon = null;
        if (sillonId != null) {
            sillon = sillones.findById(sillonId)
                    .orElseThrow(() -> ConfiguracionException.noEncontrado("el sillón", sillonId));
            if (!sillon.isActivo()) {
                throw new ConfiguracionException(CodigoError.SILLON_INACTIVO,
                        "El sillón " + sillon.getNombre() + " está inactivo");
            }
            // RN-RO-09: un sillón es el habitual de un solo profesional, activo o inactivo
            if (profesionales.existsBySillonHabitualIdAndIdNot(sillonId, id)) {
                throw new ConfiguracionException(CodigoError.SILLON_HABITUAL_OCUPADO,
                        "El sillón " + sillon.getNombre() + " ya es el habitual de otro profesional");
            }
        }
        profesional.asignarSillonHabitual(sillon);
        return ProfesionalVista.de(profesional);
    }

    private ProfesionalEntity buscar(Long id) {
        return profesionales.findById(id).orElseThrow(() -> ConfiguracionException.noEncontrado(RECURSO, id));
    }

    private static ConfiguracionException matriculaDuplicada(String matricula) {
        return new ConfiguracionException(CodigoError.MATRICULA_DUPLICADA,
                "Ya existe un profesional con la matrícula " + matricula);
    }
}
