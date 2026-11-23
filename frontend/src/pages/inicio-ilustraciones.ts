/**
 * Ilustraciones propias de la portada (C-37, D7): SVG creado con el DOM, con los colores de
 * `design-system/tokens.css`, sin archivos de imagen ni recursos remotos. Son decorativas:
 * llevan `aria-hidden="true"`.
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

function lienzo(vista: string, hijos: readonly SVGElement[]): SVGElement {
  return nodo('svg', { viewBox: vista, 'aria-hidden': 'true', focusable: 'false' }, hijos);
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

function calendario(): SVGElement {
  const puntos = [
    [30, 68],
    [68, 68],
    [106, 68],
    [30, 96],
    [106, 96],
  ] as const;
  return nodo('g', { transform: 'translate(150 50)' }, [
    nodo('rect', {
      width: '136',
      height: '132',
      rx: '16',
      fill: 'var(--ds-fondo)',
      stroke: 'var(--ds-acento-100)',
      'stroke-width': '3',
    }),
    nodo('path', { d: 'M0 16a16 16 0 0 1 16-16h104a16 16 0 0 1 16 16v26H0z', fill: 'var(--ds-acento-500)' }),
    nodo('rect', { x: '30', y: '-10', width: '10', height: '24', rx: '5', fill: 'var(--ds-acento-700)' }),
    nodo('rect', { x: '96', y: '-10', width: '10', height: '24', rx: '5', fill: 'var(--ds-acento-700)' }),
    nodo(
      'g',
      { fill: 'var(--ds-superficie-2)' },
      puntos.map(([cx, cy]) => nodo('circle', { cx: String(cx), cy: String(cy), r: '8' })),
    ),
    nodo('circle', { cx: '68', cy: '96', r: '12', fill: 'var(--ds-acento-500)' }),
    nodo('path', {
      d: 'M62 96l5 5 9-10',
      fill: 'none',
      stroke: 'var(--ds-acento-texto)',
      'stroke-width': '3.5',
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
    }),
  ]);
}

function muela(): SVGElement {
  return nodo('g', { transform: 'translate(18 76)' }, [
    nodo('path', {
      d: 'M62 8C40 0 14 12 14 44c0 24 14 34 16 60 2 22 8 40 20 40 12 0 12-22 22-22s10 22 22 22c12 0 18-18 20-40 2-26 16-36 16-60 0-32-26-44-48-36-6 2-12 2-20 0z',
      fill: 'var(--ds-fondo)',
      stroke: 'var(--ds-acento-500)',
      'stroke-width': '5',
      'stroke-linejoin': 'round',
    }),
    nodo('path', {
      d: 'M34 36c4-10 14-14 22-12',
      fill: 'none',
      stroke: 'var(--ds-acento-100)',
      'stroke-width': '6',
      'stroke-linecap': 'round',
    }),
  ]);
}

/** La muela y el calendario de la portada. */
export function crearIlustracionPortada(): SVGElement {
  return lienzo('0 0 320 270', [
    nodo('circle', { cx: '165', cy: '140', r: '118', fill: 'var(--ds-acento-100)', opacity: '.55' }),
    nodo('circle', { cx: '60', cy: '48', r: '9', fill: 'var(--ds-acento-100)' }),
    nodo('circle', { cx: '288', cy: '214', r: '12', fill: 'var(--ds-acento-100)' }),
    destello(290, 40, 28, '.55'),
    calendario(),
    muela(),
    destello(30, 218, 24, '.7'),
  ]);
}

function icono(trazos: readonly SVGElement[]): SVGElement {
  return nodo(
    'svg',
    {
      viewBox: '0 0 24 24',
      fill: 'none',
      stroke: 'currentColor',
      'stroke-width': '2',
      'stroke-linecap': 'round',
      'stroke-linejoin': 'round',
      'aria-hidden': 'true',
      focusable: 'false',
    },
    trazos,
  );
}

function trazo(d: string): SVGElement {
  return nodo('path', { d });
}

/** Ícono del paso 1: la lista de prestaciones. */
export function crearIconoPrestacion(): SVGElement {
  return icono([trazo('M9 6h11M9 12h11M9 18h11'), trazo('M4 6h.01M4 12h.01M4 18h.01')]);
}

/** Ícono del paso 2: el calendario con la hora. */
export function crearIconoHorario(): SVGElement {
  return icono([
    nodo('rect', { x: '3', y: '4', width: '18', height: '17', rx: '3' }),
    trazo('M8 2v4M16 2v4M3 10h18'),
    trazo('M12 14v3l2 1'),
  ]);
}

/** Ícono del paso 3: la confirmación. */
export function crearIconoConfirmacion(): SVGElement {
  return icono([nodo('circle', { cx: '12', cy: '12', r: '9' }), trazo('M8 12.5l3 3 5-6')]);
}
