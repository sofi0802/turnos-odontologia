import type { ClienteApi } from '../shared/api/cliente.ts';
import type { Sesion } from '../shared/sesion/sesion.ts';

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
  /** Lleva a la página de ingreso del personal con un aviso opcional. */
  irAlIngreso(aviso?: string): void;
  /** Lleva a la cuenta del paciente (`#/mi-cuenta`) con un aviso opcional. */
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
  /** Sin `menu`, la página no figura en el menú (por ejemplo, los ingresos). */
  readonly menu?: GrupoMenu;
  render(contenedor: HTMLElement, contexto: ContextoPagina): void;
}

export function crearTitulo(texto: string): HTMLHeadingElement {
  const encabezado = document.createElement('h1');
  encabezado.textContent = texto;
  encabezado.tabIndex = -1;
  return encabezado;
}
