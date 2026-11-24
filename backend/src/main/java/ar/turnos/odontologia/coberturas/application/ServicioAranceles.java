package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.Aranceles.ArancelFila;
import ar.turnos.odontologia.coberturas.application.Vistas.ArancelVista;
import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import ar.turnos.odontologia.coberturas.domain.Precio;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Precio particular de cada prestación (RN-PR-01, design D6). Cargarlo de nuevo reemplaza el anterior y guarda el
 * momento del cambio; no hay borrado. Se puede cargar el precio de una prestación inactiva: es configuración.
 */
@Service
public class ServicioAranceles {

    private final Aranceles aranceles;
    private final Clock reloj;

    public ServicioAranceles(Aranceles aranceles, Clock reloj) {
        this.aranceles = aranceles;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<ArancelVista> listar() {
        return aranceles.todos().stream().map(ArancelVista::de).toList();
    }

    @Transactional
    public ArancelVista cargar(Long prestacionId, BigDecimal precio) {
        Precio valido = Precio.de(precio);
        ArancelFila fila = aranceles.guardar(prestacionId, valido, reloj.instant())
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe la prestación"));
        return ArancelVista.de(fila);
    }
}
