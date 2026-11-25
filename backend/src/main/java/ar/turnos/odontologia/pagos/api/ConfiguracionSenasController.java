package ar.turnos.odontologia.pagos.api;

import ar.turnos.odontologia.pagos.application.ConfiguracionSenas;
import ar.turnos.odontologia.pagos.domain.ConfiguracionSena;
import ar.turnos.odontologia.pagos.domain.DatoInvalidoException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuración de las señas, solo para el {@code ADMIN} (RN-SE-01, design D9). */
@RestController
@RequestMapping("/api/v1/senas/configuracion")
@PreAuthorize("hasRole('ADMIN')")
class ConfiguracionSenasController {

    private final ConfiguracionSenas configuracion;

    ConfiguracionSenasController(ConfiguracionSenas configuracion) {
        this.configuracion = configuracion;
    }

    record ConfiguracionDto(Boolean activas, Integer porcentaje, Integer plazoMinutos) {

        static ConfiguracionDto de(ConfiguracionSena c) {
            return new ConfiguracionDto(c.activas(), c.porcentaje(), c.plazoMinutos());
        }
    }

    @GetMapping
    ConfiguracionDto leer() {
        return ConfiguracionDto.de(configuracion.leer());
    }

    @PutMapping
    ConfiguracionDto guardar(@RequestBody ConfiguracionDto pedido) {
        if (pedido.activas() == null) {
            throw new DatoInvalidoException("activas", "Es obligatorio.");
        }
        if (pedido.porcentaje() == null) {
            throw new DatoInvalidoException("porcentaje", "Escribí un número entero de 1 a 100.");
        }
        if (pedido.plazoMinutos() == null) {
            throw new DatoInvalidoException("plazoMinutos", "Escribí un plazo de 5 a 1440 minutos.");
        }
        return ConfiguracionDto.de(configuracion.guardar(
                new ConfiguracionSena(pedido.activas(), pedido.porcentaje(), pedido.plazoMinutos())));
    }
}
