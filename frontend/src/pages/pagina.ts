import type { ClienteApi } from '../shared/api/cliente.ts';
import type { Sesion } from '../shared/sesion/sesion.ts';
import type { NombreIcono } from '../shared/ui/iconos.ts';

/** Lo que la aplicación le da a una página al dibujarla. */
export interface ContextoPagina {
  readonly api: ClienteApi;
  /** Sesión ya verificada; en las páginas públicas es "sin sesión" (usar `sesionLista`). */
  readonly sesion: Sesion;
  /** Sesión del servidor; en las páginas públicas llega después de dibujarlas. */
  readonly sesionLista: Promise<Sesion>;
  /** Aviso que dejó otra pantalla para esta (por ejemplo, "sesión terminada"). */
  readonly aviso: string | null;
  /** Muestra otra página por su ruta. */
  irA(ruta: string): void;
  /** Lleva a Mi cuenta (`#/mi-cuenta`), el único ingreso de pacientes y personal, con un aviso opcional. */
  irAMiCuenta(aviso?: string): void;
}

/** Quién puede ver una página (design D2 de C-12). */
export type Acceso = 'publica' | 'personal' | 'paciente';

/** Grupo del menú en el que figura una página. */
export type GrupoMenu = 'personal' | 'paciente';

/** Una página de la aplicación: su dirección, el texto del menú y cómo se dibuja. */
export interface Pagina {
  readonly ruta: string;
  readonly titulo: string;
  /** Por defecto `'publica'`. */
  readonly acceso?: Acceso;
  /** Sin `menu`, la página no figura en el menú (por ejemplo, Mi cuenta). */
  readonly menu?: GrupoMenu;
  /** La página se ve sobre el fondo suave de las pantallas del paciente, con tarjetas blancas (C-39, etapa C). */
  readonly fondoSuave?: boolean;
  /** Ícono de la opción en la barra lateral (C-39). */
  readonly icono?: NombreIcono;
  render(contenedor: HTMLElement, contexto: ContextoPagina): void;
}

export function crearTitulo(texto: string): HTMLHeadingElement {
  const encabezado = document.createElement('h1');
  encabezado.textContent = texto;
  encabezado.tabIndex = -1;
  return encabezado;
}
