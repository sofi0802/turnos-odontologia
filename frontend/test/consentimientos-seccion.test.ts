import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MENSAJE_ERROR_GENERICO } from '../src/shared/api/errores.ts';
import { boton, json } from './apoyo.ts';
import {
  prepararEntorno,
  REVOCADO,
  resumen,
  SESION_ADMIN,
  SESION_ANA,
  TITULO_BLANQUEAMIENTO,
  TITULO_EXTRACCION,
  visible,
  VIGENTE,
  type Entorno,
} from './apoyo-consentimientos.ts';

/** C-48, grupo 4 (listado): la sección "Consentimientos" de la ficha (spec `frontend-consentimientos`). */

let entorno: Entorno;

beforeEach(() => {
  entorno = prepararEntorno(SESION_ANA);
});

afterEach(() => {
  entorno.cerrar();
});

function filas(): HTMLElement[] {
  return [...entorno.seccion().querySelectorAll<HTMLElement>('.cons-lista > li')];
}

describe('Sección de consentimientos en la ficha', () => {
  it('La sección aparece en la ficha', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const seccion = entorno.seccion();
    expect(seccion.querySelector('h2')?.textContent).toBe('Consentimientos');
    const imagenes = entorno.main().querySelector('.img-seccion') as HTMLElement;
    expect(imagenes.compareDocumentPosition(seccion) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos')).toHaveLength(1);
  });

  it('mientras carga muestra "Cargando consentimientos…"', async () => {
    let responder: (r: Response) => void = () => undefined;
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos', () => new Promise<Response>((resolver) => (responder = resolver)));
    await entorno.abrirFicha();

    expect(visible(entorno.seccion())).toContain('Cargando consentimientos…');
    expect(entorno.seccion().querySelector('.cargando')?.getAttribute('role')).toBe('status');
    responder(json(200, []));
    await entorno.seccionLista();
    expect(visible(entorno.seccion())).not.toContain('Cargando consentimientos…');
  });

  it('Falla al cargar los consentimientos', async () => {
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos', () => json(500));
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const seccion = entorno.seccion();
    expect(seccion.querySelector('[role="alert"]')?.textContent).toBe(MENSAJE_ERROR_GENERICO);
    expect(boton(seccion, 'Reintentar')).toBeDefined();
    expect(entorno.main().querySelector('.odontograma')).not.toBeNull();
    expect(entorno.main().querySelector('.img-seccion')).not.toBeNull();
    expect(seccion.querySelector('.cons-lista')).toBeNull();
  });

  it('Reintentar la carga', async () => {
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos', () => json(500));
    await entorno.abrirFicha();
    await entorno.seccionLista();
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos', () => json(200, [VIGENTE]));

    boton(entorno.seccion(), 'Reintentar').click();

    await vi.waitFor(() => expect(entorno.seccion().querySelector('.cons-lista')).not.toBeNull());
    expect(visible(entorno.seccion())).toContain(TITULO_EXTRACCION);
    expect(entorno.seccion().querySelector('[role="alert"]')?.textContent).toBe('');
    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos')).toHaveLength(2);
  });

  it('el error de carga no oculta el resto de la ficha ni se confunde con "sin consentimientos"', async () => {
    entorno.servidor.responder('GET /pacientes/{id}/consentimientos', () => json(403, { codigo: 'ACCESO_DENEGADO' }));
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(visible(entorno.seccion())).not.toContain('Todavía no hay consentimientos firmados.');
    expect(entorno.main().querySelector('.ficha-columnas')).not.toBeNull();
  });
});

describe('Listado de consentimientos firmados', () => {
  it('Filas del listado', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();

    const [primera, segunda] = filas() as [HTMLElement, HTMLElement];
    expect(filas()).toHaveLength(2);
    expect(visible(primera)).toContain(TITULO_EXTRACCION);
    expect(visible(primera)).toContain('Versión 3');
    expect(visible(primera)).toContain('Firmado el 24/11/2026 a las 10:30');
    expect(visible(primera)).toContain('Ana Ejemplo');
    expect(primera.querySelector('.etiqueta')?.textContent).toBe('Vigente');
    expect(visible(segunda)).toContain(TITULO_BLANQUEAMIENTO);
    expect(segunda.querySelector('.etiqueta')?.textContent).toBe('Revocado');
    expect(segunda.classList.contains('cons-fila-revocada')).toBe(true);
    expect(primera.classList.contains('cons-fila-revocada')).toBe(false);
  });

  it('conserva el orden de la API y cuenta los firmados', async () => {
    entorno.consentimientos = [REVOCADO, resumen({ id: 13, titulo: 'Otro consentimiento' }), VIGENTE];
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(filas().map((f) => f.querySelector('.cons-titulo span')?.textContent)).toEqual([
      TITULO_BLANQUEAMIENTO,
      'Otro consentimiento',
      TITULO_EXTRACCION,
    ]);
    expect(entorno.seccion().querySelector('.cons-contador')?.textContent).toBe('3 firmados');
  });

  it('el contador usa el singular con un solo consentimiento', async () => {
    entorno.consentimientos = [VIGENTE];
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(entorno.seccion().querySelector('.cons-contador')?.textContent).toBe('1 firmado');
  });

  it('Paciente sin consentimientos', async () => {
    entorno.consentimientos = [];
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(visible(entorno.seccion())).toContain('Todavía no hay consentimientos firmados.');
    expect(entorno.seccion().querySelector('.cons-lista')).toBeNull();
  });

  it('el título del consentimiento se muestra como texto, nunca como HTML', async () => {
    entorno.consentimientos = [resumen({ id: 14, titulo: '<img src=x onerror=alert(1)>Título' })];
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(entorno.seccion().querySelector('.cons-lista img')).toBeNull();
    expect(visible(entorno.seccion())).toContain('<img src=x onerror=alert(1)>Título');
  });

  it('la lista tiene un nombre accesible con el paciente', async () => {
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(entorno.seccion().querySelector('.cons-lista')?.getAttribute('aria-label')).toBe('Consentimientos de Paciente Uno');
  });
});

describe('La sección no se pide a quien no corresponde', () => {
  it('el administrador la ve y pide el listado', async () => {
    entorno.cerrar();
    entorno = prepararEntorno(SESION_ADMIN);
    await entorno.abrirFicha();
    await entorno.seccionLista();

    expect(entorno.servidor.a('GET', '/pacientes/1/consentimientos')).toHaveLength(1);
    expect(filas()).toHaveLength(2);
  });
});
