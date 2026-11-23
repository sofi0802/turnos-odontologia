package ar.turnos.odontologia.usuarios.application;

import ar.turnos.odontologia.usuarios.domain.PacienteResumen;
import ar.turnos.odontologia.usuarios.domain.TerminoBusqueda;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Busca pacientes para el personal por DNI o por nombre y apellido, con un máximo de resultados. */
@Service
public class BuscarPacientes {

    public static final int LIMITE = 10;

    private final BusquedaPacientes busqueda;

    public BuscarPacientes(BusquedaPacientes busqueda) {
        this.busqueda = busqueda;
    }

    /** @throws ar.turnos.odontologia.usuarios.domain.BusquedaInvalidaException si el término no cumple las reglas */
    @Transactional(readOnly = true)
    public List<PacienteResumen> buscar(String q) {
        TerminoBusqueda termino = TerminoBusqueda.de(q);
        List<PacienteResumen> encontrados = termino.porDni()
                ? busqueda.porDni(termino.dni(), LIMITE)
                : busqueda.porTexto(termino.palabrasParaLike(), LIMITE);
        return encontrados.size() > LIMITE ? List.copyOf(encontrados.subList(0, LIMITE)) : List.copyOf(encontrados);
    }
}
