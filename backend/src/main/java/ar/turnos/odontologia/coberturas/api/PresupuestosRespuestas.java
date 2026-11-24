package ar.turnos.odontologia.coberturas.api;

import ar.turnos.odontologia.coberturas.application.Vistas.CoberturaUsadaVista;
import ar.turnos.odontologia.coberturas.application.Vistas.PrestacionRef;
import ar.turnos.odontologia.coberturas.application.Vistas.PresupuestoVista;
import ar.turnos.odontologia.coberturas.application.Vistas.ProfesionalRef;
import ar.turnos.odontologia.coberturas.application.Vistas.TotalesVista;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.Authentication;

/**
 * Vista de un presupuesto para la recepción (C-19, design D7, PA-24): son records propios que NO tienen los
 * componentes {@code pieza} ni {@code cara}, no los mismos con {@code null}. Así la clave no existe en el JSON y no
 * depende de la configuración de Jackson. El servicio devuelve siempre la vista completa y el controller elige.
 */
final class PresupuestosRespuestas {

    private PresupuestosRespuestas() {
    }

    record ItemRecepcionRespuesta(Long id, PrestacionRef prestacion, String codigoNomenclador, int cantidad,
                                  BigDecimal precioUnitario, int porcentajeCobertura, BigDecimal importe,
                                  BigDecimal cobertura, BigDecimal copago) {
    }

    record PresupuestoRecepcionRespuesta(Long id, LocalDate fecha, LocalDate venceEl, String estado, boolean vencido,
                                         ProfesionalRef profesional, boolean particular, CoberturaUsadaVista cobertura,
                                         List<ItemRecepcionRespuesta> items, TotalesVista totales, Instant creadoEn) {

        static PresupuestoRecepcionRespuesta de(PresupuestoVista p) {
            List<ItemRecepcionRespuesta> items = p.items().stream()
                    .map(i -> new ItemRecepcionRespuesta(i.id(), i.prestacion(), i.codigoNomenclador(), i.cantidad(),
                            i.precioUnitario(), i.porcentajeCobertura(), i.importe(), i.cobertura(), i.copago()))
                    .toList();
            return new PresupuestoRecepcionRespuesta(p.id(), p.fecha(), p.venceEl(), p.estado(), p.vencido(),
                    p.profesional(), p.particular(), p.cobertura(), items, p.totales(), p.creadoEn());
        }
    }

    /** La respuesta que corresponde al rol: la recepción no recibe pieza ni cara. */
    static Object paraElRol(PresupuestoVista presupuesto, Authentication autenticacion) {
        return esRecepcion(autenticacion) ? PresupuestoRecepcionRespuesta.de(presupuesto) : presupuesto;
    }

    static List<?> paraElRol(List<PresupuestoVista> presupuestos, Authentication autenticacion) {
        return esRecepcion(autenticacion) ? presupuestos.stream().map(PresupuestoRecepcionRespuesta::de).toList()
                : presupuestos;
    }

    private static boolean esRecepcion(Authentication autenticacion) {
        return autenticacion.getAuthorities().stream().anyMatch(a -> "ROLE_RECEPCIONISTA".equals(a.getAuthority()));
    }
}
