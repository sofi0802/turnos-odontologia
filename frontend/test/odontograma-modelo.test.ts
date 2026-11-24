import { describe, expect, it } from 'vitest';
import type { RegistroOdontograma } from '../src/ficha/api.ts';
import {
  carasDe,
  estadoDeCara,
  marcaDePieza,
  MITADES,
  nombreAccesible,
  ubicacion,
  vigentePorPieza,
} from '../src/ficha/odontograma-modelo.ts';
import { dejarSeleccion, tomarSeleccion } from '../src/ficha/seleccion.ts';

// C-44: modelo del odontograma (task 4.1) y selección en memoria (task 2.1). Datos ficticios.

function registro(datos: Partial<RegistroOdontograma> & Pick<RegistroOdontograma, 'pieza'>): RegistroOdontograma {
  return {
    id: 1,
    cara: 'O',
    estado: 'SANO',
    profesional: 'Ana Ejemplo',
    registradoEn: '2026-12-01T10:00:00-03:00',
    ...datos,
  };
}

describe('Estado vigente por pieza y cara', () => {
  it('el registro más nuevo de cada cara es el vigente', () => {
    const vigente = vigentePorPieza([
      registro({ id: 2, pieza: 16, estado: 'OBTURADO', registradoEn: '2026-12-02T10:00:00-03:00' }),
      registro({ id: 1, pieza: 16, estado: 'CARIES', registradoEn: '2026-12-01T10:00:00-03:00' }),
    ]);

    expect(estadoDeCara(vigente.get(16), 'O')).toBe('OBTURADO');
    expect(estadoDeCara(vigente.get(16), 'M')).toBeNull();
    expect(estadoDeCara(vigente.get(26), 'O')).toBeNull();
  });

  it('la pieza completa tiñe todas las caras y una cara más nueva muestra su propio estado', () => {
    const vigente = vigentePorPieza([
      registro({ id: 1, pieza: 36, cara: null, estado: 'CORONA', registradoEn: '2026-12-01T10:00:00-03:00' }),
      registro({ id: 2, pieza: 36, cara: 'M', estado: 'CARIES', registradoEn: '2026-12-02T10:00:00-03:00' }),
      registro({ id: 3, pieza: 36, cara: 'D', estado: 'SANO', registradoEn: '2026-11-30T10:00:00-03:00' }),
    ]);

    expect(estadoDeCara(vigente.get(36), 'M')).toBe('CARIES');
    expect(estadoDeCara(vigente.get(36), 'D')).toBe('CORONA');
    expect(estadoDeCara(vigente.get(36), 'O')).toBe('CORONA');
    expect(marcaDePieza(vigente.get(36))).toBe('CORONA');
  });

  it('solo ausente, corona y endodoncia marcan la pieza entera', () => {
    const vigente = vigentePorPieza([registro({ pieza: 11, cara: null, estado: 'CARIES' })]);

    expect(marcaDePieza(vigente.get(11))).toBeNull();
    expect(estadoDeCara(vigente.get(11), 'V')).toBe('CARIES');
  });
});

describe('Caras por maxilar', () => {
  it('las piezas superiores tienen palatina y las inferiores lingual', () => {
    expect(carasDe(16)).toEqual(['O', 'M', 'D', 'V', 'P']);
    expect(carasDe(36)).toEqual(['O', 'M', 'D', 'V', 'L']);
    expect(carasDe(55)).toContain('P');
    expect(carasDe(85)).toContain('L');
    expect(ubicacion(16)).toBe('superior derecha');
    expect(ubicacion(36)).toBe('inferior izquierda');
  });

  it('nombre accesible con pieza, cara y estado', () => {
    expect(nombreAccesible(16, 'O', 'OBTURADO')).toBe('Pieza 16, cara oclusal: Obturado');
    expect(nombreAccesible(26, 'M', null)).toBe('Pieza 26, cara mesial: sin estado');
  });
});

describe('Orden de las piezas', () => {
  it('permanentes: 32 piezas, de la 18 a la 11 y de la 21 a la 28 arriba', () => {
    const [derecha, izquierda] = MITADES.permanente;
    expect(derecha.arriba).toEqual([18, 17, 16, 15, 14, 13, 12, 11]);
    expect(izquierda.arriba).toEqual([21, 22, 23, 24, 25, 26, 27, 28]);
    expect(derecha.abajo).toEqual([48, 47, 46, 45, 44, 43, 42, 41]);
    expect(izquierda.abajo).toEqual([31, 32, 33, 34, 35, 36, 37, 38]);
  });

  it('temporales: 51 a 55, 61 a 65, 71 a 75 y 81 a 85', () => {
    const piezas = MITADES.temporal.flatMap((m) => [...m.arriba, ...m.abajo]).sort((a, b) => a - b);
    expect(piezas).toEqual([51, 52, 53, 54, 55, 61, 62, 63, 64, 65, 71, 72, 73, 74, 75, 81, 82, 83, 84, 85]);
  });
});

describe('Selección en memoria', () => {
  it('leerla la borra', () => {
    dejarSeleccion({ paciente: { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: null }, turno: null });

    expect(tomarSeleccion()?.paciente.id).toBe(1);
    expect(tomarSeleccion()).toBeNull();
  });
});
