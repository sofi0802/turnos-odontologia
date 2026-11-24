package ar.turnos.odontologia.reportes.application;

import ar.turnos.odontologia.reportes.domain.CalculoReportes;
import ar.turnos.odontologia.reportes.domain.Periodo;
import ar.turnos.odontologia.reportes.domain.Reporte;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.transaction.annotation.Transactional;

/** Caso de uso de US-210: valida el período y el filtro, lee los datos y calcula el reporte (C-32). */
public class ConsultaReportes {

    private final LecturaReportes lectura;
    private final CalculoReportes calculo;
    private final ZoneId zona;
    private final Clock reloj;

    public ConsultaReportes(LecturaReportes lectura, ZoneId zona, Clock reloj) {
        this.lectura = lectura;
        this.calculo = new CalculoReportes(zona);
        this.zona = zona;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Reporte consultar(LocalDate desde, LocalDate hasta, Long profesionalId) {
        Periodo periodo = Periodo.de(desde, hasta);
        if (profesionalId != null && !lectura.existeProfesional(profesionalId)) {
            throw new ProfesionalInexistenteException();
        }
        return calculo.calcular(periodo, profesionalId, lectura.leer(periodo.intervalo(zona), profesionalId),
                Instant.now(reloj));
    }
}
