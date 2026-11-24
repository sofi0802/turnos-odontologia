package ar.turnos.odontologia.coberturas.infrastructure;

import ar.turnos.odontologia.coberturas.application.CatalogoCoberturas;
import ar.turnos.odontologia.coberturas.domain.CodigoNomenclador;
import ar.turnos.odontologia.coberturas.domain.ObraSocial;
import ar.turnos.odontologia.coberturas.domain.Plan;
import ar.turnos.odontologia.coberturas.domain.TipoCobertura;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de {@link CatalogoCoberturas} con JPA. Se escribe con {@code saveAndFlush}, así una violación de los
 * índices únicos salta dentro del caso de uso y el manejador la traduce por nombre de restricción (design D4).
 */
@Repository
class CatalogoCoberturasJpa implements CatalogoCoberturas {

    private static final long NINGUNO = -1L;

    private final ObraSocialJpaRepository obrasSociales;
    private final PlanJpaRepository planes;
    private final NomencladorJpaRepository nomenclador;

    CatalogoCoberturasJpa(ObraSocialJpaRepository obrasSociales, PlanJpaRepository planes,
                          NomencladorJpaRepository nomenclador) {
        this.obrasSociales = obrasSociales;
        this.planes = planes;
        this.nomenclador = nomenclador;
    }

    @Override
    public List<ObraSocial> obrasSociales() {
        return obrasSociales.todasPorNombre().stream().map(CatalogoCoberturasJpa::aDominio).toList();
    }

    @Override
    public Optional<ObraSocial> obraSocial(Long id) {
        return obrasSociales.findById(id).map(CatalogoCoberturasJpa::aDominio);
    }

    @Override
    public boolean existeObraSocial(String nombre, Long excluirId) {
        return obrasSociales.existeNombre(nombre, excluirId == null ? NINGUNO : excluirId);
    }

    @Override
    public ObraSocial guardar(ObraSocial obra) {
        ObraSocialEntity entidad = obra.id() == null
                ? new ObraSocialEntity(obra.nombre(), obra.sigla(), obra.tipo().name(), obra.activa())
                : obrasSociales.findById(obra.id()).orElseThrow();
        entidad.reemplazar(obra.nombre(), obra.sigla(), obra.tipo().name(), obra.activa());
        return aDominio(obrasSociales.saveAndFlush(entidad));
    }

    @Override
    public List<Plan> planes() {
        return planes.todosPorNombre().stream().map(CatalogoCoberturasJpa::aDominio).toList();
    }

    @Override
    public List<Plan> planes(Long obraSocialId) {
        return planes.deObraSocial(obraSocialId).stream().map(CatalogoCoberturasJpa::aDominio).toList();
    }

    @Override
    public Optional<Plan> plan(Long obraSocialId, Long planId) {
        return planes.findByIdAndObraSocialId(planId, obraSocialId).map(CatalogoCoberturasJpa::aDominio);
    }

    @Override
    public Optional<Plan> plan(Long planId) {
        return planes.findById(planId).map(CatalogoCoberturasJpa::aDominio);
    }

    @Override
    public boolean existePlan(Long obraSocialId, String nombre, Long excluirId) {
        return planes.existeNombre(obraSocialId, nombre, excluirId == null ? NINGUNO : excluirId);
    }

    @Override
    public Plan guardar(Plan plan) {
        PlanEntity entidad = plan.id() == null
                ? new PlanEntity(plan.obraSocialId(), plan.nombre(), plan.activo())
                : planes.findById(plan.id()).orElseThrow();
        entidad.reemplazar(plan.nombre(), plan.activo());
        return aDominio(planes.saveAndFlush(entidad));
    }

    @Override
    public List<CodigoNomenclador> nomenclador() {
        return nomenclador.todosPorCodigo().stream().map(CatalogoCoberturasJpa::aDominio).toList();
    }

    @Override
    public Optional<CodigoNomenclador> codigoNomenclador(Long id) {
        return nomenclador.findById(id).map(CatalogoCoberturasJpa::aDominio);
    }

    @Override
    public boolean existeCodigo(String codigo, Long excluirId) {
        return nomenclador.existeCodigo(codigo, excluirId == null ? NINGUNO : excluirId);
    }

    @Override
    public CodigoNomenclador guardar(CodigoNomenclador codigo) {
        NomencladorEntity entidad = codigo.id() == null
                ? new NomencladorEntity(codigo.codigo(), codigo.descripcion(), codigo.prestacionId(), codigo.activo())
                : nomenclador.findById(codigo.id()).orElseThrow();
        entidad.reemplazar(codigo.codigo(), codigo.descripcion(), codigo.prestacionId(), codigo.activo());
        return aDominio(nomenclador.saveAndFlush(entidad));
    }

    private static CodigoNomenclador aDominio(NomencladorEntity e) {
        return new CodigoNomenclador(e.id(), e.codigo(), e.descripcion(), e.prestacionId(), e.activo());
    }

    private static ObraSocial aDominio(ObraSocialEntity e) {
        return new ObraSocial(e.id(), e.nombre(), e.sigla(), TipoCobertura.valueOf(e.tipo()), e.activa());
    }

    private static Plan aDominio(PlanEntity e) {
        return new Plan(e.id(), e.obraSocialId(), e.nombre(), e.activo());
    }
}
