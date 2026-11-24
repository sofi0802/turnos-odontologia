package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.Vistas.CodigoVista;
import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import ar.turnos.odontologia.coberturas.domain.CodigoCobertura;
import ar.turnos.odontologia.coberturas.domain.CodigoNomenclador;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ABM del nomenclador (RN-OS-06): código único sin distinguir mayúsculas, vinculado opcionalmente a una prestación
 * del consultorio. Una misma prestación puede tener varios códigos. Se desactiva, nunca se borra.
 */
@Service
@Transactional
public class ServicioNomenclador {

    private final CatalogoCoberturas catalogo;
    private final PrestacionesExistentes prestaciones;

    public ServicioNomenclador(CatalogoCoberturas catalogo, PrestacionesExistentes prestaciones) {
        this.catalogo = catalogo;
        this.prestaciones = prestaciones;
    }

    @Transactional(readOnly = true)
    public List<CodigoVista> listar() {
        List<CodigoNomenclador> codigos = catalogo.nomenclador();
        Map<Long, String> nombres = prestaciones.nombres(codigos.stream()
                .map(CodigoNomenclador::prestacionId).filter(java.util.Objects::nonNull).collect(
                        java.util.stream.Collectors.toSet()));
        return codigos.stream().map(c -> CodigoVista.de(c, nombres.get(c.prestacionId()))).toList();
    }

    @Transactional(readOnly = true)
    public CodigoVista obtener(Long id) {
        return vista(buscar(id));
    }

    public CodigoVista crear(String codigo, String descripcion, Long prestacionId) {
        String codigoLimpio = codigo.strip();
        exigirPrestacion(prestacionId);
        if (catalogo.existeCodigo(codigoLimpio, null)) {
            throw repetido();
        }
        return vista(catalogo.guardar(new CodigoNomenclador(null, codigoLimpio, descripcion.strip(), prestacionId, true)));
    }

    public CodigoVista actualizar(Long id, String codigo, String descripcion, Long prestacionId) {
        CodigoNomenclador actual = buscar(id);
        String codigoLimpio = codigo.strip();
        exigirPrestacion(prestacionId);
        if (catalogo.existeCodigo(codigoLimpio, id)) {
            throw repetido();
        }
        return vista(catalogo.guardar(actual.conDatos(codigoLimpio, descripcion.strip(), prestacionId)));
    }

    public CodigoVista activar(Long id) {
        return vista(catalogo.guardar(buscar(id).conActivo(true)));
    }

    public CodigoVista desactivar(Long id) {
        return vista(catalogo.guardar(buscar(id).conActivo(false)));
    }

    private CodigoNomenclador buscar(Long id) {
        return catalogo.codigoNomenclador(id)
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe el código del nomenclador"));
    }

    private void exigirPrestacion(Long prestacionId) {
        if (prestacionId != null && !prestaciones.nombres(Set.of(prestacionId)).containsKey(prestacionId)) {
            throw new CoberturaException(CodigoCobertura.PRESTACION_INEXISTENTE, "prestacionId",
                    "No existe la prestación indicada");
        }
    }

    private CodigoVista vista(CodigoNomenclador codigo) {
        String nombre = codigo.prestacionId() == null ? null
                : prestaciones.nombres(Set.of(codigo.prestacionId())).get(codigo.prestacionId());
        return CodigoVista.de(codigo, nombre);
    }

    private static CoberturaException repetido() {
        return new CoberturaException(CodigoCobertura.CODIGO_NOMENCLADOR_EXISTENTE,
                "Ya existe un código con ese valor en el nomenclador");
    }
}
