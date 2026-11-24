package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.Vistas.AfiliacionVista;
import ar.turnos.odontologia.coberturas.domain.Afiliacion;
import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import ar.turnos.odontologia.coberturas.domain.CodigoCobertura;
import ar.turnos.odontologia.coberturas.domain.NumeroAfiliado;
import ar.turnos.odontologia.coberturas.domain.ObraSocial;
import ar.turnos.odontologia.coberturas.domain.Plan;
import ar.turnos.odontologia.coberturas.domain.Vigencia;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cada operación de afiliaciones en su propia transacción (RN-OS-03 a RN-OS-05, design D4); la reintenta
 * {@link ServicioAfiliaciones} si PostgreSQL elige a la transacción como víctima de un bloqueo mutuo.
 *
 * <p>Cada regla se verifica en el dominio con un error claro y la base la repite con una {@code EXCLUDE}: si dos
 * pedidos pasan la verificación a la vez, la base frena al segundo y la API responde el mismo código. Nada de lo que
 * se guarda ni se responde va a logs.
 */
@Service
@Transactional
public class OperacionesAfiliaciones {

    private final Afiliaciones afiliaciones;
    private final CatalogoCoberturas catalogo;
    private final PacientesExistentes pacientes;
    private final LecturaCoberturas lectura;
    private final Clock reloj;
    private final ZoneId zona;

    public OperacionesAfiliaciones(Afiliaciones afiliaciones, CatalogoCoberturas catalogo, PacientesExistentes pacientes,
                                LecturaCoberturas lectura, Clock reloj, ZoneId zona) {
        this.afiliaciones = afiliaciones;
        this.catalogo = catalogo;
        this.pacientes = pacientes;
        this.lectura = lectura;
        this.reloj = reloj;
        this.zona = zona;
    }

    public AfiliacionVista afiliar(Long pacienteId, Long planId, String numero, LocalDate desde, LocalDate hasta) {
        // 1. Datos de entrada válidos (422) antes de mirar la base.
        NumeroAfiliado numeroAfiliado = NumeroAfiliado.de(numero);
        Vigencia vigencia = new Vigencia(desde, hasta);
        // 2. El paciente y el plan existen (404) y el plan y su obra social están activos (409).
        exigirPaciente(pacienteId);
        Plan plan = catalogo.plan(planId).orElseThrow(CoberturasErrores::planInexistente);
        exigirActivos(plan);
        Afiliacion nueva = new Afiliacion(null, pacienteId, plan.id(), plan.obraSocialId(), numeroAfiliado, vigencia);
        // 3 y 4. Sin superposición ni número repetido (409).
        verificar(nueva);
        // 5. Se escribe en el acto: si otro pedido se coló, la EXCLUDE de la base salta acá.
        Afiliacion guardada = afiliaciones.guardar(nueva);
        return vista(pacienteId, guardada.id());
    }

    /**
     * Corrige errores de carga: plan, número y {@code vigenteDesde}, con las mismas reglas que al crear. El cambio real
     * de cobertura es cerrar y crear (PA-20). Si el plan no cambia, no se exige que siga activo.
     */
    public AfiliacionVista corregir(Long pacienteId, Long afiliacionId, Long planId, String numero, LocalDate desde) {
        Afiliacion actual = buscar(pacienteId, afiliacionId);
        NumeroAfiliado numeroAfiliado = NumeroAfiliado.de(numero);
        Vigencia vigencia = new Vigencia(desde, actual.vigencia().hasta());
        Long obraSocialId = actual.obraSocialId();
        if (!actual.planId().equals(planId)) {
            Plan plan = catalogo.plan(planId).orElseThrow(CoberturasErrores::planInexistente);
            exigirActivos(plan);
            obraSocialId = plan.obraSocialId();
        }
        Afiliacion corregida = actual.corregida(planId, obraSocialId, numeroAfiliado, vigencia);
        verificar(corregida);
        afiliaciones.guardar(corregida);
        return vista(pacienteId, afiliacionId);
    }

    /** Fija el fin de una afiliación abierta; la anterior queda en el historial y no se borra (RN-OS-05). */
    public AfiliacionVista cerrar(Long pacienteId, Long afiliacionId, LocalDate hasta) {
        Afiliacion cerrada = buscar(pacienteId, afiliacionId).cerradaEn(hasta);
        afiliaciones.guardar(cerrada);
        return vista(pacienteId, afiliacionId);
    }

    private Afiliacion buscar(Long pacienteId, Long afiliacionId) {
        return afiliaciones.delPaciente(pacienteId, afiliacionId)
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe la afiliación del paciente"));
    }

    @Transactional(readOnly = true)
    public List<AfiliacionVista> historial(Long pacienteId) {
        exigirPaciente(pacienteId);
        LocalDate hoy = hoy();
        return lectura.historial(pacienteId).stream().map(d -> AfiliacionVista.de(d, hoy)).toList();
    }

    private void exigirPaciente(Long pacienteId) {
        if (!pacientes.existe(pacienteId)) {
            throw CoberturaException.noEncontrado("No existe el paciente");
        }
    }

    private void exigirActivos(Plan plan) {
        if (!plan.activo()) {
            throw new CoberturaException(CodigoCobertura.PLAN_INACTIVO, "El plan está inactivo");
        }
        ObraSocial obra = catalogo.obraSocial(plan.obraSocialId()).orElseThrow(CoberturasErrores::planInexistente);
        if (!obra.activa()) {
            throw new CoberturaException(CodigoCobertura.OBRA_SOCIAL_INACTIVA, "La obra social está inactiva");
        }
    }

    /** Las dos reglas que repite la base: una sola afiliación por fecha y un número por paciente en la obra social. */
    private void verificar(Afiliacion afiliacion) {
        for (Afiliacion otra : afiliaciones.delPaciente(afiliacion.pacienteId())) {
            if (afiliacion.seSuperponeCon(otra)) {
                throw CoberturasErrores.vigenciaSuperpuesta();
            }
        }
        if (afiliaciones.numeroEnUsoPorOtroPaciente(afiliacion.obraSocialId(), afiliacion.numero(),
                afiliacion.pacienteId())) {
            throw CoberturasErrores.afiliadoDuplicado();
        }
    }

    private AfiliacionVista vista(Long pacienteId, Long afiliacionId) {
        return lectura.detalle(pacienteId, afiliacionId)
                .map(d -> AfiliacionVista.de(d, hoy()))
                .orElseThrow(() -> CoberturaException.noEncontrado("No existe la afiliación"));
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj.withZone(zona));
    }
}
