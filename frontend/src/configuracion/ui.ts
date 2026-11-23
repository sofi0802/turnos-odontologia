import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorSesion, ErrorValidacion } from '../shared/api/errores.ts';
import type { Sesion } from '../shared/sesion/sesion.ts';
import { crearAviso, crearBoton, type Campo } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import type { Permisos } from './permisos.ts';

/** Lo que cada sección de Configuración recibe de la página. */
export interface ContextoSeccion {
  readonly api: ClienteApi;
  readonly sesion: Sesion;
  readonly permisos: Permisos;
  /** Si el error es de sesión vencida (401), lleva al ingreso y devuelve `true` (regla de C-09). */
  sesionVencida(causa: unknown): boolean;
}

/** Monta una sección dentro de su panel; se llama la primera vez que se muestra la pestaña (design D2). */
export type MontarSeccion = (panel: HTMLElement, contexto: ContextoSeccion) => void;

export function esErrorDeSesion(causa: unknown): boolean {
  return causa instanceof ErrorSesion;
}

/** Encabezado de sección: título `h2` y, opcionalmente, un botón principal a la derecha. */
export function crearCabecera(titulo: string, accion?: HTMLButtonElement): HTMLDivElement {
  const cabecera = document.createElement('div');
  cabecera.className = 'cabecera-seccion';
  const h2 = document.createElement('h2');
  h2.textContent = titulo;
  cabecera.append(h2);
  if (accion !== undefined) {
    accion.classList.add('btn', 'btn-principal');
    cabecera.append(accion);
  }
  return cabecera;
}

/** Botón chico de una fila de lista. `peligro` lo pinta como acción destructiva. */
export function botonChico(texto: string, variante: 'normal' | 'principal' | 'peligro' = 'normal'): HTMLButtonElement {
  const boton = crearBoton(texto);
  boton.className = 'btn btn-chico';
  if (variante === 'principal') {
    boton.classList.add('btn-principal');
  } else if (variante === 'peligro') {
    boton.classList.add('btn-peligro');
  }
  return boton;
}

/** "Activo" o "Inactivo": se distingue por el texto y la forma del punto, no solo por el color. */
export function crearEstado(activo: boolean): HTMLSpanElement {
  const estado = document.createElement('span');
  estado.className = activo ? 'estado-activo' : 'estado-inactivo';
  estado.textContent = activo ? 'Activo' : 'Inactivo';
  return estado;
}

/**
 * Fila de `.lista-config`: nombre, datos separados por " · ", estado opcional y acciones.
 * Devuelve el `<li>` para que la sección le agregue más contenido (por ejemplo, un detalle).
 */
export function crearFila(
  nombre: string,
  datos: readonly string[],
  opciones: { activo?: boolean; acciones?: readonly HTMLButtonElement[] } = {},
): HTMLLIElement {
  const fila = document.createElement('li');
  if (opciones.activo === false) {
    fila.className = 'item-inactivo';
  }
  const cuerpo = document.createElement('div');
  const titulo = document.createElement('div');
  titulo.className = 'item-nombre';
  titulo.textContent = nombre;
  cuerpo.append(titulo);
  if (datos.length > 0) {
    const linea = document.createElement('div');
    linea.className = 'item-datos';
    linea.append(
      ...datos.map((dato) => {
        const span = document.createElement('span');
        span.textContent = dato;
        return span;
      }),
    );
    cuerpo.append(linea);
  }
  if (opciones.activo !== undefined) {
    cuerpo.append(crearEstado(opciones.activo));
  }
  fila.append(cuerpo);
  const acciones = opciones.acciones ?? [];
  if (acciones.length > 0) {
    const caja = document.createElement('div');
    caja.className = 'item-acciones';
    caja.append(...acciones);
    fila.append(caja);
  }
  return fila;
}

export function crearLista(): HTMLUListElement {
  const lista = document.createElement('ul');
  lista.className = 'lista-config';
  return lista;
}

/**
 * Carga datos para una sección con su manejo de error (requisito "Errores al cargar o guardar"):
 * mientras carga muestra "Cargando…"; si falla, el mensaje del error y "Reintentar".
 * `dibujar` recibe los datos y el contenedor ya vacío.
 */
export function cargarEn<T>(
  contenedor: HTMLElement,
  contexto: ContextoSeccion,
  cargar: () => Promise<T>,
  dibujar: (datos: T, contenedor: HTMLElement) => void,
): () => void {
  let intento = 0;
  const ejecutar = (): void => {
    intento += 1;
    const este = intento;
    const cargando = document.createElement('p');
    cargando.className = 'ayuda';
    cargando.textContent = 'Cargando…';
    contenedor.replaceChildren(cargando);
    cargar().then(
      (datos) => {
        if (este === intento) {
          contenedor.replaceChildren();
          dibujar(datos, contenedor);
        }
      },
      (causa: unknown) => {
        if (este !== intento || contexto.sesionVencida(causa)) {
          return;
        }
        const aviso = crearAviso('alert');
        aviso.textContent = mensajeDeError(causa);
        const reintentar = crearBoton('Reintentar');
        reintentar.className = 'btn';
        reintentar.addEventListener('click', ejecutar);
        contenedor.replaceChildren(aviso, reintentar);
      },
    );
  };
  ejecutar();
  return ejecutar;
}

/**
 * Ejecuta un guardado con el botón deshabilitado mientras dura, para no enviar el pedido dos veces.
 * Devuelve `true` si terminó bien.
 */
export async function guardando(boton: HTMLButtonElement, accion: () => Promise<unknown>): Promise<boolean> {
  if (boton.disabled) {
    return false;
  }
  boton.disabled = true;
  try {
    await accion();
    return true;
  } finally {
    boton.disabled = false;
  }
}

type CampoFormulario = Campo<HTMLInputElement> | Campo<HTMLSelectElement>;

/**
 * Muestra un error de la API en un formulario: los errores por campo (422 con `errores[]`) junto a
 * su campo, buscándolo por el `name` del control, y el mensaje general en `general`.
 * Devuelve `true` si el error era de sesión vencida y ya se llevó al ingreso.
 */
export function mostrarErrorEnFormulario(
  causa: unknown,
  general: HTMLElement,
  campos: readonly CampoFormulario[],
  contexto: ContextoSeccion,
): boolean {
  if (contexto.sesionVencida(causa)) {
    return true;
  }
  for (const c of campos) {
    c.mostrarError('');
  }
  if (causa instanceof ErrorValidacion) {
    for (const error of causa.errores) {
      campos.find((c) => c.control.name === error.campo)?.mostrarError(error.mensaje);
    }
  }
  general.textContent = mensajeDeError(causa);
  return false;
}

export interface Dialogo {
  readonly dialogo: HTMLDialogElement;
  readonly formulario: HTMLFormElement;
  /** Aviso `role="alert"` para los errores de negocio dentro del diálogo (design D9). */
  readonly general: HTMLParagraphElement;
  readonly enviar: HTMLButtonElement;
  cerrar(): void;
}

let contadorDialogos = 0;

/**
 * Diálogo modal con título, resumen opcional, contenido y botones (design D9), con las clases de
 * `.dialogo` que ya usa la agenda. El botón de enviar es `submit`; la sección escucha `submit`
 * del formulario.
 */
export function abrirDialogo(
  titulo: string,
  contenido: readonly HTMLElement[],
  textoEnviar: string,
  opciones: { resumen?: string; peligro?: boolean } = {},
): Dialogo {
  contadorDialogos += 1;
  const dialogo = document.createElement('dialog');
  dialogo.className = 'dialogo';
  const idTitulo = `config-dialogo-${contadorDialogos}`;
  dialogo.setAttribute('aria-labelledby', idTitulo);

  const encabezado = document.createElement('h2');
  encabezado.id = idTitulo;
  encabezado.textContent = titulo;
  dialogo.append(encabezado);
  if (opciones.resumen !== undefined) {
    const resumen = document.createElement('p');
    resumen.className = 'dialogo-resumen';
    resumen.textContent = opciones.resumen;
    dialogo.append(resumen);
  }

  const formulario = document.createElement('form');
  formulario.className = 'formulario';
  formulario.noValidate = true;
  const general = crearAviso('alert');
  const enviar = crearBoton(textoEnviar, 'submit');
  enviar.className = opciones.peligro === true ? 'btn btn-peligro' : 'btn btn-principal';
  const cancelar = crearBoton('Cancelar');
  cancelar.className = 'btn';
  const botones = document.createElement('div');
  botones.className = 'dialogo-acciones';
  botones.append(cancelar, enviar);
  formulario.append(general, ...contenido, botones);
  dialogo.append(formulario);

  const cerrar = (): void => dialogo.close();
  cancelar.addEventListener('click', cerrar);
  dialogo.addEventListener('close', () => dialogo.remove());
  document.body.append(dialogo);
  dialogo.showModal();
  const primero = formulario.querySelector<HTMLElement>('input, select');
  (primero ?? enviar).focus();
  return { dialogo, formulario, general, enviar, cerrar };
}

/**
 * Pide confirmación antes de una acción destructiva (borrar una ausencia o un bloqueo) y la ejecuta.
 * Si la acción falla, el diálogo sigue abierto con el mensaje. Al terminar bien, cierra y llama a `listo`.
 */
export function confirmar(
  titulo: string,
  resumen: string,
  textoConfirmar: string,
  contexto: ContextoSeccion,
  accion: () => Promise<unknown>,
  listo: () => void,
): void {
  const dialogo = abrirDialogo(titulo, [], textoConfirmar, { resumen, peligro: true });
  dialogo.formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    void guardando(dialogo.enviar, accion).then(
      (ok) => {
        if (ok) {
          dialogo.cerrar();
          listo();
        }
      },
      (causa: unknown) => {
        if (!contexto.sesionVencida(causa)) {
          dialogo.general.textContent = mensajeDeError(causa);
        }
      },
    );
  });
}
