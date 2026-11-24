package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.Afiliaciones;
import ar.turnos.odontologia.coberturas.domain.Afiliacion;
import ar.turnos.odontologia.coberturas.domain.NumeroAfiliado;
import ar.turnos.odontologia.coberturas.domain.Vigencia;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Adaptador de {@link Afiliaciones} con JPA. Escribe con {@code saveAndFlush} (design D4). */
@Repository
class AfiliacionesJpa implements Afiliaciones {

    private final AfiliacionJpaRepository afiliaciones;

    AfiliacionesJpa(AfiliacionJpaRepository afiliaciones) {
        this.afiliaciones = afiliaciones;
    }

    @Override
    public List<Afiliacion> delPaciente(Long pacienteId) {
        return afiliaciones.findByPacienteIdOrderByVigenteDesdeDescIdDesc(pacienteId).stream()
                .map(AfiliacionesJpa::aDominio).toList();
    }

    @Override
    public Optional<Afiliacion> delPaciente(Long pacienteId, Long afiliacionId) {
        return afiliaciones.findByIdAndPacienteId(afiliacionId, pacienteId).map(AfiliacionesJpa::aDominio);
    }

    @Override
    public boolean numeroEnUsoPorOtroPaciente(Long obraSocialId, NumeroAfiliado numero, Long pacienteId) {
        return afiliaciones.numeroEnUsoPorOtro(obraSocialId, numero.valor(), pacienteId);
    }

    @Override
    public Afiliacion guardar(Afiliacion afiliacion) {
        AfiliacionEntity entidad = afiliacion.id() == null
                ? new AfiliacionEntity(afiliacion.pacienteId())
                : afiliaciones.findById(afiliacion.id()).orElseThrow();
        entidad.reemplazar(afiliacion.planId(), afiliacion.obraSocialId(), afiliacion.numero().valor(),
                afiliacion.vigencia().desde(), afiliacion.vigencia().hasta());
        return aDominio(afiliaciones.saveAndFlush(entidad));
    }

    private static Afiliacion aDominio(AfiliacionEntity e) {
        return new Afiliacion(e.id(), e.pacienteId(), e.planId(), e.obraSocialId(),
                new NumeroAfiliado(e.numeroAfiliado()), new Vigencia(e.vigenteDesde(), e.vigenteHasta()));
    }
}
