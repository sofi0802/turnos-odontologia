package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Permisos de las liquidaciones y de su conciliación (C-20, specs liquidaciones-obras-sociales y
 * conciliacion-liquidaciones, tarea 8.1, RN-LQ-02, DD-10 y regla dura 8): solo el {@code ADMIN} entra, en los diez
 * endpoints; los demás reciben {@code 403} sin que cambie nada, sin sesión es {@code 401} y sin el token CSRF es
 * {@code 403}. Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios.
 */
class PermisosLiquidacionesIntegrationTest extends IntegracionLiquidacionesBase {

    /** Un pedido a uno de los diez endpoints, hecho por el navegador que se le pase. */
    private record Pedido(String nombre, Function<Navegador, MvcResult> enviar) {}

    private long liquidacion;
    private long lineaConsulta;
    private long lineaLimpieza;
    private long otraLiquidacion;

    @BeforeEach
    void liquidacionesDeEjemplo() throws Exception {
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        JsonNode generada = generada(obraSocialEjemplo, "2026-10");
        liquidacion = generada.path("id").asLong();
        lineaConsulta = lineaDe(generada, "Consulta o control").path("id").asLong();
        lineaLimpieza = lineaDe(generada, "Limpieza (profilaxis)").path("id").asLong();
        otraLiquidacion = generada(prepaga, "2026-09").path("id").asLong();
        // un usuario por cada rol sin permiso: recepcion, ana.ejemplo (ODONTOLOGO) y paciente.demo (PACIENTE)
        jdbc.update("INSERT INTO usuario(username, password_hash, rol, paciente_id) VALUES (?,?,'PACIENTE',?)",
                "paciente.demo", passwordEncoder.encode(clave), pacienteTres);
    }

    private static MvcResult enviar(Navegador n, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        try {
            return n.enviar(b).andReturn();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcResult con(Navegador n, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return enviar(n, b.header(Navegador.ENCABEZADO_CSRF, n.tokenCsrf() == null ? "" : n.tokenCsrf()));
    }

    /** Los diez endpoints, apuntando a la liquidación de "Obra Social Ejemplo" y a sus líneas. */
    private List<Pedido> losDiezEndpoints() {
        List<Pedido> pedidos = new ArrayList<>();
        try {
            String generar = json.writeValueAsString(generacion(prepaga, "2026-10"));
            String codigo = json.writeValueAsString(codigoSolicitud(n0101));
            String cobro = json.writeValueAsString(conciliacion(new BigDecimal("15000.00"), null));
            pedidos.add(new Pedido("POST generar", n -> con(n, MockMvcRequestBuilders.post(LIQUIDACIONES)
                    .contentType(MediaType.APPLICATION_JSON).content(generar))));
            pedidos.add(new Pedido("GET listar", n -> enviar(n, MockMvcRequestBuilders.get(LIQUIDACIONES))));
            pedidos.add(new Pedido("GET detalle", n -> enviar(n, MockMvcRequestBuilders.get(url(liquidacion)))));
            pedidos.add(new Pedido("POST regenerar", n -> con(n,
                    MockMvcRequestBuilders.post(url(liquidacion) + "/regenerar"))));
            pedidos.add(new Pedido("PUT código", n -> con(n, MockMvcRequestBuilders
                    .put(urlLinea(liquidacion, lineaConsulta) + "/codigo")
                    .contentType(MediaType.APPLICATION_JSON).content(codigo))));
            pedidos.add(new Pedido("DELETE línea", n -> con(n,
                    MockMvcRequestBuilders.delete(urlLinea(liquidacion, lineaLimpieza)))));
            pedidos.add(new Pedido("POST presentar", n -> con(n,
                    MockMvcRequestBuilders.post(url(liquidacion) + "/presentar"))));
            pedidos.add(new Pedido("PUT conciliación", n -> con(n, MockMvcRequestBuilders
                    .put(urlLinea(liquidacion, lineaConsulta) + "/conciliacion")
                    .contentType(MediaType.APPLICATION_JSON).content(cobro))));
            pedidos.add(new Pedido("POST cerrar", n -> con(n, MockMvcRequestBuilders.post(url(liquidacion) + "/cerrar"))));
            pedidos.add(new Pedido("GET csv", n -> enviar(n, MockMvcRequestBuilders.get(url(liquidacion) + "/csv"))));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return pedidos;
    }

    /** Una "foto" de todas las tablas de liquidaciones: si un pedido cambia algo, cambia el valor. */
    private String foto() {
        return jdbc.queryForObject("SELECT md5(coalesce((SELECT string_agg(l::text, '|' ORDER BY id) FROM liquidacion l), '')"
                + " || '#' || coalesce((SELECT string_agg(x::text, '|' ORDER BY id) FROM liquidacion_linea x), '')"
                + " || '#' || coalesce((SELECT string_agg(q::text, '|' ORDER BY liquidacion_id, turno_id) "
                + "FROM liquidacion_turno_quitado q), ''))", String.class);
    }

    private void assertTodosDenegados(String usuario) throws Exception {
        Navegador navegador = como(usuario);
        String antes = foto();
        for (Pedido pedido : losDiezEndpoints()) {
            MvcResult r = pedido.enviar().apply(navegador);
            assertThat(status(r)).as(usuario + " " + pedido.nombre()).isEqualTo(403);
            assertThat(codigoDeError(r)).as(usuario + " " + pedido.nombre()).isEqualTo("ACCESO_DENEGADO");
            assertThat(texto(r)).as(usuario + " " + pedido.nombre()).doesNotContain("Paciente Uno", "99000001");
        }
        assertThat(foto()).as("los pedidos de " + usuario + " no cambian nada").isEqualTo(antes);
        assertThat(cantidad("liquidacion")).isEqualTo(2);
    }

    @Test
    @DisplayName("La recepción intenta generar una liquidación")
    void laRecepcionIntentaGenerarUnaLiquidacion() throws Exception {
        // WHEN recepcion genera la liquidación de "Obra Social Ejemplo" para 2026-10
        MvcResult r = generarComo("recepcion", prepaga, "2026-10");

        // THEN la respuesta es 403 y no se crea ninguna liquidación
        assertThat(status(r)).isEqualTo(403);
        assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
        assertThat(cantidad("liquidacion")).isEqualTo(2);
        // AND recepcion no pasa en ninguno de los diez endpoints
        assertTodosDenegados("recepcion");
    }

    @Test
    @DisplayName("Otros roles intentan consultar o descargar")
    void otrosRolesIntentanConsultarODescargar() throws Exception {
        // WHEN ana.ejemplo o paciente.demo consultan una liquidación o descargan su CSV
        MvcResult ana = get(como("ana.ejemplo"), url(liquidacion));
        MvcResult demo = get(como("paciente.demo"), url(liquidacion) + "/csv");

        // THEN la respuesta es 403
        assertThat(status(ana)).isEqualTo(403);
        assertThat(status(demo)).isEqualTo(403);
        // AND ninguno de los dos pasa en ninguno de los diez endpoints
        assertTodosDenegados("ana.ejemplo");
        assertTodosDenegados("paciente.demo");
        // AND el administrador sí entra a los diez
        Navegador admin = como("admin");
        for (Pedido pedido : losDiezEndpoints()) {
            assertThat(status(pedido.enviar().apply(admin))).as("admin " + pedido.nombre()).isNotIn(401, 403);
        }
    }

    @Test
    @DisplayName("Sin sesión")
    void sinSesion() throws Exception {
        String antes = foto();

        // WHEN un pedido sin sesión lista las liquidaciones
        MvcResult listar = navegador().get(LIQUIDACIONES).andReturn();

        // THEN la respuesta es 401
        assertThat(status(listar)).isEqualTo(401);
        // AND también en los diez endpoints, sin cambiar nada
        for (Pedido pedido : losDiezEndpoints()) {
            // un navegador que ya pidió el token CSRF (como el frontend) pero no inició sesión
            Navegador anonimo = navegador();
            anonimo.get(SESION);
            assertThat(status(pedido.enviar().apply(anonimo))).as(pedido.nombre()).isEqualTo(401);
        }
        assertThat(foto()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Sin el token CSRF los pedidos que cambian datos se rechazan")
    void sinElTokenCsrf() throws Exception {
        String antes = foto();
        Navegador admin = como("admin");

        List<MvcResult> rechazados = new ArrayList<>();
        rechazados.add(enviar(admin, MockMvcRequestBuilders.post(LIQUIDACIONES).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(generacion(prepaga, "2026-10")))));
        rechazados.add(enviar(admin, MockMvcRequestBuilders.post(url(liquidacion) + "/regenerar")));
        rechazados.add(enviar(admin, MockMvcRequestBuilders.put(urlLinea(liquidacion, lineaConsulta) + "/codigo")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(codigoSolicitud(n0101)))));
        rechazados.add(enviar(admin, MockMvcRequestBuilders.delete(urlLinea(liquidacion, lineaLimpieza))));
        rechazados.add(enviar(admin, MockMvcRequestBuilders.post(url(liquidacion) + "/presentar")));
        rechazados.add(enviar(admin, MockMvcRequestBuilders.put(urlLinea(liquidacion, lineaConsulta) + "/conciliacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(conciliacion(new BigDecimal("1.00"), null)))));
        rechazados.add(enviar(admin, MockMvcRequestBuilders.post(url(liquidacion) + "/cerrar")));

        assertThat(rechazados).extracting(IntegracionCoberturasBase::status).containsOnly(403);
        assertThat(foto()).isEqualTo(antes);
        assertThat(otraLiquidacion).isPositive();
    }

    @Test
    @DisplayName("La recepción intenta conciliar")
    void laRecepcionIntentaConciliar() throws Exception {
        // GIVEN la liquidación de 2026-10 está PRESENTADA
        presentar(liquidacion);
        String antes = foto();
        Navegador recepcion = como("recepcion");

        // WHEN recepcion registra 15000.00 cobrados en la línea de "Consulta o control" o cierra la liquidación
        MvcResult cobrar = put(recepcion, urlLinea(liquidacion, lineaConsulta) + "/conciliacion",
                conciliacion(new BigDecimal("15000.00"), null));
        MvcResult cerrar = post(recepcion, url(liquidacion) + "/cerrar", null);

        // THEN la respuesta es 403 y la liquidación no cambia
        assertThat(status(cobrar)).isEqualTo(403);
        assertThat(codigoDeError(cobrar)).isEqualTo("ACCESO_DENEGADO");
        assertThat(status(cerrar)).isEqualTo(403);
        assertThat(foto()).isEqualTo(antes);
        assertThat(estadoGuardadoLiquidacion(liquidacion)).isEqualTo("PRESENTADA");
        // AND tampoco el odontólogo ni el paciente
        for (String usuario : new String[] {"ana.ejemplo", "paciente.demo"}) {
            assertThat(status(put(como(usuario), urlLinea(liquidacion, lineaConsulta) + "/conciliacion",
                    conciliacion(new BigDecimal("15000.00"), null)))).isEqualTo(403);
            assertThat(status(post(como(usuario), url(liquidacion) + "/cerrar", null))).isEqualTo(403);
        }
        assertThat(foto()).isEqualTo(antes);
    }
}
