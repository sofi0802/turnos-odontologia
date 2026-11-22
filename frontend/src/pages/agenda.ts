import { CATALOGOS_VACIOS, cargarCatalogos, type Catalogos } from '../agenda/catalogos.ts';
import { consultarAgenda, type Filtros, type TurnoAgenda } from '../agenda/consulta.ts';
import { abrirCancelacion, abrirNuevoTurno, abrirReprogramacion, type ControlAgenda } from '../agenda/formularios.ts';
import { mover, periodoDe, type Periodo, type Vista } from '../agenda/periodo.ts';
import { dibujarAgenda, fechaCorta } from '../agenda/vista.ts';
import { ErrorSesion } from '../shared/api/errores.ts';
import { hoy, sumarDias } from '../shared/fecha/zona.ts';
import { cambiarOpciones, crearAviso, crearBoton, crearCampo, crearSelector, type Opcion } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

export const MENSAJE_SESION_TERMINADA = 'Tu sesión terminó. Ingresá de nuevo.';

const TODOS: Opcion = { valor: '', texto: 'Todos' };

function textoDelPeriodo(periodo: Periodo): string {
  return periodo.vista === 'dia'
    ? fechaCorta(periodo.desde)
    : `Semana del ${fechaCorta(periodo.desde)} al ${fechaCorta(sumarDias(periodo.hasta, -1))}`;
}

function idElegido(selector: HTMLSelectElement): number | null {
  return selector.value === '' ? null : Number(selector.value);
}

function opcionesDe(items: Catalogos['profesionales']): Opcion[] {
  return [TODOS, ...items.map((item) => ({ valor: String(item.id), texto: item.nombre }))];
}

function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  let periodo = periodoDe('dia', hoy());
  let catalogos: Catalogos = CATALOGOS_VACIOS;
  let turnos: TurnoAgenda[] = [];
  let consulta = 0;
  let errorDeCarga = false;

  const exito = crearAviso('status');
  const error = crearAviso('alert');

  const botonDia = crearBoton('Día');
  const botonSemana = crearBoton('Semana');
  const vistas = document.createElement('div');
  vistas.className = 'grupo-botones';
  vistas.setAttribute('role', 'group');
  vistas.setAttribute('aria-label', 'Vista');
  vistas.append(botonDia, botonSemana);

  const anterior = crearBoton('Anterior');
  const botonHoy = crearBoton('Hoy');
  const siguiente = crearBoton('Siguiente');
  const navegacion = document.createElement('div');
  navegacion.className = 'grupo-botones';
  navegacion.setAttribute('role', 'group');
  navegacion.setAttribute('aria-label', 'Período');
  navegacion.append(anterior, botonHoy, siguiente);

  const textoPeriodo = document.createElement('p');
  textoPeriodo.className = 'agenda-periodo';
  textoPeriodo.setAttribute('aria-live', 'polite');

  const barra = document.createElement('div');
  barra.className = 'agenda-barra';
  barra.append(vistas, navegacion, textoPeriodo);

  const profesional = crearCampo('Profesional', crearSelector('profesionalId', [TODOS]));
  const sillon = crearCampo('Sillón', crearSelector('sillonId', [TODOS]));
  const nuevo = crearBoton('Nuevo turno');
  nuevo.className = 'boton-principal';
  const filtros = document.createElement('div');
  filtros.className = 'agenda-filtros';
  filtros.append(profesional.contenedor, sillon.contenedor, nuevo);

  const avisosCatalogo = document.createElement('div');
  avisosCatalogo.className = 'agenda-avisos';

  const lista = document.createElement('div');
  lista.className = 'agenda-lista';

  contenedor.replaceChildren(crearTitulo('Agenda'), exito, error, barra, filtros, avisosCatalogo, lista);

  const control: ControlAgenda = {
    api: contexto.api,
    catalogos: () => catalogos,
    fechaInicial: () => periodo.desde,
    recargar: () => void cargarTurnos(),
    mostrarExito(mensaje) {
      error.textContent = '';
      exito.textContent = mensaje;
    },
    mostrarError(mensaje) {
      exito.textContent = '';
      error.textContent = mensaje;
    },
    sesionVencida(causa) {
      if (causa instanceof ErrorSesion) {
        contexto.irAlIngreso(MENSAJE_SESION_TERMINADA);
        return true;
      }
      return false;
    },
  };

  const acciones = {
    cancelar: (turno: TurnoAgenda) => abrirCancelacion(control, turno),
    reprogramar: (turno: TurnoAgenda) => abrirReprogramacion(control, turno),
  };

  function filtrosActuales(): Filtros {
    return { profesionalId: idElegido(profesional.control), sillonId: idElegido(sillon.control) };
  }

  function actualizarBarra(): void {
    botonDia.setAttribute('aria-pressed', String(periodo.vista === 'dia'));
    botonSemana.setAttribute('aria-pressed', String(periodo.vista === 'semana'));
    textoPeriodo.textContent = textoDelPeriodo(periodo);
  }

  async function cargarTurnos(): Promise<void> {
    consulta += 1;
    const esta = consulta;
    actualizarBarra();
    lista.setAttribute('aria-busy', 'true');
    try {
      const resultado = await consultarAgenda(contexto.api, periodo, filtrosActuales());
      if (esta !== consulta) {
        return;
      }
      turnos = resultado;
      if (errorDeCarga) {
        // Solo se limpia el error de una carga anterior, no el aviso de una acción.
        error.textContent = '';
        errorDeCarga = false;
      }
      dibujarAgenda(lista, periodo, turnos, catalogos, acciones);
    } catch (causa) {
      if (esta !== consulta || control.sesionVencida(causa)) {
        return;
      }
      error.textContent = mensajeDeError(causa);
      errorDeCarga = true;
      lista.replaceChildren();
    } finally {
      if (esta === consulta) {
        lista.removeAttribute('aria-busy');
      }
    }
  }

  function cambiarPeriodo(nuevoPeriodo: Periodo): void {
    periodo = nuevoPeriodo;
    exito.textContent = '';
    void cargarTurnos();
  }

  function cambiarVista(vista: Vista): void {
    if (periodo.vista !== vista) {
      cambiarPeriodo(periodoDe(vista, periodo.desde));
    }
  }

  botonDia.addEventListener('click', () => cambiarVista('dia'));
  botonSemana.addEventListener('click', () => cambiarVista('semana'));
  anterior.addEventListener('click', () => cambiarPeriodo(mover(periodo, -1)));
  siguiente.addEventListener('click', () => cambiarPeriodo(mover(periodo, 1)));
  botonHoy.addEventListener('click', () => cambiarPeriodo(periodoDe(periodo.vista, hoy())));
  profesional.control.addEventListener('change', () => void cargarTurnos());
  sillon.control.addEventListener('change', () => void cargarTurnos());
  nuevo.addEventListener('click', () => abrirNuevoTurno(control));

  void cargarCatalogos(contexto.api).then((cargados) => {
    catalogos = cargados;
    cambiarOpciones(profesional.control, opcionesDe(cargados.profesionales));
    cambiarOpciones(sillon.control, opcionesDe(cargados.sillones));
    avisosCatalogo.replaceChildren(
      ...cargados.avisos.map((textoAviso) => {
        const parrafo = crearAviso('status');
        parrafo.textContent = textoAviso;
        return parrafo;
      }),
    );
    if (lista.getAttribute('aria-busy') !== 'true') {
      dibujarAgenda(lista, periodo, turnos, catalogos, acciones);
    }
  });
  void cargarTurnos();
}

/** Agenda del personal (US-014). Privada: solo con sesión del personal. */
export const paginaAgenda: Pagina = {
  ruta: 'agenda',
  titulo: 'Agenda',
  privada: true,
  render: dibujar,
};
