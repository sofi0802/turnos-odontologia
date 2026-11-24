package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.CoberturasPlan.CoberturaFila;
import ar.turnos.odontologia.coberturas.application.Vistas.CoberturaCodigoVista;
import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import ar.turnos.odontologia.coberturas.domain.Porcentaje;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobertura de cada plan por código del nomenclador (RN-PR-02, design D6). El plan se busca siempre junto con la obra
 * social de la ruta. Dejar de cubrir un código es cargar 0 %: no hay borrado. Un código sin porcentaje cargado no figura
 * y se considera no cubierto al presupuestar.
 */
@Service
public class ServicioCoberturaPlan {

    private final CatalogoCoberturas catalogo;
    private final CoberturasPlan coberturas;

    public ServicioCoberturaPlan(CatalogoCoberturas catalogo, CoberturasPlan coberturas) {
        this.catalogo = catalogo;
        this.coberturas = coberturas;
    }

    @Transactional(readOnly = true)
    public List<CoberturaCodigoVista> listar(Long obraSocialId, Long planId) {
        exigirPlan(obraSocialId, planId);
        return coberturas.delPlan(planId).stream().map(CoberturaCodigoVista::de).toList();
    }

    @Transactional
    public CoberturaCodigoVista cargar(Long obraSocialId, Long planId, Long nomencladorId, BigDecimal porcentaje) {
        Porcentaje valido = Porcentaje.de(porcentaje);
        exigirPlan(obraSocialId, planId);
        CoberturaFila fila = coberturas.guardar(planId, nomencladorId, valido)
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe el código del nomenclador"));
        return CoberturaCodigoVista.de(fila);
    }

    private void exigirPlan(Long obraSocialId, Long planId) {
        catalogo.plan(obraSocialId, planId).orElseThrow(CoberturasErrores::planInexistente);
    }
}
