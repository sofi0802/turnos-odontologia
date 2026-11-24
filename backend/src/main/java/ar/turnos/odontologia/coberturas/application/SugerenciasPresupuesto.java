package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.Vistas.SugerenciaVista;
import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Piezas y caras que el odontólogo puede presupuestar: las que tienen como último estado {@code A_TRATAR} o
 * {@code CARIES} (RN-PR-06, design D8). Es solo una consulta: no crea un presupuesto ni toca el odontograma. No escribe
 * logs: son datos de salud.
 */
@Service
@Transactional(readOnly = true)
public class SugerenciasPresupuesto {

    private final OdontogramaVigente odontograma;
    private final PacientesExistentes pacientes;

    public SugerenciasPresupuesto(OdontogramaVigente odontograma, PacientesExistentes pacientes) {
        this.odontograma = odontograma;
        this.pacientes = pacientes;
    }

    public List<SugerenciaVista> de(Long pacienteId) {
        if (!pacientes.existe(pacienteId)) {
            throw CoberturaException.noEncontrado("No existe el paciente");
        }
        return odontograma.aTratar(pacienteId).stream()
                .map(p -> new SugerenciaVista(p.pieza(), p.cara(), p.estado())).toList();
    }
}
