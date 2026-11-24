import {
  cargarPrestaciones,
  cargarProfesionales,
  type PrestacionPublica,
  type ProfesionalPublico,
} from '../paciente/catalogo.ts';
import { consultarDisponibilidad, type Horario } from '../paciente/disponibilidad.ts';
import { crearGrillaHorarios } from '../paciente/horarios.ts';
import {
  borrarReservaPendiente,
  guardarReservaPendiente,
  leerReservaPendiente,
  reservar,
  type TurnoPaciente,
} from '../paciente/reserva.ts';
import { ErrorNegocio, ErrorSesion } from '../shared/api/errores.ts';
import { aIsoConZona, fechaDeInstante, fechaLarga, formatearHora, hoy, type Fecha } from '../shared/fecha/zona.ts';
import { esPaciente, esPersonal } from '../shared/sesion/sesion.ts';
import { crearAviso, crearBoton, crearCampo, crearEntrada } from '../shared/ui/campos.ts';
import { usarSelectorFecha } from '../shared/ui/selector-fecha.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { crearSelectorBuscable } from '../shared/ui/selector-buscable.ts';
import { crearTitulo, type ContextoPagina, type Pagina } from './pagina.ts';

export const MENSAJE_RESERVA_PERSONAL = 'La reserva online es para pacientes. Para dar un turno, usá la Agenda.';
export const MENSAJE_SIN_PRESTACIONES = 'No pudimos cargar las prestaciones. Probá de nuevo en unos minutos.';
export const AYUDA_PASO_1 = 'Buscá la prestación o elegila de la lista. Después elegís el día y el horario.';
export const MENSAJE_ELEGI_PRESTACION = 'Elegí una prestación.';
export const MENSAJE_DIA_SIN_HORARIOS = 'No hay horarios libres para ese día. Probá con otra fecha o con otro profesional.';
export const MENSAJE_HORARIO_OCUPADO = 'Ese horario se ocupó recién. Elegí otro.';
export const MENSAJE_SIN_ALTERNATIVAS = 'No quedan horarios libres ese día. Elegí otra fecha.';
export const MENSAJE_INGRESA_PARA_CONFIRMAR = 'Ingresá o creá tu cuenta para confirmar la reserva.';
export const MENSAJE_SESION_TERMINADA = 'Tu sesión terminó. Ingresá de nuevo.';
export const SIN_PREFERENCIA_RESUMEN = 'Sin preferencia (te asignamos uno)';

const TITULOS_PASO = ['¿Qué necesitás?', 'Elegí día y horario', 'Confirmá tu turno'] as const;

/** Lo elegido en la reserva; sobrevive a "Volver" y "Continuar". */
interface Eleccion {
  prestacionId: number | null;
  profesionalId: number | null;
  fecha: Fecha;
  horario: Horario | null;
}

function textoSoloLectores(texto: string): HTMLSpanElement {
  const span = document.createElement('span');
  span.className = 'solo-lectores';
  span.textContent = texto;
  return span;
}

/** Opción de radio con el estilo `.opcion` del design system. */
function crearOpcion(
  grupo: string,
  valor: string,
  nombre: string,
  detalle: string | null,
  elegida: boolean,
  alElegir: () => void,
): HTMLLabelElement {
  const etiqueta = document.createElement('label');
  etiqueta.className = 'opcion';
  const entrada = document.createElement('input');
  entrada.type = 'radio';
  entrada.name = grupo;
  entrada.value = valor;
  entrada.checked = elegida;
  entrada.addEventListener('change', () => {
    if (entrada.checked) {
      alElegir();
    }
  });
  const textos = document.createElement('span');
  const principal = document.createElement('span');
  principal.className = 'opcion-nombre';
  principal.textContent = nombre;
  textos.append(principal);
  if (detalle !== null) {
    const chico = document.createElement('small');
    chico.textContent = detalle;
    textos.append(textoSoloLectores('. '), chico);
  }
  etiqueta.append(entrada, textos);
  return etiqueta;
}

function crearFieldset(leyenda: string, opciones: readonly HTMLElement[]): HTMLFieldSetElement {
  const fieldset = document.createElement('fieldset');
  fieldset.className = 'opciones';
  const legend = document.createElement('legend');
  legend.className = 'seccion-titulo';
  legend.textContent = leyenda;
  fieldset.append(legend, ...opciones);
  return fieldset;
}

function crearDetalle(filas: readonly (readonly [string, string])[]): HTMLDListElement {
  const lista = document.createElement('dl');
  lista.className = 'detalle-lista';
  for (const [termino, valor] of filas) {
    const dt = document.createElement('dt');
    dt.textContent = termino;
    const dd = document.createElement('dd');
    dd.textContent = valor;
    lista.append(dt, dd);
  }
  return lista;
}

function fechaLargaDeFecha(fecha: Fecha): string {
  return fechaLarga(aIsoConZona(fecha, '12:00'));
}

function dibujar(contenedor: HTMLElement, contexto: ContextoPagina): void {
  const panel = document.createElement('section');
  panel.className = 'panel';
  panel.setAttribute('aria-busy', 'true');
  const pagina = document.createElement('div');
  pagina.className = 'pagina-paciente';
  pagina.append(crearTitulo('Reservar turno'), panel);
  contenedor.replaceChildren(pagina);

  const vigente = (): boolean => contenedor.contains(panel);
  const eleccion: Eleccion = { prestacionId: null, profesionalId: null, fecha: hoy(), horario: null };
  let prestaciones: PrestacionPublica[] = [];
  let profesionales: ProfesionalPublico[] = [];
  let profesionalesDe: number | null = null;
  let horarios: Horario[] | null = null;
  let consultaHorarios = 0;
  let avisoPaso2 = '';
  let enviando = false;

  const prestacionElegida = (): PrestacionPublica | undefined =>
    prestaciones.find((p) => p.id === eleccion.prestacionId);
  const nombreProfesional = (id: number | null): string =>
    id === null ? SIN_PREFERENCIA_RESUMEN : (profesionales.find((p) => p.id === id)?.nombre ?? `Profesional ${id}`);

  /** Encabezado común de cada paso: barra, "Paso N de 3" y título. */
  function encabezadoDePaso(numero: 1 | 2 | 3): HTMLElement[] {
    const barra = document.createElement('ol');
    barra.className = 'pasos';
    barra.setAttribute('aria-hidden', 'true');
    for (let i = 1; i <= 3; i += 1) {
      const li = document.createElement('li');
      if (i <= numero) {
        li.className = 'hecho';
      }
      barra.append(li);
    }
    const indicador = document.createElement('p');
    indicador.className = 'paso-numero';
    indicador.textContent = `Paso ${numero} de 3`;
    const titulo = document.createElement('h2');
    titulo.textContent = TITULOS_PASO[numero - 1] ?? '';
    return [barra, indicador, titulo];
  }

  function acciones(...botones: HTMLElement[]): HTMLDivElement {
    const contenedorAcciones = document.createElement('div');
    contenedorAcciones.className = 'acciones';
    contenedorAcciones.append(...botones);
    return contenedorAcciones;
  }

  function boton(texto: string, principal = false): HTMLButtonElement {
    const nuevo = crearBoton(texto);
    nuevo.className = principal ? 'btn btn-principal' : 'btn';
    return nuevo;
  }

  // ---------- Paso 1: prestación ----------
  function paso1(aviso = ''): void {
    const error = crearAviso('alert');
    error.textContent = aviso;
    if (prestaciones.length === 0) {
      error.textContent = MENSAJE_SIN_PRESTACIONES;
      panel.replaceChildren(...encabezadoDePaso(1), error);
      return;
    }
    // Selector con buscador (C-38): la elección queda en `eleccion.prestacionId`; cambiar el texto la quita.
    const selector = crearSelectorBuscable({
      etiqueta: 'Prestación',
      marcador: 'Buscá una prestación',
      opciones: prestaciones.map((p) => ({ id: String(p.id), nombre: p.nombre, detalle: `${p.duracionMinutos} minutos` })),
      elegida: eleccion.prestacionId === null ? null : String(eleccion.prestacionId),
      sinCoincidencias: (texto) => `No hay prestaciones que coincidan con «${texto}».`,
      alCambiar(id) {
        eleccion.prestacionId = id === null ? null : Number(id);
        error.textContent = '';
      },
    });
    const continuar = boton('Continuar', true);
    continuar.addEventListener('click', () => {
      if (eleccion.prestacionId === null) {
        error.textContent = MENSAJE_ELEGI_PRESTACION;
        return;
      }
      if (profesionalesDe !== eleccion.prestacionId) {
        // Otra prestación: lo elegido después ya no vale.
        eleccion.profesionalId = null;
        eleccion.horario = null;
        horarios = null;
      }
      paso2();
    });
    const ayuda = document.createElement('p');
    ayuda.className = 'subtitulo';
    ayuda.textContent = AYUDA_PASO_1;
    panel.replaceChildren(
      ...encabezadoDePaso(1),
      ayuda,
      selector.contenedor,
      error,
      acciones(continuar),
    );
  }

  // ---------- Paso 2: profesional, día y horario ----------
  const zonaHorarios = document.createElement('div');
  zonaHorarios.className = 'pila';
  const continuar2 = boton('Continuar', true);

  function dibujarHorarios(): void {
    continuar2.disabled = eleccion.horario === null;
    if (horarios === null) {
      zonaHorarios.setAttribute('aria-busy', 'true');
      zonaHorarios.replaceChildren();
      return;
    }
    zonaHorarios.removeAttribute('aria-busy');
    if (horarios.length === 0) {
      const vacio = document.createElement('p');
      vacio.className = 'vacio';
      vacio.setAttribute('role', 'status');
      vacio.textContent = MENSAJE_DIA_SIN_HORARIOS;
      zonaHorarios.replaceChildren(vacio);
      return;
    }
    const titulo = document.createElement('p');
    titulo.className = 'seccion-titulo';
    titulo.textContent = 'Horarios libres';
    zonaHorarios.replaceChildren(
      titulo,
      crearGrillaHorarios(horarios, eleccion.horario?.inicio ?? null, (horario) => {
        eleccion.horario = horario;
        continuar2.disabled = false;
      }),
    );
  }

  async function pedirHorarios(): Promise<void> {
    const prestacionId = eleccion.prestacionId;
    if (prestacionId === null) {
      return;
    }
    consultaHorarios += 1;
    const esta = consultaHorarios;
    horarios = null;
    eleccion.horario = null;
    dibujarHorarios();
    try {
      const lista = await consultarDisponibilidad(contexto.api, {
        prestacionId,
        fecha: eleccion.fecha,
        profesionalId: eleccion.profesionalId,
      });
      if (esta !== consultaHorarios || !vigente()) {
        return;
      }
      horarios = lista;
      dibujarHorarios();
    } catch (error) {
      if (esta !== consultaHorarios || !vigente()) {
        return;
      }
      horarios = null;
      const aviso = crearAviso('alert');
      aviso.textContent = mensajeDeError(error);
      zonaHorarios.removeAttribute('aria-busy');
      zonaHorarios.replaceChildren(aviso);
    }
  }

  function paso2(): void {
    const prestacion = prestacionElegida();
    if (prestacion === undefined) {
      paso1();
      return;
    }
    const subtitulo = document.createElement('p');
    subtitulo.className = 'subtitulo';
    subtitulo.textContent = `${prestacion.nombre} · ${prestacion.duracionMinutos} minutos`;
    const aviso = crearAviso('alert');
    aviso.textContent = avisoPaso2;
    avisoPaso2 = '';

    const zonaProfesionales = document.createElement('div');
    function dibujarProfesionales(): void {
      const opciones = [
        crearOpcion('profesional', '', 'Sin preferencia', 'Te asignamos quien esté libre', eleccion.profesionalId === null, () => {
          eleccion.profesionalId = null;
          void pedirHorarios();
        }),
        ...profesionales.map((p) =>
          crearOpcion('profesional', String(p.id), p.nombre, null, p.id === eleccion.profesionalId, () => {
            eleccion.profesionalId = p.id;
            void pedirHorarios();
          }),
        ),
      ];
      zonaProfesionales.replaceChildren(crearFieldset('Profesional', opciones));
    }
    dibujarProfesionales();
    if (profesionalesDe !== prestacion.id) {
      profesionalesDe = prestacion.id;
      profesionales = [];
      cargarProfesionales(contexto.api, prestacion.id)
        .then((lista) => {
          if (profesionalesDe === prestacion.id && vigente()) {
            profesionales = lista;
            dibujarProfesionales();
          }
        })
        .catch(() => {
          // Sin la lista, igual se reserva con "Sin preferencia".
        });
    }

    const entrada = crearEntrada('date', 'fecha');
    entrada.classList.add('control');
    entrada.min = hoy();
    entrada.value = eleccion.fecha;
    const dia = crearCampo('Día', entrada);
    usarSelectorFecha(dia);
    const ayuda = document.createElement('p');
    ayuda.className = 'ayuda';
    ayuda.textContent = fechaLargaDeFecha(eleccion.fecha);
    dia.contenedor.append(ayuda);
    entrada.addEventListener('change', () => {
      // No se aceptan días anteriores a hoy.
      const minimo = hoy();
      const valor = entrada.value === '' || entrada.value < minimo ? minimo : entrada.value;
      entrada.value = valor;
      if (valor === eleccion.fecha) {
        return;
      }
      eleccion.fecha = valor;
      ayuda.textContent = fechaLargaDeFecha(valor);
      void pedirHorarios();
    });

    const volver = boton('Volver');
    volver.addEventListener('click', () => paso1());
    continuar2.onclick = () => {
      if (eleccion.horario !== null) {
        paso3();
      }
    };

    panel.replaceChildren(
      ...encabezadoDePaso(2),
      subtitulo,
      aviso,
      zonaProfesionales,
      dia.contenedor,
      zonaHorarios,
      acciones(volver, continuar2),
    );
    if (horarios === null) {
      void pedirHorarios();
    } else {
      dibujarHorarios();
    }
  }

  /** Vuelve al paso 2 con un aviso, sin horario elegido, y pide los horarios de nuevo. */
  function volverAlPaso2(mensaje: string): void {
    avisoPaso2 = mensaje;
    eleccion.horario = null;
    horarios = null;
    paso2();
  }

  // ---------- Paso 3: resumen y confirmación ----------
  function paso3(alternativas: readonly Horario[] = []): void {
    const prestacion = prestacionElegida();
    const horario = eleccion.horario;
    if (prestacion === undefined || horario === null) {
      paso2();
      return;
    }
    const subtitulo = document.createElement('p');
    subtitulo.className = 'subtitulo';
    subtitulo.textContent = 'Revisá los datos. Tus datos personales salen de tu cuenta.';
    const resumen = crearDetalle([
      ['Prestación', prestacion.nombre],
      ['Profesional', nombreProfesional(eleccion.profesionalId)],
      ['Día', fechaLarga(horario.inicio)],
      ['Hora', formatearHora(horario.inicio)],
    ]);
    resumen.classList.add('resumen');
    const aviso = crearAviso('alert');
    const zonaAlternativas = document.createElement('div');
    if (alternativas.length > 0) {
      aviso.textContent = MENSAJE_HORARIO_OCUPADO;
      zonaAlternativas.append(
        crearGrillaHorarios(
          alternativas,
          null,
          (alternativa) => {
            // Elegir una alternativa solo actualiza el resumen: no confirma.
            eleccion.horario = alternativa;
            eleccion.fecha = fechaDeInstante(alternativa.inicio);
            horarios = null;
            paso3();
          },
          'Otros horarios',
        ),
      );
    }

    const volver = boton('Volver');
    volver.addEventListener('click', () => paso2());
    const confirmar = boton('Confirmar reserva', true);
    confirmar.addEventListener('click', () => {
      void confirmarReserva(prestacion, horario, confirmar, aviso);
    });
    panel.replaceChildren(
      ...encabezadoDePaso(3),
      subtitulo,
      resumen,
      aviso,
      zonaAlternativas,
      acciones(volver, confirmar),
    );
  }

  function pendiente(horario: Horario): void {
    if (eleccion.prestacionId !== null) {
      guardarReservaPendiente({
        prestacionId: eleccion.prestacionId,
        profesionalId: eleccion.profesionalId,
        fecha: eleccion.fecha,
        inicio: horario.inicio,
      });
    }
  }

  async function confirmarReserva(
    prestacion: PrestacionPublica,
    horario: Horario,
    confirmar: HTMLButtonElement,
    aviso: HTMLElement,
  ): Promise<void> {
    if (enviando) {
      return;
    }
    enviando = true;
    confirmar.disabled = true;
    const sesion = await contexto.sesionLista;
    if (!sesion.autenticado || !esPaciente(sesion.rol)) {
      // Sin sesión de paciente no se envía nada: se guarda lo elegido y se pide ingresar.
      enviando = false;
      pendiente(horario);
      contexto.irAMiCuenta(MENSAJE_INGRESA_PARA_CONFIRMAR);
      return;
    }
    try {
      const turno = await reservar(contexto.api, {
        prestacionId: prestacion.id,
        inicio: horario.inicio,
        profesionalId: eleccion.profesionalId,
      });
      borrarReservaPendiente();
      if (vigente()) {
        comprobante(turno, prestacion);
      }
    } catch (error) {
      if (!vigente()) {
        return;
      }
      if (error instanceof ErrorSesion) {
        pendiente(horario);
        contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA);
      } else if (error instanceof ErrorNegocio && error.codigo === 'SIN_DISPONIBILIDAD') {
        if (error.alternativas.length > 0) {
          paso3(error.alternativas);
        } else {
          volverAlPaso2(MENSAJE_SIN_ALTERNATIVAS);
        }
      } else if (error instanceof ErrorNegocio) {
        volverAlPaso2(mensajeDeError(error));
      } else {
        aviso.textContent = mensajeDeError(error);
        confirmar.disabled = false;
      }
    } finally {
      enviando = false;
    }
  }

  // ---------- Comprobante ----------
  function comprobante(turno: TurnoPaciente, prestacion: PrestacionPublica): void {
    const caja = document.createElement('div');
    caja.className = 'comprobante';
    caja.setAttribute('role', 'status');
    const titulo = document.createElement('h2');
    titulo.textContent = '¡Turno reservado!';
    const ayuda = document.createElement('p');
    ayuda.className = 'ayuda';
    ayuda.textContent = 'Guardá tu número de reserva: lo necesitás para cancelar o reprogramar.';
    const numero = document.createElement('p');
    numero.className = 'numero-reserva';
    numero.textContent = turno.numeroReserva;
    caja.append(
      titulo,
      ayuda,
      numero,
      crearDetalle([
        ['Día', fechaLarga(turno.inicio)],
        ['Hora', formatearHora(turno.inicio)],
        ['Prestación', turno.prestacion.nombre || prestacion.nombre],
        ['Profesional', turno.profesional.nombre],
      ]),
    );
    const otro = boton('Reservar otro turno');
    otro.addEventListener('click', () => {
      eleccion.prestacionId = null;
      eleccion.profesionalId = null;
      eleccion.horario = null;
      eleccion.fecha = hoy();
      horarios = null;
      paso1();
    });
    const misTurnos = document.createElement('a');
    misTurnos.className = 'btn btn-principal';
    misTurnos.href = '#/mis-turnos';
    misTurnos.textContent = 'Ver mis turnos';
    panel.replaceChildren(caja, acciones(otro, misTurnos));
  }

  // ---------- Arranque ----------
  async function iniciar(): Promise<void> {
    const sesion = await contexto.sesionLista;
    if (!vigente()) {
      return;
    }
    if (sesion.autenticado && esPersonal(sesion.rol)) {
      panel.removeAttribute('aria-busy');
      const aviso = crearAviso('alert');
      aviso.textContent = MENSAJE_RESERVA_PERSONAL;
      panel.replaceChildren(aviso);
      return;
    }
    try {
      prestaciones = await cargarPrestaciones(contexto.api);
    } catch {
      prestaciones = [];
    }
    if (!vigente()) {
      return;
    }
    panel.removeAttribute('aria-busy');

    const guardada = leerReservaPendiente();
    if (guardada !== null && prestaciones.some((p) => p.id === guardada.prestacionId)) {
      await restaurar(guardada.prestacionId, guardada.profesionalId, guardada.fecha, guardada.inicio);
      return;
    }
    paso1();
  }

  /** Vuelve al resumen de una reserva pendiente, si su horario sigue libre (design D4). */
  async function restaurar(prestacionId: number, profesionalId: number | null, fecha: Fecha, inicio: string): Promise<void> {
    eleccion.prestacionId = prestacionId;
    eleccion.profesionalId = profesionalId;
    eleccion.fecha = fecha < hoy() ? hoy() : fecha;
    profesionalesDe = prestacionId;
    const [listaProfesionales, listaHorarios] = await Promise.allSettled([
      cargarProfesionales(contexto.api, prestacionId),
      consultarDisponibilidad(contexto.api, { prestacionId, fecha: eleccion.fecha, profesionalId }),
    ]);
    if (!vigente()) {
      return;
    }
    profesionales = listaProfesionales.status === 'fulfilled' ? listaProfesionales.value : [];
    horarios = listaHorarios.status === 'fulfilled' ? listaHorarios.value : null;
    const libre = horarios?.find((h) => h.inicio === inicio) ?? null;
    if (libre !== null) {
      eleccion.horario = libre;
      paso3();
      return;
    }
    borrarReservaPendiente();
    avisoPaso2 = MENSAJE_HORARIO_OCUPADO;
    eleccion.horario = null;
    paso2();
  }

  void iniciar();
}

/** Reserva online del paciente (`#/reserva`), pública: un paso por pantalla. */
export const paginaReserva: Pagina = {
  ruta: 'reserva',
  fondoSuave: true,
  titulo: 'Reservar turno',
  menu: 'paciente',
  icono: 'reservar',
  render: dibujar,
};
