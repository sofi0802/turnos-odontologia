package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.VistasLiquidacion.LiquidacionVista;
import ar.turnos.odontologia.coberturas.application.VistasLiquidacion.ResumenVista;
import ar.turnos.odontologia.coberturas.domain.CargoTurno;
import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import ar.turnos.odontologia.coberturas.domain.CodigoCobertura;
import ar.turnos.odontologia.coberturas.domain.EstadoLiquidacion;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Candidato;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.CodigoCubierto;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Descarte;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.Evaluacion;
import ar.turnos.odontologia.coberturas.domain.LineaLiquidacion.OrigenPrecio;
import ar.turnos.odontologia.coberturas.domain.Liquidacion;
import ar.turnos.odontologia.coberturas.domain.Liquidacion.Hitos;
import ar.turnos.odontologia.coberturas.domain.ObraSocial;
import ar.turnos.odontologia.coberturas.domain.Periodo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cada operación de liquidaciones en su propia transacción (C-20, design D4 y D8); la reintenta
 * {@link ServicioLiquidaciones} ante un bloqueo mutuo o si dos generaciones se disputan un turno. Toda operación sobre
 * una liquidación existente empieza con el bloqueo de su cabecera ({@code FOR UPDATE}), así se aplican de a una.
 *
 * <p>Cada regla se verifica en el dominio con un error claro y la base la repite con restricciones y triggers (V14).
 * Nada de lo que se guarda ni se responde (pacientes, DNI, afiliados, prestaciones, importes) va a logs (RN-LQ-08).
 */
@Service
@Transactional
public class OperacionesLiquidaciones {

    private final Liquidaciones liquidaciones;
    private final TurnosLiquidables turnos;
    private final CargosDeTurno cargos;
    private final CatalogoCoberturas catalogo;
    private final Clock reloj;
    private final ZoneId zona;

    public OperacionesLiquidaciones(Liquidaciones liquidaciones, TurnosLiquidables turnos, CargosDeTurno cargos,
                                    CatalogoCoberturas catalogo, Clock reloj, ZoneId zona) {
        this.liquidaciones = liquidaciones;
        this.turnos = turnos;
        this.cargos = cargos;
        this.catalogo = catalogo;
        this.reloj = reloj;
        this.zona = zona;
    }

    /** Crea la liquidación del período, ya cerrado, con los turnos que entran (RN-LQ-01 y RN-LQ-02). */
    public LiquidacionVista generar(Long obraSocialId, String periodoTexto, String actor) {
        // 1. Datos de entrada válidos (422) y período cerrado (409) antes de mirar la base.
        Periodo periodo = Periodo.de(periodoTexto);
        if (!periodo.estaCerrado(hoy())) {
            throw new CoberturaException(CodigoCobertura.PERIODO_NO_CERRADO, "El período todavía no está cerrado");
        }
        // 2. La obra social existe (404) y no hay otra liquidación para el período (409).
        ObraSocial obra = catalogo.obraSocial(obraSocialId)
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe la obra social"));
        if (liquidaciones.existe(obraSocialId, periodo)) {
            throw liquidacionExistente();
        }
        // 3. Se escribe en el acto: si otro pedido se coló, liquidacion_periodo_unico salta acá (409).
        Hitos hitos = Hitos.generada(ahora(), actor);
        Long id = liquidaciones.crear(obraSocialId, periodo, hitos);
        Sincronizacion s = sincronizar(new Liquidacion(id, obraSocialId, periodo, EstadoLiquidacion.BORRADOR,
                List.of(), hitos));
        return vista(s.liquidacion(), obra.nombre()).conTurnosSinArancel(s.sinArancel());
    }

    /**
     * Vuelve a armar las líneas de un borrador con los datos de hoy (RN-LQ-04): agrega los turnos que ahora cumplen,
     * recalcula las líneas con los valores vigentes, saca las que ya no cumplen y conserva el código elegido y los
     * turnos quitados a mano.
     */
    public LiquidacionVista regenerar(Long liquidacionId, String actor) {
        Liquidacion liquidacion = bloquear(liquidacionId);
        liquidacion.exigirBorrador();
        Sincronizacion s = sincronizar(liquidacion.conHitos(Hitos.generada(ahora(), actor)));
        return vista(s.liquidacion()).conTurnosSinArancel(s.sinArancel());
    }

    /** {@code BORRADOR} a {@code PRESENTADA} (RN-LQ-04): desde acá las líneas y los montos solicitados no cambian. */
    public LiquidacionVista presentar(Long liquidacionId, String actor) {
        Liquidacion presentada = bloquear(liquidacionId).presentar(ahora(), actor);
        liquidaciones.guardarCabecera(presentada);
        return vista(presentada);
    }

    /**
     * Registra o corrige lo cobrado de una línea de una liquidación presentada (RN-LQ-06, conciliación): el resultado
     * se deriva del monto, se guarda quién y cuándo, y se recalcula el total cobrado. Los montos solicitados no cambian.
     */
    public LiquidacionVista conciliar(Long liquidacionId, Long lineaId, BigDecimal montoCobrado, String motivo,
                                      String actor) {
        Liquidacion conciliada = bloquear(liquidacionId)
                .conciliar(lineaId, montoCobrado, motivo, ahora(), actor);
        liquidaciones.guardarConciliacion(conciliada.linea(lineaId));
        liquidaciones.guardarCabecera(conciliada);
        return vista(conciliada);
    }

    /** {@code PRESENTADA} a {@code CERRADA} con todas las líneas conciliadas; desde acá nada cambia más. */
    public LiquidacionVista cerrar(Long liquidacionId, String actor) {
        Liquidacion cerrada = bloquear(liquidacionId).cerrar(ahora(), actor);
        liquidaciones.guardarCabecera(cerrada);
        return vista(cerrada);
    }

    /** El CSV de una liquidación presentada o cerrada (RN-LQ-05); en borrador no hay nada que presentar todavía. */
    @Transactional(readOnly = true)
    public ArchivoCsv csv(Long liquidacionId) {
        Liquidacion liquidacion = liquidaciones.cargar(liquidacionId)
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe la liquidación"));
        if (liquidacion.estado() == EstadoLiquidacion.BORRADOR) {
            throw new CoberturaException(CodigoCobertura.LIQUIDACION_NO_PRESENTADA,
                    "La liquidación todavía no está presentada");
        }
        String nombre = "liquidacion-" + liquidacion.obraSocialId() + "-" + liquidacion.periodo() + ".csv";
        return new ArchivoCsv(nombre,
                CsvLiquidacion.generar(liquidacion.lineas(), liquidacion.totales().totalSolicitado()));
    }

    /** El nombre del archivo (solo ids y período, sin datos personales) y su contenido. */
    public record ArchivoCsv(String nombre, byte[] contenido) {}

    /** Más recientes primero; sin nombres ni DNI de pacientes (RN-LQ-08). */
    @Transactional(readOnly = true)
    public List<ResumenVista> listar(Long obraSocialId, EstadoLiquidacion estado) {
        return liquidaciones.listar(obraSocialId, estado).stream().map(ResumenVista::de).toList();
    }

    /** El detalle: lo que se guardó, sin consultar el catálogo de hoy (salvo los códigos a elegir de un borrador). */
    @Transactional(readOnly = true)
    public LiquidacionVista obtener(Long liquidacionId) {
        return vista(liquidaciones.cargar(liquidacionId)
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe la liquidación")));
    }

    /**
     * Quita una línea de un borrador (RN-LQ-04, design D7): el turno no vuelve a esa liquidación al regenerar y queda
     * libre para cualquier otra.
     */
    public void quitarLinea(Long liquidacionId, Long lineaId) {
        Liquidacion liquidacion = bloquear(liquidacionId);
        Liquidacion sinLinea = liquidacion.sinLinea(lineaId);
        Long turnoId = liquidacion.linea(lineaId).turnoId();
        liquidaciones.borrarLinea(lineaId);
        liquidaciones.quitarTurno(liquidacionId, turnoId);
        liquidaciones.guardarCabecera(sinLinea);
    }

    /**
     * Elige el código de una línea de un borrador (PA-38, design D6): uno de los códigos activos de su prestación que su
     * plan cubre con más de 0 %. Una línea con código automático (un solo código activo) no tiene alternativa válida.
     */
    public LiquidacionVista elegirCodigo(Long liquidacionId, Long lineaId, Long nomencladorId) {
        Liquidacion liquidacion = bloquear(liquidacionId);
        liquidacion.exigirBorrador();
        LineaLiquidacion linea = liquidacion.linea(lineaId);
        if (linea.tieneCodigo() && !linea.codigoElegido()) {
            throw codigoInvalido();
        }
        CodigoCubierto codigo = liquidaciones.codigoDisponible(lineaId, nomencladorId)
                .orElseThrow(OperacionesLiquidaciones::codigoInvalido);
        Integer porcentajeDelCargo = null;
        if (linea.origenPrecio() == OrigenPrecio.CARGO) {
            CargoTurno cargo = cargos.de(List.of(linea.turnoId())).get(linea.turnoId());
            porcentajeDelCargo = cargo == null ? null : cargo.porcentaje();
        }
        LineaLiquidacion elegida = linea.conCodigo(codigo, porcentajeDelCargo, true);
        if (elegida == null) {
            throw codigoInvalido();
        }
        liquidaciones.actualizarLinea(elegida);
        Liquidacion actualizada = liquidacion.conLineas(liquidacion.lineas().stream()
                .map(l -> l.id().equals(lineaId) ? elegida : l).toList());
        liquidaciones.guardarCabecera(actualizada);
        return vista(actualizada);
    }

    /** Toma el bloqueo de la cabecera y carga la liquidación con sus líneas (404 si no existe). */
    private Liquidacion bloquear(Long liquidacionId) {
        return liquidaciones.bloquear(liquidacionId)
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe la liquidación"));
    }

    private LiquidacionVista vista(Liquidacion liquidacion) {
        String nombre = catalogo.obraSocial(liquidacion.obraSocialId()).map(ObraSocial::nombre).orElse("");
        return vista(liquidacion, nombre);
    }

    private static CoberturaException codigoInvalido() {
        return new CoberturaException(CodigoCobertura.CODIGO_NOMENCLADOR_INVALIDO, "nomencladorId",
                "El código no es válido para la línea");
    }

    /** El resultado de armar las líneas: la liquidación con sus líneas y los turnos que quedaron sin arancel. */
    record Sincronizacion(Liquidacion liquidacion, int sinArancel) {}

    /**
     * Arma las líneas de un borrador con los candidatos de hoy (design D4, pasos 2 a 4): actualiza las existentes que
     * siguen siendo candidatas, inserta las nuevas y borra las que ya no lo son, y guarda la cabecera con los totales.
     */
    private Sincronizacion sincronizar(Liquidacion borrador) {
        List<Candidato> candidatos = turnos.candidatos(borrador.obraSocialId(), borrador.periodo().finExclusivo(zona),
                borrador.id());
        Map<Long, CargoTurno> cargosPorTurno = cargos.de(candidatos.stream().map(Candidato::turnoId).toList());
        Map<Long, LineaLiquidacion> existentes = new LinkedHashMap<>();
        borrador.lineas().forEach(l -> existentes.put(l.turnoId(), l));

        Map<Long, LineaLiquidacion> nuevas = new LinkedHashMap<>();
        int sinArancel = 0;
        for (Candidato candidato : candidatos) {
            LineaLiquidacion previa = existentes.get(candidato.turnoId());
            Long elegido = previa != null && previa.codigoElegido() ? previa.nomencladorId() : null;
            Evaluacion evaluacion = LineaLiquidacion.desde(candidato, cargosPorTurno.get(candidato.turnoId()), elegido);
            if (evaluacion.linea() != null) {
                nuevas.put(candidato.turnoId(), previa == null ? evaluacion.linea()
                        : evaluacion.linea().conId(previa.id()));
            } else if (evaluacion.descarte() == Descarte.SIN_ARANCEL) {
                sinArancel++;
            }
        }
        existentes.forEach((turnoId, linea) -> {
            if (!nuevas.containsKey(turnoId)) {
                liquidaciones.borrarLinea(linea.id());
            }
        });
        List<LineaLiquidacion> resultado = new ArrayList<>();
        for (LineaLiquidacion linea : nuevas.values()) {
            if (linea.id() == null) {
                Long id = liquidaciones.insertarLinea(borrador.id(), borrador.obraSocialId(), linea);
                resultado.add(linea.conId(id));
            } else {
                liquidaciones.actualizarLinea(linea);
                resultado.add(linea);
            }
        }
        Liquidacion sincronizada = borrador.conLineas(resultado);
        liquidaciones.guardarCabecera(sincronizada);
        return new Sincronizacion(sincronizada, sinArancel);
    }

    private LiquidacionVista vista(Liquidacion liquidacion, String obraSocial) {
        return VistasLiquidacion.de(liquidacion, obraSocial, liquidaciones.codigosDisponibles(liquidacion.id()));
    }

    private static CoberturaException liquidacionExistente() {
        return new CoberturaException(CodigoCobertura.LIQUIDACION_EXISTENTE,
                "Ya existe la liquidación de esa obra social para el período");
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj.withZone(zona));
    }

    /**
     * En UTC y a microsegundos: es lo que guarda y devuelve PostgreSQL, así lo que se escribe es igual a lo que
     * después se lee y la API siempre muestra los instantes con el mismo formato.
     */
    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
