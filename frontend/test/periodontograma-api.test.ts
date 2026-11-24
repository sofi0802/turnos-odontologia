import { describe, expect, it } from 'vitest';
import {
  leerResumenes,
  leerVersion,
  listarVersiones,
  obtenerVersion,
  registrarVersion,
} from '../src/ficha/periodontograma-api.ts';
import { json, ServidorFalso } from './apoyo.ts';

/** C-46: pedidos del periodontograma (rutas de C-25) y lectura defensiva (design D1). Datos ficticios. */

const SITIOS = ['DL', 'MV', 'V', 'DV', 'ML', 'L'].map((sitio, i) => ({
  sitio,
  profundidadSondaje: 2 + (i % 2),
  recesion: -1,
  sangrado: i === 0,
  placa: false,
}));

const VERSION = {
  id: 9,
  profesionalId: 2,
  profesional: 'Bruno Prueba',
  registradoEn: '2026-12-01T09:00:00-03:00',
  piezas: [
    { pieza: 16, ausente: false, movilidad: 1, furcacion: 1, estado: 'MODERADO', sitios: SITIOS },
    { pieza: 18, ausente: true, movilidad: null, furcacion: null, estado: null, sitios: [] },
  ],
};

describe('Lectura defensiva de la lista', () => {
  it('ordena de la más nueva a la más vieja y descarta lo que no sirve', () => {
    const lista = leerResumenes([
      { id: 4, profesional: 'Ana Ejemplo', registradoEn: '2026-11-20T10:30:00-03:00' },
      { id: 9, profesional: 'Bruno Prueba', registradoEn: '2026-12-01T09:00:00-03:00' },
      { id: 'x', profesional: 'Nadie', registradoEn: '2026-12-01T09:00:00-03:00' },
      { id: 5 },
      null,
    ]);
    expect(lista).toEqual([
      { id: 9, profesional: 'Bruno Prueba', registradoEn: '2026-12-01T09:00:00-03:00' },
      { id: 4, profesional: 'Ana Ejemplo', registradoEn: '2026-11-20T10:30:00-03:00' },
    ]);
  });

  it('una respuesta que no es una lista se lee como vacía', () => {
    expect(leerResumenes({ id: 1 })).toEqual([]);
    expect(leerResumenes(undefined)).toEqual([]);
  });
});

describe('Lectura defensiva de la versión', () => {
  it('ordena los sitios y conserva la pieza ausente', () => {
    const version = leerVersion(VERSION);
    expect(version?.id).toBe(9);
    expect(version?.piezas.map((p) => p.pieza)).toEqual([16, 18]);
    expect(version?.piezas[0]?.sitios.map((s) => s.sitio)).toEqual(['MV', 'V', 'DV', 'ML', 'L', 'DL']);
    expect(version?.piezas[0]?.estado).toBe('MODERADO');
    expect(version?.piezas[1]).toEqual({ pieza: 18, ausente: true, movilidad: null, furcacion: null, estado: null, sitios: [] });
  });

  it('descarta un estado, un sitio o una pieza desconocidos', () => {
    const version = leerVersion({
      ...VERSION,
      piezas: [
        { ...VERSION.piezas[0], estado: 'GRAVE', sitios: [...SITIOS, { sitio: 'X', profundidadSondaje: 1, recesion: 0, sangrado: false, placa: false }] },
        { pieza: 'dieciséis' },
      ],
    });
    expect(version?.piezas).toHaveLength(1);
    expect(version?.piezas[0]?.estado).toBeNull();
    expect(version?.piezas[0]?.sitios).toHaveLength(6);
  });

  it('una versión sin id no se lee', () => {
    expect(leerVersion({ piezas: [] })).toBeNull();
  });
});

describe('Pedidos', () => {
  it('usa las rutas de C-25', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /pacientes/{id}/periodontograma', () => json(200, [VERSION]));
    servidor.responder('GET /pacientes/{id}/periodontograma/{id}', () => json(200, VERSION));
    servidor.responder('POST /pacientes/{id}/periodontograma', () => json(201, VERSION));

    expect(await listarVersiones(servidor.api, 1)).toHaveLength(1);
    expect((await obtenerVersion(servidor.api, 1, 9)).id).toBe(9);
    const pedido = { piezas: [{ pieza: 18, ausente: true as const }] };
    expect((await registrarVersion(servidor.api, 1, pedido)).id).toBe(9);

    expect(servidor.a('GET', '/pacientes/1/periodontograma')).toHaveLength(1);
    expect(servidor.a('GET', '/pacientes/1/periodontograma/9')).toHaveLength(1);
    expect(servidor.ultimo('POST', '/pacientes/1/periodontograma').cuerpo).toEqual(pedido);
  });

  it('una versión ilegible es un error', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /pacientes/{id}/periodontograma/{id}', () => json(200, { nada: true }));
    await expect(obtenerVersion(servidor.api, 1, 9)).rejects.toThrow();
  });
});
