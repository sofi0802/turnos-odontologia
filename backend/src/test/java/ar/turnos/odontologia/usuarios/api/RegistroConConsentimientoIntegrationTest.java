package ar.turnos.odontologia.usuarios.api;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnos.odontologia.shared.security.IntegracionSeguridadBase;
import ar.turnos.odontologia.shared.security.Navegador;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** C-17: "Consentimiento inicial en el registro" del spec {@code cuentas-pacientes}, con PostgreSQL real. */
@Tag("integration")
class RegistroConConsentimientoIntegrationTest extends IntegracionSeguridadBase {

    private static final String REGISTRO = "/api/v1/public/pacientes/registro";
    private static final String DNI = "99000001";

    private String registro(Boolean aceptaMensajes) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", "Paciente");
        datos.put("apellido", "Uno");
        datos.put("dni", DNI);
        datos.put("email", "paciente.uno@example.com");
        datos.put("password", contrasenaFicticia());
        if (aceptaMensajes != null) {
            datos.put("aceptaMensajes", aceptaMensajes);
        }
        return json.writeValueAsString(datos);
    }

    private int registrar(Boolean aceptaMensajes) throws Exception {
        Navegador navegador = navegador();
        navegador.get(SESION);
        return navegador.post(REGISTRO, registro(aceptaMensajes)).andReturn().getResponse().getStatus();
    }

    private boolean consentimiento() {
        return jdbc.queryForObject("SELECT consentimiento_comunicaciones FROM paciente WHERE dni = ?",
                Boolean.class, DNI);
    }

    private long eventos() {
        return jdbc.queryForObject("SELECT count(*) FROM consentimiento_comunicacion_evento", Long.class);
    }

    @Test
    @DisplayName("Registro aceptando mensajes")
    void registroAceptandoMensajes() throws Exception {
        assertThat(registrar(true)).isEqualTo(201);

        assertThat(consentimiento()).isTrue();
        assertThat(jdbc.queryForObject("SELECT e.origen FROM consentimiento_comunicacion_evento e "
                + "JOIN paciente p ON p.id = e.paciente_id WHERE p.dni = ?", String.class, DNI)).isEqualTo("REGISTRO");
    }

    @Test
    @DisplayName("Registro sin indicar mensajes")
    void registroSinIndicarMensajes() throws Exception {
        assertThat(registrar(null)).isEqualTo(201);

        assertThat(consentimiento()).isFalse();
        assertThat(eventos()).isZero();
    }

    @Test
    void registroRechazandoMensajes() throws Exception {
        assertThat(registrar(false)).isEqualTo(201);

        assertThat(consentimiento()).isFalse();
        assertThat(eventos()).isZero();
    }

    @Test
    @DisplayName("Un registro rechazado no deja consentimiento")
    void unRegistroRechazadoNoDejaConsentimiento() throws Exception {
        assertThat(registrar(false)).isEqualTo(201);

        int estado = registrar(true);

        assertThat(estado).isEqualTo(409);
        assertThat(eventos()).isZero();
        assertThat(consentimiento()).isFalse();
    }
}
