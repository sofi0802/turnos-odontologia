import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorNegocio, ErrorSesion } from '../shared/api/errores.ts';
import { hoy, sumarDias, type Fecha } from '../shared/fecha/zona.ts';
import { crearBoton, crearCampo, crearEntrada } from '../shared/ui/campos.ts';
import { abrirDialogo } from '../shared/ui/dialogo.ts';
import { crearIcono } from '../shared/ui/iconos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { anotarme, DIAS_POR_DEFECTO, salirDeLaLista, validarRango, type Inscripcion } from './api.ts';
import { profesionalDe, rangoLargo, SIN_PREFERENCIA, textoOfertaPaciente } from './formato.ts';

export const MENSAJE_ANOTADO = 'Listo, te anotamos. Si se libera un horario, te avisamos por mensaje.';
export const MENSAJE_YA_ANOTADO = 'Ya estás en la lista de espera para esta prestación.';
export const MENSAJE_SIN_CONSENTIMIENTO_PACIENTE =
  'Para anotarte tenés que aceptar recibir mensajes del consultorio: así te avisamos cuando se libere un horario.';
export const ENLACE_ACEPTAR_MENSAJES = 'Aceptar mensajes en Mi cuenta';
export const MENSAJE_FALLA_LISTA = 'No pudimos cargar tu lista de espera.';
export const MENSAJE_INGRESA_PARA_ANOTARTE = 'Ingresá con tu cuenta para anotarte en la lista de espera.';
export const MENSAJE_SALISTE = 'Saliste de la lista de espera.';

/** Lo que la reserva ya eligió: no se edita en el diálogo. */
export interface EleccionLista {
  readonly prestacionId: number;
  readonly prestacionNombre: string;
  readonly profesionalId: number | null;
  readonly profesionalNombre: string | null;
  readonly fecha: Fecha;
}

export interface OpcionesSinHorarios {
  readonly texto: string;
  /** Resuelve si hay sesión de paciente; sin ella, la acción lleva a Mi cuenta. */
  hayPaciente(): Promise<boolean>;
  irAMiCuenta(aviso: string): void;
  abrir(origen: HTMLButtonElement): void;
}

/** "No hay horarios libres para ese día…" con la acción de anotarse en la lista de espera (C-15). */
export function crearSinHorarios(opciones: OpcionesSinHorarios): HTMLElement {
  const caja = document.createElement('div');
  caja.className = 'le-sin-horarios';
  const vacio = document.createElement('p');
  vacio.className = 'vacio';
  vacio.setAttribute('role', 'status');
  vacio.textContent = opciones.texto;
  const sugerencia = document.createElement('p');
  sugerencia.className = 'le-sugerencia';
  sugerencia.textContent = '¿Querés un turno antes? Anotate y te avisamos por mensaje si se libera un horario.';
  const accion = crearBoton('Anotarme en la lista de espera');
  accion.className = 'btn btn-principal';
  accion.addEventListener('click', () => {
    void opciones.hayPaciente().then((hay) => {
      if (hay) {
        opciones.abrir(accion);
      } else {
        opciones.irAMiCuenta(MENSAJE_INGRESA_PARA_ANOTARTE);
      }
    });
  });
  caja.append(vacio, sugerencia, accion);
  return caja;
}

function enlaceMisTurnos(): HTMLAnchorElement {
  const enlace = document.createElement('a');
  enlace.className = 'btn btn-principal';
  enlace.href = '#/mis-turnos';
  enlace.textContent = 'Ver Mis turnos';
  return enlace;
}

/** Diálogo "Anotarme en la lista de espera" desde la reserva. */
export function abrirAnotarme(
  api: ClienteApi,
  eleccion: EleccionLista,
  opciones: { readonly contenedor: HTMLElement; readonly origen: HTMLButtonElement; sesionTerminada(): void },
): void {
  const fijo = document.createElement('dl');
  fijo.className = 'le-fijo';
  for (const [clave, valor] of [
    ['Prestación', eleccion.prestacionNombre],
    ['Profesional', eleccion.profesionalNombre ?? SIN_PREFERENCIA],
  ] as const) {
    const dt = document.createElement('dt');
    dt.textContent = clave;
    const dd = document.createElement('dd');
    dd.textContent = valor;
    fijo.append(dt, dd);
  }
  const desdeEntrada = crearEntrada('date', 'desde');
  const hastaEntrada = crearEntrada('date', 'hasta');
  desdeEntrada.classList.add('control');
  hastaEntrada.classList.add('control');
  desdeEntrada.value = eleccion.fecha;
  hastaEntrada.value = sumarDias(eleccion.fecha, DIAS_POR_DEFECTO);
  const desde = crearCampo('Desde', desdeEntrada);
  const hasta = crearCampo('Hasta', hastaEntrada);
  const rango = document.createElement('div');
  rango.className = 'le-rango';
  rango.append(desde.contenedor, hasta.contenedor);
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.textContent =
    'Si se libera un horario en esas fechas, te lo ofrecemos por WhatsApp o email y tenés un plazo para aceptarlo.';

  const { formulario, general, principal, cerrar } = abrirDialogo({
    titulo: 'Anotarme en la lista de espera',
    contenido: [fijo, rango, ayuda],
    textoDescartar: 'Volver',
    textoPrincipal: 'Anotarme',
    contenedor: opciones.contenedor,
    alCerrar: () => {
      if (opciones.origen.isConnected) {
        opciones.origen.focus();
      }
    },
  });

  /** Reemplaza el formulario por un aviso final, con "Cerrar" y el enlace a Mis turnos. */
  function terminar(texto: string, exito: boolean): void {
    const aviso = document.createElement('p');
    aviso.className = exito ? 'aviso aviso-ok' : 'aviso aviso-error';
    aviso.setAttribute('role', exito ? 'status' : 'alert');
    aviso.textContent = texto;
    const cerrarBoton = crearBoton('Cerrar');
    cerrarBoton.className = 'btn';
    cerrarBoton.addEventListener('click', cerrar);
    const acciones = document.createElement('div');
    acciones.className = 'dialogo-acciones';
    acciones.append(cerrarBoton, enlaceMisTurnos());
    formulario.replaceChildren(aviso, acciones);
    cerrarBoton.focus();
  }

  let enviando = false;
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    if (enviando) {
      return;
    }
    general.textContent = '';
    const errores = validarRango(desdeEntrada.value, hastaEntrada.value, hoy());
    desde.mostrarError(errores.desde);
    hasta.mostrarError(errores.hasta);
    if (errores.desde !== '' || errores.hasta !== '') {
      return;
    }
    enviando = true;
    principal.disabled = true;
    anotarme(api, {
      prestacionId: eleccion.prestacionId,
      profesionalId: eleccion.profesionalId,
      desde: desdeEntrada.value,
      hasta: hastaEntrada.value,
    })
      .then(() => terminar(MENSAJE_ANOTADO, true))
      .catch((error: unknown) => {
        if (error instanceof ErrorSesion) {
          cerrar();
          opciones.sesionTerminada();
          return;
        }
        if (error instanceof ErrorNegocio && error.codigo === 'YA_EN_LISTA_DE_ESPERA') {
          terminar(MENSAJE_YA_ANOTADO, false);
          return;
        }
        enviando = false;
        principal.disabled = false;
        if (error instanceof ErrorNegocio && error.codigo === 'SIN_CONSENTIMIENTO') {
          // C-17: el aviso lleva a "Mi cuenta", donde el paciente acepta los mensajes.
          const enlace = document.createElement('a');
          enlace.href = '#/mi-cuenta';
          enlace.textContent = ENLACE_ACEPTAR_MENSAJES;
          general.replaceChildren(MENSAJE_SIN_CONSENTIMIENTO_PACIENTE, ' ', enlace);
        } else {
          general.textContent = mensajeDeError(error);
        }
      });
  });
}

/** Sección "Lista de espera" de Mis turnos; `null` si no hay inscripciones. */
export function crearSeccionListaEspera(
  api: ClienteApi,
  inscripciones: readonly Inscripcion[],
  opciones: {
    readonly contenedor: HTMLElement;
    alSalir(inscripcion: Inscripcion): void;
    alFallar(error: unknown): void;
  },
): HTMLElement | null {
  if (inscripciones.length === 0) {
    return null;
  }
  const seccion = document.createElement('section');
  seccion.setAttribute('aria-label', 'Lista de espera');
  const titulo = document.createElement('h2');
  titulo.className = 'seccion-titulo';
  titulo.textContent = 'Lista de espera';
  const lista = document.createElement('ul');
  lista.className = 'lista-turnos';
  for (const inscripcion of inscripciones) {
    lista.append(tarjeta(inscripcion));
  }
  seccion.append(titulo, lista);
  return seccion;

  function tarjeta(inscripcion: Inscripcion): HTMLLIElement {
    const item = document.createElement('li');
    item.className = 'turno-paciente le-tarjeta';
    item.dataset['inscripcion'] = String(inscripcion.id);
    const cabecera = document.createElement('div');
    cabecera.className = 'turno-cabecera';
    const nombre = document.createElement('p');
    nombre.className = 'turno-fecha';
    nombre.textContent = inscripcion.prestacionNombre;
    const salir = crearBoton('Salir de la lista');
    salir.className = 'btn btn-chico';
    cabecera.append(nombre, salir);
    const meta = document.createElement('p');
    meta.className = 'turno-meta';
    meta.textContent = `${profesionalDe(inscripcion)} · ${rangoLargo(inscripcion)}`;
    item.append(cabecera, meta);
    if (inscripcion.oferta !== null) {
      const oferta = document.createElement('p');
      oferta.className = 'le-oferta';
      oferta.append(crearIcono('espera'), textoOfertaPaciente(inscripcion.oferta));
      item.append(oferta);
    }
    salir.addEventListener('click', () => confirmarSalida(inscripcion, salir));
    return item;
  }

  function confirmarSalida(inscripcion: Inscripcion, origen: HTMLButtonElement): void {
    const { formulario, principal, cerrar } = abrirDialogo({
      titulo: '¿Salir de la lista de espera?',
      resumen: `${inscripcion.prestacionNombre} · ${profesionalDe(inscripcion)}. Si te enviamos un horario, se lo ofrecemos a otra persona.`,
      textoDescartar: 'Volver',
      textoPrincipal: 'Salir de la lista',
      peligro: true,
      contenedor: opciones.contenedor,
      alCerrar: () => {
        if (origen.isConnected) {
          origen.focus();
        }
      },
    });
    formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      principal.disabled = true;
      salirDeLaLista(api, inscripcion.id)
        .then(() => {
          cerrar();
          opciones.alSalir(inscripcion);
        })
        .catch((error: unknown) => {
          cerrar();
          opciones.alFallar(error);
        });
    });
  }
}
