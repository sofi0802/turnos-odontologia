package ar.turnos.odontologia.reportes.domain;

import java.util.List;

/** Todo lo que lee la infraestructura para calcular un reporte (design D2). */
public record DatosReporte(List<ProfesionalLeido> profesionales, List<SillonLeido> sillones, List<HorarioLeido> horarios,
                           List<TurnoLeido> turnos, List<AusenciaLeida> ausencias, List<BloqueoLeido> bloqueos) {
}
