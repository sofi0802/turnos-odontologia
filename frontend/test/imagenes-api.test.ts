import { describe, expect, it } from 'vitest';
import {
  anularImagen,
  cargarImagen,
  descargarArchivo,
  leerImagen,
  leerImagenes,
  listarImagenes,
} from '../src/ficha/imagenes-api.ts';
import { ErrorNegocio, ErrorValidacion } from '../src/shared/api/errores.ts';
import { json, ServidorFalso } from './apoyo.ts';

/** C-47, tarea 3.2: pedidos de las imágenes (rutas de C-26) y lectura defensiva. Datos ficticios. */

const IMAGEN = {
  id: 5,
  categoria: 'RADIOGRAFIA',
  pieza: 36,
  tomadaEl: '2026-11-30',
  descripcion: 'Control',
  tipoContenido: 'image/jpeg',
  tamanoBytes: 319_488,
  sha256: 'a'.repeat(64),
  profesionalId: 1,
  profesional: 'Ana Ejemplo',
  registradaEn: '2026-12-01T09:00:00-03:00',
  anulacion: null,
};

describe('Lectura defensiva del listado', () => {
  it('mantiene el orden de la API y lee cada campo', () => {
    const otra = { ...IMAGEN, id: 4, categoria: 'ESTUDIO', pieza: null, descripcion: null, tomadaEl: '2026-10-01' };
    expect(leerImagenes([IMAGEN, otra])).toEqual([
      {
        id: 5,
        categoria: 'RADIOGRAFIA',
        pieza: 36,
        tomadaEl: '2026-11-30',
        descripcion: 'Control',
        tipoContenido: 'image/jpeg',
        tamanoBytes: 319_488,
        profesional: 'Ana Ejemplo',
        anulacion: null,
      },
      {
        id: 4,
        categoria: 'ESTUDIO',
        pieza: null,
        tomadaEl: '2026-10-01',
        descripcion: '',
        tipoContenido: 'image/jpeg',
        tamanoBytes: 319_488,
        profesional: 'Ana Ejemplo',
        anulacion: null,
      },
    ]);
  });

  it('descarta lo que no sirve: id de otro tipo, categoría desconocida, fecha rota o basura', () => {
    const lista = leerImagenes([
      { ...IMAGEN, id: 'x' },
      { ...IMAGEN, id: 6, categoria: 'TOMOGRAFIA' },
      { ...IMAGEN, id: 7, tomadaEl: '30/11/2026' },
      { ...IMAGEN, id: 8, tipoContenido: 3 },
      null,
      'texto',
      IMAGEN,
    ]);
    expect(lista.map((i) => i.id)).toEqual([5]);
  });

  it('una respuesta que no es una lista se lee como vacía', () => {
    expect(leerImagenes({ id: 1 })).toEqual([]);
    expect(leerImagenes(undefined)).toEqual([]);
  });

  it('lee la anulación con quién, cuándo y el motivo', () => {
    const anulada = {
      ...IMAGEN,
      anulacion: { profesionalId: 2, profesional: 'Bruno Prueba', anuladaEn: '2026-11-30T17:20:00-03:00', motivo: 'Duplicada' },
    };
    expect(leerImagen(anulada)?.anulacion).toEqual({
      profesional: 'Bruno Prueba',
      anuladaEn: '2026-11-30T17:20:00-03:00',
      motivo: 'Duplicada',
    });
  });

  it('una anulación sin fecha o sin motivo de tipo texto se descarta y la imagen sigue siendo anulada', () => {
    const rota = leerImagen({ ...IMAGEN, anulacion: { profesional: 'Bruno Prueba', anuladaEn: 5, motivo: null } });
    expect(rota?.anulacion).toEqual({ profesional: 'Bruno Prueba', anuladaEn: '', motivo: '' });
    expect(leerImagen({ ...IMAGEN, anulacion: 'x' })?.anulacion).toBeNull();
  });
});

describe('Pedidos de las imágenes', () => {
  it('lista con la ruta y sin parámetros por defecto', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /pacientes/1/imagenes', () => json(200, [IMAGEN]));

    const lista = await listarImagenes(servidor.api, 1, {});

    expect(lista).toHaveLength(1);
    const pedido = servidor.ultimo('GET', '/pacientes/1/imagenes');
    expect(pedido.query.has('pieza')).toBe(false);
    expect(pedido.query.has('incluirAnuladas')).toBe(false);
  });

  it('lista con pieza e incluirAnuladas', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /pacientes/1/imagenes', () => json(200, []));

    await listarImagenes(servidor.api, 1, { pieza: 36, incluirAnuladas: true });

    const pedido = servidor.ultimo('GET', '/pacientes/1/imagenes');
    expect(pedido.query.get('pieza')).toBe('36');
    expect(pedido.query.get('incluirAnuladas')).toBe('true');
  });

  it('pide el archivo para ver y para descargar', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /pacientes/1/imagenes/5/archivo', () => new Response(new Uint8Array([1, 2, 3]), {
      status: 200,
      headers: { 'Content-Type': 'image/jpeg' },
    }));

    const visto = await descargarArchivo(servidor.api, 1, 5, false);
    expect(visto.type).toBe('image/jpeg');
    expect(servidor.ultimo('GET', '/pacientes/1/imagenes/5/archivo').query.has('descarga')).toBe(false);

    await descargarArchivo(servidor.api, 1, 5, true);
    expect(servidor.pedidos.at(-1)?.query.get('descarga')).toBe('true');
  });

  it('carga con las partes archivo, categoria, tomadaEl, pieza y descripcion', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/1/imagenes', () => json(201, IMAGEN));
    const archivo = new File([new Uint8Array([1, 2, 3])], 'rx.jpg', { type: 'image/jpeg' });

    const creada = await cargarImagen(servidor.api, 1, {
      archivo,
      categoria: 'RADIOGRAFIA',
      tomadaEl: '2026-11-30',
      pieza: 36,
      descripcion: 'Control',
    });

    const formulario = servidor.ultimo('POST', '/pacientes/1/imagenes').formulario;
    expect(formulario).toBeDefined();
    expect((formulario?.get('archivo') as File).name).toBe('rx.jpg');
    expect(formulario?.get('categoria')).toBe('RADIOGRAFIA');
    expect(formulario?.get('tomadaEl')).toBe('2026-11-30');
    expect(formulario?.get('pieza')).toBe('36');
    expect(formulario?.get('descripcion')).toBe('Control');
    expect(creada?.id).toBe(5);
  });

  it('la pieza y la descripción vacías no se mandan', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/1/imagenes', () => json(201, { ...IMAGEN, pieza: null }));

    await cargarImagen(servidor.api, 1, {
      archivo: new File([new Uint8Array([1])], 'e.pdf', { type: 'application/pdf' }),
      categoria: 'ESTUDIO',
      tomadaEl: '2026-12-01',
      pieza: null,
      descripcion: '',
    });

    const formulario = servidor.ultimo('POST', '/pacientes/1/imagenes').formulario;
    expect(formulario?.has('pieza')).toBe(false);
    expect(formulario?.has('descripcion')).toBe(false);
    expect(formulario?.get('categoria')).toBe('ESTUDIO');
  });

  it('un rechazo de la carga llega como error de validación con su código', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/1/imagenes', () => json(422, { codigo: 'TIPO_ARCHIVO_NO_PERMITIDO', detail: 'x' }));

    await expect(
      cargarImagen(servidor.api, 1, {
        archivo: new File([new Uint8Array([1])], 'a.gif', { type: 'image/gif' }),
        categoria: 'FOTOGRAFIA',
        tomadaEl: '2026-12-01',
        pieza: null,
        descripcion: '',
      }),
    ).rejects.toBeInstanceOf(ErrorValidacion);
  });

  it('anula con el motivo y lee la imagen anulada', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/1/imagenes/5/anulacion', () =>
      json(200, { ...IMAGEN, anulacion: { profesional: 'Ana Ejemplo', anuladaEn: '2026-12-01T10:00:00-03:00', motivo: 'Duplicada' } }),
    );

    const anulada = await anularImagen(servidor.api, 1, 5, 'Duplicada');

    expect(servidor.ultimo('POST', '/pacientes/1/imagenes/5/anulacion').cuerpo).toEqual({ motivo: 'Duplicada' });
    expect(anulada?.anulacion?.motivo).toBe('Duplicada');
  });

  it('una anulación sin cuerpo útil no falla', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/1/imagenes/5/anulacion', () => json(200, { raro: true }));
    expect(await anularImagen(servidor.api, 1, 5, 'Duplicada')).toBeNull();
  });

  it('el 409 IMAGEN_YA_ANULADA llega como error de negocio', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/1/imagenes/5/anulacion', () => json(409, { codigo: 'IMAGEN_YA_ANULADA' }));
    await expect(anularImagen(servidor.api, 1, 5, 'x')).rejects.toMatchObject({
      constructor: ErrorNegocio,
      codigo: 'IMAGEN_YA_ANULADA',
    });
  });
});
