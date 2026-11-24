import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorSesion, ErrorValidacion } from '../shared/api/errores.ts';
import type { Sesion } from '../shared/sesion/sesion.ts';
import { crearAviso, crearBoton, crearSelector, type Campo } from '../shared/ui/campos.ts';
import { abrirDialogo as abrirDialogoUniforme } from '../shared/ui/dialogo.ts';
import { crearCargando } from '../shared/ui/estado-vacio.ts';
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
  opciones: { activo?: boolean; acciones?: readonly HTMLButtonElement[]; profesional?: string } = {},
): HTMLLIElement {
  const fila = document.createElement('li');
  if (opciones.activo === false) {
    fila.className = 'item-inactivo';
  }
  if (opciones.profesional !== undefined) {
    const quien = document.createElement('div');
    quien.className = 'item-profesional';
    quien.textContent = opciones.profesional;
    fila.append(quien);
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

export function crearLista(conColumnaProfesional = false): HTMLUListElement {
  const lista = document.createElement('ul');
  lista.className = conColumnaProfesional ? 'lista-config con-columna' : 'lista-config';
  if (conColumnaProfesional) {
    // Encabezado de columnas: solo se ve en pantallas grandes; en el celular cada fila dice "Profesional: …".
    const encabezado = document.createElement('li');
    encabezado.className = 'encabezado-columnas';
    for (const titulo of ['Profesional', 'Período', '']) {
      const celda = document.createElement('div');
      celda.textContent = titulo;
      encabezado.append(celda);
    }
    lista.append(encabezado);
  }
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
    contenedor.setAttribute('aria-busy', 'true');
    contenedor.replaceChildren(crearCargando());
    cargar().then(
      (datos) => {
        if (este === intento) {
          contenedor.removeAttribute('aria-busy');
          contenedor.replaceChildren();
          dibujar(datos, contenedor);
        }
      },
      (causa: unknown) => {
        if (este !== intento || contexto.sesionVencida(causa)) {
          return;
        }
        contenedor.removeAttribute('aria-busy');
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

/**
 * Hora elegida con dos selectores (design D13): `control` es el de horas y `minutos` el de minutos.
 * Sirve como `Campo` para `mostrarErrorEnFormulario`, y su `name` es el del campo de la API.
 */
export interface SelectorHora extends Campo<HTMLSelectElement> {
  readonly hora: HTMLSelectElement;
  readonly minutos: HTMLSelectElement;
  /** `"HH:MM"`, o `""` si es opcional y no se eligió la hora. */
  valor(): string;
}

export interface OpcionesSelectorHora {
  /** Hora inicial `"HH:MM"`. Si sus minutos no son múltiplo de 5, se suman a las opciones tal cual. */
  readonly valor?: string;
  /** Suma "--" a las horas, y mientras esté elegida no hay valor. */
  readonly opcional?: boolean;
  /** Texto visible de la etiqueta; por defecto, el mismo nombre accesible. */
  readonly textoVisible?: string;
  readonly ariaHora?: string;
  readonly ariaMinutos?: string;
}

const HORAS_DEL_DIA = Array.from({ length: 24 }, (_, h) => String(h).padStart(2, '0'));
const MINUTOS_DE_CINCO = Array.from({ length: 12 }, (_, i) => String(i * 5).padStart(2, '0'));
const HORA_HHMM = /^(\d{2}):(\d{2})/;
let contadorHoras = 0;

/**
 * Dos selectores, hora (00 a 23) y minutos (00 a 55 de 5 en 5), dentro de un `role="group"` llamado
 * `etiqueta` (design D13). Devuelve `"HH:MM"`. `nombre` es el `name` del selector de horas y
 * `${nombre}Minutos` el del de minutos.
 */
export function crearSelectorHora(nombre: string, etiqueta: string, opciones: OpcionesSelectorHora = {}): SelectorHora {
  contadorHoras += 1;
  const id = `hora-${contadorHoras}`;
  const partes = opciones.valor === undefined ? null : HORA_HHMM.exec(opciones.valor);
  const horaInicial = partes?.[1] ?? '';
  const minutosInicial = partes?.[2] ?? '00';

  const horas = opciones.opcional === true ? ['--', ...HORAS_DEL_DIA] : HORAS_DEL_DIA;
  const hora = crearSelector(
    nombre,
    horas.map((h) => ({ valor: h === '--' ? '' : h, texto: h })),
  );
  const listaMinutos = MINUTOS_DE_CINCO.includes(minutosInicial)
    ? MINUTOS_DE_CINCO
    : [...MINUTOS_DE_CINCO, minutosInicial].sort();
  const minutos = crearSelector(
    `${nombre}Minutos`,
    listaMinutos.map((m) => ({ valor: m, texto: m })),
  );
  hora.classList.add('control');
  minutos.classList.add('control');
  hora.setAttribute('aria-label', opciones.ariaHora ?? `Hora (${etiqueta})`);
  minutos.setAttribute('aria-label', opciones.ariaMinutos ?? `Minutos (${etiqueta})`);
  if (horaInicial !== '') {
    hora.value = horaInicial;
  } else if (opciones.opcional !== true) {
    hora.value = '00';
  }
  minutos.value = minutosInicial;

  const error = document.createElement('p');
  error.id = `${id}-error`;
  error.className = 'campo-error';
  hora.setAttribute('aria-describedby', error.id);

  const separador = document.createElement('span');
  separador.className = 'separador';
  separador.setAttribute('aria-hidden', 'true');
  separador.textContent = ':';
  const fila = document.createElement('div');
  fila.className = 'hora-5';
  fila.append(hora, separador, minutos);

  const rotulo = document.createElement('span');
  rotulo.className = 'etiqueta-hora';
  rotulo.setAttribute('aria-hidden', 'true');
  rotulo.textContent = opciones.textoVisible ?? etiqueta;

  const contenedor = document.createElement('div');
  contenedor.className = 'campo';
  contenedor.setAttribute('role', 'group');
  contenedor.setAttribute('aria-label', etiqueta);
  contenedor.append(rotulo, fila, error);

  return {
    contenedor,
    control: hora,
    hora,
    minutos,
    valor() {
      return hora.value === '' ? '' : `${hora.value}:${minutos.value}`;
    },
    mostrarError(mensaje) {
      error.textContent = mensaje;
      if (mensaje === '') {
        hora.removeAttribute('aria-invalid');
      } else {
        hora.setAttribute('aria-invalid', 'true');
      }
    },
  };
}

export interface Dialogo {
  readonly dialogo: HTMLDialogElement;
  readonly formulario: HTMLFormElement;
  /** Aviso `role="alert"` para los errores de negocio dentro del diálogo (design D9). */
  readonly general: HTMLParagraphElement;
  readonly enviar: HTMLButtonElement;
  cerrar(): void;
}

/**
 * Diálogo modal de Configuración con título, resumen opcional, contenido y botones (design D9), armado con el molde
 * de `shared/ui/dialogo.ts`. El botón de enviar es `submit`; la sección escucha `submit` del formulario.
 */
export function abrirDialogo(
  titulo: string,
  contenido: readonly HTMLElement[],
  textoEnviar: string,
  opciones: { resumen?: string; peligro?: boolean } = {},
): Dialogo {
  const { dialogo, formulario, general, enviar, cerrar } = abrirDialogoUniforme({
    titulo,
    ...(opciones.resumen === undefined ? {} : { resumen: opciones.resumen }),
    contenido,
    textoDescartar: 'Cancelar',
    textoPrincipal: textoEnviar,
    ...(opciones.peligro === true ? { peligro: true } : {}),
  });
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
