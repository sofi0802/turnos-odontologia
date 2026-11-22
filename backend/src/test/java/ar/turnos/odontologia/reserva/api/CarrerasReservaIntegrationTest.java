package ar.turnos.odontologia.reserva.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.reserva.IntegracionReservaBase;
import ar.turnos.odontologia.shared.security.Navegador;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.test.web.servlet.MvcResult;

/** Escenarios "Reservas simultáneas online" de {@code specs/reserva-online}: dos hilos y la base real (design D5). */
@Tag("integration")
class CarrerasReservaIntegrationTest extends IntegracionReservaBase {

    @BeforeEach
    void franjasDelSpec() {
        martes(draA, "08:00", "13:00");
        martes(drB, "10:00", "18:00");
    }

    private Callable<MvcResult> reservar(CountDownLatch largada, Navegador navegador, Long profesional, int hora) {
        return () -> {
            Map<String, Object> pedido = new LinkedHashMap<>();
            pedido.put("prestacionId", consulta);
            pedido.put("inicio", hora(hora, 0));
            if (profesional != null) {
                pedido.put("profesionalId", profesional);
            }
            largada.await();
            return navegador.post("/api/v1/paciente/reservas", cuerpoJson(pedido)).andReturn();
        };
    }

    private List<MvcResult> pedirAlMismoTiempo(Long profesional, int hora) throws Exception {
        Navegador uno = comoPacienteUno();
        Navegador dos = comoPacienteDos();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> pedidos = List.of(hilos.submit(reservar(largada, uno, profesional, hora)),
                    hilos.submit(reservar(largada, dos, profesional, hora)));
            largada.countDown();
            List<MvcResult> resultados = new ArrayList<>();
            for (Future<MvcResult> pedido : pedidos) {
                resultados.add(pedido.get());
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    @RepeatedTest(10)
    @DisplayName("Dos pacientes piden el mismo profesional a la misma hora")
    void dosPacientesPidenElMismoProfesional() throws Exception {
        List<MvcResult> resultados = pedirAlMismoTiempo(draA, 10);

        List<Integer> estados = resultados.stream().map(r -> r.getResponse().getStatus()).toList();
        assertThat(estados).as(resultados.stream().map(r -> {
            try {
                return texto(r);
            } catch (Exception e) {
                return e.toString();
            }
        }).toList().toString()).containsExactlyInAnyOrder(201, 409);
        for (MvcResult resultado : resultados) {
            if (resultado.getResponse().getStatus() == 409) {
                assertThat(cuerpo(resultado).get("codigo").asText()).isEqualTo("SIN_DISPONIBILIDAD");
            }
        }
        assertThat(id("SELECT count(*) FROM turno WHERE profesional_id = ? AND estado <> 'CANCELADO'", draA))
                .isEqualTo(1);
    }

    @RepeatedTest(10)
    @DisplayName("Dos pacientes sin preferencia a la misma hora")
    void dosPacientesSinPreferencia() throws Exception {
        List<MvcResult> resultados = pedirAlMismoTiempo(null, 11);

        for (MvcResult resultado : resultados) {
            assertThat(resultado.getResponse().getStatus()).as(texto(resultado)).isEqualTo(201);
        }
        assertThat(id("SELECT count(DISTINCT profesional_id) FROM turno WHERE estado <> 'CANCELADO'")).isEqualTo(2);
        assertThat(id("SELECT count(DISTINCT sillon_id) FROM turno WHERE estado <> 'CANCELADO'")).isEqualTo(2);
        assertThat(id("SELECT count(*) FROM turno WHERE profesional_id = ? AND estado <> 'CANCELADO'", draA))
                .isEqualTo(1);
        assertThat(id("SELECT count(*) FROM turno WHERE profesional_id = ? AND estado <> 'CANCELADO'", drB))
                .isEqualTo(1);
    }
}
