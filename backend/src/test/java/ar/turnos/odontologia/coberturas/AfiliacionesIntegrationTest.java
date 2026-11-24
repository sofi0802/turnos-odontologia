package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.shared.security.Navegador;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Afiliaciones de los pacientes (C-18, spec afiliaciones-pacientes, tareas 4.1, 4.4 y 4.5): alta, número y vigencia
 * válidos, una sola afiliación por fecha, número único por obra social, plan o obra social inactivos, cierre,
 * corrección, historial y permisos. La carrera de dos pedidos a la vez está en {@code CarreraAfiliacionesIntegrationTest}.
 */
@Tag("integration")
class AfiliacionesIntegrationTest extends IntegracionCoberturasBase {

    private long ose;
    private long planBase;
    private long planIntegral;
    private long prepaga;
    private long planOro;

    @BeforeEach
    void catalogo() {
        ose = obraSocial("Obra Social Ejemplo", "OSE", "OBRA_SOCIAL");
        planBase = plan(ose, "Plan Base");
        planIntegral = plan(ose, "Plan Integral");
        prepaga = obraSocial("Prepaga Prueba", null, "PREPAGA");
        planOro = plan(prepaga, "Plan Oro");
    }

    private MvcResult afiliar(Navegador navegador, long paciente, long plan, String numero, String desde, String hasta)
            throws Exception {
        return post(navegador, afiliaciones(paciente), afiliacionSolicitud(plan, numero, desde, hasta));
    }

    @Nested
    class AfiliarUnPacienteAUnPlan {

        @Test
        @DisplayName("Afiliación sin fecha de fin")
        void afiliacionSinFechaDeFin() throws Exception {
            // GIVEN "Paciente Uno" sin afiliaciones
            // WHEN recepcion afilia a "Paciente Uno" a "Plan Base" con el número OSE-000001 desde 2026-01-01
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

            // THEN la respuesta es 201 con "Plan Base", "Obra Social Ejemplo", el número y la vigencia sin fin
            assertThat(status(r)).isEqualTo(201);
            JsonNode a = cuerpo(r);
            assertThat(a.path("plan").path("nombre").asText()).isEqualTo("Plan Base");
            assertThat(a.path("obraSocial").path("nombre").asText()).isEqualTo("Obra Social Ejemplo");
            assertThat(a.path("numeroAfiliado").asText()).isEqualTo("OSE-000001");
            assertThat(a.path("vigenteDesde").asText()).isEqualTo("2026-01-01");
            assertThat(a.path("vigenteHasta").isNull()).isTrue();
            assertThat(a.path("vigente").asBoolean()).isTrue();
            assertThat(r.getResponse().getHeader("Location"))
                    .endsWith(afiliaciones(pacienteUno) + "/" + a.path("id").asLong());
        }

        @Test
        @DisplayName("Afiliación con fecha de fin")
        void afiliacionConFechaDeFin() throws Exception {
            // WHEN admin afilia a "Paciente Dos" a "Plan Oro" con el número PP-77 desde 2026-01-01 hasta 2026-12-31
            MvcResult r = afiliar(como("admin"), pacienteDos, planOro, "PP-77", "2026-01-01", "2026-12-31");

            // THEN la respuesta es 201 con vigenteHasta 2026-12-31
            assertThat(status(r)).isEqualTo(201);
            assertThat(cuerpo(r).path("vigenteHasta").asText()).isEqualTo("2026-12-31");
        }

        @Test
        @DisplayName("Paciente inexistente")
        void pacienteInexistente() throws Exception {
            // WHEN recepcion afilia al paciente con id 999999 a "Plan Base" con el número OSE-000009
            MvcResult r = afiliar(como("recepcion"), 999999, planBase, "OSE-000009", "2026-01-01", null);

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("Plan inexistente")
        void planInexistente() throws Exception {
            // WHEN recepcion afilia a "Paciente Uno" al plan con id 999999
            MvcResult r = afiliar(como("recepcion"), pacienteUno, 999999, "OSE-000001", "2026-01-01", null);

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
            assertThat(cantidad("afiliacion_paciente")).isZero();
        }

        @Test
        @DisplayName("Sin plan en el pedido")
        void sinPlan() throws Exception {
            MvcResult r = post(como("recepcion"), afiliaciones(pacienteUno),
                    java.util.Map.of("numeroAfiliado", "OSE-000001", "vigenteDesde", "2026-01-01"));
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(cuerpo(r).path("errores").get(0).path("campo").asText()).isEqualTo("planId");
        }
    }

    @Nested
    class NumeroDeAfiliadoValido {

        @Test
        @DisplayName("Espacios en los extremos")
        void espaciosEnLosExtremos() throws Exception {
            // WHEN recepcion afilia con el número "  OSE-000001  "
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planBase, "  OSE-000001  ", "2026-01-01", null);

            // THEN la respuesta es 201 con el número OSE-000001
            assertThat(status(r)).isEqualTo(201);
            assertThat(cuerpo(r).path("numeroAfiliado").asText()).isEqualTo("OSE-000001");
            assertThat(jdbc.queryForObject("SELECT numero_afiliado FROM afiliacion_paciente", String.class))
                    .isEqualTo("OSE-000001");
        }

        @Test
        @DisplayName("Número con caracteres no permitidos")
        void numeroConCaracteresNoPermitidos() throws Exception {
            // WHEN recepcion afilia con el número "OSE 0001#"
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planBase, "OSE 0001#", "2026-01-01", null);

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo numeroAfiliado
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(cuerpo(r).path("errores").get(0).path("campo").asText()).isEqualTo("numeroAfiliado");
            // AND el cuerpo de la respuesta no contiene OSE 0001#
            assertThat(texto(r)).doesNotContain("OSE 0001#");
        }

        @Test
        @DisplayName("Número vacío")
        void numeroVacio() throws Exception {
            // WHEN recepcion afilia con el número en blanco
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planBase, "   ", "2026-01-01", null);

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo numeroAfiliado
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(cuerpo(r).path("errores").get(0).path("campo").asText()).isEqualTo("numeroAfiliado");
        }

        @Test
        @DisplayName("Número de 31 caracteres")
        void numeroDe31Caracteres() throws Exception {
            // WHEN recepcion afilia con un número de 31 caracteres válidos
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planBase, "N".repeat(31), "2026-01-01", null);

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo numeroAfiliado
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(cuerpo(r).path("errores").get(0).path("campo").asText()).isEqualTo("numeroAfiliado");
            assertThat(texto(r)).doesNotContain("N".repeat(31));
        }

        @Test
        @DisplayName("Número de 30 caracteres")
        void numeroDe30Caracteres() throws Exception {
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planBase, "N".repeat(30), "2026-01-01", null);
            assertThat(status(r)).isEqualTo(201);
        }

        @Test
        @DisplayName("Sin número en el pedido")
        void sinNumero() throws Exception {
            MvcResult r = post(como("recepcion"), afiliaciones(pacienteUno),
                    java.util.Map.of("planId", planBase, "vigenteDesde", "2026-01-01"));
            assertThat(status(r)).isEqualTo(422);
            assertThat(cuerpo(r).path("errores").get(0).path("campo").asText()).isEqualTo("numeroAfiliado");
        }
    }

    @Nested
    class VigenciaValida {

        @Test
        @DisplayName("Fecha de fin anterior a la de inicio")
        void fechaDeFinAnteriorALaDeInicio() throws Exception {
            // WHEN recepcion afilia desde 2026-06-01 hasta 2026-05-31
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planBase, "OSE-000001", "2026-06-01", "2026-05-31");

            // THEN la respuesta es 422 con el código VIGENCIA_INVALIDA
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("VIGENCIA_INVALIDA");
        }

        @Test
        @DisplayName("Vigencia de un solo día")
        void vigenciaDeUnSoloDia() throws Exception {
            // WHEN recepcion afilia desde 2026-06-01 hasta 2026-06-01
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planBase, "OSE-000001", "2026-06-01", "2026-06-01");

            // THEN la respuesta es 201
            assertThat(status(r)).isEqualTo(201);
        }

        @Test
        @DisplayName("Sin fecha de inicio")
        void sinFechaDeInicio() throws Exception {
            // WHEN recepcion afilia sin vigenteDesde
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planBase, "OSE-000001", null, null);

            // THEN la respuesta es 422 con el código DATOS_INVALIDOS y el campo vigenteDesde
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(cuerpo(r).path("errores").get(0).path("campo").asText()).isEqualTo("vigenteDesde");
        }

        @Test
        @DisplayName("Una fecha con formato inválido")
        void fechaConFormatoInvalido() throws Exception {
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planBase, "OSE-000001", "01/01/2026", null);
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
        }
    }

    @Nested
    class UnaSolaAfiliacionVigentePorFecha {

        @Test
        @DisplayName("Superposición con una afiliación abierta")
        void superposicionConUnaAfiliacionAbierta() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" desde 2026-01-01 sin fecha de fin
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

            // WHEN recepcion afilia a "Paciente Uno" a "Plan Oro" con el número PP-77 desde 2026-11-01
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planOro, "PP-77", "2026-11-01", null);

            // THEN la respuesta es 409 con el código VIGENCIA_SUPERPUESTA
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("VIGENCIA_SUPERPUESTA");
            // AND "Paciente Uno" sigue con una sola afiliación
            assertThat(cantidad("afiliacion_paciente")).isEqualTo(1);
        }

        @Test
        @DisplayName("Superposición de un día en el borde")
        void superposicionDeUnDiaEnElBorde() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" desde 2026-01-01 hasta 2026-06-30
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-06-30");

            // WHEN recepcion afilia a "Paciente Uno" a "Plan Oro" desde 2026-06-30
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planOro, "PP-77", "2026-06-30", null);

            // THEN la respuesta es 409 con el código VIGENCIA_SUPERPUESTA
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("VIGENCIA_SUPERPUESTA");
        }

        @Test
        @DisplayName("Vigencias contiguas")
        void vigenciasContiguas() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" desde 2026-01-01 hasta 2026-06-30
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-06-30");

            // WHEN recepcion afilia a "Paciente Uno" a "Plan Oro" desde 2026-07-01
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planOro, "PP-77", "2026-07-01", null);

            // THEN la respuesta es 201
            assertThat(status(r)).isEqualTo(201);
        }

        @Test
        @DisplayName("Otro paciente en las mismas fechas")
        void otroPacienteEnLasMismasFechas() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" desde 2026-01-01 sin fecha de fin
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

            // WHEN recepcion afilia a "Paciente Dos" a "Plan Base" con el número OSE-000002 desde 2026-01-01
            MvcResult r = afiliar(como("recepcion"), pacienteDos, planBase, "OSE-000002", "2026-01-01", null);

            // THEN la respuesta es 201
            assertThat(status(r)).isEqualTo(201);
        }

        @Test
        @DisplayName("Una afiliación anterior a la abierta que termina antes de ella")
        void anteriorQueTerminaAntes() throws Exception {
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-07-01", null);
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planOro, "PP-77", "2026-01-01", "2026-06-30");
            assertThat(status(r)).isEqualTo(201);
        }

        @Test
        @DisplayName("La base rechaza una superposición escrita directo")
        void laBaseRechazaUnaSuperposicionEscritaDirecto() {
            // GIVEN "Paciente Uno" tiene "Plan Base" desde 2026-01-01 sin fecha de fin
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

            // WHEN se inserta directamente en la base otra afiliación de "Paciente Uno" desde 2026-03-01
            // THEN la base rechaza la inserción por la restricción de vigencias superpuestas
            assertThatThrownBy(() -> afiliacion(pacienteUno, planOro, "PP-77", "2026-03-01", null))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("afiliacion_sin_superposicion");
        }
    }

    @Nested
    class NumeroDeAfiliadoUnicoPorObraSocial {

        @Test
        @DisplayName("Número repetido en otro plan de la misma obra social")
        void numeroRepetidoEnOtroPlanDeLaMismaObraSocial() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" con el número OSE-000001
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

            // WHEN recepcion afilia a "Paciente Dos" a "Plan Integral" con el número ose-000001 desde 2026-01-01
            MvcResult r = afiliar(como("recepcion"), pacienteDos, planIntegral, "ose-000001", "2026-01-01", null);

            // THEN la respuesta es 409 con el código AFILIADO_DUPLICADO
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("AFILIADO_DUPLICADO");
            // AND el cuerpo de la respuesta no menciona a "Paciente Uno"
            assertThat(texto(r)).doesNotContain("Paciente Uno").doesNotContain("Uno").doesNotContain("99000001");
            assertThat(cantidad("afiliacion_paciente")).isEqualTo(1);
        }

        @Test
        @DisplayName("Mismo número en otra obra social")
        void mismoNumeroEnOtraObraSocial() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" con el número OSE-000001
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

            // WHEN recepcion afilia a "Paciente Dos" a "Plan Oro" con el número OSE-000001 desde 2026-01-01
            MvcResult r = afiliar(como("recepcion"), pacienteDos, planOro, "OSE-000001", "2026-01-01", null);

            // THEN la respuesta es 201
            assertThat(status(r)).isEqualTo(201);
        }

        @Test
        @DisplayName("El mismo paciente conserva su número al cambiar de plan")
        void elMismoPacienteConservaSuNumeroAlCambiarDePlan() throws Exception {
            // GIVEN "Paciente Uno" tuvo "Plan Base" con el número OSE-000001 desde 2026-01-01 hasta 2026-06-30
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-06-30");

            // WHEN recepcion afilia a "Paciente Uno" a "Plan Integral" con el número OSE-000001 desde 2026-07-01
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planIntegral, "OSE-000001", "2026-07-01", null);

            // THEN la respuesta es 201
            assertThat(status(r)).isEqualTo(201);
        }

        @Test
        @DisplayName("Un número de una afiliación ya vencida de otro paciente sigue siendo suyo")
        void numeroDeUnaAfiliacionVencida() throws Exception {
            afiliacion(pacienteUno, planBase, "OSE-000001", "2025-01-01", "2025-12-31");
            MvcResult r = afiliar(como("recepcion"), pacienteDos, planBase, "OSE-000001", "2026-01-01", null);
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("AFILIADO_DUPLICADO");
        }
    }

    @Nested
    class NoSeAfiliaAUnPlanOUnaObraSocialInactivos {

        @Test
        @DisplayName("Plan inactivo")
        void planInactivo() throws Exception {
            // GIVEN "Plan Integral" está inactivo
            jdbc.update("UPDATE plan SET activo = FALSE WHERE id = ?", planIntegral);

            // WHEN recepcion afilia a "Paciente Uno" a "Plan Integral"
            MvcResult r = afiliar(como("recepcion"), pacienteUno, planIntegral, "OSE-000001", "2026-01-01", null);

            // THEN la respuesta es 409 con el código PLAN_INACTIVO
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("PLAN_INACTIVO");
        }

        @Test
        @DisplayName("Obra social inactiva")
        void obraSocialInactiva() throws Exception {
            // GIVEN "Prepaga Prueba" está inactiva y "Plan Oro" activo
            jdbc.update("UPDATE obra_social SET activa = FALSE WHERE id = ?", prepaga);

            // WHEN recepcion afilia a "Paciente Dos" a "Plan Oro"
            MvcResult r = afiliar(como("recepcion"), pacienteDos, planOro, "PP-77", "2026-01-01", null);

            // THEN la respuesta es 409 con el código OBRA_SOCIAL_INACTIVA
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("OBRA_SOCIAL_INACTIVA");
        }

        @Test
        @DisplayName("Desactivar el plan no cambia la afiliación existente")
        void desactivarElPlanNoCambiaLaAfiliacionExistente() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" vigente desde 2026-01-01
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

            // WHEN admin desactiva "Plan Base"
            MvcResult desactivado = post(como("admin"), OBRAS_SOCIALES + "/" + ose + "/planes/" + planBase + "/desactivar", null);

            // THEN la afiliación sigue siendo "Plan Base" con el número OSE-000001 (la consulta de la cobertura
            // por la API se verifica en CoberturaIntegrationTest, tarea 5.1)
            assertThat(status(desactivado)).isEqualTo(200);
            assertThat(jdbc.queryForObject("SELECT plan_id FROM afiliacion_paciente", Long.class)).isEqualTo(planBase);
            assertThat(jdbc.queryForObject("SELECT numero_afiliado FROM afiliacion_paciente", String.class))
                    .isEqualTo("OSE-000001");
            assertThat(jdbc.queryForObject("SELECT vigente_hasta FROM afiliacion_paciente", java.sql.Date.class)).isNull();
        }
    }

    private MvcResult cerrar(Navegador navegador, long paciente, long afiliacion, String hasta) throws Exception {
        java.util.Map<String, Object> pedido = new java.util.LinkedHashMap<>();
        pedido.put("vigenteHasta", hasta);
        return post(navegador, afiliaciones(paciente) + "/" + afiliacion + "/cerrar", pedido);
    }

    private MvcResult corregir(Navegador navegador, long paciente, long afiliacion, long plan, String numero,
                               String desde) throws Exception {
        java.util.Map<String, Object> pedido = new java.util.LinkedHashMap<>();
        pedido.put("planId", plan);
        pedido.put("numeroAfiliado", numero);
        pedido.put("vigenteDesde", desde);
        return put(navegador, afiliaciones(paciente) + "/" + afiliacion, pedido);
    }

    @Nested
    class CerrarUnaAfiliacionYCambiarDeCobertura {

        @Test
        @DisplayName("Cambio de obra social a prepaga")
        void cambioDeObraSocialAPrepaga() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" desde 2026-01-01 sin fecha de fin
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            Navegador recepcion = como("recepcion");

            // WHEN recepcion cierra esa afiliación al 2026-11-30 y afilia a "Paciente Uno" a "Plan Oro" desde 2026-12-01
            MvcResult cierre = cerrar(recepcion, pacienteUno, base, "2026-11-30");
            MvcResult alta = afiliar(recepcion, pacienteUno, planOro, "PP-77", "2026-12-01", null);

            // THEN las dos respuestas son exitosas
            assertThat(status(cierre)).isEqualTo(200);
            assertThat(cuerpo(cierre).path("vigenteHasta").asText()).isEqualTo("2026-11-30");
            assertThat(status(alta)).isEqualTo(201);
            // AND quedan "Plan Oro" desde 2026-12-01 y "Plan Base" desde 2026-01-01 hasta 2026-11-30 (el historial
            // por la API se verifica en "Historial con dos afiliaciones", tarea 4.5)
            assertThat(jdbc.queryForList("SELECT p.nombre || ' ' || a.vigente_desde || ' ' || coalesce(a.vigente_hasta::text, '-') "
                    + "FROM afiliacion_paciente a JOIN plan p ON p.id = a.plan_id ORDER BY a.vigente_desde DESC",
                    String.class)).containsExactly("Plan Oro 2026-12-01 -", "Plan Base 2026-01-01 2026-11-30");
        }

        @Test
        @DisplayName("Cierre anterior al inicio")
        void cierreAnteriorAlInicio() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" desde 2026-01-01 sin fecha de fin
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

            // WHEN recepcion cierra esa afiliación al 2025-12-31
            MvcResult r = cerrar(como("recepcion"), pacienteUno, base, "2025-12-31");

            // THEN la respuesta es 422 con el código VIGENCIA_INVALIDA
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("VIGENCIA_INVALIDA");
            assertThat(jdbc.queryForObject("SELECT vigente_hasta FROM afiliacion_paciente", java.sql.Date.class)).isNull();
        }

        @Test
        @DisplayName("Cerrar una afiliación ya cerrada")
        void cerrarUnaAfiliacionYaCerrada() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" desde 2026-01-01 hasta 2026-06-30
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-06-30");

            // WHEN recepcion cierra esa afiliación al 2026-07-31
            MvcResult r = cerrar(como("recepcion"), pacienteUno, base, "2026-07-31");

            // THEN la respuesta es 409 con el código AFILIACION_CERRADA
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("AFILIACION_CERRADA");
            assertThat(jdbc.queryForObject("SELECT vigente_hasta FROM afiliacion_paciente", java.sql.Date.class)
                    .toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 6, 30));
        }

        @Test
        @DisplayName("Afiliación de otro paciente")
        void afiliacionDeOtroPaciente() throws Exception {
            // GIVEN la afiliación de "Paciente Uno" a "Plan Base"
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

            // WHEN recepcion la cierra usando la ruta de "Paciente Dos"
            MvcResult r = cerrar(como("recepcion"), pacienteDos, base, "2026-11-30");

            // THEN la respuesta es 404 con el código RECURSO_NO_ENCONTRADO
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
            // AND la afiliación de "Paciente Uno" no cambia
            assertThat(jdbc.queryForObject("SELECT vigente_hasta FROM afiliacion_paciente", java.sql.Date.class)).isNull();
        }

        @Test
        @DisplayName("Cerrar sin fecha de fin")
        void cerrarSinFechaDeFin() throws Exception {
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            MvcResult r = cerrar(como("recepcion"), pacienteUno, base, null);
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("DATOS_INVALIDOS");
            assertThat(cuerpo(r).path("errores").get(0).path("campo").asText()).isEqualTo("vigenteHasta");
        }

        @Test
        @DisplayName("Cerrar el mismo día de inicio deja una vigencia de un día")
        void cerrarElMismoDiaDeInicio() throws Exception {
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            MvcResult r = cerrar(como("recepcion"), pacienteUno, base, "2026-01-01");
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("vigenteHasta").asText()).isEqualTo("2026-01-01");
        }
    }

    @Nested
    class HistorialDeAfiliacionesDelPaciente {

        @Test
        @DisplayName("Historial con dos afiliaciones")
        void historialConDosAfiliaciones() throws Exception {
            // GIVEN "Paciente Uno" tuvo "Plan Base" desde 2026-01-01 hasta 2026-06-30 y tiene "Plan Oro" desde 2026-07-01
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-06-30");
            afiliacion(pacienteUno, planOro, "PP-77", "2026-07-01", null);

            // WHEN ana.ejemplo lista las afiliaciones de "Paciente Uno"
            MvcResult r = get(como("ana.ejemplo"), afiliaciones(pacienteUno));

            // THEN la respuesta es 200 con "Plan Oro" vigente y luego "Plan Base" no vigente
            assertThat(status(r)).isEqualTo(200);
            JsonNode historial = cuerpo(r);
            assertThat(historial).hasSize(2);
            assertThat(historial.get(0).path("plan").path("nombre").asText()).isEqualTo("Plan Oro");
            assertThat(historial.get(0).path("obraSocial").path("nombre").asText()).isEqualTo("Prepaga Prueba");
            assertThat(historial.get(0).path("numeroAfiliado").asText()).isEqualTo("PP-77");
            assertThat(historial.get(0).path("vigente").asBoolean()).isTrue();
            assertThat(historial.get(1).path("plan").path("nombre").asText()).isEqualTo("Plan Base");
            assertThat(historial.get(1).path("vigenteHasta").asText()).isEqualTo("2026-06-30");
            assertThat(historial.get(1).path("vigente").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("Paciente sin afiliaciones")
        void pacienteSinAfiliaciones() throws Exception {
            // WHEN recepcion lista las afiliaciones de "Paciente Dos", que no tiene ninguna
            MvcResult r = get(como("recepcion"), afiliaciones(pacienteDos));

            // THEN la respuesta es 200 con una lista vacía
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r)).isEmpty();
        }

        @Test
        @DisplayName("Historial de un paciente inexistente")
        void historialDeUnPacienteInexistente() throws Exception {
            MvcResult r = get(como("recepcion"), afiliaciones(999999));
            assertThat(status(r)).isEqualTo(404);
            assertThat(codigoDeError(r)).isEqualTo("RECURSO_NO_ENCONTRADO");
        }

        @Test
        @DisplayName("El historial de un paciente no trae las afiliaciones de otro")
        void historialSoloDelPaciente() throws Exception {
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            MvcResult r = get(como("recepcion"), afiliaciones(pacienteDos));
            assertThat(cuerpo(r)).isEmpty();
        }

        @Test
        @DisplayName("Dos afiliaciones con el mismo inicio se ordenan por id descendente")
        void ordenDeterministaConElMismoInicio() throws Exception {
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-01-01");
            afiliacion(pacienteUno, planOro, "PP-77", "2026-01-02", null);
            MvcResult r = get(como("admin"), afiliaciones(pacienteUno));
            assertThat(cuerpo(r)).extracting(a -> a.path("vigenteDesde").asText())
                    .containsExactly("2026-01-02", "2026-01-01");
        }
    }

    @Nested
    class PermisosSobreLasAfiliaciones {

        @Test
        @DisplayName("El odontólogo no afilia")
        void elOdontologoNoAfilia() throws Exception {
            // WHEN ana.ejemplo afilia a "Paciente Uno" a "Plan Base" con el número OSE-000001 desde 2026-01-01
            MvcResult r = afiliar(como("ana.ejemplo"), pacienteUno, planBase, "OSE-000001", "2026-01-01", null);

            // THEN la respuesta es 403 con el código ACCESO_DENEGADO
            assertThat(status(r)).isEqualTo(403);
            assertThat(codigoDeError(r)).isEqualTo("ACCESO_DENEGADO");
            // AND "Paciente Uno" no tiene afiliaciones
            assertThat(cantidad("afiliacion_paciente")).isZero();
        }

        @Test
        @DisplayName("El odontólogo no corrige ni cierra")
        void elOdontologoNoCorrigeNiCierra() throws Exception {
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            Navegador ana = como("ana.ejemplo");

            MvcResult corregida = corregir(ana, pacienteUno, base, planBase, "OSE-000099", "2026-01-01");
            MvcResult cerrada = cerrar(ana, pacienteUno, base, "2026-11-30");

            assertThat(status(corregida)).isEqualTo(403);
            assertThat(status(cerrada)).isEqualTo(403);
            assertThat(jdbc.queryForObject("SELECT numero_afiliado FROM afiliacion_paciente", String.class))
                    .isEqualTo("OSE-000001");
            assertThat(jdbc.queryForObject("SELECT vigente_hasta FROM afiliacion_paciente", java.sql.Date.class)).isNull();
        }

        @Test
        @DisplayName("El admin y la recepción escriben y todo el personal consulta")
        void adminYRecepcionEscribenYElPersonalConsulta() throws Exception {
            assertThat(status(afiliar(como("admin"), pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-06-30")))
                    .isEqualTo(201);
            assertThat(status(afiliar(como("recepcion"), pacienteUno, planOro, "PP-77", "2026-07-01", null)))
                    .isEqualTo(201);
            for (String usuario : new String[] {"admin", "recepcion", "ana.ejemplo"}) {
                assertThat(status(get(como(usuario), afiliaciones(pacienteUno)))).isEqualTo(200);
            }
        }

        @Test
        @DisplayName("El paciente no usa las rutas del personal")
        void elPacienteNoUsaLasRutasDelPersonal() throws Exception {
            // GIVEN "Paciente Uno" con una afiliación
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            Navegador paciente = como("paciente.uno");

            // WHEN paciente.uno lista las afiliaciones de "Paciente Uno" por la ruta del personal
            MvcResult lista = get(paciente, afiliaciones(pacienteUno));

            // THEN la respuesta es 403 con el código ACCESO_DENEGADO
            assertThat(status(lista)).isEqualTo(403);
            assertThat(codigoDeError(lista)).isEqualTo("ACCESO_DENEGADO");
            // AND tampoco escribe por esas rutas
            assertThat(status(afiliar(paciente, pacienteUno, planBase, "OSE-000002", "2027-01-01", null))).isEqualTo(403);
            assertThat(status(cerrar(paciente, pacienteUno, base, "2026-11-30"))).isEqualTo(403);
            assertThat(status(corregir(paciente, pacienteUno, base, planBase, "OSE-000099", "2026-01-01"))).isEqualTo(403);
            assertThat(cantidad("afiliacion_paciente")).isEqualTo(1);
        }
    }

    @Nested
    class CorregirUnaAfiliacion {

        @Test
        @DisplayName("Corregir el número de afiliado")
        void corregirElNumeroDeAfiliado() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" con el número OSE-000010
            long base = afiliacion(pacienteUno, planBase, "OSE-000010", "2026-01-01", null);

            // WHEN recepcion corrige el número a OSE-000001
            MvcResult r = corregir(como("recepcion"), pacienteUno, base, planBase, "OSE-000001", "2026-01-01");

            // THEN la respuesta es 200 con el número OSE-000001
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("numeroAfiliado").asText()).isEqualTo("OSE-000001");
            assertThat(jdbc.queryForObject("SELECT numero_afiliado FROM afiliacion_paciente", String.class))
                    .isEqualTo("OSE-000001");
        }

        @Test
        @DisplayName("Corrección que superpone vigencias")
        void correccionQueSuperponeVigencias() throws Exception {
            // GIVEN "Paciente Uno" tiene "Plan Base" desde 2026-01-01 hasta 2026-06-30 y "Plan Oro" desde 2026-07-01
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-06-30");
            long oro = afiliacion(pacienteUno, planOro, "PP-77", "2026-07-01", null);

            // WHEN recepcion corrige vigenteDesde de "Plan Oro" a 2026-06-15
            MvcResult r = corregir(como("recepcion"), pacienteUno, oro, planOro, "PP-77", "2026-06-15");

            // THEN la respuesta es 409 con el código VIGENCIA_SUPERPUESTA
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("VIGENCIA_SUPERPUESTA");
        }

        @Test
        @DisplayName("No hay borrado de afiliaciones")
        void noHayBorradoDeAfiliaciones() throws Exception {
            // WHEN admin pide DELETE sobre la afiliación de "Paciente Uno"
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            MvcResult r = delete(como("admin"), afiliaciones(pacienteUno) + "/" + base);

            // THEN la respuesta no es exitosa y la afiliación sigue existiendo
            assertThat(status(r)).isGreaterThanOrEqualTo(400);
            assertThat(cantidad("afiliacion_paciente")).isEqualTo(1);
        }

        @Test
        @DisplayName("Corregir sin cambiar nada no choca con la propia afiliación")
        void corregirSinCambiarNada() throws Exception {
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            MvcResult r = corregir(como("recepcion"), pacienteUno, base, planBase, "OSE-000001", "2026-01-01");
            assertThat(status(r)).isEqualTo(200);
        }

        @Test
        @DisplayName("Corregir el plan a otra obra social actualiza la obra social de la afiliación")
        void corregirElPlanAOtraObraSocial() throws Exception {
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            MvcResult r = corregir(como("recepcion"), pacienteUno, base, planOro, "PP-77", "2026-01-01");
            assertThat(status(r)).isEqualTo(200);
            assertThat(cuerpo(r).path("obraSocial").path("nombre").asText()).isEqualTo("Prepaga Prueba");
            assertThat(jdbc.queryForObject("SELECT obra_social_id FROM afiliacion_paciente", Long.class))
                    .isEqualTo(prepaga);
        }

        @Test
        @DisplayName("Corregir al plan inactivo se rechaza, pero conservar el plan inactivo no")
        void corregirConPlanInactivo() throws Exception {
            long base = afiliacion(pacienteUno, planBase, "OSE-000010", "2026-01-01", null);
            jdbc.update("UPDATE plan SET activo = FALSE WHERE id IN (?, ?)", planBase, planIntegral);
            Navegador recepcion = como("recepcion");

            MvcResult alOtro = corregir(recepcion, pacienteUno, base, planIntegral, "OSE-000010", "2026-01-01");
            MvcResult mismoPlan = corregir(recepcion, pacienteUno, base, planBase, "OSE-000001", "2026-01-01");

            assertThat(status(alOtro)).isEqualTo(409);
            assertThat(codigoDeError(alOtro)).isEqualTo("PLAN_INACTIVO");
            assertThat(status(mismoPlan)).isEqualTo(200);
        }

        @Test
        @DisplayName("Corregir con el número de otro paciente")
        void corregirConElNumeroDeOtroPaciente() throws Exception {
            afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            long dos = afiliacion(pacienteDos, planBase, "OSE-000002", "2026-01-01", null);
            MvcResult r = corregir(como("recepcion"), pacienteDos, dos, planBase, "ose-000001", "2026-01-01");
            assertThat(status(r)).isEqualTo(409);
            assertThat(codigoDeError(r)).isEqualTo("AFILIADO_DUPLICADO");
            assertThat(texto(r)).doesNotContainIgnoringCase("OSE-000001");
        }

        @Test
        @DisplayName("Corregir una afiliación de otro paciente o inexistente")
        void corregirUnaAfiliacionAjena() throws Exception {
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", null);
            Navegador recepcion = como("recepcion");
            assertThat(status(corregir(recepcion, pacienteDos, base, planBase, "OSE-000009", "2026-01-01")))
                    .isEqualTo(404);
            assertThat(status(corregir(recepcion, pacienteUno, 999999, planBase, "OSE-000009", "2026-01-01")))
                    .isEqualTo(404);
        }

        @Test
        @DisplayName("Corregir con una fecha de inicio posterior al fin")
        void corregirConInicioPosteriorAlFin() throws Exception {
            long base = afiliacion(pacienteUno, planBase, "OSE-000001", "2026-01-01", "2026-06-30");
            MvcResult r = corregir(como("recepcion"), pacienteUno, base, planBase, "OSE-000001", "2026-07-01");
            assertThat(status(r)).isEqualTo(422);
            assertThat(codigoDeError(r)).isEqualTo("VIGENCIA_INVALIDA");
        }
    }
}
