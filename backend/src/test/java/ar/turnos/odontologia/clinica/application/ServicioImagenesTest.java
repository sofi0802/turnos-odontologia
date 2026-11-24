package ar.turnos.odontologia.clinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.clinica.application.RegistroImagenes.Anulacion;
import ar.turnos.odontologia.clinica.application.RegistroImagenes.Imagen;
import ar.turnos.odontologia.clinica.application.ServicioImagenes.ArchivoImagen;
import ar.turnos.odontologia.clinica.domain.CategoriaImagen;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.clinica.domain.ImagenClinica;
import ar.turnos.odontologia.clinica.domain.TipoArchivoClinico;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Casos de uso de las imágenes clínicas sobre puertos en memoria (C-26, tarea 5.1). Archivos ficticios. */
class ServicioImagenesTest {

    private static final long PACIENTE_UNO = 1L;
    private static final long PACIENTE_DOS = 2L;
    private static final long DRA_A = 10L;
    private static final long DR_B = 11L;
    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    /** 2026-12-01 a las 09:00 en Buenos Aires. */
    private static final Instant NUEVE = Instant.parse("2026-12-01T12:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 12, 1);

    private final RegistroEnMemoria registro = new RegistroEnMemoria();
    private final AlmacenEnMemoria almacen = new AlmacenEnMemoria();
    private final ServicioImagenes servicio = new ServicioImagenes(new PacientesEnMemoria(), registro, almacen,
            Clock.fixed(NUEVE, BUENOS_AIRES), BUENOS_AIRES);

    @Test
    @DisplayName("Cargar una radiografía de una pieza")
    void cargarUnaRadiografia() {
        byte[] archivo = jpeg(300);

        Imagen imagen = servicio.cargar(PACIENTE_UNO, DRA_A, "RADIOGRAFIA", 36, HOY.minusDays(1), null, archivo);

        assertThat(imagen.categoria()).isEqualTo(CategoriaImagen.RADIOGRAFIA);
        assertThat(imagen.pieza()).isEqualTo(36);
        assertThat(imagen.tipo()).isEqualTo(TipoArchivoClinico.JPEG);
        assertThat(imagen.tamanoBytes()).isEqualTo(300);
        assertThat(imagen.profesionalId()).isEqualTo(DRA_A);
        assertThat(imagen.registradaEn()).isEqualTo(NUEVE);
        assertThat(imagen.sha256()).isEqualTo(ServicioImagenes.sha256(archivo)).hasSize(64);
        assertThat(almacen.archivos).containsKey(imagen.claveAlmacen());
    }

    @Test
    @DisplayName("Un pedido inválido no guarda archivo ni fila")
    void pedidoInvalidoNoGuardaNada() {
        assertCodigo(() -> servicio.cargar(PACIENTE_UNO, DRA_A, "RADIOGRAFIA", 19, HOY, null, jpeg(10)),
                CodigoClinica.PIEZA_INVALIDA);
        assertCodigo(() -> servicio.cargar(PACIENTE_UNO, DRA_A, "RADIOGRAFIA", null, HOY.plusDays(1), null, jpeg(10)),
                CodigoClinica.DATOS_INVALIDOS);

        assertThat(almacen.archivos).isEmpty();
        assertThat(registro.filas).isEmpty();
    }

    @Test
    @DisplayName("Paciente inexistente")
    void pacienteInexistente() {
        assertCodigo(() -> servicio.cargar(999_999L, DRA_A, "RADIOGRAFIA", null, HOY, null, jpeg(10)),
                CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.listar(999_999L, null, false), CodigoClinica.RECURSO_NO_ENCONTRADO);
    }

    @Test
    @DisplayName("Odontólogo sin profesional vinculado")
    void odontologoSinProfesional() {
        assertThatThrownBy(() -> servicio.cargar(PACIENTE_UNO, null, "RADIOGRAFIA", null, HOY, null, jpeg(10)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(almacen.archivos).isEmpty();
    }

    @Test
    @DisplayName("Si falla la inserción, el archivo se borra del almacén")
    void compensacion() {
        registro.fallarAlRegistrar = true;

        assertThatThrownBy(() -> servicio.cargar(PACIENTE_UNO, DRA_A, "ESTUDIO", null, HOY, null, pdf(20)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(almacen.guardados).isEqualTo(1);
        assertThat(almacen.archivos).isEmpty();
    }

    @Test
    @DisplayName("Listar filtra por pieza y oculta las anuladas salvo que se pidan")
    void listar() {
        Imagen de36 = servicio.cargar(PACIENTE_UNO, DRA_A, "RADIOGRAFIA", 36, HOY, null, jpeg(10));
        Imagen de11 = servicio.cargar(PACIENTE_UNO, DRA_A, "FOTOGRAFIA", 11, HOY, null, png(10));
        servicio.anular(PACIENTE_UNO, de11.id(), DRA_A, "Paciente equivocado");

        assertThat(servicio.listar(PACIENTE_UNO, null, false)).extracting(Imagen::id).containsExactly(de36.id());
        assertThat(servicio.listar(PACIENTE_UNO, null, true)).extracting(Imagen::id)
                .containsExactlyInAnyOrder(de36.id(), de11.id());
        assertThat(servicio.listar(PACIENTE_UNO, 11, true)).extracting(Imagen::id).containsExactly(de11.id());
        assertThat(servicio.listar(PACIENTE_DOS, null, true)).isEmpty();
    }

    @Test
    @DisplayName("El archivo se entrega idéntico y también si está anulada")
    void archivo() {
        byte[] contenido = pdf(50);
        Imagen imagen = servicio.cargar(PACIENTE_UNO, DRA_A, "ESTUDIO", null, HOY, null, contenido);
        servicio.anular(PACIENTE_UNO, imagen.id(), DRA_A, "Duplicada");

        ArchivoImagen archivo = servicio.archivo(PACIENTE_UNO, imagen.id());

        assertThat(archivo.contenido()).isEqualTo(contenido);
        assertThat(archivo.imagen().tipo()).isEqualTo(TipoArchivoClinico.PDF);
    }

    @Test
    @DisplayName("Imagen de otro paciente")
    void imagenDeOtroPaciente() {
        Imagen imagen = servicio.cargar(PACIENTE_UNO, DRA_A, "RADIOGRAFIA", null, HOY, null, jpeg(10));

        assertCodigo(() -> servicio.archivo(PACIENTE_DOS, imagen.id()), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertCodigo(() -> servicio.anular(PACIENTE_DOS, imagen.id(), DRA_A, "x"), CodigoClinica.RECURSO_NO_ENCONTRADO);
    }

    @Test
    @DisplayName("Archivo alterado en el almacén")
    void archivoAlterado() {
        Imagen imagen = servicio.cargar(PACIENTE_UNO, DRA_A, "RADIOGRAFIA", null, HOY, null, jpeg(10));
        almacen.archivos.get(imagen.claveAlmacen())[5] ^= 0x01;

        assertThatThrownBy(() -> servicio.archivo(PACIENTE_UNO, imagen.id()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Anular una imagen")
    void anularUnaImagen() {
        Imagen imagen = servicio.cargar(PACIENTE_UNO, DRA_A, "RADIOGRAFIA", null, HOY, null, jpeg(10));

        Imagen anulada = servicio.anular(PACIENTE_UNO, imagen.id(), DR_B, "Se cargó en el paciente equivocado");

        assertThat(anulada.anulacion().profesionalId()).isEqualTo(DR_B);
        assertThat(anulada.anulacion().anuladaEn()).isEqualTo(NUEVE);
        assertThat(anulada.anulacion().motivo()).isEqualTo("Se cargó en el paciente equivocado");
    }

    @Test
    @DisplayName("Anular dos veces")
    void anularDosVeces() {
        Imagen imagen = servicio.cargar(PACIENTE_UNO, DRA_A, "RADIOGRAFIA", null, HOY, null, jpeg(10));
        servicio.anular(PACIENTE_UNO, imagen.id(), DRA_A, "Primera");

        assertCodigo(() -> servicio.anular(PACIENTE_UNO, imagen.id(), DR_B, "Segunda"), CodigoClinica.IMAGEN_YA_ANULADA);
        assertThat(registro.filas.get(imagen.id()).anulacion().profesionalId()).isEqualTo(DRA_A);
    }

    @Test
    @DisplayName("Anular sin motivo")
    void anularSinMotivo() {
        Imagen imagen = servicio.cargar(PACIENTE_UNO, DRA_A, "RADIOGRAFIA", null, HOY, null, jpeg(10));

        assertCodigo(() -> servicio.anular(PACIENTE_UNO, imagen.id(), DRA_A, " "), CodigoClinica.DATOS_INVALIDOS);
        assertThat(registro.filas.get(imagen.id()).anulada()).isFalse();
    }

    @Test
    @DisplayName("Anular una imagen inexistente")
    void anularInexistente() {
        assertCodigo(() -> servicio.anular(PACIENTE_UNO, 999_999L, DRA_A, "x"), CodigoClinica.RECURSO_NO_ENCONTRADO);
        assertThatThrownBy(() -> servicio.anular(PACIENTE_UNO, 999_999L, null, "x"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static byte[] jpeg(int tamano) {
        return conFirma(tamano, 0xFF, 0xD8, 0xFF, 0xE0);
    }

    private static byte[] png(int tamano) {
        return conFirma(tamano, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    }

    private static byte[] pdf(int tamano) {
        return conFirma(tamano, 0x25, 0x50, 0x44, 0x46, 0x2D);
    }

    private static byte[] conFirma(int tamano, int... firma) {
        byte[] contenido = new byte[tamano];
        for (int i = 0; i < tamano; i++) {
            contenido[i] = i < firma.length ? (byte) firma[i] : (byte) (i % 251);
        }
        return contenido;
    }

    private static void assertCodigo(ThrowingCallable accion, CodigoClinica codigo) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ClinicaException.class,
                e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    private static final class PacientesEnMemoria implements RegistroClinico {

        @Override
        public boolean existePaciente(Long pacienteId) {
            return pacienteId == PACIENTE_UNO || pacienteId == PACIENTE_DOS;
        }

        @Override
        public Optional<Ficha> ficha(Long pacienteId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Ficha guardarFicha(Ficha ficha) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registrarCambios(Long pacienteId, Long profesionalId, Instant momento, List<Cambio> cambios) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RegistroOdontograma> vigente(Long pacienteId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RegistroOdontograma> historial(Long pacienteId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class AlmacenEnMemoria implements AlmacenArchivos {

        final Map<String, byte[]> archivos = new HashMap<>();
        int guardados;

        @Override
        public String guardar(byte[] contenido) {
            guardados++;
            String clave = "aa/bb/clave-" + guardados;
            archivos.put(clave, contenido.clone());
            return clave;
        }

        @Override
        public byte[] leer(String clave) {
            return archivos.get(clave).clone();
        }

        @Override
        public void borrar(String clave) {
            archivos.remove(clave);
        }
    }

    private static final class RegistroEnMemoria implements RegistroImagenes {

        final Map<Long, Imagen> filas = new HashMap<>();
        boolean fallarAlRegistrar;
        private long siguiente = 1;

        @Override
        public long registrar(Long pacienteId, Long profesionalId, Instant momento, ImagenClinica imagen,
                              String sha256, String claveAlmacen) {
            if (fallarAlRegistrar) {
                throw new IllegalStateException("La base no está disponible");
            }
            long id = siguiente++;
            filas.put(id, new Imagen(id, pacienteId, imagen.categoria(), imagen.pieza(), imagen.tomadaEl(),
                    imagen.descripcion(), imagen.tipo(), imagen.tamanoBytes(), sha256, claveAlmacen, profesionalId,
                    "Profesional " + profesionalId, momento, null));
            return id;
        }

        @Override
        public List<Imagen> listar(Long pacienteId, Integer pieza, boolean incluirAnuladas) {
            List<Imagen> lista = new ArrayList<>();
            for (Imagen imagen : filas.values()) {
                if (imagen.pacienteId().equals(pacienteId) && (pieza == null || pieza.equals(imagen.pieza()))
                        && (incluirAnuladas || !imagen.anulada())) {
                    lista.add(imagen);
                }
            }
            lista.sort(Comparator.comparing(Imagen::tomadaEl).thenComparing(Imagen::id).reversed());
            return lista;
        }

        @Override
        public Optional<Imagen> buscar(Long pacienteId, Long imagenId) {
            return Optional.ofNullable(filas.get(imagenId)).filter(i -> i.pacienteId().equals(pacienteId));
        }

        @Override
        public boolean anular(Long pacienteId, Long imagenId, Long profesionalId, Instant momento, String motivo) {
            Imagen imagen = buscar(pacienteId, imagenId).orElse(null);
            if (imagen == null || imagen.anulada()) {
                return false;
            }
            filas.put(imagenId, new Imagen(imagen.id(), imagen.pacienteId(), imagen.categoria(), imagen.pieza(),
                    imagen.tomadaEl(), imagen.descripcion(), imagen.tipo(), imagen.tamanoBytes(), imagen.sha256(),
                    imagen.claveAlmacen(), imagen.profesionalId(), imagen.profesional(), imagen.registradaEn(),
                    new Anulacion(profesionalId, "Profesional " + profesionalId, momento, motivo)));
            return true;
        }
    }
}
