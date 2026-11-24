package ar.turnos.odontologia.clinica.api;

import ar.turnos.odontologia.clinica.application.RegistroPeriodontal.ResumenVersion;
import ar.turnos.odontologia.clinica.application.RegistroPeriodontal.Version;
import ar.turnos.odontologia.clinica.application.ServicioPeriodontograma.PiezaPedido;
import ar.turnos.odontologia.clinica.application.ServicioPeriodontograma.SitioPedido;
import ar.turnos.odontologia.clinica.domain.EstadoPeriodontal;
import ar.turnos.odontologia.clinica.domain.MedicionSitio;
import ar.turnos.odontologia.clinica.domain.PiezaPeriodontal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * DTOs del periodontograma (C-25, design D1 y D5). Bean Validation cubre tipos y rangos y responde
 * {@code DATOS_INVALIDOS} con solo el campo; las reglas que cruzan datos las valida el dominio. Nunca se devuelven
 * entidades (regla dura 9).
 */
final class PeriodontogramaDtos {

    private PeriodontogramaDtos() {}

    private static OffsetDateTime local(Instant instante, ZoneId zona) {
        return instante.atZone(zona).toOffsetDateTime();
    }

    /** Pedido de una versión nueva: de 1 a 32 piezas. */
    record PeriodontogramaSolicitud(@NotNull @Size(min = 1, max = 32) List<@NotNull @Valid PiezaDto> piezas) {

        List<PiezaPedido> aPedido() {
            return piezas.stream().map(PiezaDto::aPedido).toList();
        }
    }

    record PiezaDto(@NotNull Integer pieza,
                    Boolean ausente,
                    @Min(0) @Max(3) Integer movilidad,
                    @Min(0) @Max(3) Integer furcacion,
                    @Size(max = 6) List<@NotNull @Valid SitioDto> sitios) {

        PiezaPedido aPedido() {
            List<SitioPedido> pedidos = sitios == null ? List.of() : sitios.stream().map(SitioDto::aPedido).toList();
            return new PiezaPedido(pieza, Boolean.TRUE.equals(ausente), movilidad, furcacion, pedidos);
        }
    }

    record SitioDto(@NotNull String sitio,
                    @NotNull @Min(MedicionSitio.PROFUNDIDAD_MINIMA) @Max(MedicionSitio.PROFUNDIDAD_MAXIMA)
                    Integer profundidadSondaje,
                    @NotNull @Min(MedicionSitio.RECESION_MINIMA) @Max(MedicionSitio.RECESION_MAXIMA) Integer recesion,
                    @NotNull Boolean sangrado,
                    @NotNull Boolean placa) {

        SitioPedido aPedido() {
            return new SitioPedido(sitio, profundidadSondaje, recesion, sangrado, placa);
        }
    }

    /** Una versión en la lista: sin piezas. */
    record ResumenVista(Long id, Long profesionalId, String profesional, OffsetDateTime registradoEn) {

        static ResumenVista de(ResumenVersion resumen, ZoneId zona) {
            return new ResumenVista(resumen.id(), resumen.profesionalId(), resumen.profesional(),
                    local(resumen.registradoEn(), zona));
        }

        static List<ResumenVista> de(List<ResumenVersion> versiones, ZoneId zona) {
            return versiones.stream().map(v -> de(v, zona)).toList();
        }
    }

    record VersionVista(Long id, Long profesionalId, String profesional, OffsetDateTime registradoEn,
                        List<PiezaVista> piezas) {

        static VersionVista de(Version version, ZoneId zona) {
            ResumenVersion resumen = version.resumen();
            return new VersionVista(resumen.id(), resumen.profesionalId(), resumen.profesional(),
                    local(resumen.registradoEn(), zona), version.piezas().stream().map(PiezaVista::de).toList());
        }
    }

    /** {@code estado} es {@code null} en una pieza ausente (RN-CL-06). */
    record PiezaVista(int pieza, boolean ausente, Integer movilidad, Integer furcacion, String estado,
                      List<SitioVista> sitios) {

        static PiezaVista de(PiezaPeriodontal pieza) {
            EstadoPeriodontal estado = pieza.estado();
            return new PiezaVista(pieza.pieza().numero(), pieza.ausente(), pieza.movilidad(), pieza.furcacion(),
                    estado == null ? null : estado.name(), pieza.sitios().stream().map(SitioVista::de).toList());
        }
    }

    record SitioVista(String sitio, int profundidadSondaje, int recesion, boolean sangrado, boolean placa) {

        static SitioVista de(MedicionSitio sitio) {
            return new SitioVista(sitio.sitio().name(), sitio.profundidadSondaje(), sitio.recesion(),
                    sitio.sangrado(), sitio.placa());
        }
    }
}
