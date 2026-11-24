package ar.turnos.odontologia.coberturas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnos.odontologia.coberturas.domain.CoberturaException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;

/**
 * Reintento de las afiliaciones cuando PostgreSQL aborta una transacción por un bloqueo mutuo entre dos inserciones
 * que chocan en una {@code EXCLUDE} (C-18, tarea 4.3).
 */
class ReintentoDeAfiliacionesTest {

    @Test
    @DisplayName("Reintenta después de un bloqueo mutuo y devuelve el resultado")
    void reintentaYDevuelve() {
        AtomicInteger llamadas = new AtomicInteger();
        String resultado = ServicioAfiliaciones.conReintento(() -> {
            if (llamadas.incrementAndGet() == 1) {
                throw new DeadlockLoserDataAccessException("deadlock", null);
            }
            return "listo";
        });
        assertThat(resultado).isEqualTo("listo");
        assertThat(llamadas).hasValue(2);
    }

    @Test
    @DisplayName("Se rinde después de tres intentos")
    void seRindeAlTercerIntento() {
        AtomicInteger llamadas = new AtomicInteger();
        assertThatThrownBy(() -> ServicioAfiliaciones.conReintento(() -> {
            llamadas.incrementAndGet();
            throw new CannotAcquireLockException("bloqueo");
        })).isInstanceOf(CannotAcquireLockException.class);
        assertThat(llamadas).hasValue(ServicioAfiliaciones.INTENTOS);
    }

    @Test
    @DisplayName("No reintenta un error de negocio")
    void noReintentaUnErrorDeNegocio() {
        AtomicInteger llamadas = new AtomicInteger();
        assertThatThrownBy(() -> ServicioAfiliaciones.conReintento(() -> {
            llamadas.incrementAndGet();
            throw CoberturaException.noEncontrado("No existe el paciente");
        })).isInstanceOf(CoberturaException.class);
        assertThat(llamadas).hasValue(1);
    }
}
