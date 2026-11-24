package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.Vistas.ObraSocialVista;
import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import ar.turnos.odontologia.coberturas.domain.CodigoCobertura;
import ar.turnos.odontologia.coberturas.domain.ObraSocial;
import ar.turnos.odontologia.coberturas.domain.Plan;
import ar.turnos.odontologia.coberturas.domain.TipoCobertura;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ABM de obras sociales y prepagas (RN-OS-01): nombre único sin distinguir mayúsculas, activas o no; se desactivan,
 * nunca se borran, y desactivar una obra social no desactiva sus planes.
 */
@Service
@Transactional
public class ServicioObrasSociales {

    private final CatalogoCoberturas catalogo;

    public ServicioObrasSociales(CatalogoCoberturas catalogo) {
        this.catalogo = catalogo;
    }

    @Transactional(readOnly = true)
    public List<ObraSocialVista> listar() {
        Map<Long, List<Plan>> planes = catalogo.planes().stream().collect(Collectors.groupingBy(Plan::obraSocialId));
        return catalogo.obrasSociales().stream()
                .map(o -> ObraSocialVista.de(o, planes.getOrDefault(o.id(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ObraSocialVista obtener(Long id) {
        return vista(buscar(id));
    }

    public ObraSocialVista crear(String nombre, String sigla, String tipo) {
        TipoCobertura tipoCobertura = TipoCobertura.de(tipo);
        String nombreLimpio = nombre.strip();
        if (catalogo.existeObraSocial(nombreLimpio, null)) {
            throw repetida();
        }
        return vista(catalogo.guardar(new ObraSocial(null, nombreLimpio, limpiar(sigla), tipoCobertura, true)));
    }

    public ObraSocialVista actualizar(Long id, String nombre, String sigla, String tipo) {
        ObraSocial obra = buscar(id);
        TipoCobertura tipoCobertura = TipoCobertura.de(tipo);
        String nombreLimpio = nombre.strip();
        if (catalogo.existeObraSocial(nombreLimpio, id)) {
            throw repetida();
        }
        return vista(catalogo.guardar(obra.conDatos(nombreLimpio, limpiar(sigla), tipoCobertura)));
    }

    public ObraSocialVista activar(Long id) {
        return vista(catalogo.guardar(buscar(id).conActiva(true)));
    }

    public ObraSocialVista desactivar(Long id) {
        return vista(catalogo.guardar(buscar(id).conActiva(false)));
    }

    private ObraSocial buscar(Long id) {
        return catalogo.obraSocial(id).orElseThrow(() -> CoberturaException.noEncontrado("No existe la obra social"));
    }

    private ObraSocialVista vista(ObraSocial obra) {
        return ObraSocialVista.de(obra, catalogo.planes(obra.id()));
    }

    private static String limpiar(String sigla) {
        return sigla == null || sigla.isBlank() ? null : sigla.strip();
    }

    private static CoberturaException repetida() {
        return new CoberturaException(CodigoCobertura.OBRA_SOCIAL_EXISTENTE, "Ya existe una obra social con ese nombre");
    }
}
