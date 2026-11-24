package ar.turnos.odontologia.clinica.api;

import ar.turnos.odontologia.clinica.application.RegistroImagenes.Anulacion;
import ar.turnos.odontologia.clinica.application.RegistroImagenes.Imagen;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Formas de la API de imágenes clínicas (C-26, design D1). Nunca exponen la clave del almacén ni el id del paciente:
 * solo los metadatos que la pantalla necesita.
 */
final class ImagenClinicaDtos {

    private ImagenClinicaDtos() {
    }

    record AnulacionSolicitud(String motivo) {}

    record ImagenVista(Long id, String categoria, Integer pieza, LocalDate tomadaEl, String descripcion,
                       String tipoContenido, int tamanoBytes, String sha256, Long profesionalId, String profesional,
                       OffsetDateTime registradaEn, AnulacionVista anulacion) {

        static ImagenVista de(Imagen imagen, ZoneId zona) {
            return new ImagenVista(imagen.id(), imagen.categoria().name(), imagen.pieza(), imagen.tomadaEl(),
                    imagen.descripcion(), imagen.tipo().tipoContenido(), imagen.tamanoBytes(), imagen.sha256(),
                    imagen.profesionalId(), imagen.profesional(), imagen.registradaEn().atZone(zona).toOffsetDateTime(),
                    AnulacionVista.de(imagen.anulacion(), zona));
        }

        static List<ImagenVista> de(List<Imagen> imagenes, ZoneId zona) {
            return imagenes.stream().map(i -> de(i, zona)).toList();
        }
    }

    record AnulacionVista(Long profesionalId, String profesional, OffsetDateTime anuladaEn, String motivo) {

        static AnulacionVista de(Anulacion anulacion, ZoneId zona) {
            if (anulacion == null) {
                return null;
            }
            return new AnulacionVista(anulacion.profesionalId(), anulacion.profesional(),
                    anulacion.anuladaEn().atZone(zona).toOffsetDateTime(), anulacion.motivo());
        }
    }
}
