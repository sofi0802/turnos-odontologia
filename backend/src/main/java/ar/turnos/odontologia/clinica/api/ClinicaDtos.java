package ar.turnos.odontologia.clinica.api;

import ar.turnos.odontologia.agenda.domain.Turno;
import ar.turnos.odontologia.clinica.application.AusenciasPaciente.Ausencia;
import ar.turnos.odontologia.clinica.application.RegistroClinico.Ficha;
import ar.turnos.odontologia.clinica.application.RegistroClinico.RegistroOdontograma;
import ar.turnos.odontologia.clinica.application.ServicioFicha.FichaCompleta;
import ar.turnos.odontologia.clinica.application.ServicioOdontograma.CambioPedido;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Pedidos y respuestas de la API clínica (C-13). Records, nunca entidades JPA (regla dura 9). Ningún record tiene
 * un {@code toString} propio que se use en logs: el módulo no loguea pedidos (design D9).
 */
final class ClinicaDtos {

    private ClinicaDtos() {}

    private static OffsetDateTime local(Instant instante, ZoneId zona) {
        return instante == null ? null : instante.atZone(zona).toOffsetDateTime();
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    record FichaSolicitud(
            @Size(max = 4000) String antecedentes,
            @Size(max = 4000) String alergias,
            @Size(max = 4000) String medicacion) {}

    record AusenciaVista(Long turnoId, OffsetDateTime inicio, Long profesionalId, String profesional) {

        static AusenciaVista de(Ausencia a, ZoneId zona) {
            return new AusenciaVista(a.turnoId(), local(a.inicio(), zona), a.profesionalId(), a.profesional());
        }
    }

    /** Un paciente sin ficha la recibe con los textos vacíos y sin firma (design D2). */
    record FichaRespuesta(Long pacienteId, String antecedentes, String alergias, String medicacion,
                          String actualizadoPor, OffsetDateTime actualizadoEn, List<AusenciaVista> ausencias) {

        static FichaRespuesta de(FichaCompleta completa, ZoneId zona) {
            Ficha f = completa.ficha();
            return new FichaRespuesta(f.pacienteId(), texto(f.antecedentes()), texto(f.alergias()),
                    texto(f.medicacion()), f.actualizadoPor(), local(f.actualizadoEn(), zona),
                    completa.ausencias().stream().map(a -> AusenciaVista.de(a, zona)).toList());
        }
    }

    /** {@code cara} nula u omitida es la pieza completa. */
    record CambioDto(@NotNull Integer pieza, String cara, @NotNull String estado) {

        CambioPedido aPedido() {
            return new CambioPedido(pieza, cara, estado);
        }
    }

    record OdontogramaSolicitud(@NotNull @Size(min = 1, max = 32) List<@NotNull @Valid CambioDto> cambios) {}

    record RegistroVista(Long id, int pieza, String cara, String estado, Long profesionalId, String profesional,
                         OffsetDateTime registradoEn) {

        static RegistroVista de(RegistroOdontograma r, ZoneId zona) {
            return new RegistroVista(r.id(), r.pieza(), r.cara() == null ? null : r.cara().name(), r.estado().name(),
                    r.profesionalId(), r.profesional(), local(r.registradoEn(), zona));
        }

        static List<RegistroVista> de(List<RegistroOdontograma> registros, ZoneId zona) {
            return registros.stream().map(r -> de(r, zona)).toList();
        }
    }

    record AusenciaSolicitud(@NotNull Long turnoId) {}

    record TurnoAusente(Long turnoId, OffsetDateTime inicio, String estado) {

        static TurnoAusente de(Turno turno, ZoneId zona) {
            return new TurnoAusente(turno.id(), local(turno.intervalo().inicio(), zona), turno.estado().name());
        }
    }
}
