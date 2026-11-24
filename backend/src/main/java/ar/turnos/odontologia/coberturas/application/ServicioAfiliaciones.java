package ar.turnos.odontologia.coberturas.application;

import ar.turnos.odontologia.coberturas.application.Vistas.AfiliacionVista;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Casos de uso de afiliaciones (RN-OS-03 a RN-OS-05, design D4). Delega en {@link OperacionesAfiliaciones}, que
 * corre cada operación en su transacción, y reintenta si PostgreSQL elige a esa transacción como víctima de un
 * bloqueo mutuo.
 *
 * <p>Por qué: cuando dos pedidos insertan a la vez filas que chocan en una {@code EXCLUDE}, cada inserción espera a la
 * otra y la base resuelve el cruce abortando una (SQLSTATE 40P01, "deadlock detected") en lugar de dar la violación
 * de la restricción. Al reintentar, la verificación del dominio ya ve la fila que sí se guardó y responde el
 * {@code 409} preciso ({@code VIGENCIA_SUPERPUESTA} o {@code AFILIADO_DUPLICADO}); si no había conflicto real, la
 * operación simplemente se completa.
 */
@Service
public class ServicioAfiliaciones {

    static final int INTENTOS = 3;

    private final OperacionesAfiliaciones operaciones;

    public ServicioAfiliaciones(OperacionesAfiliaciones operaciones) {
        this.operaciones = operaciones;
    }

    public AfiliacionVista afiliar(Long pacienteId, Long planId, String numero, LocalDate desde, LocalDate hasta) {
        return conReintento(() -> operaciones.afiliar(pacienteId, planId, numero, desde, hasta));
    }

    public AfiliacionVista corregir(Long pacienteId, Long afiliacionId, Long planId, String numero, LocalDate desde) {
        return conReintento(() -> operaciones.corregir(pacienteId, afiliacionId, planId, numero, desde));
    }

    public AfiliacionVista cerrar(Long pacienteId, Long afiliacionId, LocalDate hasta) {
        return conReintento(() -> operaciones.cerrar(pacienteId, afiliacionId, hasta));
    }

    public List<AfiliacionVista> historial(Long pacienteId) {
        return operaciones.historial(pacienteId);
    }

    static <T> T conReintento(Supplier<T> operacion) {
        for (int intento = 1; ; intento++) {
            try {
                return operacion.get();
            } catch (PessimisticLockingFailureException e) {
                if (intento == INTENTOS) {
                    throw e;
                }
            }
        }
    }
}
