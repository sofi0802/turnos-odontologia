/**
 * Íconos de línea propios (C-39, D7): SVG creado con el DOM, con `currentColor`, sin archivos ni fuentes
 * remotas. Son decorativos: llevan `aria-hidden="true"`; el texto que los acompaña es el nombre accesible.
 */

const NS = 'http://www.w3.org/2000/svg';

export type NombreIcono =
  | 'reservar'
  | 'turnos'
  | 'agenda'
  | 'configuracion'
  | 'menu'
  | 'cerrar'
  | 'llave'
  | 'reloj'
  | 'copiar'
  | 'volver';

type Trazo = readonly [etiqueta: 'path' | 'rect' | 'circle', atributos: Readonly<Record<string, string>>];

const TRAZOS: Readonly<Record<NombreIcono, readonly Trazo[]>> = {
  reservar: [
    ['rect', { x: '3', y: '4', width: '18', height: '17', rx: '3' }],
    ['path', { d: 'M8 2v4M16 2v4M3 10h18' }],
    ['path', { d: 'M12 13v5M9.5 15.5h5' }],
  ],
  turnos: [['path', { d: 'M9 6h11M9 12h11M9 18h11' }], ['path', { d: 'M4 6h.01M4 12h.01M4 18h.01' }]],
  agenda: [
    ['rect', { x: '3', y: '4', width: '18', height: '17', rx: '3' }],
    ['path', { d: 'M8 2v4M16 2v4M3 10h18' }],
    ['path', { d: 'M8 14h.01M12 14h.01M16 14h.01' }],
  ],
  configuracion: [
    ['circle', { cx: '12', cy: '12', r: '3' }],
    [
      'path',
      {
        d: 'M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
      },
    ],
  ],
  menu: [['path', { d: 'M4 6h16M4 12h16M4 18h16' }]],
  cerrar: [['path', { d: 'M6 6l12 12M18 6L6 18' }]],
  // C-41: "Mi contraseña", contraseña temporal pendiente, copiar y volver.
  llave: [['circle', { cx: '8', cy: '15', r: '4' }], ['path', { d: 'M10.8 12.2L20 3M16 7l3 3M14 9l2 2' }]],
  reloj: [['circle', { cx: '12', cy: '12', r: '9' }], ['path', { d: 'M12 7v5l3 2' }]],
  copiar: [['rect', { x: '9', y: '9', width: '12', height: '12', rx: '2' }], ['path', { d: 'M5 15V5a2 2 0 0 1 2-2h10' }]],
  volver: [['path', { d: 'M15 18l-6-6 6-6' }]],
};

export function crearIcono(nombre: NombreIcono): SVGElement {
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '2');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  svg.setAttribute('focusable', 'false');
  svg.setAttribute('class', 'icono');
  for (const [etiqueta, atributos] of TRAZOS[nombre]) {
    const trazo = document.createElementNS(NS, etiqueta);
    for (const [clave, valor] of Object.entries(atributos)) {
      trazo.setAttribute(clave, valor);
    }
    svg.append(trazo);
  }
  return svg;
}
