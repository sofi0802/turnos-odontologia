package ar.turnos.odontologia.coberturas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Regenerar una liquidación en borrador (C-20, spec liquidaciones-obras-sociales, tarea 5.2, design D4 paso 4): agrega
 * los turnos nuevos, recalcula con los valores vigentes, saca los que ya no cumplen y conserva el código elegido.
 * Reloj: 2026-11-24 a las 12:00 en Buenos Aires. Solo datos ficticios del seed de la KB.
 */
class RegenerarLiquidacionIntegrationTest extends IntegracionLiquidacionesBase {

    /** Presenta por SQL (la base lo permite): la prueba de regenerar no depende del endpoint de presentar. */
    private void presentarPorSql(long liquidacion) {
        jdbc.update("UPDATE liquidacion SET estado = 'PRESENTADA', presentada_en = now(), presentada_por = 'admin' "
                + "WHERE id = ?", liquidacion);
    }

    @Test
    @DisplayName("Regenerar agrega turnos y recalcula")
    void regenerarAgregaTurnosYRecalcula() throws Exception {
        // GIVEN la liquidación de "Obra Social Ejemplo" de 2026-10 en BORRADOR con la "Consulta o control" de "Paciente Uno" en 15000.00
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        long limpiezaTurno = turno(pacienteUno, limpieza, "2026-10-20T10:00", "CONFIRMADO");
        JsonNode original = generada(obraSocialEjemplo, "2026-10");
        long id = original.path("id").asLong();
        assertThat(numero(lineaDe(original, "Consulta o control").path("montoSolicitado")))
                .isEqualByComparingTo("15000.00");
        // AND después el arancel de "Consulta o control" pasa a 16000.00 y se marca ATENDIDO el turno de "Limpieza (profilaxis)" de "Paciente Uno" del 2026-10-20
        arancel(consulta, "16000.00");
        marcarAtendido(limpiezaTurno);

        // WHEN admin regenera la liquidación
        MvcResult r = regenerar(id);

        // THEN la consulta tiene monto 16000.00, aparece la limpieza con 12500.00 y el total solicitado es 28500.00
        assertThat(status(r)).isEqualTo(200);
        JsonNode regenerada = cuerpo(r);
        assertThat(numero(lineaDe(regenerada, "Consulta o control").path("montoSolicitado")))
                .isEqualByComparingTo("16000.00");
        assertThat(numero(lineaDe(regenerada, "Consulta o control").path("precio"))).isEqualByComparingTo("16000.00");
        assertThat(numero(lineaDe(regenerada, "Limpieza (profilaxis)").path("montoSolicitado")))
                .isEqualByComparingTo("12500.00");
        assertThat(numero(regenerada.path("totalSolicitado"))).isEqualByComparingTo("28500.00");
        assertThat(totalSolicitadoGuardado(id)).isEqualByComparingTo("28500.00");
        assertThat(sumaDeLasLineas(id)).isEqualByComparingTo("28500.00");
        assertThat(lineas(id)).isEqualTo(2);
        // AND la línea de la consulta conservó su id: se actualizó, no se reemplazó
        assertThat(lineaDe(regenerada, "Consulta o control").path("id").asLong())
                .isEqualTo(lineaDe(original, "Consulta o control").path("id").asLong());
        assertThat(regenerada.path("turnosSinArancel").asInt(-1)).isZero();
    }

    @Test
    @DisplayName("Regenerar conserva el código elegido")
    void regenerarConservaElCodigoElegido() throws Exception {
        // GIVEN una línea de "Consulta o control" en BORRADOR con el código N-0102 elegido por admin
        semilla();
        long n0102 = codigo("N-0102", "Consulta con diagnóstico", consulta);
        cobertura(planBase, n0102, 80);
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        JsonNode original = generada(obraSocialEjemplo, "2026-10");
        long id = original.path("id").asLong();
        long linea = lineaDe(original, "Consulta o control").path("id").asLong();
        assertThat(status(put(como("admin"), urlLinea(id, linea) + "/codigo", codigoSolicitud(n0102)))).isEqualTo(200);

        // WHEN admin regenera la liquidación
        JsonNode regenerada = cuerpo(regenerar(id));

        // THEN la línea conserva N-0102 con porcentaje 80
        JsonNode conservada = lineaDe(regenerada, "Consulta o control");
        assertThat(conservada.path("codigo").asText()).isEqualTo("N-0102");
        assertThat(conservada.path("porcentaje").asInt()).isEqualTo(80);
        assertThat(conservada.path("codigoElegido").asBoolean()).isTrue();
        assertThat(numero(conservada.path("montoSolicitado"))).isEqualByComparingTo("12000.00");
        assertThat(numero(regenerada.path("totalSolicitado"))).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("Regenerar una liquidación presentada")
    void regenerarUnaLiquidacionPresentada() throws Exception {
        // GIVEN la liquidación de "Obra Social Ejemplo" de 2026-10 está PRESENTADA
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();
        presentarPorSql(id);
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        arancel(consulta, "16000.00");

        // WHEN admin la regenera
        MvcResult r = regenerar(id);

        // THEN la respuesta es 409 con el código LIQUIDACION_PRESENTADA y las líneas no cambian
        assertThat(status(r)).isEqualTo(409);
        assertThat(codigoDeError(r)).isEqualTo("LIQUIDACION_PRESENTADA");
        assertThat(lineas(id)).isEqualTo(1);
        assertThat(sumaDeLasLineas(id)).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("Regenerar una liquidación cerrada o inexistente")
    void regenerarUnaCerradaOInexistente() throws Exception {
        semilla();
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();
        presentarPorSql(id);
        jdbc.update("UPDATE liquidacion SET estado = 'CERRADA', cerrada_en = now(), cerrada_por = 'admin' "
                + "WHERE id = ?", id);

        MvcResult cerrada = regenerar(id);
        MvcResult inexistente = regenerar(999999L);

        assertThat(status(cerrada)).isEqualTo(409);
        assertThat(codigoDeError(cerrada)).isEqualTo("LIQUIDACION_CERRADA");
        assertThat(status(inexistente)).isEqualTo(404);
        assertThat(codigoDeError(inexistente)).isEqualTo("RECURSO_NO_ENCONTRADO");
    }

    @Test
    @DisplayName("Regenerar saca las líneas cuyo turno ya no cumple las condiciones")
    void regenerarSacaLasQueYaNoCumplen() throws Exception {
        // GIVEN una liquidación en BORRADOR con la consulta y la limpieza de "Paciente Uno"
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        long limpiezaTurno = atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();
        // AND después se cierra la afiliación de "Paciente Uno" el 2026-10-10 (la limpieza queda sin cobertura vigente)
        jdbc.update("UPDATE afiliacion_paciente SET vigente_hasta = DATE '2026-10-10' WHERE paciente_id = ?",
                pacienteUno);

        // WHEN admin regenera la liquidación
        JsonNode regenerada = cuerpo(regenerar(id));

        // THEN solo queda la consulta, y la limpieza queda libre para otra liquidación
        assertThat(lineasDe(regenerada)).hasSize(1);
        assertThat(idsDeTurnos(regenerada)).doesNotContain(limpiezaTurno);
        assertThat(numero(regenerada.path("totalSolicitado"))).isEqualByComparingTo("15000.00");
        assertThat(lineas(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Regenerar sin cambios no cambia nada y registra quién regeneró")
    void regenerarSinCambios() throws Exception {
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();
        long linea = jdbc.queryForObject("SELECT id FROM liquidacion_linea WHERE liquidacion_id = ?", Long.class, id);
        ahoraEs("2026-11-25T15:00:00Z");

        JsonNode regenerada = cuerpo(regenerar(id));

        assertThat(lineas(id)).isEqualTo(1);
        assertThat(lineaDe(regenerada, "Consulta o control").path("id").asLong()).isEqualTo(linea);
        assertThat(numero(regenerada.path("totalSolicitado"))).isEqualByComparingTo("15000.00");
        assertThat(regenerada.path("generadaEn").asText()).startsWith("2026-11-25");
        assertThat(regenerada.path("generadaPor").asText()).isEqualTo("admin");
    }

    @Test
    @DisplayName("Se carga el arancel y se regenera")
    void seCargaElArancelYSeRegenera() throws Exception {
        // GIVEN la liquidación anterior está en BORRADOR (sin arancel de "Limpieza (profilaxis)") y después se carga el arancel 25000.00
        semilla();
        jdbc.update("DELETE FROM arancel WHERE prestacion_id = ?", limpieza);
        atendido(pacienteUno, limpieza, "2026-10-20T10:00");
        JsonNode original = generada(obraSocialEjemplo, "2026-10");
        long id = original.path("id").asLong();
        assertThat(original.path("turnosSinArancel").asInt()).isEqualTo(1);
        arancel(limpieza, "25000.00");

        // WHEN admin regenera la liquidación
        JsonNode regenerada = cuerpo(regenerar(id));

        // THEN la liquidación tiene la línea de "Limpieza (profilaxis)" con monto 12500.00 y la respuesta informa 0 turnos sin arancel
        assertThat(numero(lineaDe(regenerada, "Limpieza (profilaxis)").path("montoSolicitado")))
                .isEqualByComparingTo("12500.00");
        assertThat(regenerada.path("turnosSinArancel").asInt(-1)).isZero();
    }

    @Test
    @DisplayName("Regenerar sin cargo de caja sigue el arancel vigente")
    void regenerarSinCargoUsaElArancel() throws Exception {
        // GIVEN una línea al arancel de 15000.00 que después sube a 16000.00 (sin cargos de caja: C-22 no está)
        semilla();
        atendido(pacienteUno, consulta, "2026-10-06T10:00");
        long id = generada(obraSocialEjemplo, "2026-10").path("id").asLong();
        arancel(consulta, "16000.00");

        // WHEN admin regenera
        JsonNode regenerada = cuerpo(regenerar(id));

        // THEN la línea (origen ARANCEL) sigue al arancel nuevo
        JsonNode linea = lineaDe(regenerada, "Consulta o control");
        assertThat(linea.path("origenPrecio").asText()).isEqualTo("ARANCEL");
        assertThat(numero(linea.path("precio"))).isEqualByComparingTo("16000.00");
    }
}
