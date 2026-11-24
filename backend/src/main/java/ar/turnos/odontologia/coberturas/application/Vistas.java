package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.domain.CodigoNomenclador;
import ar.turnos.odontologia.coberturas.domain.ObraSocial;
import ar.turnos.odontologia.coberturas.domain.Plan;
import ar.turnos.odontologia.coberturas.application.LecturaCoberturas.AfiliacionDetalle;
import java.time.LocalDate;
import java.util.List;

/** Datos que devuelven los casos de uso del catálogo: records, nunca entidades JPA (regla dura 9). */
public final class Vistas {

    private Vistas() {
    }

    public record PlanVista(Long id, Long obraSocialId, String nombre, boolean activo) {
        static PlanVista de(Plan plan) {
            return new PlanVista(plan.id(), plan.obraSocialId(), plan.nombre(), plan.activo());
        }
    }

    public record ObraSocialVista(Long id, String nombre, String sigla, String tipo, boolean activa,
                                  List<PlanVista> planes) {
        static ObraSocialVista de(ObraSocial obra, List<Plan> planes) {
            return new ObraSocialVista(obra.id(), obra.nombre(), obra.sigla(), obra.tipo().name(), obra.activa(),
                    planes.stream().map(PlanVista::de).toList());
        }
    }

    public record PrestacionRef(Long id, String nombre) {}

    public record CodigoVista(Long id, String codigo, String descripcion, PrestacionRef prestacion, boolean activo) {
        static CodigoVista de(CodigoNomenclador c, String nombrePrestacion) {
            return new CodigoVista(c.id(), c.codigo(), c.descripcion(),
                    c.prestacionId() == null ? null : new PrestacionRef(c.prestacionId(), nombrePrestacion),
                    c.activo());
        }
    }

    /** La cobertura de un paciente en una fecha: su afiliación vigente o {@code particular} (RN-OS-04). */
    public record CoberturaVista(boolean particular, AfiliacionVista afiliacion) {}

    public record ObraSocialResumen(Long id, String nombre, String sigla, String tipo) {}

    public record PlanResumen(Long id, String nombre) {}

    /** Una afiliación con su plan y su obra social; {@code vigente} es si lo es en la fecha de la consulta. */
    public record AfiliacionVista(Long id, ObraSocialResumen obraSocial, PlanResumen plan, String numeroAfiliado,
                                  LocalDate vigenteDesde, LocalDate vigenteHasta, boolean vigente) {
        static AfiliacionVista de(AfiliacionDetalle d, LocalDate fecha) {
            boolean vigente = !fecha.isBefore(d.vigenteDesde()) && (d.vigenteHasta() == null
                    || !fecha.isAfter(d.vigenteHasta()));
            return new AfiliacionVista(d.id(), new ObraSocialResumen(d.obraSocialId(), d.obraSocial(), d.sigla(),
                    d.tipo()), new PlanResumen(d.planId(), d.plan()), d.numeroAfiliado(), d.vigenteDesde(),
                    d.vigenteHasta(), vigente);
        }
    }
}
