package ar.turnos.odontologia.coberturas.application;

import java.util.List;

/**
 * Puerto de solo lectura sobre el odontograma del paciente (C-19, design D8). Lo implementa un adaptador JDBC propio de
 * este módulo: no usa ni modifica código de {@code clinica/}, que es de otro dueño.
 */
public interface OdontogramaVigente {

    /**
     * Las piezas y caras cuyo último estado es {@code A_TRATAR} o {@code CARIES}, ordenadas por pieza y cara (la pieza
     * completa, sin cara, primero).
     */
    List<PiezaVigente> aTratar(Long pacienteId);

    record PiezaVigente(int pieza, String cara, String estado) {}
}
