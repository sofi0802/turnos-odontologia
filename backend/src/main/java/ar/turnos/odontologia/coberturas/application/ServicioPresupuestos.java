package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.LecturaCoberturas.AfiliacionDetalle;
import ar.turnos.odontologia.coberturas.application.LecturaPresupuestos.PresupuestoFila;
import ar.turnos.odontologia.coberturas.application.PrestacionesPresupuestables.CodigoActivo;
import ar.turnos.odontologia.coberturas.application.PrestacionesPresupuestables.Presupuestable;
import ar.turnos.odontologia.coberturas.application.Presupuestos.EstadoGuardado;
import ar.turnos.odontologia.coberturas.application.Vistas.PresupuestoVista;
import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import ar.turnos.odontologia.coberturas.domain.CodigoCobertura;
import ar.turnos.odontologia.coberturas.domain.EstadoPresupuesto;
import ar.turnos.odontologia.coberturas.domain.PiezaCara;
import ar.turnos.odontologia.coberturas.domain.Porcentaje;
import ar.turnos.odontologia.coberturas.domain.Precio;
import ar.turnos.odontologia.coberturas.domain.Presupuesto;
import ar.turnos.odontologia.coberturas.domain.Presupuesto.CoberturaUsada;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emisión, consulta y estados de los presupuestos (RN-PR-01 a RN-PR-07, design D4, D5 y D7). "Hoy" es la fecha de
 * Buenos Aires del reloj inyectado. Un presupuesto emitido es una foto: se guarda una vez, solo cambia de estado y sus
 * lecturas usan solo las columnas de la foto. Este servicio no escribe logs: piezas, caras e importes son datos de
 * salud y de dinero del paciente (RN-PR-07).
 */
@Service
public class ServicioPresupuestos {

    /** Un ítem del pedido de emisión; la pieza y la cara se validan en el dominio ({@link PiezaCara}). */
    public record ItemPedido(Long prestacionId, Long nomencladorId, Integer pieza, String cara, int cantidad) {}

    private final Presupuestos presupuestos;
    private final LecturaPresupuestos lectura;
    private final LecturaCoberturas coberturasVigentes;
    private final CoberturasPlan coberturasPlan;
    private final PrestacionesPresupuestables prestaciones;
    private final PacientesExistentes pacientes;
    private final Clock reloj;
    private final ZoneId zona;

    public ServicioPresupuestos(Presupuestos presupuestos, LecturaPresupuestos lectura,
                                LecturaCoberturas coberturasVigentes, CoberturasPlan coberturasPlan,
                                PrestacionesPresupuestables prestaciones, PacientesExistentes pacientes, Clock reloj,
                                ZoneId zona) {
        this.presupuestos = presupuestos;
        this.lectura = lectura;
        this.coberturasVigentes = coberturasVigentes;
        this.coberturasPlan = coberturasPlan;
        this.prestaciones = prestaciones;
        this.pacientes = pacientes;
        this.reloj = reloj;
        this.zona = zona;
    }

    /**
     * Emite el presupuesto de un paciente (design D4). El profesional sale de la sesión. Si un ítem falla, el primero
     * en orden responde y no se guarda nada.
     */
    @Transactional
    public PresupuestoVista emitir(Long pacienteId, Long profesionalId, List<ItemPedido> pedido) {
        List<PiezaCara> piezasCaras = validarPiezasYCaras(pedido);
        if (!pacientes.existe(pacienteId)) {
            throw CoberturaException.noEncontrado("No existe el paciente");
        }
        LocalDate hoy = hoy();
        AfiliacionDetalle afiliacion = coberturasVigentes.vigenteEn(pacienteId, hoy).orElse(null);
        Map<Long, Presupuestable> catalogo = prestaciones.de(pedido.stream().map(ItemPedido::prestacionId)
                .collect(Collectors.toSet()));
        List<CodigoActivo> elegidos = new ArrayList<>();
        List<Presupuestable> resueltas = new ArrayList<>();
        for (int i = 0; i < pedido.size(); i++) {
            Presupuestable prestacion = catalogo.get(pedido.get(i).prestacionId());
            resueltas.add(exigirPresupuestable(prestacion));
            elegidos.add(elegirCodigo(prestacion, pedido.get(i).nomencladorId()));
        }
        Map<Long, Integer> porcentajes = afiliacion == null ? Map.of() : coberturasPlan.porcentajes(
                afiliacion.planId(), elegidos.stream().filter(Objects::nonNull).map(CodigoActivo::id).toList());

        List<Presupuesto.Item> items = new ArrayList<>();
        for (int i = 0; i < pedido.size(); i++) {
            Presupuestable prestacion = resueltas.get(i);
            CodigoActivo codigo = elegidos.get(i);
            int porcentaje = codigo == null ? 0 : porcentajes.getOrDefault(codigo.id(), 0);
            try {
                items.add(Presupuesto.Item.de(prestacion.id(), prestacion.nombre(), codigo == null ? null : codigo.id(),
                        codigo == null ? null : codigo.codigo(), piezasCaras.get(i), pedido.get(i).cantidad(),
                        Precio.guardado(prestacion.precio()), Porcentaje.guardado(porcentaje)));
            } catch (CoberturaException e) {
                throw enItem(e, i);
            }
        }
        CoberturaUsada usada = afiliacion == null ? null : new CoberturaUsada(afiliacion.id(), afiliacion.obraSocial(),
                afiliacion.plan(), afiliacion.numeroAfiliado());
        Long id = presupuestos.guardar(Presupuesto.emitir(pacienteId, profesionalId, hoy, usada, items,
                reloj.instant()));
        return lectura.detalle(pacienteId, id).map(f -> PresupuestoVista.de(f, hoy)).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<PresupuestoVista> listar(Long pacienteId) {
        if (!pacientes.existe(pacienteId)) {
            throw CoberturaException.noEncontrado("No existe el paciente");
        }
        return propios(pacienteId);
    }

    @Transactional(readOnly = true)
    public PresupuestoVista obtener(Long pacienteId, Long presupuestoId) {
        return propio(pacienteId, presupuestoId);
    }

    /** Los presupuestos del paciente de la sesión: el id sale de la sesión, no del pedido. */
    @Transactional(readOnly = true)
    public List<PresupuestoVista> propios(Long pacienteDeLaSesion) {
        LocalDate hoy = hoy();
        return lectura.delPaciente(pacienteDeLaSesion).stream().map(f -> PresupuestoVista.de(f, hoy)).toList();
    }

    @Transactional(readOnly = true)
    public PresupuestoVista propio(Long pacienteDeLaSesion, Long presupuestoId) {
        PresupuestoFila fila = lectura.detalle(pacienteDeLaSesion, presupuestoId)
                .orElseThrow(ServicioPresupuestos::presupuestoInexistente);
        return PresupuestoVista.de(fila, hoy());
    }

    @Transactional
    public PresupuestoVista aceptar(Long pacienteId, Long presupuestoId) {
        return cambiar(pacienteId, presupuestoId, EstadoPresupuesto.ACEPTADO);
    }

    @Transactional
    public PresupuestoVista rechazar(Long pacienteId, Long presupuestoId) {
        return cambiar(pacienteId, presupuestoId, EstadoPresupuesto.RECHAZADO);
    }

    /**
     * Un {@code UPDATE} condicional cambia el estado de un presupuesto pendiente (y no vencido, si se acepta): con dos
     * pedidos a la vez PostgreSQL serializa las filas y el segundo ve que ya no está pendiente. Si no cambió nada se
     * relee para responder {@code 404}, {@code PRESUPUESTO_CERRADO} o {@code PRESUPUESTO_VENCIDO}.
     */
    private PresupuestoVista cambiar(Long pacienteId, Long presupuestoId, EstadoPresupuesto destino) {
        LocalDate hoy = hoy();
        if (presupuestos.cambiarEstado(pacienteId, presupuestoId, destino, hoy) == 0) {
            EstadoGuardado guardado = presupuestos.estado(pacienteId, presupuestoId)
                    .orElseThrow(ServicioPresupuestos::presupuestoInexistente);
            // lanza el error que corresponde; si no lanza, otro pedido lo dejó como estaba y se informa como cerrado
            guardado.estado().pasarA(destino, guardado.venceEl(), hoy);
            throw new CoberturaException(CodigoCobertura.PRESUPUESTO_CERRADO, "El presupuesto ya no está pendiente");
        }
        return lectura.detalle(pacienteId, presupuestoId).map(f -> PresupuestoVista.de(f, hoy)).orElseThrow();
    }

    private List<PiezaCara> validarPiezasYCaras(List<ItemPedido> pedido) {
        List<PiezaCara> validas = new ArrayList<>();
        for (int i = 0; i < pedido.size(); i++) {
            try {
                validas.add(PiezaCara.de(pedido.get(i).pieza(), pedido.get(i).cara()));
            } catch (CoberturaException e) {
                throw enItem(e, i);
            }
        }
        return validas;
    }

    private static Presupuestable exigirPresupuestable(Presupuestable prestacion) {
        if (prestacion == null) {
            throw new CoberturaException(CodigoCobertura.PRESTACION_INEXISTENTE, "No existe la prestación");
        }
        if (!prestacion.activa()) {
            throw new CoberturaException(CodigoCobertura.PRESTACION_INACTIVA, "La prestación está inactiva");
        }
        if (prestacion.precio() == null) {
            throw new CoberturaException(CodigoCobertura.ARANCEL_INEXISTENTE, "La prestación no tiene precio");
        }
        return prestacion;
    }

    /** El código del ítem (RN-PR-02): el único activo, el indicado si hay varios, o ninguno si es particular. */
    private static CodigoActivo elegirCodigo(Presupuestable prestacion, Long indicado) {
        List<CodigoActivo> activos = prestacion.codigosActivos();
        if (indicado != null) {
            return activos.stream().filter(c -> c.id().equals(indicado)).findFirst().orElseThrow(
                    () -> new CoberturaException(CodigoCobertura.CODIGO_NOMENCLADOR_INVALIDO,
                            "El código del nomenclador no es válido para la prestación"));
        }
        if (activos.size() > 1) {
            throw new CoberturaException(CodigoCobertura.CODIGO_NOMENCLADOR_REQUERIDO,
                    "La prestación tiene varios códigos: hay que indicar cuál");
        }
        return activos.isEmpty() ? null : activos.get(0);
    }

    /** El campo del error pasa a {@code items[i].campo}; un campo que ya es de ítems queda como está. */
    private static CoberturaException enItem(CoberturaException e, int indice) {
        if (e.campo() == null || e.campo().startsWith("items")) {
            return e;
        }
        return e.conCampo("items[" + indice + "]." + e.campo());
    }

    private static CoberturaException presupuestoInexistente() {
        return CoberturaException.noEncontrado("No existe el presupuesto");
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj.withZone(zona));
    }
}
