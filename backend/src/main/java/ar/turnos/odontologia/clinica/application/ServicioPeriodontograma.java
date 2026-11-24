package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.application.RegistroPeriodontal.ResumenVersion;
import ar.turnos.odontologia.clinica.application.RegistroPeriodontal.Version;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.clinica.domain.MedicionSitio;
import ar.turnos.odontologia.clinica.domain.PiezaDental;
import ar.turnos.odontologia.clinica.domain.PiezaPeriodontal;
import ar.turnos.odontologia.clinica.domain.SitioPeriodontal;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodontograma por visita (C-25, RN-CL-04 a RN-CL-06). Un pedido es atómico: se valida cada pieza en el dominio
 * antes de guardar, y la versión queda con el profesional de la sesión y el momento del reloj (design D5 y D7).
 */
@Service
@Transactional
public class ServicioPeriodontograma {

    private final RegistroClinico pacientes;
    private final RegistroPeriodontal registro;
    private final Clock reloj;

    public ServicioPeriodontograma(RegistroClinico pacientes, RegistroPeriodontal registro, Clock reloj) {
        this.pacientes = pacientes;
        this.registro = registro;
        this.reloj = reloj;
    }

    /**
     * @param profesionalId el profesional vinculado al usuario de la sesión
     * @throws AccessDeniedException si el usuario no está vinculado a un profesional (403)
     * @throws ClinicaException {@code RECURSO_NO_ENCONTRADO}, {@code DATOS_INVALIDOS}, {@code PIEZA_INVALIDA},
     *                          {@code SITIOS_INCOMPLETOS} o {@code FURCACION_NO_APLICA}, sin guardar nada
     */
    public Version registrar(Long pacienteId, List<PiezaPedido> pedido, Long profesionalId) {
        if (profesionalId == null) {
            throw new AccessDeniedException("El usuario no está vinculado a un profesional");
        }
        exigirPaciente(pacienteId);
        if (pedido == null || pedido.isEmpty()) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "El periodontograma tiene que tener al menos una pieza");
        }
        Set<Integer> numeros = new HashSet<>();
        for (PiezaPedido pieza : pedido) {
            if (!numeros.add(pieza.pieza())) {
                throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS,
                        "La pieza " + pieza.pieza() + " está repetida en el periodontograma");
            }
        }
        List<PiezaPeriodontal> piezas = pedido.stream().map(ServicioPeriodontograma::validar).toList();
        long versionId = registro.registrar(pacienteId, profesionalId, reloj.instant(), piezas);
        return registro.version(pacienteId, versionId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ResumenVersion> versiones(Long pacienteId) {
        exigirPaciente(pacienteId);
        return registro.versiones(pacienteId);
    }

    @Transactional(readOnly = true)
    public Version version(Long pacienteId, Long versionId) {
        exigirPaciente(pacienteId);
        return registro.version(pacienteId, versionId)
                .orElseThrow(() -> new ClinicaException(CodigoClinica.RECURSO_NO_ENCONTRADO,
                        "No existe esa versión del periodontograma para el paciente"));
    }

    private static PiezaPeriodontal validar(PiezaPedido pedido) {
        List<MedicionSitio> sitios = pedido.sitios() == null ? List.of() : pedido.sitios().stream()
                .map(s -> new MedicionSitio(SitioPeriodontal.de(s.sitio()), s.profundidadSondaje(), s.recesion(),
                        s.sangrado(), s.placa()))
                .toList();
        return PiezaPeriodontal.de(PiezaDental.de(pedido.pieza()), pedido.ausente(), pedido.movilidad(),
                pedido.furcacion(), sitios);
    }

    private void exigirPaciente(Long pacienteId) {
        if (!pacientes.existePaciente(pacienteId)) {
            throw ClinicaException.pacienteInexistente();
        }
    }

    /** Una pieza tal como llega en el pedido, todavía sin validar. */
    public record PiezaPedido(int pieza, boolean ausente, Integer movilidad, Integer furcacion,
                              List<SitioPedido> sitios) {}

    /** Un sitio tal como llega en el pedido, todavía sin validar. */
    public record SitioPedido(String sitio, int profundidadSondaje, int recesion, boolean sangrado, boolean placa) {}
}
