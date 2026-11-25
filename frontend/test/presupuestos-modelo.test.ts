import { describe, expect, it } from 'vitest';
import {
  aCentavos,
  cuerpoDeEmision,
  estadoPresupuesto,
  estimarItem,
  formatearPesos,
  leerPorcentaje,
  leerPrecio,
  sumarTotales,
  validarCantidad,
  validarCara,
  validarCodigo,
  validarPieza,
  validarPrestacion,
} from '../src/presupuestos/modelo.ts';

/** C-50, tarea 1.2 (design D4 y D5): reglas puras de los presupuestos y aranceles, sin DOM. */

describe('Estimado de un ítem (RN-PR-03, en centavos)', () => {
  it('Consulta o control al 100 %', () => {
    expect(estimarItem({ precio: aCentavos(15000), cantidad: 1, porcentaje: 100 })).toEqual({
      importe: 1_500_000,
      cobertura: 1_500_000,
      copago: 0,
    });
  });

  it('Limpieza (profilaxis) al 50 %', () => {
    expect(estimarItem({ precio: aCentavos(25000), cantidad: 1, porcentaje: 50 })).toEqual({
      importe: 2_500_000,
      cobertura: 1_250_000,
      copago: 1_250_000,
    });
  });

  it('redondea half up a centavos: 25000.15 al 50 %', () => {
    expect(estimarItem({ precio: aCentavos(25000.15), cantidad: 1, porcentaje: 50 })).toEqual({
      importe: 2_500_015,
      cobertura: 1_250_008,
      copago: 1_250_007,
    });
  });

  it('redondea hacia abajo por debajo de medio centavo', () => {
    // 0,03 × 50 % = 0,015 → 0,02 (half up); 0,01 × 30 % = 0,003 → 0,00.
    expect(estimarItem({ precio: 3, cantidad: 1, porcentaje: 50 }).cobertura).toBe(2);
    expect(estimarItem({ precio: 1, cantidad: 1, porcentaje: 30 }).cobertura).toBe(0);
  });

  it('cantidad mayor que uno', () => {
    expect(estimarItem({ precio: aCentavos(30000), cantidad: 2, porcentaje: 80 })).toEqual({
      importe: 6_000_000,
      cobertura: 4_800_000,
      copago: 1_200_000,
    });
  });

  it('particular: porcentaje 0, paga todo', () => {
    expect(estimarItem({ precio: aCentavos(15000), cantidad: 1, porcentaje: 0 })).toEqual({
      importe: 1_500_000,
      cobertura: 0,
      copago: 1_500_000,
    });
  });

  it('suma los totales de los ítems de C-19', () => {
    const items = [
      estimarItem({ precio: aCentavos(15000), cantidad: 1, porcentaje: 100 }),
      estimarItem({ precio: aCentavos(25000), cantidad: 1, porcentaje: 50 }),
    ];
    expect(sumarTotales(items)).toEqual({ precio: 4_000_000, cobertura: 2_750_000, copago: 1_250_000 });
  });

  it('aCentavos no arrastra errores de punto flotante', () => {
    expect(aCentavos(0.1 + 0.2)).toBe(30);
    expect(aCentavos(25000.15)).toBe(2_500_015);
  });
});

describe('Formato de pesos', () => {
  it.each([
    [1_500_000, '$ 15.000,00'],
    [1_250_008, '$ 12.500,08'],
    [0, '$ 0,00'],
    [5, '$ 0,05'],
    [999_999_999_999, '$ 9.999.999.999,99'],
  ])('%i centavos es %s', (centavos, texto) => {
    expect(formatearPesos(centavos)).toBe(texto);
  });
});

describe('Estado escrito', () => {
  it.each([
    [{ estado: 'PENDIENTE', vencido: false }, 'Pendiente'],
    [{ estado: 'PENDIENTE', vencido: true }, 'Vencido'],
    [{ estado: 'ACEPTADO', vencido: false }, 'Aceptado'],
    [{ estado: 'RECHAZADO', vencido: false }, 'Rechazado'],
  ] as const)('%o es %s', (presupuesto, texto) => {
    expect(estadoPresupuesto(presupuesto)).toBe(texto);
  });
});

describe('Validación de cada ítem', () => {
  it('pide la prestación', () => {
    expect(validarPrestacion(null)).toBe('Elegí la prestación.');
    expect(validarPrestacion(1)).toBe('');
  });

  it('pide el código solo si la prestación tiene varios', () => {
    expect(validarCodigo(true, null)).toBe('Elegí el código del nomenclador.');
    expect(validarCodigo(true, 101)).toBe('');
    expect(validarCodigo(false, null)).toBe('');
  });

  it.each([
    ['', ''],
    ['16', ''],
    ['36', ''],
    ['55', ''],
    ['19', 'La pieza no existe en la notación FDI.'],
    ['9', 'La pieza no existe en la notación FDI.'],
    ['1a', 'La pieza no existe en la notación FDI.'],
  ])('pieza "%s"', (pieza, mensaje) => {
    expect(validarPieza(pieza)).toBe(mensaje);
  });

  it.each([
    ['16', '', ''],
    ['16', 'O', ''],
    ['16', 'P', ''],
    ['36', 'L', ''],
    ['36', 'P', 'Esa cara no corresponde a la pieza.'],
    ['16', 'L', 'Esa cara no corresponde a la pieza.'],
    ['', 'O', 'Indicá la pieza para elegir una cara.'],
    ['19', 'O', ''],
  ])('pieza "%s" con cara "%s"', (pieza, cara, mensaje) => {
    // Con una pieza inválida el error va en "Pieza", no en "Cara".
    expect(validarCara(pieza, cara)).toBe(mensaje);
  });

  it.each([
    ['1', ''],
    ['99', ''],
    ['0', 'Usá un número entero de 1 a 99.'],
    ['100', 'Usá un número entero de 1 a 99.'],
    ['1,5', 'Usá un número entero de 1 a 99.'],
    ['', 'Usá un número entero de 1 a 99.'],
  ])('cantidad "%s"', (cantidad, mensaje) => {
    expect(validarCantidad(cantidad)).toBe(mensaje);
  });
});

describe('Precio que escribe el administrador', () => {
  it.each([
    ['15000', 1_500_000],
    ['27000,50', 2_700_050],
    ['27000.5', 2_700_050],
    [' 15.000,00 ', 1_500_000],
    ['9999999999,99', 999_999_999_999],
  ])('"%s" son %i centavos', (texto, centavos) => {
    expect(leerPrecio(texto)).toEqual({ centavos });
  });

  it.each([
    ['', 'Ingresá el precio.'],
    ['0', 'Ingresá un precio mayor que cero.'],
    ['-100', 'Ingresá un precio mayor que cero.'],
    ['15000,005', 'Usá como máximo dos decimales.'],
    ['10000000000', 'El precio no puede superar $ 9.999.999.999,99.'],
    ['quince mil', 'Ingresá un número, por ejemplo 15000 o 15000,50.'],
  ])('"%s" se rechaza', (texto, error) => {
    expect(leerPrecio(texto)).toEqual({ error });
  });
});

describe('Porcentaje de cobertura', () => {
  it.each([
    ['0', 0],
    ['50', 50],
    ['100', 100],
  ])('"%s" es %i', (texto, porcentaje) => {
    expect(leerPorcentaje(texto, true)).toEqual({ porcentaje });
  });

  it.each(['101', '-1', '50,5', '50.5', 'mitad'])('"%s" no es un entero de 0 a 100', (texto) => {
    expect(leerPorcentaje(texto, false)).toEqual({ error: 'Usá un número entero de 0 a 100.' });
  });

  it('vacío sin valor previo es "no cubre"', () => {
    expect(leerPorcentaje('  ', false)).toEqual({ vacio: true });
  });

  it('vaciar un porcentaje cargado pide cargar 0', () => {
    expect(leerPorcentaje('', true)).toEqual({ error: 'Para dejar de cubrirlo, cargá 0.' });
  });
});

describe('Cuerpo del POST de emisión', () => {
  it('manda prestación, código, pieza, cara y cantidad; null cuando no corresponde', () => {
    expect(
      cuerpoDeEmision([
        { prestacionId: 1, nomencladorId: 101, pieza: '16', cara: 'O', cantidad: '1' },
        { prestacionId: 2, nomencladorId: null, pieza: '', cara: '', cantidad: '2' },
      ]),
    ).toEqual({
      items: [
        { prestacionId: 1, nomencladorId: 101, pieza: 16, cara: 'O', cantidad: 1 },
        { prestacionId: 2, nomencladorId: null, pieza: null, cara: null, cantidad: 2 },
      ],
    });
  });

  it('no manda profesional, precios ni porcentajes', () => {
    const cuerpo = cuerpoDeEmision([{ prestacionId: 1, nomencladorId: 101, pieza: '', cara: '', cantidad: '1' }]);
    expect(JSON.stringify(cuerpo)).not.toMatch(/profesional|precio|porcentaje/);
  });
});
