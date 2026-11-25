import { describe, expect, it } from 'vitest';
import {
  dniValido,
  fechaDeFirma,
  firmaNueva,
  firmadoHoy,
  formatoContador,
  horaDeFirma,
  nombreAccesible,
  validarDni,
  validarMotivo,
  validarNombre,
  validarTexto,
  validarTitulo,
  type ConsentimientoResumen,
} from '../src/consentimientos/modelo.ts';

function resumen(datos: Partial<ConsentimientoResumen> & { id: number }): ConsentimientoResumen {
  return {
    plantillaId: 4,
    versionNumero: 3,
    titulo: 'Consentimiento para extracción dentaria',
    firmadoEn: '2026-11-24T10:30:00-03:00',
    profesionalId: 1,
    profesional: 'Ana Ejemplo',
    estado: 'VIGENTE',
    ...datos,
  };
}

describe('Validación del título (1 a 120)', () => {
  it.each([
    ['', 'Escribí el título.'],
    ['   ', 'Escribí el título.'],
    ['a', ''],
    ['a'.repeat(120), ''],
    ['a'.repeat(121), 'El título supera los 120 caracteres.'],
    [`  ${'a'.repeat(120)}  `, ''],
  ])('%j da %j', (valor, esperado) => {
    expect(validarTitulo(valor)).toBe(esperado);
  });
});

describe('Validación del texto (1 a 20.000)', () => {
  it.each([
    ['', 'Escribí el texto del consentimiento.'],
    [' \n ', 'Escribí el texto del consentimiento.'],
    ['x', ''],
    ['x'.repeat(20_000), ''],
    ['x'.repeat(20_001), 'El texto supera los 20.000 caracteres.'],
  ])('un texto de %j caracteres da %j', (valor, esperado) => {
    expect(validarTexto(valor)).toBe(esperado);
  });

  it('cuenta sobre el texto recortado y normalizado a NFC', () => {
    // "e" más el acento combinado son dos unidades; en NFC es una sola.
    const descompuesto = 'é'.repeat(20_000);
    expect(descompuesto.length).toBe(40_000);
    expect(validarTexto(descompuesto)).toBe('');
    expect(validarTexto(`${'x'.repeat(20_000)}   `)).toBe('');
  });
});

describe('Validación del nombre (3 a 200)', () => {
  it.each([
    ['', 'Escribí tu nombre completo.'],
    ['Pa', 'Escribí tu nombre completo.'],
    ['  Pa  ', 'Escribí tu nombre completo.'],
    ['Paz', ''],
    ['a'.repeat(200), ''],
    ['a'.repeat(201), 'El nombre supera los 200 caracteres.'],
  ])('%j da %j', (valor, esperado) => {
    expect(validarNombre(valor)).toBe(esperado);
  });

  it('normaliza a NFC antes de contar', () => {
    expect(validarNombre('José')).toBe('');
    expect(validarNombre('éé')).toBe('Escribí tu nombre completo.');
  });
});

describe('Validación del DNI (7 u 8 dígitos sin puntos ni espacios)', () => {
  it.each([
    ['99000001', ''],
    ['99.000.001', ''],
    ['99 000 001', ''],
    ['9900001', ''],
    ['990000', 'Escribí el DNI sin letras: 7 u 8 números.'],
    ['9900', 'Escribí el DNI sin letras: 7 u 8 números.'],
    ['990000012', 'Escribí el DNI sin letras: 7 u 8 números.'],
    ['99A00001', 'Escribí el DNI sin letras: 7 u 8 números.'],
    ['', 'Escribí el DNI sin letras: 7 u 8 números.'],
  ])('%j da %j', (valor, esperado) => {
    expect(validarDni(valor)).toBe(esperado);
    expect(dniValido(valor)).toBe(esperado === '');
  });
});

describe('Validación del motivo (1 a 200)', () => {
  it.each([
    ['', 'Escribí el motivo de la revocación.'],
    ['   ', 'Escribí el motivo de la revocación.'],
    ['x', ''],
    ['x'.repeat(200), ''],
    ['x'.repeat(201), 'El motivo supera los 200 caracteres.'],
  ])('%j da %j', (valor, esperado) => {
    expect(validarMotivo(valor)).toBe(esperado);
  });
});

describe('Contador', () => {
  it.each([
    [0, 20_000, '0/20.000'],
    [1234, 20_000, '1.234/20.000'],
    [999, 120, '999/120'],
    [20_001, 20_000, '20.001/20.000'],
    [1_234_567, 20_000, '1.234.567/20.000'],
  ])('%i de %i se ve %s', (n, limite, esperado) => {
    expect(formatoContador(n, limite)).toBe(esperado);
  });
});

describe('Nombres accesibles y fechas', () => {
  it('arma el nombre del botón con el título y la fecha de la firma', () => {
    expect(nombreAccesible('Ver', 'Consentimiento para extracción dentaria', '2026-11-24T10:30:00-03:00')).toBe(
      'Ver Consentimiento para extracción dentaria del 24/11/2026',
    );
    expect(nombreAccesible('Revocar', 'Otro', '2026-10-01T09:05:00-03:00')).toBe('Revocar Otro del 01/10/2026');
  });

  it('da la fecha y la hora en la zona del consultorio', () => {
    expect(fechaDeFirma('2026-11-24T10:30:00-03:00')).toBe('24/11/2026');
    expect(horaDeFirma('2026-11-24T10:30:00-03:00')).toBe('10:30');
    // 01:30 UTC es la noche anterior en Buenos Aires.
    expect(fechaDeFirma('2026-11-25T01:30:00Z')).toBe('24/11/2026');
    expect(horaDeFirma('2026-11-25T01:30:00Z')).toBe('22:30');
  });
});

describe('firmaNueva (comprobación de un envío dudoso, design D13)', () => {
  const intento = { plantillaId: 4, versionNumero: 3, profesionalId: 1, antes: [10, 11] };

  it('encuentra un id nuevo de la misma plantilla, versión y profesional', () => {
    const nueva = resumen({ id: 12 });
    expect(firmaNueva(intento, [resumen({ id: 10 }), resumen({ id: 11 }), nueva])).toBe(nueva);
  });

  it('no cuenta un id que ya estaba', () => {
    expect(firmaNueva(intento, [resumen({ id: 10 }), resumen({ id: 11 })])).toBeNull();
  });

  it('no cuenta otra versión ni otra plantilla', () => {
    expect(firmaNueva(intento, [resumen({ id: 12, versionNumero: 4 })])).toBeNull();
    expect(firmaNueva(intento, [resumen({ id: 12, plantillaId: 5 })])).toBeNull();
  });

  it('no cuenta la firma de otro profesional', () => {
    expect(firmaNueva(intento, [resumen({ id: 12, profesionalId: 2, profesional: 'Bruno Prueba' })])).toBeNull();
  });

  it('con una lista vacía no hay firma nueva', () => {
    expect(firmaNueva(intento, [])).toBeNull();
  });
});

describe('firmadoHoy (aviso de doble firma, design D14)', () => {
  const HOY = '2026-11-24';

  it('encuentra la firma vigente de hoy de la misma plantilla y versión', () => {
    const vigente = resumen({ id: 12 });
    expect(firmadoHoy([resumen({ id: 1, versionNumero: 2 }), vigente], 4, 3, HOY)).toBe(vigente);
  });

  it('no cuenta una revocada', () => {
    expect(firmadoHoy([resumen({ id: 12, estado: 'REVOCADO' })], 4, 3, HOY)).toBeNull();
  });

  it('no cuenta otra versión ni otra plantilla', () => {
    expect(firmadoHoy([resumen({ id: 12, versionNumero: 2 })], 4, 3, HOY)).toBeNull();
    expect(firmadoHoy([resumen({ id: 12, plantillaId: 9 })], 4, 3, HOY)).toBeNull();
  });

  it('no cuenta otro día', () => {
    expect(firmadoHoy([resumen({ id: 12, firmadoEn: '2026-11-20T10:30:00-03:00' })], 4, 3, HOY)).toBeNull();
  });

  it('usa la medianoche de Buenos Aires, no la del navegador ni la UTC', () => {
    // 00:30 del 24/11 en Buenos Aires son las 03:30 UTC del 24/11: hoy.
    expect(firmadoHoy([resumen({ id: 1, firmadoEn: '2026-11-24T00:30:00-03:00' })], 4, 3, HOY)).not.toBeNull();
    // 23:30 del 23/11 en Buenos Aires son las 02:30 UTC del 24/11: ayer.
    expect(firmadoHoy([resumen({ id: 2, firmadoEn: '2026-11-23T23:30:00-03:00' })], 4, 3, HOY)).toBeNull();
    // 22:30 del 24/11 en Buenos Aires son las 01:30 UTC del 25/11: sigue siendo hoy.
    expect(firmadoHoy([resumen({ id: 3, firmadoEn: '2026-11-25T01:30:00Z' })], 4, 3, HOY)).not.toBeNull();
  });
});
