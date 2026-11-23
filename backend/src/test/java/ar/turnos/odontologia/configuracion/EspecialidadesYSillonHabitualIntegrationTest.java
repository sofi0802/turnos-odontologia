package ar.turnos.odontologia.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnos.odontologia.configuracion.application.HabilitacionProfesional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Escenarios de especialidades, habilitación y sillón habitual del spec {@code catalogo-consultorio}. */
class EspecialidadesYSillonHabitualIntegrationTest extends ConfiguracionIntegrationTest {

    @Autowired
    private HabilitacionProfesional habilitacion;

    // ---------- Requirement: Especialidades de cada profesional ----------

    @Test
    @DisplayName("Definir las especialidades")
    void definirLasEspecialidades() throws Exception {
        // GIVEN al Dr. Bruno Prueba sin especialidades
        long bruno = crearProfesional("Bruno", "Prueba", "MP-0002");
        long consulta = crearPrestacion("Consulta o control", 30);
        long conducto = crearPrestacion("Tratamiento de conducto", 60);
        long limpieza = crearPrestacion("Limpieza (profilaxis)", 45);

        // WHEN el administrador le asigna "Consulta o control" y "Tratamiento de conducto"
        putJson("/profesionales/" + bruno + "/prestaciones", """
                {"prestacionIds":[%d,%d]}""".formatted(consulta, conducto))
                .andExpect(status().isOk());

        // THEN queda habilitado para esas dos prestaciones y para ninguna otra
        obtener("/profesionales/" + bruno + "/prestaciones")
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].nombre",
                        containsInAnyOrder("Consulta o control", "Tratamiento de conducto")));
        assertThat(habilitacion.estaHabilitado(bruno, limpieza)).isFalse();
    }

    @Test
    @DisplayName("Especialidad con una prestación inactiva")
    void especialidadConUnaPrestacionInactiva() throws Exception {
        // GIVEN la prestación "Extracción simple" desactivada
        long ana = crearProfesional("Ana", "Ejemplo", "MP-0001");
        long consulta = crearPrestacion("Consulta o control", 30);
        long extraccion = crearPrestacion("Extracción simple", 30);
        putJson("/profesionales/" + ana + "/prestaciones", """
                {"prestacionIds":[%d]}""".formatted(consulta)).andExpect(status().isOk());
        postVacio("/prestaciones/" + extraccion + "/desactivar").andExpect(status().isOk());

        // WHEN el administrador asigna "Extracción simple" a la Dra. Ana Ejemplo
        putJson("/profesionales/" + ana + "/prestaciones", """
                {"prestacionIds":[%d,%d]}""".formatted(consulta, extraccion))
                // THEN el cambio se rechaza con 422 y PRESTACION_INACTIVA
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PRESTACION_INACTIVA"));

        // AND las especialidades de la Dra. Ana Ejemplo no cambian
        obtener("/profesionales/" + ana + "/prestaciones")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Consulta o control"));
    }

    // ---------- Requirement: Consulta de habilitación por prestación ----------

    @Test
    @DisplayName("Profesional no habilitado")
    void profesionalNoHabilitado() throws Exception {
        // GIVEN que la Dra. Ana Ejemplo hace "Consulta o control" pero no "Tratamiento de conducto"
        long ana = crearProfesional("Ana", "Ejemplo", "MP-0001");
        long consulta = crearPrestacion("Consulta o control", 30);
        long conducto = crearPrestacion("Tratamiento de conducto", 60);
        putJson("/profesionales/" + ana + "/prestaciones", """
                {"prestacionIds":[%d]}""".formatted(consulta)).andExpect(status().isOk());

        // WHEN se consulta si está habilitada para "Tratamiento de conducto"
        // THEN la respuesta es que no está habilitada
        assertThat(habilitacion.estaHabilitado(ana, conducto)).isFalse();
    }

    @Test
    @DisplayName("Profesional habilitado")
    void profesionalHabilitado() throws Exception {
        // GIVEN que el Dr. Bruno Prueba hace "Tratamiento de conducto"
        long bruno = crearProfesional("Bruno", "Prueba", "MP-0002");
        long conducto = crearPrestacion("Tratamiento de conducto", 60);
        putJson("/profesionales/" + bruno + "/prestaciones", """
                {"prestacionIds":[%d]}""".formatted(conducto)).andExpect(status().isOk());

        // WHEN se consulta si está habilitado para "Tratamiento de conducto"
        // THEN la respuesta es que está habilitado
        assertThat(habilitacion.estaHabilitado(bruno, conducto)).isTrue();
    }

    // ---------- Requirement: Sillón habitual del profesional ----------

    @Test
    @DisplayName("Asignar el sillón habitual")
    void asignarElSillonHabitual() throws Exception {
        long ana = crearProfesional("Ana", "Ejemplo", "MP-0001");
        long sillon1 = crearSillon("Sillón 1");

        // WHEN el administrador asigna el "Sillón 1" como sillón habitual de la Dra. Ana Ejemplo
        putJson("/profesionales/" + ana + "/sillon-habitual", """
                {"sillonId":%d}""".formatted(sillon1))
                .andExpect(status().isOk());

        // THEN figura con el "Sillón 1" como sillón habitual
        obtener("/profesionales/" + ana)
                .andExpect(jsonPath("$.sillonHabitualId").value(sillon1));
    }

    @Test
    @DisplayName("Sillón habitual inactivo")
    void sillonHabitualInactivo() throws Exception {
        // GIVEN el "Sillón 2" desactivado
        long bruno = crearProfesional("Bruno", "Prueba", "MP-0002");
        long sillon2 = crearSillon("Sillón 2");
        postVacio("/sillones/" + sillon2 + "/desactivar").andExpect(status().isOk());

        // WHEN el administrador asigna el "Sillón 2" como sillón habitual del Dr. Bruno Prueba
        putJson("/profesionales/" + bruno + "/sillon-habitual", """
                {"sillonId":%d}""".formatted(sillon2))
                // THEN el cambio se rechaza con 422 y SILLON_INACTIVO
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SILLON_INACTIVO"));
    }

    @Test
    @DisplayName("Quitar el sillón habitual")
    void quitarElSillonHabitual() throws Exception {
        // GIVEN la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
        long ana = crearProfesional("Ana", "Ejemplo", "MP-0001");
        long sillon1 = crearSillon("Sillón 1");
        putJson("/profesionales/" + ana + "/sillon-habitual", """
                {"sillonId":%d}""".formatted(sillon1)).andExpect(status().isOk());

        // WHEN el administrador le quita el sillón habitual
        putJson("/profesionales/" + ana + "/sillon-habitual", """
                {"sillonId":null}""")
                .andExpect(status().isOk());

        // THEN figura sin sillón habitual
        obtener("/profesionales/" + ana)
                .andExpect(jsonPath("$.sillonHabitualId").value(nullValue()));
    }

    @Test
    @DisplayName("Sillón habitual de otro profesional")
    void sillonHabitualDeOtroProfesional() throws Exception {
        // GIVEN la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
        long ana = crearProfesional("Ana", "Ejemplo", "MP-0001");
        long bruno = crearProfesional("Bruno", "Prueba", "MP-0002");
        long sillon1 = crearSillon("Sillón 1");
        long sillon2 = crearSillon("Sillón 2");
        asignarSillon(ana, sillon1);
        asignarSillon(bruno, sillon2);

        // WHEN el administrador asigna el "Sillón 1" como sillón habitual del Dr. Bruno Prueba
        putJson("/profesionales/" + bruno + "/sillon-habitual", """
                {"sillonId":%d}""".formatted(sillon1))
                // THEN el cambio se rechaza con 409 y SILLON_HABITUAL_OCUPADO
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SILLON_HABITUAL_OCUPADO"));

        // AND la Dra. Ana Ejemplo sigue con el "Sillón 1" y el Dr. Bruno Prueba conserva el que tenía
        obtener("/profesionales/" + ana).andExpect(jsonPath("$.sillonHabitualId").value(sillon1));
        obtener("/profesionales/" + bruno).andExpect(jsonPath("$.sillonHabitualId").value(sillon2));
    }

    @Test
    @DisplayName("Sillón habitual de un profesional inactivo")
    void sillonHabitualDeUnProfesionalInactivo() throws Exception {
        // GIVEN un profesional inactivo con el "Sillón 2" como sillón habitual
        long bruno = crearProfesional("Bruno", "Prueba", "MP-0002");
        long ana = crearProfesional("Ana", "Ejemplo", "MP-0001");
        long sillon2 = crearSillon("Sillón 2");
        asignarSillon(bruno, sillon2);
        postVacio("/profesionales/" + bruno + "/desactivar").andExpect(status().isOk());

        // WHEN el administrador asigna el "Sillón 2" como sillón habitual de la Dra. Ana Ejemplo
        putJson("/profesionales/" + ana + "/sillon-habitual", """
                {"sillonId":%d}""".formatted(sillon2))
                // THEN el cambio se rechaza con 409 y SILLON_HABITUAL_OCUPADO
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SILLON_HABITUAL_OCUPADO"));
    }

    @Test
    @DisplayName("Volver a asignar el mismo sillón")
    void volverAAsignarElMismoSillon() throws Exception {
        // GIVEN la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
        long ana = crearProfesional("Ana", "Ejemplo", "MP-0001");
        long sillon1 = crearSillon("Sillón 1");
        asignarSillon(ana, sillon1);

        // WHEN el administrador le asigna otra vez el "Sillón 1"
        putJson("/profesionales/" + ana + "/sillon-habitual", """
                {"sillonId":%d}""".formatted(sillon1))
                // THEN el cambio se acepta y la Dra. Ana Ejemplo sigue con el "Sillón 1"
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sillonHabitualId").value(sillon1));
    }

    @Test
    @DisplayName("Liberar un sillón y asignarlo a otro profesional")
    void liberarUnSillonYAsignarloAOtroProfesional() throws Exception {
        // GIVEN la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual, que el administrador le quitó
        long ana = crearProfesional("Ana", "Ejemplo", "MP-0001");
        long bruno = crearProfesional("Bruno", "Prueba", "MP-0002");
        long sillon1 = crearSillon("Sillón 1");
        asignarSillon(ana, sillon1);
        putJson("/profesionales/" + ana + "/sillon-habitual", """
                {"sillonId":null}""").andExpect(status().isOk());

        // WHEN el administrador asigna el "Sillón 1" como sillón habitual del Dr. Bruno Prueba
        putJson("/profesionales/" + bruno + "/sillon-habitual", """
                {"sillonId":%d}""".formatted(sillon1))
                .andExpect(status().isOk());

        // THEN el Dr. Bruno Prueba figura con el "Sillón 1" como sillón habitual
        obtener("/profesionales/" + bruno).andExpect(jsonPath("$.sillonHabitualId").value(sillon1));
    }

    private void asignarSillon(long profesional, long sillon) throws Exception {
        putJson("/profesionales/" + profesional + "/sillon-habitual", """
                {"sillonId":%d}""".formatted(sillon)).andExpect(status().isOk());
    }
}
