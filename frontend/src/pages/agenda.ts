import { CATALOGOS_VACIOS, cargarCatalogos, type Catalogos } from '../agenda/catalogos.ts';
import { consultarAgenda, type Filtros, type TurnoAgenda } from '../agenda/consulta.ts';
import { abrirCancelacion, abrirNuevoTurno, abrirReprogramacion, type ControlAgenda } from '../agenda/formularios.ts';
import { mover, periodoDe, type Periodo, type Vista } from '../agenda/periodo.ts';
import { registrarAsistencia, type AccionAsistencia } from '../agenda/asistencia.ts';
import { abrirExtension, abrirSobreturno } from '../agenda/sobreturno.ts';
import { dibujarAgenda, fechaCorta, type AccionesTurno } from '../agenda/vista.ts';
import { dejarSeleccion } from '../ficha/seleccion.ts';
import { ErrorSesion } from '../shared/api/errores.ts';
import { hoy, sumarDias } from '../shared/fecha/zona.ts';
import { cambiarOpciones, crearAviso, crearBoton, crearCampo, crearSelector, type Opcion } from '../shared/ui/campos.ts';
import { mensajeDeDeshacerAusencia, mensajeDeError } from '../shared/ui/mensajes.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

export const MENSAJE_SESION_TERMINADA = 'Tu sesión terminó. Ingresá de nuevo.';

const TODOS: Opcion = { valor: '', texto: 'Todos' };

/** C-43: aviso de cada acción de asistencia; solo el nombre del paciente, nunca datos de salud. */
const MENSAJES_ASISTENCIA: Readonly<Record<AccionAsistencia, (paciente: string) => string>> = {
  llegada: (paciente) => `Se registró la llegada de ${paciente}.`,
  ausencia: (paciente) => `Se marcó la ausencia de ${paciente}.`,
  'deshacer-ausencia': (paciente) => `Se deshizo la ausencia de ${paciente}.`,
};

function textoDelPeriodo(periodo: Periodo): string {
  return periodo.vista === 'dia'
    ? fechaCorta(periodo.desde)
    : `Semana del ${fechaCorta(periodo.desde)} al ${fechaCorta(sumarDias(periodo.hasta, -1))}`;
}

const SVG = 'http://www.w3.org/2000/svg';

/** Botón con una flecha; la palabra queda como nombre accesible y texto de ayuda (C-35). */
function crearBotonFlecha(nombre: string, trazo: string): HTMLButtonElement {
  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'boton-icono';
  boton.setAttribute('aria-label', nombre);
  boton.title = nombre;
  const icono = document.createElementNS(SVG, 'svg');
  icono.setAttribute('viewBox', '0 0 24 24');
  icono.setAttribute('width', '20');
  icono.setAttribute('height', '20');
  icono.setAttribute('aria-hidden', 'true');
  const camino = document.createElementNS(SVG, 'path');
  camino.setAttribute('d', trazo);
  icono.append(camino);
  boton.append(icono);
  return boton;
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
  // RN-AG-18: el odontólogo ve solo su agenda; el backend aplica la misma regla.
  const profesionalFijo = contexto.sesion.profesionalId;
  // El odontólogo ve solo sus turnos: la tarjeta lleva al paciente; recepción y administración ven al profesional.
  const nombreEnTarjeta = profesionalFijo === undefined ? 'profesional' : 'paciente';

  const exito = crearAviso('status');
  const error = crearAviso('alert');
  // Aviso que dejó otra pantalla, por ejemplo "Tu contraseña se cambió." (C-41).
  exito.textContent = contexto.aviso ?? '';

  const botonDia = crearBoton('Día');
  const botonSemana = crearBoton('Semana');
  const vistas = document.createElement('div');
  vistas.className = 'grupo-botones';
  vistas.setAttribute('role', 'group');
  vistas.setAttribute('aria-label', 'Vista');
  vistas.append(botonDia, botonSemana);

  const anterior = crearBotonFlecha('Anterior', 'M15 6l-6 6 6 6');
  const botonHoy = crearBoton('Hoy');
  const siguiente = crearBotonFlecha('Siguiente', 'M9 6l6 6-6 6');
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
  // C-45: "Sobreturno" como acción secundaria, antes de "Nuevo turno" (design D10).
  const sobreturno = crearBoton('Sobreturno');
  sobreturno.className = 'boton-secundario';
  const botonesAgenda = document.createElement('div');
  botonesAgenda.className = 'agenda-botones';
  botonesAgenda.append(sobreturno, nuevo);
  const filtros = document.createElement('div');
  filtros.className = 'agenda-filtros';
  if (profesionalFijo === undefined) {
    filtros.append(profesional.contenedor, sillon.contenedor, botonesAgenda);
  } else {
    filtros.append(sillon.contenedor, botonesAgenda);
  }

  const avisosCatalogo = document.createElement('div');
  avisosCatalogo.className = 'agenda-avisos';

  const lista = document.createElement('div');
  lista.className = 'agenda-lista';

  const titulo = crearTitulo(profesionalFijo === undefined ? 'Agenda' : 'Mi agenda');

  contenedor.replaceChildren(titulo, exito, error, barra, filtros, avisosCatalogo, lista);

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
    turnosCargados: () => turnos,
    profesionalFijo,
    sesionVencida(causa) {
      if (causa instanceof ErrorSesion) {
        contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA);
        return true;
      }
      return false;
    },
  };

  let asistenciaEnCurso = false;

  /** C-43: envía la acción de asistencia; con éxito o rechazo vuelve a pedir la agenda. */
  async function registrar(turno: TurnoAgenda, accion: AccionAsistencia): Promise<void> {
    if (asistenciaEnCurso) {
      return;
    }
    asistenciaEnCurso = true;
    lista.querySelectorAll<HTMLButtonElement>('.turno-asistencia button').forEach((b) => (b.disabled = true));
    try {
      await registrarAsistencia(contexto.api, turno.id, accion);
      control.mostrarExito(MENSAJES_ASISTENCIA[accion](`${turno.pacienteNombre} ${turno.pacienteApellido}`.trim()));
    } catch (causa) {
      if (control.sesionVencida(causa)) {
        return;
      }
      control.mostrarError(accion === 'deshacer-ausencia' ? mensajeDeDeshacerAusencia(causa) : mensajeDeError(causa));
    } finally {
      asistenciaEnCurso = false;
    }
    await cargarTurnos();
  }

  /** C-44: deja el paciente y el turno en memoria y va a "Pacientes" (design D1). */
  function abrirFicha(turno: TurnoAgenda): void {
    if (turno.pacienteId === null) {
      return;
    }
    dejarSeleccion({
      paciente: { id: turno.pacienteId, nombre: turno.pacienteNombre, apellido: turno.pacienteApellido, dni: null },
      turno: {
        id: turno.id,
        profesionalId: turno.profesionalId,
        inicio: turno.inicio,
        fin: turno.fin,
        estado: turno.estado,
        llegadaEn: turno.llegadaEn,
      },
    });
    contexto.irA('pacientes');
  }

  const acciones: AccionesTurno = {
    cancelar: (turno: TurnoAgenda) => abrirCancelacion(control, turno),
    reprogramar: (turno: TurnoAgenda) => abrirReprogramacion(control, turno),
    extender: (turno: TurnoAgenda) => abrirExtension(control, turno),
    asistencia: { rol: contexto.sesion.rol, registrar: (turno, accion) => void registrar(turno, accion) },
    ...(contexto.sesion.rol === 'ADMIN' || contexto.sesion.rol === 'ODONTOLOGO'
      ? { verFicha: (turno: TurnoAgenda) => abrirFicha(turno) }
      : {}),
  };

  function filtrosActuales(): Filtros {
    return { profesionalId: profesionalFijo ?? idElegido(profesional.control), sillonId: idElegido(sillon.control) };
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
      dibujarAgenda(lista, periodo, turnos, catalogos, acciones, nombreEnTarjeta);
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
  sobreturno.addEventListener('click', () => abrirSobreturno(control));

  void cargarCatalogos(contexto.api).then((cargados) => {
    catalogos = cargados;
    cambiarOpciones(profesional.control, opcionesDe(cargados.profesionales));
    cambiarOpciones(sillon.control, opcionesDe(cargados.sillones));
    const propio = cargados.profesionales.find((item) => item.id === profesionalFijo);
    if (propio !== undefined) {
      const nombre = document.createElement('span');
      nombre.className = 'agenda-titulo-profesional';
      nombre.textContent = propio.nombre;
      titulo.append(nombre);
    }
    avisosCatalogo.replaceChildren(
      ...cargados.avisos.map((textoAviso) => {
        const parrafo = crearAviso('status');
        parrafo.textContent = textoAviso;
        return parrafo;
      }),
    );
    if (lista.getAttribute('aria-busy') !== 'true') {
      dibujarAgenda(lista, periodo, turnos, catalogos, acciones, nombreEnTarjeta);
    }
  });
  void cargarTurnos();
}

/** Agenda del personal (US-014). Privada: solo con sesión del personal. */
export const paginaAgenda: Pagina = {
  ruta: 'agenda',
  fondoSuave: true,
  titulo: 'Agenda',
  acceso: 'personal',
  menu: 'personal',
  icono: 'agenda',
  render: dibujar,
};
