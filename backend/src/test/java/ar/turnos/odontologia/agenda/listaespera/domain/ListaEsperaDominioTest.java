package ar.turnos.odontologia.agenda.listaespera.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Reglas puras de la lista de espera (C-15, RN-LE-01, RN-LE-03 a RN-LE-05), sin base de datos. */
class ListaEsperaDominioTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate HOY = LocalDate.of(2026, 12, 1);

    private static Instant a(int dia, int hora, int minuto) {
        return LocalDateTime.of(2026, 12, dia, hora, minuto).atZone(BUENOS_AIRES).toInstant();
    }

    @Nested
    class Rango {

        @Test
        void rangoValidoDeHastaSesentaDias() {
            RangoDeFechas rango = RangoDeFechas.validado(HOY, HOY.plusDays(60), HOY);
            assertThat(rango.contiene(HOY.plusDays(30))).isTrue();
            assertThat(rango.contiene(HOY.plusDays(61))).isFalse();
        }

        @Test
        void rangoDeMasDeSesentaDias() {
            assertThatThrownBy(() -> RangoDeFechas.validado(HOY, LocalDate.of(2027, 2, 15), HOY))
                    .isInstanceOfSatisfying(ListaEsperaException.class,
                            e -> assertThat(e.codigo()).isEqualTo(CodigoListaEspera.DATOS_INVALIDOS));
        }

        @Test
        void rangoQueEmpiezaEnElPasado() {
            assertThatThrownBy(() -> RangoDeFechas.validado(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 5), HOY))
                    .isInstanceOfSatisfying(ListaEsperaException.class,
                            e -> assertThat(e.codigo()).isEqualTo(CodigoListaEspera.DATOS_INVALIDOS));
        }

        @Test
        void rangoQueTerminaAntesDeEmpezar() {
            assertThatThrownBy(() -> RangoDeFechas.validado(HOY.plusDays(5), HOY.plusDays(2), HOY))
                    .isInstanceOfSatisfying(ListaEsperaException.class,
                            e -> assertThat(e.codigo()).isEqualTo(CodigoListaEspera.DATOS_INVALIDOS));
        }
    }

    @Nested
    class Plazo {

        @Test
        void plazoPorDefecto() {
            // la oferta de las 08:00 para un horario de las 15:00 vence a las 10:00
            assertThat(PlazoOferta.PREDETERMINADO.vencimiento(a(1, 8, 0), a(1, 15, 0))).isEqualTo(a(1, 10, 0));
        }

        @Test
        void elInicioLlegaAntesQueElPlazo() {
            assertThat(PlazoOferta.deHoras(2).vencimiento(a(1, 9, 0), a(1, 10, 0))).isEqualTo(a(1, 10, 0));
        }

        @Test
        void unPlazoMasLargo() {
            assertThat(PlazoOferta.deHoras(4).vencimiento(a(1, 8, 0), a(3, 10, 0))).isEqualTo(a(1, 12, 0));
        }

        @Test
        void plazoFueraDeRango() {
            for (int horas : new int[] {0, 25, -1}) {
                assertThatThrownBy(() -> PlazoOferta.deHoras(horas))
                        .isInstanceOfSatisfying(ListaEsperaException.class,
                                e -> assertThat(e.codigo()).isEqualTo(CodigoListaEspera.PLAZO_INVALIDO));
            }
            assertThat(PlazoOferta.deHoras(1).horas()).isEqualTo(1);
            assertThat(PlazoOferta.deHoras(24).horas()).isEqualTo(24);
        }
    }

    @Nested
    class Candidatas {

        private final RangoDeFechas delUnoAlCinco = new RangoDeFechas(HOY, LocalDate.of(2026, 12, 5));

        @Test
        void sinPreferenciaAdmiteCualquierProfesionalDentroDelRango() {
            Inscripcion inscripcion = Inscripcion.nueva(1L, 10L, null, delUnoAlCinco, a(1, 8, 0));
            assertThat(inscripcion.admite(5L, LocalDate.of(2026, 12, 3))).isTrue();
        }

        @Test
        void otroProfesionalElegido() {
            Inscripcion inscripcion = Inscripcion.nueva(1L, 10L, 7L, delUnoAlCinco, a(1, 8, 0));
            assertThat(inscripcion.admite(5L, LocalDate.of(2026, 12, 3))).isFalse();
            assertThat(inscripcion.admite(7L, LocalDate.of(2026, 12, 3))).isTrue();
        }

        @Test
        void fueraDelRangoDeFechas() {
            Inscripcion inscripcion = Inscripcion.nueva(1L, 10L, null, delUnoAlCinco, a(1, 8, 0));
            assertThat(inscripcion.admite(5L, LocalDate.of(2026, 12, 9))).isFalse();
        }

        @Test
        void unaInscripcionQueNoEstaActivaNoAdmiteNada() {
            Inscripcion baja = Inscripcion.nueva(1L, 10L, null, delUnoAlCinco, a(1, 8, 0))
                    .conEstado(EstadoInscripcion.BAJA);
            assertThat(baja.admite(5L, LocalDate.of(2026, 12, 3))).isFalse();
        }
    }

    @Nested
    class Respuesta {

        private Oferta pendiente() {
            return new Oferta(1L, 2L, 3L, 4L, a(3, 10, 0), EstadoOferta.PENDIENTE, a(1, 10, 0));
        }

        @Test
        void unaOfertaPendienteSeResponde() {
            pendiente().verificarRespondible(a(1, 9, 59));
        }

        @Test
        void aceptarDespuesDelVencimiento() {
            assertThatThrownBy(() -> pendiente().verificarRespondible(a(1, 10, 5)))
                    .isInstanceOfSatisfying(ListaEsperaException.class,
                            e -> assertThat(e.codigo()).isEqualTo(CodigoListaEspera.OFERTA_VENCIDA));
        }

        @Test
        void justoAlVencerYaVencio() {
            assertThatThrownBy(() -> pendiente().verificarRespondible(a(1, 10, 0)))
                    .isInstanceOfSatisfying(ListaEsperaException.class,
                            e -> assertThat(e.codigo()).isEqualTo(CodigoListaEspera.OFERTA_VENCIDA));
        }

        @Test
        void aceptarDosVeces() {
            Oferta aceptada = pendiente().conEstado(EstadoOferta.ACEPTADA);
            assertThatThrownBy(() -> aceptada.verificarRespondible(a(1, 9, 0)))
                    .isInstanceOfSatisfying(ListaEsperaException.class,
                            e -> assertThat(e.codigo()).isEqualTo(CodigoListaEspera.OFERTA_YA_RESPONDIDA));
        }

        @Test
        void unaOfertaMarcadaVencidaSeInformaComoVencida() {
            Oferta vencida = pendiente().conEstado(EstadoOferta.VENCIDA);
            assertThatThrownBy(() -> vencida.verificarRespondible(a(1, 9, 0)))
                    .isInstanceOfSatisfying(ListaEsperaException.class,
                            e -> assertThat(e.codigo()).isEqualTo(CodigoListaEspera.OFERTA_VENCIDA));
        }
    }

    @Test
    void cadaCodigoTieneSuEstadoHttp() {
        assertThat(CodigoListaEspera.DATOS_INVALIDOS.estadoHttp()).isEqualTo(422);
        assertThat(CodigoListaEspera.PLAZO_INVALIDO.estadoHttp()).isEqualTo(422);
        assertThat(CodigoListaEspera.YA_EN_LISTA_DE_ESPERA.estadoHttp()).isEqualTo(409);
        assertThat(CodigoListaEspera.SIN_CONSENTIMIENTO.estadoHttp()).isEqualTo(409);
        assertThat(CodigoListaEspera.OFERTA_NO_DISPONIBLE.estadoHttp()).isEqualTo(409);
        assertThat(CodigoListaEspera.OFERTA_INEXISTENTE.estadoHttp()).isEqualTo(404);
        assertThat(CodigoListaEspera.RECURSO_NO_ENCONTRADO.estadoHttp()).isEqualTo(404);
    }
}
