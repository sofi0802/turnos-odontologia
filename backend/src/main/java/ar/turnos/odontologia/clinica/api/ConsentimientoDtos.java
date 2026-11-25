package ar.turnos.odontologia.clinica.api;

import ar.turnos.odontologia.clinica.application.RegistroConsentimientos.Consentimiento;
import ar.turnos.odontologia.clinica.application.RegistroConsentimientos.Revocacion;
import ar.turnos.odontologia.clinica.application.RegistroPlantillas.Plantilla;
import ar.turnos.odontologia.clinica.application.RegistroPlantillas.Version;
import ar.turnos.odontologia.clinica.application.ServicioConsentimientos.Verificacion;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Formas de la API de consentimientos (C-27, design D2): el contrato que usa C-48. Son records propios, nunca entidades
 * ni filas de la base. La firma es electrónica ({@code tipoFirma: "ELECTRONICA"}), no una firma digital.
 */
final class ConsentimientoDtos {

    private ConsentimientoDtos() {
    }

    // ---------------------------------------------------------------- Pedidos

    record PlantillaSolicitud(String titulo, String texto) {}

    record EdicionSolicitud(Integer versionBase, String titulo, String texto) {}

    record EstadoSolicitud(Boolean activa) {}

    record FirmaSolicitud(Long plantillaId, Integer versionNumero, String firmanteNombre, String firmanteDni,
                          Boolean aceptado) {}

    record RevocacionSolicitud(String motivo) {}

    // ---------------------------------------------------------------- Plantillas

    record PlantillaResumen(long id, boolean activa, int versionActual, String titulo, OffsetDateTime actualizadaEn) {

        static List<PlantillaResumen> de(List<Plantilla> plantillas, ZoneId zona) {
            return plantillas.stream().map(p -> new PlantillaResumen(p.id(), p.activa(), p.ultima().numero(),
                    p.ultima().titulo(), fecha(p.ultima().creadaEn(), zona))).toList();
        }
    }

    record PlantillaVista(long id, boolean activa, VersionVista version) {

        static PlantillaVista de(Plantilla plantilla, ZoneId zona) {
            return new PlantillaVista(plantilla.id(), plantilla.activa(), VersionVista.de(plantilla.ultima(), zona));
        }
    }

    record VersionVista(int numero, String titulo, String texto, OffsetDateTime creadaEn, String creadaPor) {

        static VersionVista de(Version version, ZoneId zona) {
            return new VersionVista(version.numero(), version.titulo(), version.texto(), fecha(version.creadaEn(), zona),
                    version.creadaPor());
        }

        static List<VersionVista> de(List<Version> versiones, ZoneId zona) {
            return versiones.stream().map(v -> de(v, zona)).toList();
        }
    }

    // ---------------------------------------------------------------- Consentimientos

    /** Sin el texto ni el firmante. */
    record ConsentimientoResumen(Long id, Long plantillaId, int versionNumero, String titulo, OffsetDateTime firmadoEn,
                                 Long profesionalId, String profesional, String estado) {

        static List<ConsentimientoResumen> de(List<Consentimiento> consentimientos, ZoneId zona) {
            return consentimientos.stream().map(c -> new ConsentimientoResumen(c.id(), c.plantillaId(),
                    c.versionNumero(), c.titulo(), fecha(c.firmadoEn(), zona), c.profesionalId(), c.profesional(),
                    estadoDe(c))).toList();
        }
    }

    record ConsentimientoVista(Long id, Long plantillaId, int versionNumero, String titulo, OffsetDateTime firmadoEn,
                               Long profesionalId, String profesional, String estado, String texto,
                               FirmanteVista firmante, EvidenciaVista evidencia, String tipoFirma, HashVista hash,
                               RevocacionVista revocacion) {

        static ConsentimientoVista de(Consentimiento c, ZoneId zona) {
            return new ConsentimientoVista(c.id(), c.plantillaId(), c.versionNumero(), c.titulo(),
                    fecha(c.firmadoEn(), zona), c.profesionalId(), c.profesional(), estadoDe(c), c.texto(),
                    new FirmanteVista(c.firmanteNombre(), c.firmanteDni()),
                    new EvidenciaVista(c.ipOrigen(), c.dispositivo()), c.tipoFirma(),
                    new HashVista(c.hashAlgoritmo(), c.formatoCanonico(), c.hash()),
                    RevocacionVista.de(c.revocacion(), zona));
        }
    }

    record FirmanteVista(String nombre, String dni) {}

    record EvidenciaVista(String ip, String dispositivo) {}

    /** @param formato la versión del contenido canónico (hoy, 1) */
    record HashVista(String algoritmo, int formato, String valor) {}

    record RevocacionVista(Long profesionalId, String profesional, OffsetDateTime revocadoEn, String motivo) {

        static RevocacionVista de(Revocacion revocacion, ZoneId zona) {
            if (revocacion == null) {
                return null;
            }
            return new RevocacionVista(revocacion.profesionalId(), revocacion.profesional(),
                    fecha(revocacion.revocadoEn(), zona), revocacion.motivo());
        }
    }

    record VerificacionVista(Long consentimientoId, boolean integro, String algoritmo, String hashGuardado,
                             String hashCalculado, OffsetDateTime verificadoEn) {

        static VerificacionVista de(Verificacion v, ZoneId zona) {
            return new VerificacionVista(v.consentimientoId(), v.integro(), v.algoritmo(), v.hashGuardado(),
                    v.hashCalculado(), fecha(v.verificadoEn(), zona));
        }
    }

    private static String estadoDe(Consentimiento consentimiento) {
        return consentimiento.revocado() ? "REVOCADO" : "VIGENTE";
    }

    private static OffsetDateTime fecha(Instant instante, ZoneId zona) {
        return instante.atZone(zona).toOffsetDateTime();
    }
}
