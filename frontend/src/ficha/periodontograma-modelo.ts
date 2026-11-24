import {
  SITIOS,
  type EstadoPeriodontal,
  type PedidoPeriodontograma,
  type PiezaPedido,
  type Sitio,
  type VersionPeriodontograma,
} from './periodontograma-api.ts';
import { cuadrante, esLadoDerecho, esSuperior } from './odontograma-modelo.ts';

/**
 * Periodontograma sin DOM (C-46, design D1, D3 y D6): orden de las piezas, nombres de los sitios, borrador de la
 * nueva versión, validación con las reglas de RN-CL-05, armado del pedido y geometría del dibujo de cada diente.
 */

export interface Arcada {
  readonly titulo: string;
  readonly cuadrantes: readonly [readonly number[], readonly number[]];
}

/** Lado derecho del paciente a la izquierda de quien mira, como el odontograma (D4 de C-44). */
export const ARCADAS: readonly [Arcada, Arcada] = [
  { titulo: 'Maxilar superior', cuadrantes: [[18, 17, 16, 15, 14, 13, 12, 11], [21, 22, 23, 24, 25, 26, 27, 28]] },
  { titulo: 'Maxilar inferior', cuadrantes: [[48, 47, 46, 45, 44, 43, 42, 41], [31, 32, 33, 34, 35, 36, 37, 38]] },
];

/** Orden de las piezas en la boca, para elegir la primera con errores. */
export const ORDEN_DE_PIEZAS: readonly number[] = ARCADAS.flatMap((a) => [...a.cuadrantes[0], ...a.cuadrantes[1]]);

/** Lo que se ve en el mapa: el estado calculado, ausente, sin cargar o "en carga" (pieza nueva sin guardar). */
export type EstadoDelMapa = EstadoPeriodontal | 'AUSENTE' | 'SIN_CARGAR' | 'CARGANDO';

export const NOMBRE_ESTADO_PERIO: Readonly<Record<EstadoDelMapa, string>> = {
  SANO: 'Sano',
  LEVE: 'Leve',
  MODERADO: 'Moderado',
  SEVERO: 'Severo',
  AUSENTE: 'Ausente',
  SIN_CARGAR: 'Sin cargar',
  CARGANDO: 'En carga',
};

export const MENSAJE_SIN_PIEZAS = 'Cargá al menos una pieza.';
export const MENSAJE_PROFUNDIDAD = 'Entre 0 y 15 mm.';
export const MENSAJE_RECESION = 'Entre −5 y 15 mm.';
export const MENSAJE_GRADO = 'Entre 0 y 3.';
export const MENSAJE_ENTERO = 'Ingresá un número entero.';
export const MENSAJE_COMPLETAR = 'Completá este dato.';
export const MENSAJE_FURCACION = 'La furcación solo se carga en molares y en las piezas 14 y 24.';

export function mensajePiezaIncompleta(pieza: number): string {
  return `Completá los 6 sitios y la movilidad de la pieza ${pieza}.`;
}

/** Piezas permanentes (cuadrantes 1 a 4), como `PiezaDental.esPermanente()` del backend. */
export function esPermanente(pieza: number): boolean {
  return cuadrante(pieza) >= 1 && cuadrante(pieza) <= 4;
}

/** Molares y las piezas 14 y 24: la misma regla que `PiezaDental.esMultirradicular()`. */
export function esMultirradicular(pieza: number): boolean {
  return esPermanente(pieza) && (pieza % 10 >= 6 || pieza === 14 || pieza === 24);
}

/** Nombre del sitio según el maxilar: palatinos arriba y linguales abajo, aunque la API diga siempre ML, L y DL. */
export function nombreDeSitio(pieza: number, sitio: Sitio): string {
  const lado = esSuperior(pieza) ? 'palatino' : 'lingual';
  switch (sitio) {
    case 'MV':
      return 'Mesiovestibular';
    case 'V':
      return 'Vestibular';
    case 'DV':
      return 'Distovestibular';
    case 'ML':
      return `Mesio${lado}`;
    case 'L':
      return esSuperior(pieza) ? 'Palatino' : 'Lingual';
    case 'DL':
      return `Disto${lado}`;
  }
}

// ---- Borrador de la nueva versión (design D5) ----

export interface SitioBorrador {
  readonly profundidad: number | null;
  readonly recesion: number | null;
  readonly sangrado: boolean;
  readonly placa: boolean;
}

export interface PiezaBorrador {
  readonly pieza: number;
  readonly ausente: boolean;
  readonly movilidad: number | null;
  readonly furcacion: number | null;
  /** Siempre 6, en el orden de {@link SITIOS}. */
  readonly sitios: readonly SitioBorrador[];
}

export type Borrador = ReadonlyMap<number, PiezaBorrador>;

const SITIO_VACIO: SitioBorrador = { profundidad: null, recesion: null, sangrado: false, placa: false };

/** Pieza que se carga de cero: campos vacíos, sin valores inventados. */
export function piezaVacia(pieza: number): PiezaBorrador {
  return { pieza, ausente: false, movilidad: null, furcacion: null, sitios: SITIOS.map(() => SITIO_VACIO) };
}

export function piezaAusente(pieza: number): PiezaBorrador {
  return { ...piezaVacia(pieza), ausente: true };
}

/** Copia de una versión para empezar la nueva (spec "Nueva versión precargada"). */
export function borradorDe(version: VersionPeriodontograma | null): Map<number, PiezaBorrador> {
  const borrador = new Map<number, PiezaBorrador>();
  for (const pieza of version?.piezas ?? []) {
    if (pieza.ausente) {
      borrador.set(pieza.pieza, piezaAusente(pieza.pieza));
      continue;
    }
    borrador.set(pieza.pieza, {
      pieza: pieza.pieza,
      ausente: false,
      movilidad: pieza.movilidad,
      furcacion: pieza.furcacion,
      sitios: SITIOS.map((sitio) => {
        const medido = pieza.sitios.find((s) => s.sitio === sitio);
        return medido === undefined
          ? SITIO_VACIO
          : { profundidad: medido.profundidadSondaje, recesion: medido.recesion, sangrado: medido.sangrado, placa: medido.placa };
      }),
    });
  }
  return borrador;
}

function normalizar(borrador: Borrador): string {
  return JSON.stringify([...borrador.values()].sort((a, b) => a.pieza - b.pieza));
}

/** Dos borradores con los mismos datos (para saber si hay algo sin guardar). */
export function iguales(a: Borrador, b: Borrador): boolean {
  return normalizar(a) === normalizar(b);
}

// ---- Validación (design D6, RN-CL-05) ----

export interface ErroresDePieza {
  /** Mensaje de la pieza, si está incompleta. */
  readonly mensaje: string | null;
  /** Error por campo: `MV-profundidad`, `DL-recesion`, `movilidad`, `furcacion`. */
  readonly campos: ReadonlyMap<string, string>;
}

export interface Errores {
  /** Primer mensaje para el aviso general; `null` si no hay errores. */
  readonly general: string | null;
  /** Piezas con errores, en el orden de la boca. */
  readonly piezas: ReadonlyMap<number, ErroresDePieza>;
}

function errorDeNumero(valor: number | null, minimo: number, maximo: number, fueraDeRango: string): string | null {
  if (valor === null) {
    return MENSAJE_COMPLETAR;
  }
  if (!Number.isInteger(valor)) {
    return MENSAJE_ENTERO;
  }
  return valor < minimo || valor > maximo ? fueraDeRango : null;
}

function validarPieza(pieza: PiezaBorrador): ErroresDePieza | null {
  if (pieza.ausente) {
    return null;
  }
  const campos = new Map<string, string>();
  let incompleta = false;
  pieza.sitios.forEach((sitio, i) => {
    const codigo = SITIOS[i] ?? 'MV';
    const profundidad = errorDeNumero(sitio.profundidad, 0, 15, MENSAJE_PROFUNDIDAD);
    const recesion = errorDeNumero(sitio.recesion, -5, 15, MENSAJE_RECESION);
    if (profundidad !== null) {
      campos.set(`${codigo}-profundidad`, profundidad);
    }
    if (recesion !== null) {
      campos.set(`${codigo}-recesion`, recesion);
    }
    incompleta ||= sitio.profundidad === null || sitio.recesion === null;
  });
  const movilidad = errorDeNumero(pieza.movilidad, 0, 3, MENSAJE_GRADO);
  if (movilidad !== null) {
    campos.set('movilidad', movilidad);
  }
  incompleta ||= pieza.movilidad === null;
  if (pieza.furcacion !== null) {
    const furcacion = esMultirradicular(pieza.pieza) ? errorDeNumero(pieza.furcacion, 0, 3, MENSAJE_GRADO) : MENSAJE_FURCACION;
    if (furcacion !== null) {
      campos.set('furcacion', furcacion);
    }
  }
  if (campos.size === 0) {
    return null;
  }
  return { mensaje: incompleta ? mensajePiezaIncompleta(pieza.pieza) : null, campos };
}

export function validar(borrador: Borrador): Errores {
  if (borrador.size === 0) {
    return { general: MENSAJE_SIN_PIEZAS, piezas: new Map() };
  }
  const piezas = new Map<number, ErroresDePieza>();
  const orden = [...borrador.keys()].sort((a, b) => posicion(a) - posicion(b));
  for (const numero of orden) {
    const pieza = borrador.get(numero);
    const errores = pieza === undefined ? null : validarPieza(pieza);
    if (errores !== null) {
      piezas.set(numero, errores);
    }
  }
  const primera = [...piezas.entries()][0];
  const general =
    primera === undefined ? null : (primera[1].mensaje ?? `Revisá los datos de la pieza ${primera[0]}.`);
  return { general, piezas };
}

function posicion(pieza: number): number {
  const i = ORDEN_DE_PIEZAS.indexOf(pieza);
  return i === -1 ? ORDEN_DE_PIEZAS.length + pieza : i;
}

/** Pedido de `POST /periodontograma`: todas las piezas, ordenadas por número, con la recesión tal como se cargó. */
export function armarPedido(borrador: Borrador): PedidoPeriodontograma {
  const piezas: PiezaPedido[] = [...borrador.values()]
    .sort((a, b) => a.pieza - b.pieza)
    .map((pieza): PiezaPedido => {
      if (pieza.ausente) {
        return { pieza: pieza.pieza, ausente: true };
      }
      return {
        pieza: pieza.pieza,
        ausente: false,
        movilidad: pieza.movilidad ?? 0,
        furcacion: pieza.furcacion,
        sitios: pieza.sitios.map((s, i) => ({
          sitio: SITIOS[i] ?? 'MV',
          profundidadSondaje: s.profundidad ?? 0,
          recesion: s.recesion ?? 0,
          sangrado: s.sangrado,
          placa: s.placa,
        })),
      };
    });
  return { piezas };
}

// ---- Geometría del dibujo (design D3) ----

/** Lienzo de 40 × 100 con la raíz arriba (pieza superior); el límite amelocementario está en y = 55. */
export const LAC = 55;
/** Unidades del lienzo por milímetro. */
export const MM = 3.2;

export interface Forma {
  readonly corona: string;
  readonly raices: readonly string[];
}

const CORONA_PREMOLAR = 'M7 55 L33 55 Q37 72 31 89 Q20 96 9 89 Q3 72 7 55 Z';
const CORONA_MOLAR = 'M3 55 L37 55 Q40 74 35 90 L28 94 L20 90 L12 94 L5 90 Q0 74 3 55 Z';

const FORMAS = {
  incisivo: { corona: 'M9 55 L31 55 L33 78 Q33 93 20 95 Q7 93 7 78 Z', raices: ['M10 55 Q11 18 20 8 Q29 18 30 55 Z'] },
  canino: { corona: 'M8 55 L32 55 Q35 72 26 88 L20 97 L14 88 Q5 72 8 55 Z', raices: ['M10 55 Q11 10 20 1 Q29 10 30 55 Z'] },
  premolar: { corona: CORONA_PREMOLAR, raices: ['M11 55 Q12 16 20 9 Q28 16 29 55 Z'] },
  premolarDoble: {
    corona: CORONA_PREMOLAR,
    raices: ['M9 55 Q9 20 14 12 Q19 22 19 55 Z', 'M21 55 Q21 22 26 12 Q31 20 31 55 Z'],
  },
  molarSuperior: {
    corona: CORONA_MOLAR,
    raices: ['M5 55 Q4 22 10 15 Q15 24 15 55 Z', 'M16 55 Q16 14 21 8 Q25 16 24 55 Z', 'M26 55 Q26 24 31 15 Q37 22 35 55 Z'],
  },
  molarInferior: { corona: CORONA_MOLAR, raices: ['M5 55 Q4 20 11 12 Q17 22 17 55 Z', 'M23 55 Q23 22 29 12 Q36 20 35 55 Z'] },
} as const satisfies Record<string, Forma>;

/** Dibujo propio y simplificado según el tipo de pieza. */
export function formaDe(pieza: number): Forma {
  const posicion = pieza % 10;
  const superior = esSuperior(pieza);
  if (posicion <= 2) return FORMAS.incisivo;
  if (posicion === 3) return FORMAS.canino;
  if (posicion <= 5) return posicion === 4 && superior ? FORMAS.premolarDoble : FORMAS.premolar;
  return superior ? FORMAS.molarSuperior : FORMAS.molarInferior;
}

/** x de los sitios mesial, medio y distal de una cara: el mesial queda del lado de la línea media. */
export function xDeSitios(pieza: number): readonly [number, number, number] {
  return esLadoDerecho(pieza) ? [32, 20, 8] : [8, 20, 32];
}

/** y de un nivel en mm medido desde el límite amelocementario hacia la raíz (positivo) o la corona (negativo). */
export function yDeNivel(mm: number): number {
  const y = LAC - mm * MM;
  return Math.round(Math.max(1, Math.min(99, y)) * 10) / 10;
}

export interface Lineas {
  readonly margen: string;
  readonly fondo: string;
}

/**
 * Margen de la encía y fondo de la bolsa de una cara a partir de sus tres sitios [profundidad, recesión]. El margen
 * está a la recesión del límite y el fondo, a la profundidad del margen. Sin líneas si falta un valor.
 */
export function lineasDe(pieza: number, tres: ReadonlyArray<readonly [number | null, number | null]>): Lineas | null {
  const xs = xDeSitios(pieza);
  const puntos: Array<{ x: number; profundidad: number; recesion: number }> = [];
  for (const [i, [profundidad, recesion]] of tres.entries()) {
    const x = xs[i];
    if (profundidad === null || recesion === null || x === undefined || !Number.isFinite(profundidad) || !Number.isFinite(recesion)) {
      return null;
    }
    puntos.push({ x, profundidad, recesion });
  }
  return {
    margen: puntos.map((p) => `${p.x},${yDeNivel(p.recesion)}`).join(' '),
    fondo: puntos.map((p) => `${p.x},${yDeNivel(p.recesion + p.profundidad)}`).join(' '),
  };
}
