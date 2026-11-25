import { describe, expect, it } from 'vitest';
import {
  afiliar,
  cambiarEstadoCodigo,
  cambiarEstadoObraSocial,
  cambiarEstadoPlan,
  cerrarAfiliacion,
  corregirAfiliacion,
  guardarCodigo,
  guardarObraSocial,
  guardarPlan,
  leerAfiliaciones,
  leerCobertura,
  leerNomenclador,
  leerObrasSociales,
  listarAfiliaciones,
  listarNomenclador,
  listarObrasSociales,
  obtenerCobertura,
  obtenerMiCobertura,
} from '../src/coberturas/api.ts';
import { json, ServidorFalso } from './apoyo.ts';
import { AFILIACION_DOS, AFILIACION_UNO, COBERTURA_UNO, NOMENCLADOR, OBRAS_SOCIALES } from './apoyo-coberturas.ts';

/** C-49, tarea 1.4 (design D4): pedidos de las coberturas (API de C-18) y lectura defensiva. Datos ficticios. */

describe('Lectura defensiva del catálogo', () => {
  it('lee las obras sociales con sus planes en el orden de la API', () => {
    expect(leerObrasSociales(OBRAS_SOCIALES)).toEqual([
      {
        id: 1,
        nombre: 'Obra Social Ejemplo',
        sigla: 'OSE',
        tipo: 'OBRA_SOCIAL',
        activa: true,
        planes: [
          { id: 11, nombre: 'Plan Base', activo: true },
          { id: 12, nombre: 'Plan Integral', activo: false },
        ],
      },
      { id: 2, nombre: 'Prepaga Prueba', sigla: null, tipo: 'PREPAGA', activa: true, planes: [{ id: 21, nombre: 'Plan Oro', activo: true }] },
    ]);
  });

  it('descarta las obras sociales y los planes con datos de otro tipo', () => {
    const cuerpo = [
      { id: '1', nombre: 'Sin id numérico', tipo: 'OBRA_SOCIAL', activa: true, planes: [] },
      { id: 3, nombre: 'Tipo raro', tipo: 'OTRO', activa: true, planes: [] },
      { id: 4, nombre: 'Con un plan roto', sigla: 7, tipo: 'PREPAGA', activa: false, planes: [{ id: 5 }, null, { id: 6, nombre: 'Plan Seis', activo: true }] },
    ];
    expect(leerObrasSociales(cuerpo)).toEqual([
      { id: 4, nombre: 'Con un plan roto', sigla: null, tipo: 'PREPAGA', activa: false, planes: [{ id: 6, nombre: 'Plan Seis', activo: true }] },
    ]);
    expect(leerObrasSociales({ no: 'es una lista' })).toEqual([]);
  });

  it('lee el nomenclador con la prestación vinculada o null', () => {
    expect(leerNomenclador(NOMENCLADOR)).toEqual([
      { id: 101, codigo: 'N-0101', descripcion: 'Consulta', prestacion: { id: 1, nombre: 'Consulta o control' }, activo: true },
      { id: 102, codigo: 'N-0201', descripcion: 'Limpieza', prestacion: { id: 2, nombre: 'Limpieza (profilaxis)' }, activo: true },
      { id: 103, codigo: 'N-0301', descripcion: 'Extracción simple', prestacion: null, activo: false },
    ]);
    expect(leerNomenclador([{ id: 1, codigo: 'X' }, 'roto'])).toEqual([]);
  });
});

describe('Lectura defensiva de las afiliaciones', () => {
  it('lee la cobertura vigente', () => {
    expect(leerCobertura(COBERTURA_UNO)).toEqual({
      particular: false,
      afiliacion: {
        id: 31,
        obraSocial: { nombre: 'Obra Social Ejemplo', sigla: 'OSE' },
        plan: { id: 11, nombre: 'Plan Base' },
        numeroAfiliado: 'OSE-000001',
        vigenteDesde: '2026-01-01',
        vigenteHasta: null,
        vigente: true,
      },
    });
  });

  it('una respuesta sin afiliación legible es particular', () => {
    expect(leerCobertura({ particular: true, afiliacion: null })).toEqual({ particular: true, afiliacion: null });
    expect(leerCobertura({ particular: false, afiliacion: { id: 'x' } })).toEqual({ particular: true, afiliacion: null });
    expect(leerCobertura(undefined)).toEqual({ particular: true, afiliacion: null });
  });

  it('lee el historial en el orden de la API y descarta lo ilegible', () => {
    const historial = leerAfiliaciones([AFILIACION_DOS, { id: 9, plan: null }, AFILIACION_UNO]);
    expect(historial.map((a) => a.id)).toEqual([32, 31]);
    expect(historial[0]?.vigenteHasta).toBe('2026-10-31');
    expect(historial[0]?.obraSocial.sigla).toBeNull();
  });
});

describe('Pedidos del catálogo', () => {
  it('lista obras sociales y nomenclador', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /obras-sociales', () => json(200, OBRAS_SOCIALES));
    servidor.responder('GET /nomenclador', () => json(200, NOMENCLADOR));

    expect(await listarObrasSociales(servidor.api)).toHaveLength(2);
    expect(await listarNomenclador(servidor.api)).toHaveLength(3);
  });

  it('alta de obra social con la sigla vacía manda null', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /obras-sociales', () => json(201, {}));

    await guardarObraSocial(servidor.api, null, { nombre: 'Prepaga Prueba', sigla: '', tipo: 'PREPAGA' });

    expect(servidor.ultimo('POST', '/obras-sociales').cuerpo).toEqual({ nombre: 'Prepaga Prueba', sigla: null, tipo: 'PREPAGA' });
  });

  it('edición de obra social con sigla', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('PUT /obras-sociales/{id}', () => json(200, {}));

    await guardarObraSocial(servidor.api, 2, { nombre: 'Prepaga Prueba', sigla: 'PP', tipo: 'PREPAGA' });

    expect(servidor.ultimo('PUT', '/obras-sociales/2').cuerpo).toEqual({ nombre: 'Prepaga Prueba', sigla: 'PP', tipo: 'PREPAGA' });
  });

  it('alta y edición de planes', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /obras-sociales/{id}/planes', () => json(201, {}));
    servidor.responder('PUT /obras-sociales/{id}/planes/{id}', () => json(200, {}));

    await guardarPlan(servidor.api, 2, null, 'Plan Plata');
    await guardarPlan(servidor.api, 1, 11, 'Plan Base 2027');

    expect(servidor.ultimo('POST', '/obras-sociales/2/planes').cuerpo).toEqual({ nombre: 'Plan Plata' });
    expect(servidor.ultimo('PUT', '/obras-sociales/1/planes/11').cuerpo).toEqual({ nombre: 'Plan Base 2027' });
  });

  it('códigos con y sin prestación', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /nomenclador', () => json(201, {}));
    servidor.responder('PUT /nomenclador/{id}', () => json(200, {}));

    await guardarCodigo(servidor.api, null, { codigo: 'N-0401', descripcion: 'Tratamiento de conducto', prestacionId: 3 });
    await guardarCodigo(servidor.api, 101, { codigo: 'N-0101', descripcion: 'Consulta', prestacionId: null });

    expect(servidor.ultimo('POST', '/nomenclador').cuerpo).toEqual({ codigo: 'N-0401', descripcion: 'Tratamiento de conducto', prestacionId: 3 });
    expect(servidor.ultimo('PUT', '/nomenclador/101').cuerpo).toEqual({ codigo: 'N-0101', descripcion: 'Consulta', prestacionId: null });
  });

  it('activar y desactivar van a sus rutas', async () => {
    const servidor = new ServidorFalso();
    for (const clave of [
      'POST /obras-sociales/{id}/desactivar',
      'POST /obras-sociales/{id}/planes/{id}/activar',
      'POST /nomenclador/{id}/activar',
    ]) {
      servidor.responder(clave, () => json(200, {}));
    }

    await cambiarEstadoObraSocial(servidor.api, 1, false);
    await cambiarEstadoPlan(servidor.api, 1, 12, true);
    await cambiarEstadoCodigo(servidor.api, 103, true);

    expect(servidor.pedidos.map((p) => `${p.metodo} ${p.ruta}`)).toEqual([
      'POST /obras-sociales/1/desactivar',
      'POST /obras-sociales/1/planes/12/activar',
      'POST /nomenclador/103/activar',
    ]);
  });
});

describe('Pedidos de las afiliaciones', () => {
  it('cobertura e historial de un paciente', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /pacientes/1/cobertura', () => json(200, COBERTURA_UNO));
    servidor.responder('GET /pacientes/1/afiliaciones', () => json(200, [AFILIACION_UNO]));

    expect((await obtenerCobertura(servidor.api, 1)).afiliacion?.numeroAfiliado).toBe('OSE-000001');
    expect(await listarAfiliaciones(servidor.api, 1)).toHaveLength(1);
  });

  it('afiliar manda vigenteHasta null si no hay fin', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/{id}/afiliaciones', () => json(201, {}));

    await afiliar(servidor.api, 2, { planId: 11, numeroAfiliado: 'OSE-000002', vigenteDesde: '2026-11-24', vigenteHasta: null });

    expect(servidor.ultimo('POST', '/pacientes/2/afiliaciones').cuerpo).toEqual({
      planId: 11,
      numeroAfiliado: 'OSE-000002',
      vigenteDesde: '2026-11-24',
      vigenteHasta: null,
    });
  });

  it('corregir y cerrar', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('PUT /pacientes/{id}/afiliaciones/{id}', () => json(200, {}));
    servidor.responder('POST /pacientes/{id}/afiliaciones/{id}/cerrar', () => json(200, {}));

    await corregirAfiliacion(servidor.api, 1, 31, { planId: 11, numeroAfiliado: 'OSE-000010', vigenteDesde: '2026-01-01' });
    await cerrarAfiliacion(servidor.api, 1, 31, '2026-11-30');

    expect(servidor.ultimo('PUT', '/pacientes/1/afiliaciones/31').cuerpo).toEqual({
      planId: 11,
      numeroAfiliado: 'OSE-000010',
      vigenteDesde: '2026-01-01',
    });
    expect(servidor.ultimo('POST', '/pacientes/1/afiliaciones/31/cerrar').cuerpo).toEqual({ vigenteHasta: '2026-11-30' });
  });

  it('"Mi cobertura" no manda ids ni parámetros', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /paciente/cobertura', () => json(200, COBERTURA_UNO));

    const cobertura = await obtenerMiCobertura(servidor.api);

    const pedido = servidor.ultimo('GET', '/paciente/cobertura');
    expect([...pedido.query.keys()]).toEqual([]);
    expect(cobertura.afiliacion?.plan.nombre).toBe('Plan Base');
  });
});
