package ar.turnos.odontologia.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.TurnosOdontologiaApplication;
import ar.turnos.odontologia.agenda.domain.CodigoRechazo;
import ar.turnos.odontologia.agenda.domain.TurnoRechazadoException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;

/**
 * El margen configurado se valida al arrancar: un valor menor a 10 minutos no deja iniciar la aplicación (D8).
 * Arranca con el perfil {@code test}, como el resto de los tests de integración: usa la base de tests y no carga
 * el seed de desarrollo.
 */
@Tag("integration")
class AgendaConfigIntegrationTest {

    @Test
    void unMargenMenorAlMinimoImpideArrancar() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(TurnosOdontologiaApplication.class)
                .profiles("test")
                .properties("app.agenda.margen-minutos=8", "server.port=0")
                .run()
                .close())
                .satisfies(e -> {
                    Throwable causa = NestedExceptionUtils.getMostSpecificCause(e);
                    assertThat(causa).isInstanceOf(TurnoRechazadoException.class);
                    assertThat(((TurnoRechazadoException) causa).codigo()).isEqualTo(CodigoRechazo.MARGEN_INVALIDO);
                });
    }
}
