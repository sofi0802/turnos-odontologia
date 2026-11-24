package ar.turnos.odontologia.coberturas.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Afiliación de un paciente a un plan con su número y su vigencia (RN-OS-03 a RN-OS-05). La obra social es la del plan:
 * se guarda aparte para que la base pueda exigir un número único por obra social (design D3).
 */
public record Afiliacion(Long id, Long pacienteId, Long planId, Long obraSocialId, NumeroAfiliado numero,
                         Vigencia vigencia) {

    public Afiliacion {
        Objects.requireNonNull(pacienteId, "pacienteId");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(obraSocialId, "obraSocialId");
        Objects.requireNonNull(numero, "numero");
        Objects.requireNonNull(vigencia, "vigencia");
    }

    /**
     * {@code true} si es otra afiliación del mismo paciente cuya vigencia comparte algún día con esta. Una afiliación
     * no se superpone consigo misma, así al corregirla no choca con su versión anterior.
     */
    public boolean seSuperponeCon(Afiliacion otra) {
        boolean esLaMisma = id != null && id.equals(otra.id);
        return !esLaMisma && pacienteId.equals(otra.pacienteId) && vigencia.seSuperponeCon(otra.vigencia);
    }

    public Afiliacion corregida(Long planNuevo, Long obraSocialNueva, NumeroAfiliado numeroNuevo, Vigencia vigenciaNueva) {
        return new Afiliacion(id, pacienteId, planNuevo, obraSocialNueva, numeroNuevo, vigenciaNueva);
    }

    public Afiliacion cerradaEn(LocalDate fin) {
        return new Afiliacion(id, pacienteId, planId, obraSocialId, numero, vigencia.cerrarEn(fin));
    }
}
