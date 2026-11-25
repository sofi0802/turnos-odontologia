package ar.turnos.odontologia.pagos.infrastructure;

import ar.turnos.odontologia.pagos.application.PasarelaNoDisponibleException;
import ar.turnos.odontologia.pagos.application.PasarelaPago;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pasarela en memoria para el desarrollo y los tests ({@code PAGOS_PASARELA=simulada}, design D6). Los tests le cargan
 * pagos y le programan fallos. No llama a ningún servicio externo.
 */
public class PasarelaSimulada implements PasarelaPago {

    public record LinkPedido(String referencia, BigDecimal importe, String descripcion, Instant vence) {
    }

    public record Reintegro(String idPago, BigDecimal importe, String clave) {
    }

    private final List<LinkPedido> links = new CopyOnWriteArrayList<>();
    private final List<Reintegro> reintegros = new CopyOnWriteArrayList<>();
    private final Set<String> clavesUsadas = ConcurrentHashMap.newKeySet();
    private final Map<String, PagoExterno> pagos = new ConcurrentHashMap<>();
    private final AtomicInteger reintegrosARechazar = new AtomicInteger();
    private volatile boolean fallarLinks;

    @Override
    public String crearLink(String referencia, BigDecimal importe, String descripcion, Instant vence) {
        if (fallarLinks) {
            throw new PasarelaNoDisponibleException("La pasarela simulada no responde.");
        }
        links.add(new LinkPedido(referencia, importe, descripcion, vence));
        return "#/mis-turnos?pago=simulado&ref=" + referencia;
    }

    @Override
    public Optional<PagoExterno> consultarPago(String idPago) {
        return Optional.ofNullable(pagos.get(idPago));
    }

    @Override
    public void reintegrar(String idPago, BigDecimal importe, String claveIdempotencia) {
        if (reintegrosARechazar.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new PasarelaNoDisponibleException("La pasarela simulada rechazó el reintegro.");
        }
        if (clavesUsadas.add(claveIdempotencia)) {
            reintegros.add(new Reintegro(idPago, importe, claveIdempotencia));
        }
    }

    public void fallarLinks(boolean fallar) {
        this.fallarLinks = fallar;
    }

    public void rechazarReintegros(int cantidad) {
        reintegrosARechazar.set(cantidad);
    }

    public void cargarPago(String id, String estado, String referencia, BigDecimal importe) {
        pagos.put(id, new PagoExterno(id, estado, referencia, importe));
    }

    public List<LinkPedido> links() {
        return new ArrayList<>(links);
    }

    public List<Reintegro> reintegros() {
        return new ArrayList<>(reintegros);
    }

    /** Vuelve al estado inicial; los tests la llaman antes de cada escenario. */
    public void reiniciar() {
        links.clear();
        reintegros.clear();
        clavesUsadas.clear();
        pagos.clear();
        reintegrosARechazar.set(0);
        fallarLinks = false;
    }
}
