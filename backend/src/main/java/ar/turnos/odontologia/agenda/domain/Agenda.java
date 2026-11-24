package ar.turnos.odontologia.agenda.domain;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Reglas de conflicto de la agenda (RN-AG-02 a RN-AG-05).
 *
 * <p>Si hay más de un conflicto, informa uno solo con prioridad fija:
 * {@code SOLAPAMIENTO_PROFESIONAL}, después {@code SOLAPAMIENTO_SILLON} y por último
 * {@code MARGEN_INSUFICIENTE}. Así el resultado no depende del orden de los turnos.
 */
public final class Agenda {

    private final Margen margen;

    public Agenda(Margen margen) {
        this.margen = Objects.requireNonNull(margen, "margen");
    }

    /**
     * Lanza {@link TurnoRechazadoException} si el turno nuevo entra en conflicto con algún
     * turno activo existente que comparte profesional o sillón.
     */
    public void verificar(Turno nuevo, Collection<Turno> existentes) {
        buscarConflicto(nuevo, existentes).ifPresent(codigo -> {
            throw new TurnoRechazadoException(codigo, mensajePara(codigo));
        });
    }

    /**
     * C-08: verificación de sobreturnos. Ignora las colisiones por profesional (RN-AG-06), pero mantiene
     * la restricción del sillón y su margen de limpieza (RN-AG-03 y RN-AG-04).
     */
    public void verificarSillon(Turno nuevo, Collection<Turno> existentes) {
        if (existentes.stream().anyMatch(e -> conflictoConSillon(nuevo, e) != ConflictoSillon.NINGUNO)) {
            throw new TurnoRechazadoException(CodigoRechazo.SIN_SILLON_DISPONIBLE,
                    "No hay un sillón libre con el margen de " + margen.valor().toMinutes() + " minutos");
        }
    }

    /**
     * C-43: verificación al deshacer una ausencia. Un turno normal se verifica como uno nuevo; un sobreturno, solo
     * contra el sillón y su margen, como al crearlo, pero con los códigos de superposición y no
     * {@code SIN_SILLON_DISPONIBLE}, porque el sillón es el suyo.
     */
    public void verificarRestauracion(Turno restaurado, Collection<Turno> existentes) {
        buscarConflicto(restaurado, existentes, !restaurado.esSobreturno()).ifPresent(codigo -> {
            throw new TurnoRechazadoException(codigo, mensajePara(codigo));
        });
    }

    Optional<CodigoRechazo> buscarConflicto(Turno nuevo, Collection<Turno> existentes) {
        return buscarConflicto(nuevo, existentes, true);
    }

    private Optional<CodigoRechazo> buscarConflicto(Turno nuevo, Collection<Turno> existentes,
                                                    boolean porProfesional) {
        boolean solapaProfesional = false;
        boolean solapaSillon = false;
        boolean faltaMargen = false;

        for (Turno existente : existentes) {
            if (!existente.esActivo()) {
                continue;
            }
            boolean compartePorProfesional = porProfesional && nuevo.mismoProfesionalQue(existente);
            boolean seSuperpone = nuevo.intervalo().seSuperponeCon(existente.intervalo());
            if (compartePorProfesional && seSuperpone) {
                solapaProfesional = true;
            }
            if (nuevo.mismoSillonQue(existente)) {
                switch (conflictoConSillon(nuevo, existente)) {
                    case SOLAPE -> solapaSillon = true;
                    case MARGEN -> faltaMargen = true;
                    case NINGUNO -> { }
                }
            } else if (compartePorProfesional && !seSuperpone
                    && nuevo.intervalo().seAcercaA(existente.intervalo(), margen)) {
                faltaMargen = true;
            }
        }

        if (solapaProfesional) {
            return Optional.of(CodigoRechazo.SOLAPAMIENTO_PROFESIONAL);
        }
        if (solapaSillon) {
            return Optional.of(CodigoRechazo.SOLAPAMIENTO_SILLON);
        }
        if (faltaMargen) {
            return Optional.of(CodigoRechazo.MARGEN_INSUFICIENTE);
        }
        return Optional.empty();
    }

    /** Cálculo compartido por la verificación normal y por la de sobreturnos. */
    private ConflictoSillon conflictoConSillon(Turno nuevo, Turno existente) {
        if (!existente.esActivo() || !nuevo.mismoSillonQue(existente)) {
            return ConflictoSillon.NINGUNO;
        }
        if (nuevo.intervalo().seSuperponeCon(existente.intervalo())) {
            return ConflictoSillon.SOLAPE;
        }
        if (nuevo.intervalo().seAcercaA(existente.intervalo(), margen)) {
            return ConflictoSillon.MARGEN;
        }
        return ConflictoSillon.NINGUNO;
    }

    private enum ConflictoSillon { NINGUNO, SOLAPE, MARGEN }

    private String mensajePara(CodigoRechazo codigo) {
        return switch (codigo) {
            case SOLAPAMIENTO_PROFESIONAL -> "El profesional ya tiene un turno en ese horario";
            case SOLAPAMIENTO_SILLON -> "El sillón ya está ocupado en ese horario";
            case MARGEN_INSUFICIENTE -> "No queda el margen de " + margen.valor().toMinutes()
                    + " minutos con otro turno del mismo profesional o sillón";
            default -> codigo.name();
        };
    }
}
