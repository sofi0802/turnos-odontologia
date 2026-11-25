import { describe, expect, it } from 'vitest';
import {
  estadoAfiliacion,
  formatearFecha,
  MENSAJE_FIN_ANTERIOR,
  MENSAJE_NUMERO_INVALIDO,
  normalizarNumero,
  textoVigencia,
  validarCodigo,
  validarDescripcion,
  validarDesde,
  validarFin,
  validarNombreObraSocial,
  validarNombrePlan,
  validarNumero,
  validarSigla,
  vigenteDesdePorDefecto,
} from '../src/coberturas/modelo.ts';

/** C-49, tarea 1.3 (design D4): funciones puras de las coberturas, con hoy = 2026-11-24 en Buenos Aires. */

const HOY = '2026-11-24';

describe('Número de afiliado', () => {
  it.each(['OSE-000001', 'PP-77', 'a/b.c-1', 'X', 'A'.repeat(30)])('%s es válido', (numero) => {
    expect(validarNumero(numero)).toBe('');
  });

  it('los espacios de los extremos no cuentan y se quitan', () => {
    expect(validarNumero('  OSE-000001  ')).toBe('');
    expect(normalizarNumero('  OSE-000001  ')).toBe('OSE-000001');
  });

  it.each([
    ['con un espacio en el medio', 'OSE 0001'],
    ['con #', 'OSE-0001#'],
    ['de 31 caracteres', 'A'.repeat(31)],
    ['vacío', '   '],
  ])('%s no es válido', (_caso, numero) => {
    expect(validarNumero(numero)).toBe(MENSAJE_NUMERO_INVALIDO);
  });

  it('el mensaje no repite el número escrito', () => {
    expect(MENSAJE_NUMERO_INVALIDO).toBe('Usá de 1 a 30 letras, números, guiones, barras o puntos.');
    expect(validarNumero('OSE 0001#')).not.toContain('OSE');
  });
});

describe('Fechas de la afiliación', () => {
  it('"Vigente desde" es obligatorio', () => {
    expect(validarDesde('')).toBe('Completá la fecha de inicio.');
    expect(validarDesde('2026-01-01')).toBe('');
  });

  it.each([
    ['igual al inicio', '2026-06-01', ''],
    ['posterior al inicio', '2026-06-02', ''],
    ['sin fecha de fin', '', ''],
    ['anterior al inicio', '2026-05-31', MENSAJE_FIN_ANTERIOR],
  ])('fin %s', (_caso, hasta, esperado) => {
    expect(validarFin('2026-06-01', hasta)).toBe(esperado);
  });

  it('sin inicio no se compara el fin', () => {
    expect(validarFin('', '2026-05-31')).toBe('');
  });

  it('el mensaje del fin anterior', () => {
    expect(MENSAJE_FIN_ANTERIOR).toBe('Tiene que ser igual o posterior a la fecha de inicio.');
  });
});

describe('Largos del catálogo', () => {
  it.each([
    ['nombre de obra social de 120', () => validarNombreObraSocial('A'.repeat(120)), ''],
    ['nombre de obra social de 121', () => validarNombreObraSocial('A'.repeat(121)), 'El nombre puede tener hasta 120 caracteres.'],
    ['nombre de obra social vacío', () => validarNombreObraSocial('  '), 'Completá el nombre.'],
    ['sigla de 20', () => validarSigla('A'.repeat(20)), ''],
    ['sigla vacía', () => validarSigla(''), ''],
    ['sigla de 21', () => validarSigla('A'.repeat(21)), 'La sigla puede tener hasta 20 caracteres.'],
    ['nombre de plan de 80', () => validarNombrePlan('A'.repeat(80)), ''],
    ['nombre de plan de 81', () => validarNombrePlan('A'.repeat(81)), 'El nombre puede tener hasta 80 caracteres.'],
    ['nombre de plan vacío', () => validarNombrePlan(''), 'Completá el nombre.'],
    ['código de 20', () => validarCodigo('N'.repeat(20)), ''],
    ['código de 21', () => validarCodigo('N'.repeat(21)), 'El código puede tener hasta 20 caracteres.'],
    ['código vacío', () => validarCodigo(' '), 'Completá el código.'],
    ['descripción de 200', () => validarDescripcion('D'.repeat(200)), ''],
    ['descripción de 201', () => validarDescripcion('D'.repeat(201)), 'La descripción puede tener hasta 200 caracteres.'],
    ['descripción vacía', () => validarDescripcion(''), 'Completá la descripción.'],
  ])('%s', (_caso, validar, esperado) => {
    expect(validar()).toBe(esperado);
  });

  it('los espacios de los extremos no cuentan para el largo', () => {
    expect(validarNombrePlan('  ' + 'A'.repeat(80) + '  ')).toBe('');
  });
});

describe('Estado de una afiliación', () => {
  it('"Vigente" cuando la API lo dice', () => {
    expect(estadoAfiliacion({ vigente: true, vigenteDesde: '2026-01-01' }, HOY)).toBe('Vigente');
  });

  it('"Próxima" cuando empieza después de hoy', () => {
    expect(estadoAfiliacion({ vigente: false, vigenteDesde: '2026-12-01' }, HOY)).toBe('Próxima');
  });

  it('"Finalizada" cuando ya terminó', () => {
    expect(estadoAfiliacion({ vigente: false, vigenteDesde: '2026-01-01' }, HOY)).toBe('Finalizada');
  });

  it('empezar hoy sin ser vigente no es "Próxima"', () => {
    expect(estadoAfiliacion({ vigente: false, vigenteDesde: HOY }, HOY)).toBe('Finalizada');
  });
});

describe('Textos de vigencia', () => {
  it('formatea las fechas como DD/MM/AAAA', () => {
    expect(formatearFecha('2026-01-01')).toBe('01/01/2026');
    expect(formatearFecha('2026-10-31')).toBe('31/10/2026');
  });

  it('sin fecha de fin', () => {
    expect(textoVigencia('2026-01-01', null)).toBe('Desde 01/01/2026, sin fecha de fin');
  });

  it('con fecha de fin', () => {
    expect(textoVigencia('2026-01-01', '2026-12-31')).toBe('Del 01/01/2026 al 31/12/2026');
  });
});

describe('"Vigente desde" por defecto', () => {
  it('sin afiliaciones es hoy', () => {
    expect(vigenteDesdePorDefecto([], HOY)).toBe(HOY);
  });

  it('con una afiliación cerrada antes de hoy sigue siendo hoy', () => {
    expect(vigenteDesdePorDefecto([{ vigenteHasta: '2026-10-31' }], HOY)).toBe(HOY);
  });

  it('con un cierre posterior a hoy es el día siguiente al cierre más reciente', () => {
    expect(
      vigenteDesdePorDefecto([{ vigenteHasta: '2026-11-30' }, { vigenteHasta: null }, { vigenteHasta: '2025-12-31' }], HOY),
    ).toBe('2026-12-01');
  });

  it('el cierre de hoy propone mañana', () => {
    expect(vigenteDesdePorDefecto([{ vigenteHasta: HOY }], HOY)).toBe('2026-11-25');
  });
});
