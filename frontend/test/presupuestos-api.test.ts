import { describe, expect, it } from 'vitest';
import {
  cambiarPorcentaje,
  cambiarPrecio,
  leerAranceles,
  leerCoberturaPlan,
  listarAranceles,
  listarCoberturaPlan,
} from '../src/aranceles/api.ts';
import {
  aceptarPresupuesto,
  emitirPresupuesto,
  leerPresupuesto,
  leerPresupuestos,
  leerSugerencias,
  listarMisPresupuestos,
  listarPresupuestos,
  listarSugerencias,
  rechazarPresupuesto,
} from '../src/presupuestos/api.ts';
import { json, ServidorFalso } from './apoyo.ts';
import { ARANCELES, COBERTURA_PLAN_BASE, PRESUPUESTO_40, PRESUPUESTO_41, SUGERENCIAS, sinDatosClinicos } from './apoyo-presupuestos.ts';

/** C-50, tarea 1.3 (design D5): pedidos de presupuestos y aranceles (API de C-19) y lectura defensiva. */

describe('Lectura defensiva de los presupuestos', () => {
  it('lee el presupuesto 41 con importes en centavos', () => {
    expect(leerPresupuesto(PRESUPUESTO_41)).toEqual({
      id: 41,
      fecha: '2026-11-24',
      venceEl: '2026-12-24',
      estado: 'PENDIENTE',
      vencido: false,
      profesional: 'Dra. Ana Ejemplo',
      particular: false,
      cobertura: { obraSocial: 'Obra Social Ejemplo', plan: 'Plan Base', numeroAfiliado: 'OSE-000001' },
      items: [
        { id: 1, prestacion: 'Consulta o control', codigo: 'N-0101', pieza: null, cara: null, cantidad: 1, precioUnitario: 1_500_000, porcentaje: 100, importe: 1_500_000, cobertura: 1_500_000, copago: 0 },
        { id: 2, prestacion: 'Limpieza (profilaxis)', codigo: 'N-0201', pieza: null, cara: null, cantidad: 1, precioUnitario: 2_500_000, porcentaje: 50, importe: 2_500_000, cobertura: 1_250_000, copago: 1_250_000 },
      ],
      totales: { precio: 4_000_000, cobertura: 2_750_000, copago: 1_250_000 },
    });
  });

  it('lee la pieza del 40 y el vencido', () => {
    const leido = leerPresupuesto(PRESUPUESTO_40);
    expect(leido?.vencido).toBe(true);
    expect(leido?.items[0]?.pieza).toBe(36);
  });

  it('un ítem sin pieza ni cara (recepción) se lee con null', () => {
    const leido = leerPresupuesto(sinDatosClinicos(PRESUPUESTO_40));
    expect(leido?.items[0]?.pieza).toBeNull();
    expect(leido?.items[0]?.cara).toBeNull();
  });

  it('un particular no tiene cobertura', () => {
    expect(leerPresupuesto({ ...PRESUPUESTO_41, particular: true, cobertura: null })?.cobertura).toBeNull();
  });

  it('descarta lo ilegible: estado desconocido, importes que no son números y cara inválida', () => {
    expect(leerPresupuesto({ ...PRESUPUESTO_41, estado: 'BORRADOR' })).toBeNull();
    expect(leerPresupuesto({ ...PRESUPUESTO_41, totales: { precio: '40000' } })).toBeNull();
    const conCaraRara = { ...PRESUPUESTO_41, items: [{ ...PRESUPUESTO_41.items[0], cara: 'X' }] };
    expect(leerPresupuesto(conCaraRara)?.items[0]?.cara).toBeNull();
    expect(leerPresupuestos([PRESUPUESTO_41, null, 'roto', PRESUPUESTO_40]).map((p) => p.id)).toEqual([41, 40]);
    expect(leerPresupuestos({})).toEqual([]);
  });

  it('lee las sugerencias en el orden de la API', () => {
    expect(leerSugerencias([...SUGERENCIAS, { pieza: 'x' }])).toEqual([
      { pieza: 16, cara: 'O', estado: 'CARIES' },
      { pieza: 36, cara: null, estado: 'A_TRATAR' },
    ]);
  });
});

describe('Lectura defensiva de aranceles y cobertura', () => {
  it('lee el precio en centavos o null', () => {
    expect(leerAranceles([...ARANCELES, { prestacion: { id: 5, nombre: 'Nueva' }, activa: false, precio: null }])).toEqual([
      { prestacionId: 1, nombre: 'Consulta o control', activa: true, precio: 1_500_000 },
      { prestacionId: 2, nombre: 'Limpieza (profilaxis)', activa: true, precio: 2_500_000 },
      { prestacionId: 3, nombre: 'Tratamiento de conducto', activa: true, precio: 9_000_000 },
      { prestacionId: 4, nombre: 'Extracción simple', activa: true, precio: 3_000_000 },
      { prestacionId: 5, nombre: 'Nueva', activa: false, precio: null },
    ]);
  });

  it('lee la cobertura de un plan por código', () => {
    expect(leerCoberturaPlan([...COBERTURA_PLAN_BASE, { codigo: 'roto' }])).toEqual([
      { nomencladorId: 101, porcentaje: 100 },
      { nomencladorId: 102, porcentaje: 50 },
    ]);
  });
});

describe('Pedidos de los presupuestos', () => {
  it('lista los de un paciente, las sugerencias y los del paciente con sesión sin parámetros', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /pacientes/1/presupuestos', () => json(200, [PRESUPUESTO_41]));
    servidor.responder('GET /pacientes/1/presupuestos/sugerencias', () => json(200, SUGERENCIAS));
    servidor.responder('GET /paciente/presupuestos', () => json(200, [PRESUPUESTO_40]));

    expect(await listarPresupuestos(servidor.api, 1)).toHaveLength(1);
    expect(await listarSugerencias(servidor.api, 1)).toHaveLength(2);
    expect((await listarMisPresupuestos(servidor.api))[0]?.id).toBe(40);
    expect([...servidor.ultimo('GET', '/paciente/presupuestos').query.keys()]).toEqual([]);
  });

  it('emite con el cuerpo armado y devuelve el presupuesto de la respuesta', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/{id}/presupuestos', () => json(201, PRESUPUESTO_41));
    const cuerpo = { items: [{ prestacionId: 1, nomencladorId: 101, pieza: null, cara: null, cantidad: 1 }] };

    const emitido = await emitirPresupuesto(servidor.api, 1, cuerpo);

    expect(servidor.ultimo('POST', '/pacientes/1/presupuestos').cuerpo).toEqual(cuerpo);
    expect(emitido?.id).toBe(41);
  });

  it('acepta y rechaza en sus rutas', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/{id}/presupuestos/{id}/aceptar', () => json(200, { ...PRESUPUESTO_41, estado: 'ACEPTADO' }));
    servidor.responder('POST /pacientes/{id}/presupuestos/{id}/rechazar', () => json(200, { ...PRESUPUESTO_40, estado: 'RECHAZADO' }));

    await aceptarPresupuesto(servidor.api, 1, 41);
    await rechazarPresupuesto(servidor.api, 1, 40);

    expect(servidor.pedidos.map((p) => `${p.metodo} ${p.ruta}`)).toEqual([
      'POST /pacientes/1/presupuestos/41/aceptar',
      'POST /pacientes/1/presupuestos/40/rechazar',
    ]);
  });
});

describe('Pedidos de aranceles y cobertura', () => {
  it('lista y cambia el precio en pesos con dos decimales', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /aranceles', () => json(200, ARANCELES));
    servidor.responder('PUT /aranceles/{id}', () => json(200, {}));

    expect(await listarAranceles(servidor.api)).toHaveLength(4);
    await cambiarPrecio(servidor.api, 2, 2_700_050);

    expect(servidor.ultimo('PUT', '/aranceles/2').cuerpo).toEqual({ precio: 27000.5 });
  });

  it('lista y cambia el porcentaje de un código de un plan', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /obras-sociales/1/planes/11/coberturas', () => json(200, COBERTURA_PLAN_BASE));
    servidor.responder('PUT /obras-sociales/{id}/planes/{id}/coberturas/{id}', () => json(200, {}));

    expect(await listarCoberturaPlan(servidor.api, 1, 11)).toHaveLength(2);
    await cambiarPorcentaje(servidor.api, 1, 11, 102, 60);

    expect(servidor.ultimo('PUT', '/obras-sociales/1/planes/11/coberturas/102').cuerpo).toEqual({ porcentaje: 60 });
  });
});
