package ar.turnos.odontologia.reportes.api;

import ar.turnos.odontologia.reportes.domain.Reporte;
import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta de {@code GET /api/v1/reportes} (design D9). Solo cifras e identificadores y nombres de profesionales y
 * sillones: ningún dato de pacientes (RN-RE-02).
 */
record ReporteRespuesta(LocalDate desde, LocalDate hasta, Long profesionalId, OcupacionRespuesta ocupacion,
                        OrigenRespuesta origen, AusenciasRespuesta ausencias,
                        CancelacionesRespuesta cancelaciones) {

    record OcupacionRespuesta(List<FilaProfesional> profesionales, List<FilaSillon> sillones) {
    }

    record FilaProfesional(long profesionalId, String nombre, long minutosOcupados, long minutosAtencion,
                           Integer porcentaje) {
    }

    record FilaSillon(long sillonId, String nombre, long minutosOcupados, long minutosAtencion, Integer porcentaje) {
    }

    record OrigenRespuesta(int online, int consultorio, int sinDato) {
    }

    record AusenciasRespuesta(PacientesRespuesta pacientes, List<ProfesionalAusente> profesionales) {
    }

    record PacientesRespuesta(int ausentes, int atendidos, Integer tasa, int sinRegistrar) {
    }

    record ProfesionalAusente(long profesionalId, String nombre, String tipo, int cantidad, long minutos) {
    }

    record CancelacionesRespuesta(int porPaciente, int porConsultorio, int porReprogramacion, int sinDato) {
    }

    static ReporteRespuesta de(Reporte reporte) {
        var ocupacion = new OcupacionRespuesta(
                reporte.ocupacion().profesionales().stream()
                        .map(f -> new FilaProfesional(f.id(), f.nombre(), f.minutosOcupados(), f.minutosAtencion(),
                                f.porcentaje()))
                        .toList(),
                reporte.ocupacion().sillones().stream()
                        .map(f -> new FilaSillon(f.id(), f.nombre(), f.minutosOcupados(), f.minutosAtencion(),
                                f.porcentaje()))
                        .toList());
        var origen = reporte.origen();
        var pacientes = reporte.ausencias().pacientes();
        var ausencias = new AusenciasRespuesta(
                new PacientesRespuesta(pacientes.ausentes(), pacientes.atendidos(), pacientes.tasa(),
                        pacientes.sinRegistrar()),
                reporte.ausencias().profesionales().stream()
                        .map(a -> new ProfesionalAusente(a.profesionalId(), a.nombre(), a.tipo(), a.cantidad(),
                                a.minutos()))
                        .toList());
        var cancelaciones = reporte.cancelaciones();
        return new ReporteRespuesta(reporte.periodo().desde(), reporte.periodo().hasta(), reporte.profesionalId(),
                ocupacion, new OrigenRespuesta(origen.online(), origen.consultorio(), origen.sinDato()), ausencias,
                new CancelacionesRespuesta(cancelaciones.porPaciente(), cancelaciones.porConsultorio(),
                        cancelaciones.porReprogramacion(), cancelaciones.sinDato()));
    }
}
