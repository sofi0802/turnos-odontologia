package ar.turnos.odontologia.clinica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import ar.turnos.odontologia.shared.security.Navegador;
import ar.turnos.odontologia.shared.security.UsuarioAutenticado;
import ar.turnos.odontologia.usuarios.domain.Rol;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Imágenes y radiografías de punta a punta (C-26, tareas 6.2 y 6.3): PostgreSQL real, sesión y CSRF de verdad, y el
 * almacén en una carpeta temporal. Cada escenario de {@code specs/imagenes-clinicas/spec.md} tiene su test con el
 * mismo nombre. Los archivos son bytes ficticios con la firma de cada tipo, nunca imágenes de personas. El reloj está
 * en el 2026-12-01 a las 08:00 de Buenos Aires.
 */
class ImagenClinicaIntegrationTest extends IntegracionClinicaBase {

    private static final LocalDate HOY = LocalDate.of(2026, 12, 1);
    private static final LocalDate AYER = HOY.minusDays(1);

    @Value("${app.clinica.imagenes.directorio}")
    private String directorio;

    /** {@code user(...)} deja el principal en el contexto de test, y MockMvc lo aplicaría a los pedidos siguientes. */
    @AfterEach
    void limpiarPrincipalInyectado() {
        TestSecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- Cargar una imagen clínica

    @Nested
    class CargarUnaImagenClinica {

        @Test
        @DisplayName("Cargar una radiografía de una pieza")
        void cargarUnaRadiografiaDeUnaPieza() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("placa.jpg", "image/jpeg", jpeg(300_000)),
                    datos("RADIOGRAFIA", AYER, "36", null));

            assertThat(status(resultado)).isEqualTo(201);
            JsonNode imagen = cuerpo(resultado);
            assertThat(imagen.path("categoria").asText()).isEqualTo("RADIOGRAFIA");
            assertThat(imagen.path("pieza").asInt()).isEqualTo(36);
            assertThat(imagen.path("tomadaEl").asText()).isEqualTo("2026-11-30");
            assertThat(imagen.path("tipoContenido").asText()).isEqualTo("image/jpeg");
            assertThat(imagen.path("tamanoBytes").asInt()).isEqualTo(300_000);
            assertThat(imagen.path("profesionalId").asLong()).isEqualTo(draA);
            assertThat(imagen.path("profesional").asText()).isEqualTo("Ana Ejemplo");
            assertThat(imagen.path("registradaEn").asText()).isEqualTo(iso(diciembre(1, 8, 0)));
            assertThat(imagen.path("anulacion").isNull()).isTrue();
            assertThat(imagen.has("claveAlmacen")).isFalse();
            assertThat(imagenes(pacienteUno)).isEqualTo(1);
        }

        @Test
        @DisplayName("Cargar un estudio sin pieza")
        void cargarUnEstudioSinPieza() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("estudio.pdf", "application/pdf", pdf(2_000)),
                    datos("ESTUDIO", HOY, null, null));

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("pieza").isNull()).isTrue();
            assertThat(cuerpo(resultado).path("tipoContenido").asText()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("Categoría inexistente")
        void categoriaInexistente() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("foto.png", "image/png", png(500)),
                    datos("TOMOGRAFIA", HOY, null, null));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(imagenes(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Descripción demasiado larga")
        void descripcionDemasiadoLarga() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("foto.png", "image/png", png(500)),
                    datos("FOTOGRAFIA", HOY, null, "x".repeat(201)));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(imagenes(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Paciente inexistente")
        void pacienteInexistente() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), 999_999L, archivo("placa.jpg", "image/jpeg", jpeg(100)),
                    datos("RADIOGRAFIA", HOY, null, null));

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("Odontólogo sin profesional vinculado")
        // La sesión de MockMvc puede quedar con este principal: se descarta el contexto para los tests siguientes.
        @DirtiesContext(methodMode = MethodMode.AFTER_METHOD)
        void odontologoSinProfesionalVinculado() throws Exception {
            // La base no admite un ODONTOLOGO sin profesional (CHECK de V3): el principal apunta a la cuenta real de
            // ana.ejemplo, pero sin su profesional, como en PeriodontogramaIntegrationTest.
            long usuarioId = jdbc.queryForObject("SELECT id FROM usuario WHERE username = 'ana.ejemplo'", Long.class);
            String hash = jdbc.queryForObject("SELECT password_hash FROM usuario WHERE id = ?", String.class, usuarioId);
            UsuarioAutenticado suelto = new UsuarioAutenticado(usuarioId, "ana.ejemplo", hash, Rol.ODONTOLOGO,
                    null, null, true);
            MockMultipartHttpServletRequestBuilder pedido = MockMvcRequestBuilders.multipart(url(pacienteUno));
            pedido.file(archivo("placa.jpg", "image/jpeg", jpeg(100)));
            datos("RADIOGRAFIA", HOY, null, null).forEach(pedido::param);

            MvcResult resultado = mvc.perform(pedido.with(user(suelto)).with(csrf())).andReturn();

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(imagenes(pacienteUno)).isZero();
        }
    }

    // ---------------------------------------------------------------- Tipos de archivo permitidos

    @Nested
    class TiposDeArchivoPermitidos {

        @Test
        @DisplayName("Archivo de un tipo no permitido")
        void archivoDeUnTipoNoPermitido() throws Exception {
            byte[] gif = "GIF89a contenido ficticio".getBytes(StandardCharsets.US_ASCII);
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("animacion.gif", "image/gif", gif),
                    datos("FOTOGRAFIA", HOY, null, null));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("TIPO_ARCHIVO_NO_PERMITIDO");
            assertThat(imagenes(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Extensión engañosa")
        void extensionEnganosa() throws Exception {
            byte[] texto = "texto plano ficticio".getBytes(StandardCharsets.UTF_8);
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("radiografia.jpg", "image/jpeg", texto),
                    datos("RADIOGRAFIA", HOY, null, null));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("TIPO_ARCHIVO_NO_PERMITIDO");
        }

        @Test
        @DisplayName("El tipo guardado es el del contenido")
        void elTipoGuardadoEsElDelContenido() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("foto.jpg", "image/jpeg", png(500)),
                    datos("FOTOGRAFIA", HOY, null, null));

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("tipoContenido").asText()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("Archivo vacío")
        void archivoVacio() throws Exception {
            MvcResult vacio = cargar(como("ana.ejemplo"), pacienteUno, archivo("vacio.jpg", "image/jpeg", new byte[0]),
                    datos("RADIOGRAFIA", HOY, null, null));
            MvcResult sinArchivo = cargar(como("ana.ejemplo"), pacienteUno, null, datos("RADIOGRAFIA", HOY, null, null));

            assertThat(status(vacio)).isEqualTo(422);
            assertThat(codigo(vacio)).isEqualTo("DATOS_INVALIDOS");
            assertThat(status(sinArchivo)).isEqualTo(422);
            assertThat(codigo(sinArchivo)).isEqualTo("DATOS_INVALIDOS");
        }
    }

    // ---------------------------------------------------------------- Tamaño máximo del archivo

    @Nested
    class TamanoMaximoDelArchivo {

        @Test
        @DisplayName("Archivo de más de 10 MB")
        void archivoDeMasDe10Mb() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno,
                    archivo("grande.jpg", "image/jpeg", jpeg(10_485_761)), datos("RADIOGRAFIA", HOY, null, null));

            assertThat(status(resultado)).isEqualTo(413);
            assertThat(codigo(resultado)).isEqualTo("ARCHIVO_DEMASIADO_GRANDE");
            assertThat(imagenes(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Archivo de exactamente 10 MB")
        void archivoDeExactamente10Mb() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno,
                    archivo("justo.jpg", "image/jpeg", jpeg(10_485_760)), datos("RADIOGRAFIA", HOY, null, null));

            assertThat(status(resultado)).isEqualTo(201);
        }
    }

    // ---------------------------------------------------------------- Fecha de la toma y pieza

    @Nested
    class FechaDeLaTomaYPieza {

        @Test
        @DisplayName("Fecha de la toma futura")
        void fechaDeLaTomaFutura() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("placa.jpg", "image/jpeg", jpeg(100)),
                    datos("RADIOGRAFIA", HOY.plusDays(1), null, null));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(imagenes(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("Pieza temporal válida")
        void piezaTemporalValida() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("foto.png", "image/png", png(100)),
                    datos("FOTOGRAFIA", HOY, "55", null));

            assertThat(status(resultado)).isEqualTo(201);
            assertThat(cuerpo(resultado).path("pieza").asInt()).isEqualTo(55);
        }

        @Test
        @DisplayName("Pieza inexistente")
        void piezaInexistente() throws Exception {
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("placa.jpg", "image/jpeg", jpeg(100)),
                    datos("RADIOGRAFIA", HOY, "19", null));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("PIEZA_INVALIDA");
            assertThat(imagenes(pacienteUno)).isZero();
        }
    }

    // ---------------------------------------------------------------- Imágenes inmutables

    @Nested
    class ImagenesInmutables {

        @Test
        @DisplayName("No hay forma de borrar una imagen")
        void noHayFormaDeBorrarUnaImagen() throws Exception {
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, "36", jpeg(100));
            Navegador ana = como("ana.ejemplo");

            MvcResult borrar = ana.enviar(MockMvcRequestBuilders.delete(url(pacienteUno) + "/" + imagen)
                    .header(Navegador.ENCABEZADO_CSRF, ana.tokenCsrf())).andReturn();
            MvcResult editar = put(ana, url(pacienteUno) + "/" + imagen, Map.of("categoria", "FOTOGRAFIA"));

            assertThat(status(borrar)).isGreaterThanOrEqualTo(400);
            assertThat(status(editar)).isGreaterThanOrEqualTo(400);
            assertThat(imagenes(pacienteUno)).isEqualTo(1);
            assertThat(status(ana.get(archivoUrl(pacienteUno, imagen)).andReturn())).isEqualTo(200);
        }
    }

    // ---------------------------------------------------------------- Listar las imágenes del paciente

    @Nested
    class ListarLasImagenesDelPaciente {

        @Test
        @DisplayName("Orden del listado")
        void ordenDelListado() throws Exception {
            long radiografia = cargada(pacienteUno, "RADIOGRAFIA", LocalDate.of(2026, 10, 1), null, jpeg(100));
            long fotografia = cargada(pacienteUno, "FOTOGRAFIA", LocalDate.of(2026, 11, 1), null, png(100));

            MvcResult resultado = como("ana.ejemplo").get(url(pacienteUno)).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(ids(cuerpo(resultado))).containsExactly(fotografia, radiografia);
        }

        @Test
        @DisplayName("Filtrar por pieza")
        void filtrarPorPieza() throws Exception {
            long de36 = cargada(pacienteUno, "RADIOGRAFIA", AYER, "36", jpeg(100));
            cargada(pacienteUno, "RADIOGRAFIA", AYER, "11", jpeg(100));

            MvcResult resultado = como("ana.ejemplo").get(url(pacienteUno) + "?pieza=36").andReturn();

            assertThat(ids(cuerpo(resultado))).containsExactly(de36);
        }

        @Test
        @DisplayName("Las anuladas no se listan por defecto")
        void lasAnuladasNoSeListanPorDefecto() throws Exception {
            long vigente = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));
            long anulada = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));
            anular(como("ana.ejemplo"), pacienteUno, anulada, "Duplicada");

            MvcResult resultado = como("ana.ejemplo").get(url(pacienteUno)).andReturn();

            assertThat(ids(cuerpo(resultado))).containsExactly(vigente);
        }

        @Test
        @DisplayName("Listar también las anuladas")
        void listarTambienLasAnuladas() throws Exception {
            long vigente = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));
            long anulada = cargada(pacienteUno, "RADIOGRAFIA", LocalDate.of(2026, 11, 1), null, jpeg(100));
            anular(como("bruno.prueba"), pacienteUno, anulada, "Duplicada");

            JsonNode lista = cuerpo(como("ana.ejemplo").get(url(pacienteUno) + "?incluirAnuladas=true").andReturn());

            assertThat(ids(lista)).containsExactly(vigente, anulada);
            JsonNode anulacion = lista.get(1).path("anulacion");
            assertThat(anulacion.path("profesionalId").asLong()).isEqualTo(drB);
            assertThat(anulacion.path("profesional").asText()).isEqualTo("Bruno Prueba");
            assertThat(anulacion.path("anuladaEn").asText()).isEqualTo(iso(diciembre(1, 8, 0)));
            assertThat(anulacion.path("motivo").asText()).isEqualTo("Duplicada");
        }

        @Test
        @DisplayName("Paciente sin imágenes")
        void pacienteSinImagenes() throws Exception {
            MvcResult resultado = como("ana.ejemplo").get(url(pacienteDos)).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(cuerpo(resultado).isArray()).isTrue();
            assertThat(cuerpo(resultado)).isEmpty();
        }
    }

    // ---------------------------------------------------------------- Descargar el archivo de una imagen

    @Nested
    class DescargarElArchivoDeUnaImagen {

        @Test
        @DisplayName("Ver una radiografía")
        void verUnaRadiografia() throws Exception {
            byte[] contenido = jpeg(2_048);
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, "36", contenido);

            MvcResult resultado = como("ana.ejemplo").get(archivoUrl(pacienteUno, imagen)).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(resultado.getResponse().getContentType()).isEqualTo("image/jpeg");
            assertThat(resultado.getResponse().getContentAsByteArray()).isEqualTo(contenido);
            assertThat(resultado.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
            assertThat(resultado.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(resultado.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline");
        }

        @Test
        @DisplayName("Descargar como adjunto")
        void descargarComoAdjunto() throws Exception {
            long imagen = cargada(pacienteUno, "ESTUDIO", AYER, null, pdf(1_000));

            MvcResult resultado = como("ana.ejemplo").get(archivoUrl(pacienteUno, imagen) + "?descarga=true").andReturn();

            String disposicion = resultado.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(disposicion).startsWith("attachment").contains("imagen-" + imagen + ".pdf");
            assertThat(disposicion).doesNotContain("Paciente").doesNotContain("Uno").doesNotContain(DNI_PACIENTE_UNO);
        }

        @Test
        @DisplayName("Imagen de otro paciente")
        void imagenDeOtroPaciente() throws Exception {
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));

            MvcResult resultado = como("ana.ejemplo").get(archivoUrl(pacienteDos, imagen)).andReturn();

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("Archivo de una imagen anulada")
        void archivoDeUnaImagenAnulada() throws Exception {
            byte[] contenido = png(300);
            long imagen = cargada(pacienteUno, "FOTOGRAFIA", AYER, null, contenido);
            anular(como("ana.ejemplo"), pacienteUno, imagen, "Duplicada");

            MvcResult resultado = como("admin").get(archivoUrl(pacienteUno, imagen)).andReturn();

            assertThat(status(resultado)).isEqualTo(200);
            assertThat(resultado.getResponse().getContentAsByteArray()).isEqualTo(contenido);
        }
    }

    // ---------------------------------------------------------------- Integridad del archivo

    @Nested
    class IntegridadDelArchivo {

        @Test
        @DisplayName("El hash se devuelve al cargar")
        void elHashSeDevuelveAlCargar() throws Exception {
            byte[] contenido = png(700);
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("foto.png", "image/png", contenido),
                    datos("FOTOGRAFIA", HOY, null, null));

            assertThat(cuerpo(resultado).path("sha256").asText()).isEqualTo(sha256(contenido));
        }

        @Test
        @DisplayName("Archivo alterado en el almacén")
        void archivoAlteradoEnElAlmacen() throws Exception {
            byte[] contenido = jpeg(1_000);
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, contenido);
            Path archivo = rutaEnElAlmacen(imagen);
            byte[] alterado = contenido.clone();
            alterado[500] ^= 0x01;
            Files.write(archivo, alterado);

            MvcResult resultado = como("ana.ejemplo").get(archivoUrl(pacienteUno, imagen)).andReturn();

            assertThat(status(resultado)).isEqualTo(500);
            assertThat(resultado.getResponse().getContentAsByteArray()).isNotEqualTo(alterado);
            assertThat(texto(resultado)).doesNotContain(sha256(contenido));
        }
    }

    // ---------------------------------------------------------------- Anular una imagen con motivo

    @Nested
    class AnularUnaImagenConMotivo {

        @Test
        @DisplayName("Anular una imagen")
        void anularUnaImagen() throws Exception {
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));
            reloj.ahoraEs(diciembre(1, 9, 30));

            MvcResult resultado = anular(como("bruno.prueba"), pacienteUno, imagen, "Se cargó en el paciente equivocado");

            assertThat(status(resultado)).isEqualTo(200);
            JsonNode anulacion = cuerpo(resultado).path("anulacion");
            assertThat(anulacion.path("profesionalId").asLong()).isEqualTo(drB);
            assertThat(anulacion.path("anuladaEn").asText()).isEqualTo(iso(diciembre(1, 9, 30)));
            assertThat(anulacion.path("motivo").asText()).isEqualTo("Se cargó en el paciente equivocado");
        }

        @Test
        @DisplayName("Anular sin motivo")
        void anularSinMotivo() throws Exception {
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));

            MvcResult resultado = anular(como("ana.ejemplo"), pacienteUno, imagen, "");

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(anulada(imagen)).isFalse();
        }

        @Test
        @DisplayName("Anular dos veces")
        void anularDosVeces() throws Exception {
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));
            anular(como("ana.ejemplo"), pacienteUno, imagen, "Primera");

            MvcResult resultado = anular(como("bruno.prueba"), pacienteUno, imagen, "Segunda");

            assertThat(status(resultado)).isEqualTo(409);
            assertThat(codigo(resultado)).isEqualTo("IMAGEN_YA_ANULADA");
            assertThat(jdbc.queryForObject("SELECT anulada_por FROM imagen_clinica WHERE id = ?", Long.class, imagen))
                    .isEqualTo(draA);
        }

        @Test
        @DisplayName("Anular una imagen inexistente")
        void anularUnaImagenInexistente() throws Exception {
            MvcResult resultado = anular(como("ana.ejemplo"), pacienteUno, 999_999L, "Motivo");

            assertThat(status(resultado)).isEqualTo(404);
            assertThat(codigo(resultado)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }
    }

    // ---------------------------------------------------------------- Acceso del odontólogo y del administrador

    @Nested
    class AccesoDelOdontologoYDelAdministradorALasImagenes {

        @Test
        @DisplayName("El administrador lista y descarga")
        void elAdministradorListaYDescarga() throws Exception {
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));
            Navegador admin = como("admin");

            assertThat(status(admin.get(url(pacienteUno)).andReturn())).isEqualTo(200);
            assertThat(status(admin.get(archivoUrl(pacienteUno, imagen)).andReturn())).isEqualTo(200);
        }

        @Test
        @DisplayName("El administrador intenta cargar")
        void elAdministradorIntentaCargar() throws Exception {
            MvcResult resultado = cargar(como("admin"), pacienteUno, archivo("placa.jpg", "image/jpeg", jpeg(100)),
                    datos("RADIOGRAFIA", HOY, null, null));

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(imagenes(pacienteUno)).isZero();
        }

        @Test
        @DisplayName("El administrador intenta anular")
        void elAdministradorIntentaAnular() throws Exception {
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));

            MvcResult resultado = anular(como("admin"), pacienteUno, imagen, "Motivo");

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(anulada(imagen)).isFalse();
        }
    }

    // ---------------------------------------------------------------- La recepción y el paciente no acceden

    @Nested
    class LaRecepcionYElPacienteNoAccedenALasImagenes {

        @Test
        @DisplayName("La recepción lista las imágenes")
        void laRecepcionListaLasImagenes() throws Exception {
            cargada(pacienteUno, "RADIOGRAFIA", AYER, "36", jpeg(100));

            MvcResult resultado = como("recepcion").get(url(pacienteUno)).andReturn();

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(texto(resultado)).doesNotContain("RADIOGRAFIA").doesNotContain("sha256");
        }

        @Test
        @DisplayName("La recepción pide un archivo")
        void laRecepcionPideUnArchivo() throws Exception {
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));

            MvcResult resultado = como("recepcion").get(archivoUrl(pacienteUno, imagen)).andReturn();

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(codigo(resultado)).isEqualTo("ACCESO_DENEGADO");
        }

        @Test
        @DisplayName("El paciente pide su propia imagen")
        void elPacientePideSuPropiaImagen() throws Exception {
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, null, jpeg(100));
            jdbc.update("INSERT INTO usuario(username, password_hash, rol, paciente_id) VALUES (?,?,'PACIENTE',?)",
                    "paciente.uno@example.com", passwordEncoder.encode(clave), pacienteUno);
            Navegador paciente = como("paciente.uno@example.com");

            MvcResult listado = paciente.get(url(pacienteUno)).andReturn();
            MvcResult archivo = paciente.get(archivoUrl(pacienteUno, imagen)).andReturn();

            assertThat(status(listado)).isEqualTo(403);
            assertThat(codigo(listado)).isEqualTo("ACCESO_DENEGADO");
            assertThat(status(archivo)).isEqualTo(403);
            assertThat(codigo(archivo)).isEqualTo("ACCESO_DENEGADO");
        }

        @Test
        @DisplayName("Sin sesión")
        void sinSesion() throws Exception {
            MvcResult resultado = navegador().get(url(pacienteUno)).andReturn();

            assertThat(status(resultado)).isEqualTo(401);
        }

        @Test
        @DisplayName("Carga sin token CSRF")
        void cargaSinTokenCsrf() throws Exception {
            Navegador ana = como("ana.ejemplo");
            MockMultipartHttpServletRequestBuilder pedido = MockMvcRequestBuilders.multipart(url(pacienteUno));
            pedido.file(archivo("placa.jpg", "image/jpeg", jpeg(100)));
            datos("RADIOGRAFIA", HOY, null, null).forEach(pedido::param);

            MvcResult resultado = ana.enviar(pedido).andReturn();

            assertThat(status(resultado)).isEqualTo(403);
            assertThat(imagenes(pacienteUno)).isZero();
        }
    }

    // ---------------------------------------------------------------- Datos fuera de los canales externos

    @Nested
    class DatosDeLasImagenesFueraDeLosCanalesExternos {

        @Test
        @DisplayName("Un error de validación no repite la descripción")
        void unErrorDeValidacionNoRepiteLaDescripcion() throws Exception {
            String descripcion = "Lesión" + "x".repeat(195);
            MvcResult resultado = cargar(como("ana.ejemplo"), pacienteUno, archivo("placa.jpg", "image/jpeg", jpeg(100)),
                    datos("RADIOGRAFIA", HOY, null, descripcion));

            assertThat(status(resultado)).isEqualTo(422);
            assertThat(codigo(resultado)).isEqualTo("DATOS_INVALIDOS");
            assertThat(texto(resultado)).doesNotContain("Lesión");
        }

        @Test
        @DisplayName("La ubicación del archivo no tiene datos del paciente")
        void laUbicacionDelArchivoNoTieneDatosDelPaciente() throws Exception {
            long imagen = cargada(pacienteUno, "RADIOGRAFIA", AYER, "36", jpeg(100));

            String clave = jdbc.queryForObject("SELECT clave_almacen FROM imagen_clinica WHERE id = ?", String.class,
                    imagen);

            assertThat(clave).matches("[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(clave).doesNotContain(DNI_PACIENTE_UNO).doesNotContain("Paciente").doesNotContain("Uno")
                    .doesNotContain("RADIOGRAFIA").doesNotContain(".jpg");
            assertThat(Files.exists(rutaEnElAlmacen(imagen))).isTrue();
        }
    }

    // ---------------------------------------------------------------- Ayudas

    private static String url(long paciente) {
        return "/api/v1/pacientes/" + paciente + "/imagenes";
    }

    private static String archivoUrl(long paciente, long imagen) {
        return url(paciente) + "/" + imagen + "/archivo";
    }

    private static MockMultipartFile archivo(String nombre, String tipo, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, tipo, contenido);
    }

    private static Map<String, String> datos(String categoria, LocalDate tomadaEl, String pieza, String descripcion) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("categoria", categoria);
        datos.put("tomadaEl", tomadaEl.toString());
        if (pieza != null) {
            datos.put("pieza", pieza);
        }
        if (descripcion != null) {
            datos.put("descripcion", descripcion);
        }
        return datos;
    }

    private MvcResult cargar(Navegador navegador, long paciente, MockMultipartFile archivo, Map<String, String> datos)
            throws Exception {
        MockMultipartHttpServletRequestBuilder pedido = MockMvcRequestBuilders.multipart(url(paciente));
        if (archivo != null) {
            pedido.file(archivo);
        }
        datos.forEach(pedido::param);
        pedido.header(Navegador.ENCABEZADO_CSRF, navegador.tokenCsrf());
        return navegador.enviar(pedido).andReturn();
    }

    /** Carga una imagen como {@code ana.ejemplo} y devuelve su id. */
    private long cargada(long paciente, String categoria, LocalDate tomadaEl, String pieza, byte[] contenido)
            throws Exception {
        String tipo = switch (contenido[0]) {
            case (byte) 0xFF -> "image/jpeg";
            case (byte) 0x89 -> "image/png";
            default -> "application/pdf";
        };
        MvcResult resultado = cargar(como("ana.ejemplo"), paciente, archivo("archivo", tipo, contenido),
                datos(categoria, tomadaEl, pieza, null));
        assertThat(status(resultado)).as(texto(resultado)).isEqualTo(201);
        return cuerpo(resultado).path("id").asLong();
    }

    private MvcResult anular(Navegador navegador, long paciente, long imagen, String motivo) throws Exception {
        return navegador.post(url(paciente) + "/" + imagen + "/anulacion", json.writeValueAsString(Map.of("motivo", motivo)))
                .andReturn();
    }

    private int imagenes(long paciente) {
        return jdbc.queryForObject("SELECT count(*) FROM imagen_clinica WHERE paciente_id = ?", Integer.class, paciente);
    }

    private boolean anulada(long imagen) {
        return jdbc.queryForObject("SELECT anulada_en IS NOT NULL FROM imagen_clinica WHERE id = ?", Boolean.class,
                imagen);
    }

    private Path rutaEnElAlmacen(long imagen) {
        String clave = jdbc.queryForObject("SELECT clave_almacen FROM imagen_clinica WHERE id = ?", String.class, imagen);
        return Path.of(directorio).toAbsolutePath().normalize().resolve(clave);
    }

    private static java.util.List<Long> ids(JsonNode lista) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        lista.forEach(imagen -> ids.add(imagen.path("id").asLong()));
        return ids;
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

    private static String sha256(byte[] contenido) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
    }
}
