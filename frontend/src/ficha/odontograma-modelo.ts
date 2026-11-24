import type { Cara, EstadoPieza, RegistroOdontograma } from './api.ts';

/** Notación FDI y estado vigente del odontograma (C-44, design D4). Sin DOM: lo prueban tests unitarios. */

export type Denticion = 'permanente' | 'temporal';

export interface Mitad {
  readonly titulo: string;
  readonly arriba: readonly number[];
  readonly abajo: readonly number[];
}

/** Piezas en el orden clínico, partidas en el lado derecho y el izquierdo del paciente. */
export const MITADES: Readonly<Record<Denticion, readonly [Mitad, Mitad]>> = {
  permanente: [
    { titulo: 'Lado derecho del paciente', arriba: [18, 17, 16, 15, 14, 13, 12, 11], abajo: [48, 47, 46, 45, 44, 43, 42, 41] },
    { titulo: 'Lado izquierdo del paciente', arriba: [21, 22, 23, 24, 25, 26, 27, 28], abajo: [31, 32, 33, 34, 35, 36, 37, 38] },
  ],
  temporal: [
    { titulo: 'Lado derecho del paciente', arriba: [55, 54, 53, 52, 51], abajo: [85, 84, 83, 82, 81] },
    { titulo: 'Lado izquierdo del paciente', arriba: [61, 62, 63, 64, 65], abajo: [71, 72, 73, 74, 75] },
  ],
};

export const NOMBRE_ESTADO: Readonly<Record<EstadoPieza, string>> = {
  SANO: 'Sano',
  CARIES: 'Caries',
  OBTURADO: 'Obturado',
  A_TRATAR: 'A tratar',
  AUSENTE: 'Ausente',
  CORONA: 'Corona',
  ENDODONCIA: 'Endodoncia',
};

/** Letra que acompaña al color, así el estado no depende solo de él. */
export const LETRA_ESTADO: Readonly<Record<EstadoPieza, string>> = {
  SANO: 'S',
  CARIES: 'C',
  OBTURADO: 'O',
  A_TRATAR: 'T',
  AUSENTE: 'A',
  CORONA: 'Co',
  ENDODONCIA: 'E',
};

export const NOMBRE_CARA: Readonly<Record<Cara, string>> = {
  O: 'oclusal',
  M: 'mesial',
  D: 'distal',
  V: 'vestibular',
  L: 'lingual',
  P: 'palatina',
};

/** Estados que se marcan sobre la pieza completa (cruz, contorno y línea), no pintando una cara. */
export const ESTADOS_DE_PIEZA: ReadonlySet<EstadoPieza> = new Set<EstadoPieza>(['AUSENTE', 'CORONA', 'ENDODONCIA']);

export function cuadrante(pieza: number): number {
  return Math.floor(pieza / 10);
}

/** Cuadrantes 1, 2, 5 y 6: maxilar superior (la misma regla que el backend). */
export function esSuperior(pieza: number): boolean {
  return [1, 2, 5, 6].includes(cuadrante(pieza));
}

/** Cuadrantes 1, 4, 5 y 8: derecha del paciente, que se dibuja a la izquierda de quien mira. */
export function esLadoDerecho(pieza: number): boolean {
  return [1, 4, 5, 8].includes(cuadrante(pieza));
}

/** Caras de la pieza: lingual solo en las inferiores y palatina solo en las superiores. */
export function carasDe(pieza: number): readonly Cara[] {
  return ['O', 'M', 'D', 'V', esSuperior(pieza) ? 'P' : 'L'];
}

export function ubicacion(pieza: number): string {
  return `${esSuperior(pieza) ? 'superior' : 'inferior'} ${esLadoDerecho(pieza) ? 'derecha' : 'izquierda'}`;
}

export function nombreDeCara(cara: Cara | null): string {
  return cara === null ? 'pieza completa' : NOMBRE_CARA[cara];
}

interface Marca {
  readonly estado: EstadoPieza;
  readonly en: number;
}

export interface PiezaVigente {
  readonly completa: Marca | null;
  readonly caras: ReadonlyMap<Cara, Marca>;
}

/** Agrupa el estado vigente por pieza: el último registro de la pieza completa y de cada cara. */
export function vigentePorPieza(registros: readonly RegistroOdontograma[]): Map<number, PiezaVigente> {
  const piezas = new Map<number, { completa: Marca | null; caras: Map<Cara, Marca> }>();
  for (const registro of registros) {
    const pieza = piezas.get(registro.pieza) ?? { completa: null, caras: new Map<Cara, Marca>() };
    const marca: Marca = { estado: registro.estado, en: Date.parse(registro.registradoEn) };
    if (registro.cara === null) {
      if (pieza.completa === null || marca.en >= pieza.completa.en) {
        pieza.completa = marca;
      }
    } else {
      const anterior = pieza.caras.get(registro.cara);
      if (anterior === undefined || marca.en >= anterior.en) {
        pieza.caras.set(registro.cara, marca);
      }
    }
    piezas.set(registro.pieza, pieza);
  }
  return piezas;
}

/** Estado que se ve en una cara: el suyo si es más nuevo que el de la pieza completa; si no, el de la pieza. */
export function estadoDeCara(pieza: PiezaVigente | undefined, cara: Cara): EstadoPieza | null {
  if (pieza === undefined) {
    return null;
  }
  const propia = pieza.caras.get(cara);
  if (propia !== undefined && (pieza.completa === null || propia.en > pieza.completa.en)) {
    return propia.estado;
  }
  return pieza.completa?.estado ?? propia?.estado ?? null;
}

/** Marca de pieza completa que se dibuja encima (ausente, corona o endodoncia), si es la vigente. */
export function marcaDePieza(pieza: PiezaVigente | undefined): EstadoPieza | null {
  const completa = pieza?.completa ?? null;
  return completa !== null && ESTADOS_DE_PIEZA.has(completa.estado) ? completa.estado : null;
}

export function nombreAccesible(pieza: number, cara: Cara, estado: EstadoPieza | null): string {
  return `Pieza ${pieza}, cara ${NOMBRE_CARA[cara]}: ${estado === null ? 'sin estado' : NOMBRE_ESTADO[estado]}`;
}
