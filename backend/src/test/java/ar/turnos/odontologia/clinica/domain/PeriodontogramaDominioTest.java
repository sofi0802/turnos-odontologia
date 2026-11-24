package ar.turnos.odontologia.clinica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Reglas puras del periodontograma (C-25, tareas 2.1 a 2.3): RN-CL-05 y RN-CL-06. Cada escenario del spec
 * {@code periodontograma} que no necesita la base tiene acá su test con el mismo nombre.
 */
class PeriodontogramaDominioTest {

    @Nested
    class TiposDePieza {

        @ParameterizedTest(name = "pieza {0}: permanente {1}, multirradicular {2}")
        @CsvSource({"11, true, false", "14, true, true", "15, true, false", "24, true, true", "34, true, false",
                "36, true, true", "48, true, true", "55, false, false"})
        @DisplayName("Distingue las piezas permanentes y las multirradiculares")
        void tipos(int numero, boolean permanente, boolean multirradicular) {
            PiezaDental pieza = PiezaDental.de(numero);
            assertThat(pieza.esPermanente()).isEqualTo(permanente);
            assertThat(pieza.esMultirradicular()).isEqualTo(multirradicular);
        }
    }

    @Nested
    class SoloPiezasPermanentes {

        @Test
        @DisplayName("Pieza temporal")
        void piezaTemporal() {
            assertCodigo(() -> presente(55, 0, null, sanos()), CodigoClinica.PIEZA_INVALIDA);
        }

        @Test
        @DisplayName("Pieza fuera de la notación FDI")
        void piezaFueraDeLaNotacionFdi() {
            assertCodigo(() -> presente(19, 0, null, sanos()), CodigoClinica.PIEZA_INVALIDA);
        }
    }

    @Nested
    class SeisSitiosPorPiezaPresente {

        @Test
        @DisplayName("Falta un sitio")
        void faltaUnSitio() {
            List<MedicionSitio> sitios = new ArrayList<>(sanos());
            sitios.removeIf(s -> s.sitio() == SitioPeriodontal.DL);
            assertCodigo(() -> presente(36, 0, null, sitios), CodigoClinica.SITIOS_INCOMPLETOS);
        }

        @Test
        @DisplayName("Sitio repetido")
        void sitioRepetido() {
            List<MedicionSitio> sitios = new ArrayList<>(sanos());
            sitios.set(0, medicion(SitioPeriodontal.V, 2, -2, false));
            assertCodigo(() -> presente(36, 0, null, sitios), CodigoClinica.SITIOS_INCOMPLETOS);
        }

        @Test
        @DisplayName("Falta la movilidad")
        void faltaLaMovilidad() {
            assertCodigo(() -> presente(11, null, null, sanos()), CodigoClinica.SITIOS_INCOMPLETOS);
        }

        @Test
        @DisplayName("Una pieza presente con sus 6 sitios y movilidad es válida")
        void piezaCompleta() {
            PiezaPeriodontal pieza = presente(11, 1, null, sanos());
            assertThat(pieza.sitios()).hasSize(6);
            assertThat(pieza.movilidad()).isEqualTo(1);
        }
    }

    @Nested
    class RangosDeLosValores {

        @ParameterizedTest(name = "profundidad {0}")
        @ValueSource(ints = {0, 15})
        @DisplayName("Acepta los bordes de la profundidad de sondaje")
        void bordesDeProfundidad(int profundidad) {
            assertThat(medicion(SitioPeriodontal.MV, profundidad, 0, false).profundidadSondaje()).isEqualTo(profundidad);
        }

        @ParameterizedTest(name = "recesión {0}")
        @ValueSource(ints = {-5, 15})
        @DisplayName("Acepta los bordes de la recesión")
        void bordesDeRecesion(int recesion) {
            assertThat(medicion(SitioPeriodontal.V, 2, recesion, false).recesion()).isEqualTo(recesion);
        }

        @Test
        @DisplayName("Profundidad fuera de rango")
        void profundidadFueraDeRango() {
            assertCodigo(() -> medicion(SitioPeriodontal.MV, 16, 0, false), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> medicion(SitioPeriodontal.MV, -1, 0, false), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Recesión negativa permitida")
        void recesionNegativaPermitida() {
            assertThat(medicion(SitioPeriodontal.V, 2, -2, false).recesion()).isEqualTo(-2);
        }

        @Test
        @DisplayName("Recesión fuera de rango")
        void recesionFueraDeRango() {
            assertCodigo(() -> medicion(SitioPeriodontal.V, 2, -6, false), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> medicion(SitioPeriodontal.V, 2, 16, false), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Movilidad fuera de rango")
        void movilidadFueraDeRango() {
            assertCodigo(() -> presente(11, 4, null, sanos()), CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> presente(11, -1, null, sanos()), CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Furcación fuera de rango")
        void furcacionFueraDeRango() {
            assertCodigo(() -> presente(46, 0, 4, sanos()), CodigoClinica.DATOS_INVALIDOS);
        }
    }

    @Nested
    class FurcacionSoloEnPiezasMultirradiculares {

        @Test
        @DisplayName("Furcación en un molar")
        void furcacionEnUnMolar() {
            assertThat(presente(46, 0, 2, sanos()).furcacion()).isEqualTo(2);
        }

        @Test
        @DisplayName("Furcación en el primer premolar superior")
        void furcacionEnElPrimerPremolarSuperior() {
            assertThat(presente(14, 0, 1, sanos()).furcacion()).isEqualTo(1);
        }

        @Test
        @DisplayName("Furcación en un incisivo")
        void furcacionEnUnIncisivo() {
            assertCodigo(() -> presente(11, 0, 1, sanos()), CodigoClinica.FURCACION_NO_APLICA);
        }

        @Test
        @DisplayName("Molar sin furcación")
        void molarSinFurcacion() {
            assertThat(presente(36, 0, null, sanos()).furcacion()).isNull();
        }
    }

    @Nested
    class PiezaAusente {

        @Test
        @DisplayName("Registrar una pieza ausente")
        void registrarUnaPiezaAusente() {
            PiezaPeriodontal pieza = PiezaPeriodontal.de(PiezaDental.de(18), true, null, null, List.of());
            assertThat(pieza.ausente()).isTrue();
            assertThat(pieza.estado()).isNull();
        }

        @Test
        @DisplayName("Pieza ausente con datos")
        void piezaAusenteConDatos() {
            assertCodigo(() -> PiezaPeriodontal.de(PiezaDental.de(18), true, 1, null, List.of()),
                    CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> PiezaPeriodontal.de(PiezaDental.de(18), true, null, 0, List.of()),
                    CodigoClinica.DATOS_INVALIDOS);
            assertCodigo(() -> PiezaPeriodontal.de(PiezaDental.de(18), true, null, null, sanos()),
                    CodigoClinica.DATOS_INVALIDOS);
        }

        @Test
        @DisplayName("Una pieza temporal ausente también se rechaza")
        void temporalAusente() {
            assertCodigo(() -> PiezaPeriodontal.de(PiezaDental.de(55), true, null, null, List.of()),
                    CodigoClinica.PIEZA_INVALIDA);
        }
    }

    @Nested
    class EstadoPeriodontalDeCadaPieza {

        @Test
        @DisplayName("Pieza sana")
        void piezaSana() {
            assertThat(estado(todos(2, -2, false))).isEqualTo(EstadoPeriodontal.SANO);
        }

        @Test
        @DisplayName("Recesión negativa compensa la profundidad")
        void recesionNegativaCompensaLaProfundidad() {
            assertThat(estado(todos(3, -3, false))).isEqualTo(EstadoPeriodontal.SANO);
        }

        @Test
        @DisplayName("Sangrado sin pérdida de inserción")
        void sangradoSinPerdidaDeInsercion() {
            List<MedicionSitio> sitios = todos(2, -2, false);
            reemplazar(sitios, medicion(SitioPeriodontal.V, 2, -2, true));
            assertThat(estado(sitios)).isEqualTo(EstadoPeriodontal.LEVE);
        }

        @Test
        @DisplayName("Bolsa sin pérdida de inserción")
        void bolsaSinPerdidaDeInsercion() {
            List<MedicionSitio> sitios = todos(2, -2, false);
            reemplazar(sitios, medicion(SitioPeriodontal.V, 4, -4, false));
            assertThat(estado(sitios)).isEqualTo(EstadoPeriodontal.LEVE);
        }

        @Test
        @DisplayName("Pérdida leve")
        void perdidaLeve() {
            List<MedicionSitio> sitios = todos(2, -2, false);
            reemplazar(sitios, medicion(SitioPeriodontal.MV, 2, 0, false));
            assertThat(estado(sitios)).isEqualTo(EstadoPeriodontal.LEVE);
        }

        @Test
        @DisplayName("Pérdida de 1 mm es leve")
        void perdidaDeUnMilimetro() {
            List<MedicionSitio> sitios = todos(2, -2, false);
            reemplazar(sitios, medicion(SitioPeriodontal.MV, 2, -1, false));
            assertThat(estado(sitios)).isEqualTo(EstadoPeriodontal.LEVE);
        }

        @Test
        @DisplayName("Límite entre leve y moderado")
        void limiteEntreLeveYModerado() {
            List<MedicionSitio> sitios = todos(2, -2, false);
            reemplazar(sitios, medicion(SitioPeriodontal.DV, 2, 1, false));
            assertThat(estado(sitios)).isEqualTo(EstadoPeriodontal.MODERADO);
        }

        @Test
        @DisplayName("Pérdida moderada")
        void perdidaModerada() {
            List<MedicionSitio> sitios = todos(2, -2, false);
            reemplazar(sitios, medicion(SitioPeriodontal.L, 4, 0, false));
            assertThat(estado(sitios)).isEqualTo(EstadoPeriodontal.MODERADO);
        }

        @Test
        @DisplayName("Límite entre moderado y severo")
        void limiteEntreModeradoYSevero() {
            List<MedicionSitio> sitios = todos(2, -2, false);
            reemplazar(sitios, medicion(SitioPeriodontal.ML, 3, 2, false));
            assertThat(estado(sitios)).isEqualTo(EstadoPeriodontal.SEVERO);
        }

        @Test
        @DisplayName("Pérdida severa")
        void perdidaSevera() {
            List<MedicionSitio> sitios = todos(2, -2, false);
            reemplazar(sitios, medicion(SitioPeriodontal.DL, 7, 1, false));
            assertThat(estado(sitios)).isEqualTo(EstadoPeriodontal.SEVERO);
        }

        @Test
        @DisplayName("El sangrado no cambia una pérdida moderada")
        void sangradoConPerdida() {
            List<MedicionSitio> sitios = todos(2, -2, true);
            reemplazar(sitios, medicion(SitioPeriodontal.L, 4, 0, true));
            assertThat(estado(sitios)).isEqualTo(EstadoPeriodontal.MODERADO);
        }

        private EstadoPeriodontal estado(List<MedicionSitio> sitios) {
            return presente(11, 0, null, sitios).estado();
        }
    }

    // ---- helpers ----

    private static PiezaPeriodontal presente(int pieza, Integer movilidad, Integer furcacion,
                                             List<MedicionSitio> sitios) {
        return PiezaPeriodontal.de(PiezaDental.de(pieza), false, movilidad, furcacion, sitios);
    }

    private static MedicionSitio medicion(SitioPeriodontal sitio, int profundidad, int recesion, boolean sangrado) {
        return new MedicionSitio(sitio, profundidad, recesion, sangrado, false);
    }

    /** Los 6 sitios con la misma medición. */
    private static List<MedicionSitio> todos(int profundidad, int recesion, boolean sangrado) {
        List<MedicionSitio> sitios = new ArrayList<>();
        for (SitioPeriodontal sitio : SitioPeriodontal.values()) {
            sitios.add(medicion(sitio, profundidad, recesion, sangrado));
        }
        return sitios;
    }

    private static List<MedicionSitio> sanos() {
        return todos(2, -2, false);
    }

    private static void reemplazar(List<MedicionSitio> sitios, MedicionSitio nueva) {
        sitios.replaceAll(s -> s.sitio() == nueva.sitio() ? nueva : s);
    }

    private static void assertCodigo(Runnable accion, CodigoClinica codigo) {
        assertThatThrownBy(accion::run)
                .isInstanceOf(ClinicaException.class)
                .extracting(e -> ((ClinicaException) e).codigo())
                .isEqualTo(codigo);
    }
}
