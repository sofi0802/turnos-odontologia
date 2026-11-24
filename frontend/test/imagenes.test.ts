import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { boton, escribir, json, texto } from './apoyo.ts';
import {
  ANULADA,
  ESTUDIO_PDF,
  FOTO11,
  imagen,
  prepararEntorno,
  RX36,
  SESION_ANA,
  type Entorno,
} from './apoyo-imagenes.ts';

/**
 * C-47 frontend-imagenes-y-radiografias: un test por escenario del delta `frontend-imagenes-clinicas` (listado,
 * filtros, visor y descarga). Datos ficticios del seed: "Paciente Uno" (id 1), `ana.ejemplo`, `bruno.prueba`, `admin`.
 */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
});

afterEach(() => {
  entorno.cerrar();
});

function filas(): HTMLElement[] {
  return [...entorno.seccion().querySelectorAll<HTMLElement>('.img-fila')];
}

describe('Sección de imágenes en la ficha', () => {
  it('La sección aparece en la ficha', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const seccion = entorno.seccion();
    expect(seccion.querySelector('h2')?.textContent).toBe('Imágenes y estudios');
    expect(seccion.getAttribute('aria-labelledby')).toBe(seccion.querySelector('h2')?.id);
    // Debajo del periodontograma y arriba del resto de la ficha.
    expect(entorno.main().querySelector('.perio-seccion')?.nextElementSibling).toBe(seccion);
    expect(seccion.nextElementSibling?.classList.contains('ficha-columnas')).toBe(true);
    expect(entorno.servidor.a('GET', '/pacientes/1/imagenes')).toHaveLength(1);
  });

  it('Mientras carga dice "Cargando imágenes…"', async () => {
    let terminar: (r: Response) => void = () => undefined;
    entorno.servidor.responder('GET /pacientes/{id}/imagenes', () => new Promise<Response>((resolver) => (terminar = resolver)));

    await entorno.abrirFicha();

    expect(texto(entorno.seccion())).toContain('Cargando imágenes…');
    expect(entorno.seccion().querySelector('.cargando')?.getAttribute('role')).toBe('status');
    terminar(json(200, []));
    await entorno.seccionLista();
    expect(texto(entorno.seccion())).not.toContain('Cargando imágenes…');
  });

  it('Falla al cargar las imágenes', async () => {
    entorno.servidor.responder('GET /pacientes/{id}/imagenes', () => json(500, { detail: 'traza interna' }));

    await entorno.abrirFicha();
    await entorno.seccionLista();

    const seccion = entorno.seccion();
    expect(seccion.querySelector('[role="alert"]')?.textContent).toBe(
      'No pudimos cargar las imágenes. Revisá la conexión e intentá de nuevo.',
    );
    expect(texto(seccion)).not.toContain('traza interna');
    expect(boton(seccion, 'Reintentar')).toBeDefined();
    expect(entorno.main().querySelector('.odontograma')).not.toBeNull();
    expect(entorno.main().querySelector('.ficha-columnas')).not.toBeNull();
  });

  it('Reintentar la carga', async () => {
    entorno.servidor.responder('GET /pacientes/{id}/imagenes', () => json(500, {}));
    await entorno.abrirFicha();
    await entorno.seccionLista();

    entorno.servidor.responder('GET /pacientes/{id}/imagenes', () => json(200, [RX36]));
    boton(entorno.seccion(), 'Reintentar').click();

    await vi.waitFor(() => expect(filas()).toHaveLength(1));
    expect(entorno.seccion().querySelector('[role="alert"]')?.textContent).toBe('');
    expect(entorno.servidor.a('GET', '/pacientes/1/imagenes')).toHaveLength(2);
    expect(entorno.servidor.a('GET', '/pacientes/1/ficha')).toHaveLength(1);
  });

  it('Un 403 al listar muestra el mensaje de permiso, sin "Reintentar" de la conexión', async () => {
    entorno.servidor.responder('GET /pacientes/{id}/imagenes', () => json(403, { codigo: 'ACCESO_DENEGADO' }));
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(entorno.seccion().querySelector('[role="alert"]')?.textContent).toBe('No tenés permiso para esta acción.');
  });
});

describe('Listado de imágenes', () => {
  it('Filas del listado', async () => {
    entorno.imagenes = [RX36, ESTUDIO_PDF];
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const [primera, segunda] = filas();
    expect(filas()).toHaveLength(2);
    const t1 = texto(primera as HTMLElement);
    expect(t1).toContain('Radiografía');
    expect(t1).toContain('30/11/2026');
    expect(t1).toContain('Pieza 36');
    expect(t1).toContain('Control');
    expect(t1).toContain('Ana Ejemplo');
    expect(t1).toContain('JPEG, 312 KB');
    const t2 = texto(segunda as HTMLElement);
    expect(t2).toContain('Estudio');
    expect(t2).toContain('01/10/2026');
    expect(t2).not.toContain('Pieza');
    expect(t2).toContain('PDF, 840 KB');
    expect(texto(entorno.seccion())).toContain('2 vigentes');
  });

  it('el orden es el de la API y una imagen sin profesional ni descripción no deja huecos', async () => {
    entorno.imagenes = [imagen({ id: 20, tomadaEl: '2026-12-01', profesional: '', descripcion: null }), RX36];
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(filas().map((f) => f.querySelector('.img-titulo span')?.textContent)).toEqual(['Radiografía', 'Radiografía']);
    expect(texto(filas()[0] as HTMLElement)).not.toContain('Cargó');
    expect(filas()[0]?.querySelector('.img-descripcion')).toBeNull();
    expect(texto(filas()[1] as HTMLElement)).toContain('Cargó Ana Ejemplo');
  });

  it('Paciente sin imágenes', async () => {
    entorno.imagenes = [];
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(texto(entorno.seccion())).toContain('Todavía no hay imágenes ni estudios cargados.');
    expect(filas()).toHaveLength(0);
    expect(entorno.seccion().querySelector('.img-filtros')).toBeNull();
  });

  it('Sin miniaturas', async () => {
    entorno.imagenes = [RX36, FOTO11, ESTUDIO_PDF];
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(entorno.seccion().querySelector('img')).toBeNull();
    expect(entorno.servidor.pedidos.filter((p) => p.ruta.endsWith('/archivo'))).toEqual([]);
  });
});

describe('Filtrar por pieza y mostrar anuladas', () => {
  function selectorDePieza(): HTMLSelectElement {
    const encontrado = entorno.seccion().querySelector<HTMLSelectElement>('.img-filtros select');
    if (encontrado === null) {
      throw new Error('No está el filtro de pieza');
    }
    return encontrado;
  }

  it('Filtrar por una pieza', async () => {
    entorno.imagenes = [RX36, FOTO11];
    await entorno.abrirFicha();
    await entorno.seccionLista();
    expect([...selectorDePieza().options].map((o) => o.textContent)).toEqual(['Todas las piezas', '11', '36']);
    expect(filas()).toHaveLength(2);

    escribir(selectorDePieza(), '36');

    await vi.waitFor(() => expect(filas()).toHaveLength(1));
    expect(entorno.servidor.ultimo('GET', '/pacientes/1/imagenes').query.get('pieza')).toBe('36');
    expect(texto(filas()[0] as HTMLElement)).toContain('Pieza 36');
    // Las opciones siguen siendo las del listado sin filtrar.
    expect([...selectorDePieza().options].map((o) => o.textContent)).toEqual(['Todas las piezas', '11', '36']);
    expect(selectorDePieza().value).toBe('36');
  });

  it('volver a "Todas las piezas" pide el listado sin la pieza', async () => {
    entorno.imagenes = [RX36, FOTO11];
    await entorno.abrirFicha();
    await entorno.seccionLista();
    escribir(selectorDePieza(), '36');
    await vi.waitFor(() => expect(filas()).toHaveLength(1));

    escribir(selectorDePieza(), '');

    await vi.waitFor(() => expect(filas()).toHaveLength(2));
    expect(entorno.servidor.ultimo('GET', '/pacientes/1/imagenes').query.has('pieza')).toBe(false);
  });

  it('un filtro sin resultados lo dice', async () => {
    entorno.imagenes = [RX36, FOTO11];
    await entorno.abrirFicha();
    await entorno.seccionLista();
    entorno.imagenes = [RX36];

    escribir(selectorDePieza(), '11');

    await vi.waitFor(() => expect(texto(entorno.seccion())).toContain('No hay imágenes de la pieza 11.'));
    expect(selectorDePieza().value).toBe('11');
  });

  it('Mostrar las anuladas', async () => {
    entorno.imagenes = [RX36, ANULADA];
    await entorno.abrirFicha();
    await entorno.seccionLista();
    expect(filas()).toHaveLength(1);

    boton(entorno.seccion(), 'Anular radiografía de la pieza 36 del 30/11/2026');
    const casilla = [...entorno.seccion().querySelectorAll<HTMLLabelElement>('label')].find((l) => l.textContent?.trim() === 'Mostrar anuladas')?.control as HTMLInputElement;
    casilla.click();

    await vi.waitFor(() => expect(filas()).toHaveLength(2));
    expect(entorno.servidor.ultimo('GET', '/pacientes/1/imagenes').query.get('incluirAnuladas')).toBe('true');
    const anulada = filas()[1] as HTMLElement;
    const t = texto(anulada);
    expect(anulada.classList.contains('img-fila-anulada')).toBe(true);
    expect(anulada.querySelector('.etiqueta-anulada')?.textContent).toBe('Anulada');
    expect(t).toContain('Anulada por Bruno Prueba el 30/11/2026 a las 17:20: Duplicada');
    expect([...anulada.querySelectorAll('button')].map((b) => b.textContent?.trim())).not.toContain('Anular');
    expect(texto(entorno.seccion())).toContain('1 vigente · 1 anulada');
  });
});

describe('Sección de imágenes accesible', () => {
  it('Nombre accesible de los botones', async () => {
    entorno.imagenes = [RX36, ESTUDIO_PDF, ANULADA];
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const primera = filas()[0] as HTMLElement;
    expect([...primera.querySelectorAll('button')].map((b) => b.getAttribute('aria-label'))).toEqual([
      'Ver radiografía de la pieza 36 del 30/11/2026',
      'Descargar radiografía de la pieza 36 del 30/11/2026',
      'Anular radiografía de la pieza 36 del 30/11/2026',
    ]);
    expect(boton(entorno.seccion(), 'Descargar estudio del 01/10/2026')).toBeDefined();
  });

  it('la lista y los íconos de la fila son legibles para los lectores de pantalla', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const lista = entorno.seccion().querySelector('ul.img-lista');
    expect(lista?.getAttribute('aria-label')).toBe('Imágenes y estudios de Paciente Uno');
    expect(entorno.seccion().querySelectorAll('.img-fila svg[aria-hidden="true"]').length).toBeGreaterThanOrEqual(2);
    // El control de filtros es un grupo con nombre y el filtro tiene su etiqueta.
    expect(entorno.seccion().querySelector('.img-filtros')?.getAttribute('role')).toBe('group');
    expect(entorno.seccion().querySelector('.img-filtros')?.getAttribute('aria-label')).toBe('Filtros de las imágenes');
    expect(entorno.seccion().querySelector('.img-filtros label')?.textContent).toBe('Pieza');
  });
});
