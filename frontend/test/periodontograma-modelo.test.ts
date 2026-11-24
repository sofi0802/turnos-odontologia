import { describe, expect, it } from 'vitest';
import type { VersionPeriodontograma } from '../src/ficha/periodontograma-api.ts';
import {
  ARCADAS,
  armarPedido,
  borradorDe,
  esMultirradicular,
  formaDe,
  iguales,
  lineasDe,
  MENSAJE_SIN_PIEZAS,
  nombreDeSitio,
  piezaAusente,
  piezaVacia,
  validar,
  xDeSitios,
  yDeNivel,
  LAC,
  MM,
  type PiezaBorrador,
} from '../src/ficha/periodontograma-modelo.ts';

/** C-46: funciones puras del periodontograma (design D1 y D6). Datos ficticios. */

function pieza(numero: number, datos: Partial<PiezaBorrador> = {}): PiezaBorrador {
  return {
    ...piezaVacia(numero),
    movilidad: 0,
    sitios: Array.from({ length: 6 }, () => ({ profundidad: 2, recesion: -1, sangrado: false, placa: false })),
    ...datos,
  };
}

function conSitio(base: PiezaBorrador, indice: number, cambios: Partial<PiezaBorrador['sitios'][number]>): PiezaBorrador {
  return { ...base, sitios: base.sitios.map((s, i) => (i === indice ? { ...s, ...cambios } : s)) };
}

const VERSION: VersionPeriodontograma = {
  id: 9,
  profesional: 'Bruno Prueba',
  registradoEn: '2026-12-01T09:00:00-03:00',
  piezas: [
    {
      pieza: 16,
      ausente: false,
      movilidad: 1,
      furcacion: 1,
      estado: 'MODERADO',
      sitios: [
        { sitio: 'MV', profundidadSondaje: 4, recesion: 0, sangrado: true, placa: false },
        { sitio: 'V', profundidadSondaje: 4, recesion: 0, sangrado: false, placa: false },
        { sitio: 'DV', profundidadSondaje: 3, recesion: -1, sangrado: false, placa: true },
        { sitio: 'ML', profundidadSondaje: 3, recesion: 0, sangrado: true, placa: false },
        { sitio: 'L', profundidadSondaje: 2, recesion: -1, sangrado: false, placa: false },
        { sitio: 'DL', profundidadSondaje: 3, recesion: 0, sangrado: false, placa: true },
      ],
    },
    { pieza: 18, ausente: true, movilidad: null, furcacion: null, estado: null, sitios: [] },
  ],
};

describe('Orden de las piezas', () => {
  it('cada arcada tiene dos cuadrantes en el orden de la boca', () => {
    expect(ARCADAS[0].cuadrantes).toEqual([
      [18, 17, 16, 15, 14, 13, 12, 11],
      [21, 22, 23, 24, 25, 26, 27, 28],
    ]);
    expect(ARCADAS[1].cuadrantes).toEqual([
      [48, 47, 46, 45, 44, 43, 42, 41],
      [31, 32, 33, 34, 35, 36, 37, 38],
    ]);
  });
});

describe('Piezas multirradiculares', () => {
  it.each([16, 17, 18, 26, 36, 46, 38, 14, 24])('la %i admite furcación', (numero) => {
    expect(esMultirradicular(numero)).toBe(true);
  });

  it.each([11, 13, 15, 25, 34, 44, 45, 51, 55])('la %i no admite furcación', (numero) => {
    expect(esMultirradicular(numero)).toBe(false);
  });
});

describe('Nombres de los sitios', () => {
  it('palatinos en el maxilar superior', () => {
    expect(['MV', 'V', 'DV', 'ML', 'L', 'DL'].map((s) => nombreDeSitio(16, s as 'MV'))).toEqual([
      'Mesiovestibular',
      'Vestibular',
      'Distovestibular',
      'Mesiopalatino',
      'Palatino',
      'Distopalatino',
    ]);
  });

  it('linguales en el maxilar inferior', () => {
    expect(['ML', 'L', 'DL'].map((s) => nombreDeSitio(36, s as 'ML'))).toEqual(['Mesiolingual', 'Lingual', 'Distolingual']);
  });
});

describe('Validación', () => {
  it('una pieza completa no tiene errores', () => {
    expect(validar(new Map([[16, pieza(16)]]))).toEqual({ general: null, piezas: new Map() });
  });

  it('versión sin piezas', () => {
    expect(validar(new Map()).general).toBe(MENSAJE_SIN_PIEZAS);
    expect(MENSAJE_SIN_PIEZAS).toBe('Cargá al menos una pieza.');
  });

  it.each([
    [0, null],
    [15, null],
    [-1, 'Entre 0 y 15 mm.'],
    [16, 'Entre 0 y 15 mm.'],
  ])('profundidad %i', (valor, error) => {
    const errores = validar(new Map([[11, conSitio(pieza(11), 0, { profundidad: valor })]]));
    expect(errores.piezas.get(11)?.campos.get('MV-profundidad') ?? null).toBe(error);
  });

  it.each([
    [-5, null],
    [15, null],
    [-6, 'Entre −5 y 15 mm.'],
    [16, 'Entre −5 y 15 mm.'],
  ])('recesión %i', (valor, error) => {
    const errores = validar(new Map([[11, conSitio(pieza(11), 1, { recesion: valor })]]));
    expect(errores.piezas.get(11)?.campos.get('V-recesion') ?? null).toBe(error);
  });

  it('un valor que no es entero', () => {
    const errores = validar(new Map([[11, conSitio(pieza(11), 0, { profundidad: 2.5 })]]));
    expect(errores.piezas.get(11)?.campos.get('MV-profundidad')).toBe('Ingresá un número entero.');
  });

  it('una pieza incompleta dice qué completar', () => {
    const incompleta = conSitio({ ...pieza(36), movilidad: null }, 5, { profundidad: null, recesion: null });
    const errores = validar(new Map([[36, incompleta]]));
    const deLaPieza = errores.piezas.get(36);
    expect(deLaPieza?.mensaje).toBe('Completá los 6 sitios y la movilidad de la pieza 36.');
    expect(deLaPieza?.campos.get('DL-profundidad')).toBe('Completá este dato.');
    expect(deLaPieza?.campos.get('DL-recesion')).toBe('Completá este dato.');
    expect(deLaPieza?.campos.get('movilidad')).toBe('Completá este dato.');
    expect(errores.general).toBe('Completá los 6 sitios y la movilidad de la pieza 36.');
  });

  it('la primera pieza con errores es la del orden de la boca', () => {
    const errores = validar(
      new Map([
        [36, conSitio(pieza(36), 0, { profundidad: null })],
        [21, conSitio(pieza(21), 0, { profundidad: 20 })],
      ]),
    );
    expect([...errores.piezas.keys()]).toEqual([21, 36]);
  });

  it('furcación fuera de los multirradiculares', () => {
    const errores = validar(new Map([[11, pieza(11, { furcacion: 1 })]]));
    expect(errores.piezas.get(11)?.campos.get('furcacion')).toBe('La furcación solo se carga en molares y en las piezas 14 y 24.');
  });

  it('una pieza ausente no necesita datos', () => {
    expect(validar(new Map([[18, piezaAusente(18)]])).general).toBeNull();
  });
});

describe('Borrador y pedido', () => {
  it('la nueva versión copia la última', () => {
    const borrador = borradorDe(VERSION);
    expect([...borrador.keys()]).toEqual([16, 18]);
    expect(borrador.get(16)?.sitios[0]).toEqual({ profundidad: 4, recesion: 0, sangrado: true, placa: false });
    expect(borrador.get(16)?.movilidad).toBe(1);
    expect(borrador.get(18)?.ausente).toBe(true);
    expect(iguales(borrador, borradorDe(VERSION))).toBe(true);
  });

  it('una pieza nueva arranca con los campos vacíos', () => {
    expect(piezaVacia(36).sitios.every((s) => s.profundidad === null && s.recesion === null)).toBe(true);
    expect(piezaVacia(36).movilidad).toBeNull();
  });

  it('el pedido lleva la recesión negativa tal como se cargó', () => {
    const borrador = new Map([
      [21, pieza(21, { movilidad: 0 })],
      [18, piezaAusente(18)],
    ]);
    const pedido = armarPedido(borrador);
    expect(pedido.piezas.map((p) => p.pieza)).toEqual([18, 21]);
    expect(pedido.piezas[0]).toEqual({ pieza: 18, ausente: true });
    expect(pedido.piezas[1]).toEqual({
      pieza: 21,
      ausente: false,
      movilidad: 0,
      furcacion: null,
      sitios: ['MV', 'V', 'DV', 'ML', 'L', 'DL'].map((sitio) => ({
        sitio,
        profundidadSondaje: 2,
        recesion: -1,
        sangrado: false,
        placa: false,
      })),
    });
  });

  it('un cambio hace que el borrador sea distinto', () => {
    const borrador = borradorDe(VERSION);
    const otro = new Map(borrador);
    const p16 = otro.get(16);
    if (p16 !== undefined) {
      otro.set(16, conSitio(p16, 0, { profundidad: 5 }));
    }
    expect(iguales(borrador, otro)).toBe(false);
  });
});

describe('Dibujo de las piezas', () => {
  it('la forma depende del tipo de pieza', () => {
    expect(formaDe(11).raices).toHaveLength(1);
    expect(formaDe(13).raices).toHaveLength(1);
    expect(formaDe(14).raices).toHaveLength(2);
    expect(formaDe(34).raices).toHaveLength(1);
    expect(formaDe(16).raices).toHaveLength(3);
    expect(formaDe(46).raices).toHaveLength(2);
  });

  it('el mesial queda del lado de la línea media', () => {
    expect(xDeSitios(16)).toEqual([32, 20, 8]);
    expect(xDeSitios(46)).toEqual([32, 20, 8]);
    expect(xDeSitios(26)).toEqual([8, 20, 32]);
    expect(xDeSitios(36)).toEqual([8, 20, 32]);
  });

  it('Líneas del margen y de la bolsa', () => {
    // Pieza 16, sitio V: recesión 0 y profundidad 4.
    const lineas = lineasDe(16, [
      [4, 0],
      [4, 0],
      [3, -1],
    ]);
    expect(lineas?.margen).toBe(`32,${LAC} 20,${LAC} 8,${yDeNivel(-1)}`);
    expect(lineas?.fondo).toBe(`32,${yDeNivel(4)} 20,${yDeNivel(4)} 8,${yDeNivel(2)}`);
    expect(LAC - Number(yDeNivel(4))).toBeCloseTo(4 * MM);
  });

  it('una cara con un sitio vacío no tiene líneas', () => {
    expect(lineasDe(36, [
      [3, -1],
      [2, -2],
      [null, null],
    ])).toBeNull();
  });
});
