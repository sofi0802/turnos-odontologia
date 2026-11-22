import type { ClienteApi } from '../shared/api/cliente.ts';
import type { Sesion } from '../shared/sesion/sesion.ts';

/** Lo que la aplicación le da a una página al dibujarla. */
export interface ContextoPagina {
  readonly api: ClienteApi;
  readonly sesion: Sesion;
  /** Aviso que dejó otra pantalla para esta (por ejemplo, "sesión terminada"). */
  readonly aviso: string | null;
  /** Muestra otra página por su ruta. */
  irA(ruta: string): void;
  /** Lleva a la página de ingreso con un aviso opcional. */
  irAlIngreso(aviso?: string): void;
}

/** Una página de la aplicación: su dirección, el texto del menú y cómo se dibuja. */
export interface Pagina {
  readonly ruta: string;
  readonly titulo: string;
  /** `false` la deja fuera del menú (por ejemplo, el ingreso). */
  readonly enMenu?: boolean;
  /** Solo se muestra con una sesión del personal (design D3). */
  readonly privada?: boolean;
  render(contenedor: HTMLElement, contexto: ContextoPagina): void;
}

export function crearTitulo(texto: string): HTMLHeadingElement {
  const encabezado = document.createElement('h1');
  encabezado.textContent = texto;
  encabezado.tabIndex = -1;
  return encabezado;
}

/** Página que por ahora muestra solo su título (C-12 suma Reserva; Configuración, otro change). */
export function paginaConTitulo(ruta: string, titulo: string): Pagina {
  return {
    ruta,
    titulo,
    render(contenedor) {
      contenedor.replaceChildren(crearTitulo(titulo));
    },
  };
}
