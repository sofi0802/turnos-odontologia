package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.Vistas.PlanVista;
import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import ar.turnos.odontologia.coberturas.domain.CodigoCobertura;
import ar.turnos.odontologia.coberturas.domain.Plan;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ABM de planes (RN-OS-02): nombre único dentro de su obra social. Un plan siempre se busca junto con su obra social
 * de la ruta, así no se puede mover ni tocar un plan de otra obra social.
 */
@Service
@Transactional
public class ServicioPlanes {

    private final CatalogoCoberturas catalogo;

    public ServicioPlanes(CatalogoCoberturas catalogo) {
        this.catalogo = catalogo;
    }

    @Transactional(readOnly = true)
    public List<PlanVista> listar(Long obraSocialId) {
        exigirObraSocial(obraSocialId);
        return catalogo.planes(obraSocialId).stream().map(PlanVista::de).toList();
    }

    @Transactional(readOnly = true)
    public PlanVista obtener(Long obraSocialId, Long planId) {
        return PlanVista.de(buscar(obraSocialId, planId));
    }

    public PlanVista crear(Long obraSocialId, String nombre) {
        exigirObraSocial(obraSocialId);
        String nombreLimpio = nombre.strip();
        if (catalogo.existePlan(obraSocialId, nombreLimpio, null)) {
            throw repetido();
        }
        return PlanVista.de(catalogo.guardar(new Plan(null, obraSocialId, nombreLimpio, true)));
    }

    public PlanVista actualizar(Long obraSocialId, Long planId, String nombre) {
        Plan plan = buscar(obraSocialId, planId);
        String nombreLimpio = nombre.strip();
        if (catalogo.existePlan(obraSocialId, nombreLimpio, planId)) {
            throw repetido();
        }
        return PlanVista.de(catalogo.guardar(plan.conNombre(nombreLimpio)));
    }

    public PlanVista activar(Long obraSocialId, Long planId) {
        return PlanVista.de(catalogo.guardar(buscar(obraSocialId, planId).conActivo(true)));
    }

    public PlanVista desactivar(Long obraSocialId, Long planId) {
        return PlanVista.de(catalogo.guardar(buscar(obraSocialId, planId).conActivo(false)));
    }

    private void exigirObraSocial(Long obraSocialId) {
        if (catalogo.obraSocial(obraSocialId).isEmpty()) {
            throw CoberturaException.noEncontrado("No existe la obra social");
        }
    }

    private Plan buscar(Long obraSocialId, Long planId) {
        return catalogo.plan(obraSocialId, planId)
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe el plan en esa obra social"));
    }

    private static CoberturaException repetido() {
        return new CoberturaException(CodigoCobertura.PLAN_EXISTENTE,
                "Ya existe un plan con ese nombre en la obra social");
    }
}
