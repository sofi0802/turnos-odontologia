/**
 * Estados vacíos y de carga (C-39, D7): una ilustración propia, un texto breve y, si corresponde, la acción
 * siguiente; y un indicador de carga que se anuncia a los lectores de pantalla. Las ilustraciones son
 * decorativas (`aria-hidden`); el contenedor que carga lleva `aria-busy` y lo pone quien lo usa.
 */

const NS = 'http://www.w3.org/2000/svg';

type Atributos = Readonly<Record<string, string>>;

function nodo(nombre: string, atributos: Atributos = {}, hijos: readonly SVGElement[] = []): SVGElement {
  const elemento = document.createElementNS(NS, nombre);
  for (const [clave, valor] of Object.entries(atributos)) {
    elemento.setAttribute(clave, valor);
  }
  elemento.append(...hijos);
  return elemento;
}

function destello(x: number, y: number, tamano: number, opacidad: string): SVGElement {
  const m = tamano / 2;
  const c = tamano / 5;
  return nodo('path', {
    d: `M${x} ${y - m}l${c} ${m - c}l${m - c} ${c}l${-(m - c)} ${c}l${-c} ${m - c}l${-c} ${-(m - c)}l${-(m - c)} ${-c}l${m - c} ${-c}z`,
    fill: 'var(--ds-acento-500)',
    opacity: opacidad,
  });
}

/** Un calendario sin turnos sobre un círculo suave, con los colores del design system. */
function crearIlustracionVacia(): SVGElement {
  const puntos = [
    [18, 40],
    [34, 40],
    [50, 40],
    [18, 54],
    [34, 54],
  ] as const;
  const calendario = nodo('g', { transform: 'translate(46 26)' }, [
    nodo('rect', { width: '68', height: '66', rx: '10', fill: 'var(--ds-fondo)', stroke: 'var(--ds-acento-500)', 'stroke-width': '3' }),
    nodo('path', { d: 'M0 10a10 10 0 0 1 10-10h48a10 10 0 0 1 10 10v14H0z', fill: 'var(--ds-acento-500)' }),
    nodo('rect', { x: '15', y: '-6', width: '6', height: '14', rx: '3', fill: 'var(--ds-acento-700)' }),
    nodo('rect', { x: '47', y: '-6', width: '6', height: '14', rx: '3', fill: 'var(--ds-acento-700)' }),
    nodo(
      'g',
      { fill: 'var(--ds-superficie-2)' },
      puntos.map(([cx, cy]) => nodo('circle', { cx: String(cx), cy: String(cy), r: '5' })),
    ),
  ]);
  const svg = nodo(
    'svg',
    { viewBox: '0 0 160 120', 'aria-hidden': 'true', focusable: 'false' },
    [
      nodo('ellipse', { cx: '80', cy: '64', rx: '64', ry: '50', fill: 'var(--ds-acento-100)', opacity: '.55' }),
      calendario,
      destello(130, 22, 14, '.6'),
      destello(26, 92, 12, '.5'),
    ],
  );
  return svg;
}

export interface OpcionesEstadoVacio {
  /** Título breve opcional, encima del texto. */
  readonly titulo?: string;
  readonly texto: string;
  /** Acción siguiente, como un enlace con aspecto de botón. */
  readonly accion?: { readonly texto: string; readonly ruta: string };
  /** Versión compacta, para listas dentro de otras pantallas. */
  readonly chico?: boolean;
}

export function crearEstadoVacio(opciones: OpcionesEstadoVacio): HTMLElement {
  const vacio = document.createElement('div');
  vacio.className = opciones.chico === true ? 'estado-vacio chico' : 'estado-vacio';
  vacio.append(crearIlustracionVacia());
  if (opciones.titulo !== undefined) {
    const titulo = document.createElement('p');
    titulo.className = 'estado-vacio-titulo';
    titulo.textContent = opciones.titulo;
    vacio.append(titulo);
  }
  const texto = document.createElement('p');
  texto.className = 'estado-vacio-texto';
  texto.textContent = opciones.texto;
  vacio.append(texto);
  if (opciones.accion !== undefined) {
    const accion = document.createElement('a');
    accion.className = 'btn btn-principal';
    accion.href = `#/${opciones.accion.ruta}`;
    accion.textContent = opciones.accion.texto;
    vacio.append(accion);
  }
  return vacio;
}

/** Indicador de carga: una rueda decorativa y un texto que se anuncia con `role="status"`. */
export function crearCargando(texto = 'Cargando…'): HTMLElement {
  const cargando = document.createElement('div');
  cargando.className = 'cargando';
  cargando.setAttribute('role', 'status');
  const rueda = document.createElement('span');
  rueda.className = 'rueda';
  rueda.setAttribute('aria-hidden', 'true');
  const mensaje = document.createElement('span');
  mensaje.textContent = texto;
  cargando.append(rueda, mensaje);
  return cargando;
}
