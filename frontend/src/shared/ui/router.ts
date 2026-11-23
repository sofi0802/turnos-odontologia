import type { Pagina } from '../../pages/pagina.ts';

export interface Router {
  /** Dibuja la página de la dirección actual. */
  actualizar(): void;
  /** Deja de escuchar los cambios de dirección. */
  detener(): void;
}

function rutaDelHash(hash: string): string {
  return hash.replace(/^#\/?/, '');
}

/**
 * Router por hash (`#/agenda`). Con una dirección vacía o desconocida muestra
 * la página inicial y reemplaza la dirección, sin sumar una entrada al historial.
 * `redirecciones` lleva direcciones viejas a la ruta de una página, del mismo modo
 * (por ejemplo, `ingreso` → `mi-cuenta`).
 */
export function crearRouter(
  paginas: readonly Pagina[],
  inicial: Pagina,
  alCambiar: (pagina: Pagina) => void,
  redirecciones: Readonly<Record<string, string>> = {},
): Router {
  const porRuta = new Map(paginas.map((pagina) => [pagina.ruta, pagina]));
  const viejas = new Map(Object.entries(redirecciones));

  function actualizar(): void {
    const ruta = rutaDelHash(window.location.hash);
    const nueva = viejas.get(ruta);
    const destino = nueva === undefined ? undefined : porRuta.get(nueva);
    if (destino !== undefined) {
      history.replaceState(history.state, '', `#/${destino.ruta}`);
      alCambiar(destino);
      return;
    }
    const pagina = porRuta.get(ruta);
    if (pagina === undefined) {
      history.replaceState(history.state, '', `#/${inicial.ruta}`);
      alCambiar(inicial);
      return;
    }
    alCambiar(pagina);
  }

  window.addEventListener('hashchange', actualizar);
  return {
    actualizar,
    detener: () => window.removeEventListener('hashchange', actualizar),
  };
}
