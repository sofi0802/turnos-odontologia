package ar.turnos.odontologia.usuarios.application;

import ar.turnos.odontologia.usuarios.domain.PacienteResumen;

import java.util.ArrayList;
import java.util.List;

/** Puerto de búsqueda en memoria para los tests del caso de uso: devuelve todo lo que se le cargó. */
class BusquedaPacientesEnMemoria implements BusquedaPacientes {

    final List<PacienteResumen> pacientes = new ArrayList<>();
    String ultimoPrefijoDni;
    List<String> ultimasPalabras;

    @Override
    public List<PacienteResumen> porDni(String prefijo, int limite) {
        ultimoPrefijoDni = prefijo;
        return pacientes;
    }

    @Override
    public List<PacienteResumen> porTexto(List<String> palabrasParaLike, int limite) {
        ultimasPalabras = palabrasParaLike;
        return pacientes;
    }
}
