package ar.turnos.odontologia.clinica.application;

import ar.turnos.odontologia.clinica.application.RegistroConsentimientos.Consentimiento;
import ar.turnos.odontologia.clinica.application.RegistroConsentimientos.PlantillaParaFirmar;
import ar.turnos.odontologia.clinica.domain.ClinicaException;
import ar.turnos.odontologia.clinica.domain.CodigoClinica;
import ar.turnos.odontologia.clinica.domain.ConsentimientoFirmado;
import ar.turnos.odontologia.clinica.domain.ConsentimientoFirmado.Firmante;
import ar.turnos.odontologia.clinica.domain.EvidenciaFirma;
import ar.turnos.odontologia.clinica.domain.Revocacion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consentimientos informados firmados en el consultorio (C-27, RN-CL-10 y RN-CL-11, design D1, D4, D6 y D8). Es una
 * firma electrónica, no una firma digital. El documento firmado no se modifica: la revocación se registra aparte y el
 * hash sigue verificando. Este servicio no escribe logs: el texto, el firmante, la IP, el dispositivo y el motivo son
 * datos personales y de salud (design D11).
 */
@Service
@Transactional
public class ServicioConsentimientos {

    private final RegistroConsentimientos registro;
    private final Clock reloj;

    public ServicioConsentimientos(RegistroConsentimientos registro, Clock reloj) {
        this.registro = registro;
        this.reloj = reloj;
    }

    /**
     * @param profesionalId el profesional vinculado al usuario de la sesión
     * @throws AccessDeniedException si el usuario no está vinculado a un profesional (403)
     * @throws ClinicaException {@code RECURSO_NO_ENCONTRADO}, {@code DATOS_INVALIDOS}, {@code FIRMANTE_NO_COINCIDE},
     *                          {@code PLANTILLA_INACTIVA} o {@code VERSION_DESACTUALIZADA}
     */
    public Consentimiento firmar(Long pacienteId, Long profesionalId, Long plantillaId, Integer versionNumero,
                                 String firmanteNombre, String firmanteDni, Boolean aceptado,
                                 EvidenciaFirma evidencia) {
        exigirProfesional(profesionalId);
        String dniPaciente = registro.dniDelPaciente(pacienteId).orElseThrow(ClinicaException::pacienteInexistente);
        Firmante firmante = Firmante.de(firmanteNombre, firmanteDni, aceptado, dniPaciente);
        if (plantillaId == null) {
            throw new ClinicaException(CodigoClinica.DATOS_INVALIDOS, "Falta la plantilla que se firma");
        }
        PlantillaParaFirmar plantilla = registro.plantillaParaFirmar(plantillaId)
                .orElseThrow(() -> new ClinicaException(CodigoClinica.RECURSO_NO_ENCONTRADO, "No existe la plantilla"));
        plantilla.dominio().exigirFirmable(versionNumero);
        ConsentimientoFirmado firmado = ConsentimientoFirmado.firmar(pacienteId, firmante, plantilla.versionId(),
                plantilla.contenido(), profesionalId, evidencia, reloj);
        long id = registro.registrar(firmado, firmado.hash());
        return registro.buscar(pacienteId, id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Consentimiento> listar(Long pacienteId) {
        exigirPaciente(pacienteId);
        return registro.listar(pacienteId);
    }

    @Transactional(readOnly = true)
    public Consentimiento ver(Long pacienteId, Long consentimientoId) {
        exigirPaciente(pacienteId);
        return registro.buscar(pacienteId, consentimientoId)
                .orElseThrow(() -> new ClinicaException(CodigoClinica.RECURSO_NO_ENCONTRADO,
                        "No existe ese consentimiento para el paciente"));
    }

    /**
     * Rearma el contenido canónico con los datos guardados y compara su SHA-256 en tiempo constante con el hash
     * guardado. Un resultado no íntegro no es un error: informarlo es el objetivo (design D4).
     */
    @Transactional(readOnly = true)
    public Verificacion verificar(Long pacienteId, Long consentimientoId) {
        Consentimiento consentimiento = ver(pacienteId, consentimientoId);
        String calculado = consentimiento.contenidoCanonico().sha256();
        boolean integro = MessageDigest.isEqual(calculado.getBytes(StandardCharsets.US_ASCII),
                consentimiento.hash().getBytes(StandardCharsets.US_ASCII));
        return new Verificacion(consentimiento.id(), integro, consentimiento.hashAlgoritmo(), consentimiento.hash(),
                calculado, reloj.instant());
    }

    /**
     * @throws AccessDeniedException si el usuario no está vinculado a un profesional (403)
     * @throws ClinicaException {@code RECURSO_NO_ENCONTRADO}, {@code DATOS_INVALIDOS} o
     *                          {@code CONSENTIMIENTO_YA_REVOCADO}
     */
    public Consentimiento revocar(Long pacienteId, Long consentimientoId, Long profesionalId, String motivo) {
        exigirProfesional(profesionalId);
        String elMotivo = Revocacion.motivo(motivo);
        Consentimiento consentimiento = ver(pacienteId, consentimientoId);
        if (consentimiento.revocado()
                || !registro.revocar(consentimientoId, profesionalId, reloj.instant(), elMotivo)) {
            throw new ClinicaException(CodigoClinica.CONSENTIMIENTO_YA_REVOCADO, "El consentimiento ya está revocado");
        }
        return registro.buscar(pacienteId, consentimientoId).orElseThrow();
    }

    private void exigirPaciente(Long pacienteId) {
        if (registro.dniDelPaciente(pacienteId).isEmpty()) {
            throw ClinicaException.pacienteInexistente();
        }
    }

    private static void exigirProfesional(Long profesionalId) {
        if (profesionalId == null) {
            throw new AccessDeniedException("El usuario no está vinculado a un profesional");
        }
    }

    public record Verificacion(Long consentimientoId, boolean integro, String algoritmo, String hashGuardado,
                               String hashCalculado, Instant verificadoEn) {}
}
