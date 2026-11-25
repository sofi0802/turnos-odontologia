import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { dejarSeleccion } from '../src/ficha/seleccion.ts';
import { abrirApp, boton, elegirPaciente, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';
import { responderCoberturas } from './apoyo-coberturas.ts';

/**
 * C-49, grupo 4: la sección "Cobertura" en la ficha (`#/pacientes`) y la página "Coberturas" (`#/coberturas`) con su
 * opción en la barra lateral (delta de `frontend-navegacion`). Datos ficticios del seed.
 */

const SESION_ADMIN = { autenticado: true, username: 'admin', rol: 'ADMIN' };
const SESION_RECEPCION = { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' };
const SESION_ANA = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };

const FICHA = { pacienteId: 1, antecedentes: '', alergias: '', medicacion: '', actualizadoPor: null, actualizadoEn: null, ausencias: [] };

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-24T15:00:00Z'));
  servidor = new ServidorFalso();
  responderCoberturas(servidor);
});

afterEach(() => {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
  app = undefined;
  vi.useRealTimers();
});

function conSesion(sesion: object): void {
  servidor.responder('GET /public/sesion', () => json(200, sesion));
}

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

function main(): HTMLElement {
  return raiz().querySelector('main') ?? raiz();
}

function seccionCobertura(): HTMLElement | null {
  return main().querySelector<HTMLElement>('.cob-seccion[aria-labelledby]');
}

function enlacesVisibles(): string[] {
  return [...raiz().querySelectorAll<HTMLAnchorElement>('aside nav[aria-label="Principal"] li:not([hidden]) a')].map(
    (a) => a.textContent ?? '',
  );
}

function responderFicha(): void {
  servidor.responder('GET /pacientes/{id}/ficha', () => json(200, FICHA));
  servidor.responder('GET /pacientes/{id}/odontograma', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/odontograma/historial', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/periodontograma', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/imagenes', () => json(200, []));
}

/** Rutas clínicas que la página "Coberturas" nunca pide (RN-CL-01). */
const CLINICAS = ['/ficha', '/odontograma', '/periodontograma', '/imagenes', '/historial'];

describe('Sección "Cobertura" en la ficha', () => {
  async function abrirFicha(): Promise<void> {
    responderFicha();
    dejarSeleccion({ paciente: { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: '99000001' }, turno: null });
    app = abrirApp(servidor, '#/pacientes');
    await vi.waitFor(() => expect(raiz().querySelector('.ficha-columnas')).not.toBeNull());
  }

  it('La ficha muestra la cobertura', async () => {
    conSesion(SESION_ADMIN);
    await abrirFicha();

    await vi.waitFor(() => expect(texto(seccionCobertura() as HTMLElement)).toContain('Plan Base'));
    const cobertura = seccionCobertura() as HTMLElement;
    const odontograma = [...main().querySelectorAll('h2')].find((h) => h.textContent === 'Odontograma')?.closest('section');
    expect(odontograma).toBeDefined();
    // Arriba del odontograma y debajo de la cabecera del paciente.
    expect(cobertura.compareDocumentPosition(odontograma as Node) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(main().querySelector('.ficha-paciente')?.compareDocumentPosition(cobertura)).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    // El administrador puede afiliar aunque la ficha clínica sea de solo lectura para él.
    expect(boton(cobertura, 'Afiliar').hidden).toBe(false);
  });

  it('Falla de la cobertura en la ficha', async () => {
    conSesion(SESION_ANA);
    servidor.responder('GET /pacientes/1/cobertura', () => json(500));
    await abrirFicha();

    await vi.waitFor(() => expect(seccionCobertura()?.querySelector('[role="alert"]:not(:empty)')).not.toBeNull());
    const cobertura = seccionCobertura() as HTMLElement;
    expect(boton(cobertura, 'Reintentar')).toBeDefined();
    expect(texto(main())).toContain('Odontograma');
    expect(main().querySelector('.ficha-columnas')).not.toBeNull();
    // El odontólogo la ve en solo lectura.
    expect(texto(cobertura)).toContain('Solo lectura: la cobertura la cargan la recepción y la administración.');
  });

  it('"Cambiar de paciente" deja de mostrar la cobertura', async () => {
    conSesion(SESION_ADMIN);
    await abrirFicha();
    await vi.waitFor(() => expect(seccionCobertura()).not.toBeNull());

    boton(main(), 'Cambiar de paciente').click();

    expect(seccionCobertura()).toBeNull();
  });
});

describe('Página "Coberturas" sin datos clínicos', () => {
  async function abrirYElegir(busqueda: string, opcion: string): Promise<HTMLElement> {
    conSesion(SESION_RECEPCION);
    app = abrirApp(servidor, '#/coberturas');
    await vi.waitFor(() => expect(main().querySelector('h1')?.textContent).toBe('Coberturas'));
    await elegirPaciente(main(), busqueda, opcion);
    await vi.waitFor(() => expect(seccionCobertura()?.getAttribute('aria-busy')).toBeNull());
    return seccionCobertura() as HTMLElement;
  }

  it('La recepción ve la cobertura de un paciente', async () => {
    const cobertura = await abrirYElegir('99000002', 'Dos, Paciente · DNI 99000002');

    expect(texto(main())).toContain('Paciente Dos');
    expect(texto(main())).toContain('DNI 99000002');
    expect(cobertura.querySelector('h2')?.textContent).toBe('Cobertura');
    const pedidos = servidor.pedidos.filter((p) => p.ruta.startsWith('/pacientes')).map((p) => `${p.ruta}${p.query.size > 0 ? `?${p.query.toString()}` : ''}`);
    expect(new Set(pedidos)).toEqual(new Set(['/pacientes?q=99000002', '/pacientes/2/cobertura', '/pacientes/2/afiliaciones']));
  });

  it('Ningún pedido clínico', async () => {
    const cobertura = await abrirYElegir('99000001', 'Uno, Paciente · DNI 99000001');

    expect(texto(cobertura)).toContain('OSE-000001');
    for (const pedido of servidor.pedidos) {
      expect(CLINICAS.some((ruta) => pedido.ruta.includes(ruta))).toBe(false);
    }
  });

  it('Cambiar de paciente', async () => {
    await abrirYElegir('99000001', 'Uno, Paciente · DNI 99000001');

    boton(main(), 'Cambiar de paciente').click();

    expect(seccionCobertura()).toBeNull();
    expect(texto(main())).not.toContain('Paciente Uno');
    expect(texto(main())).not.toContain('OSE-000001');
    expect(main().querySelector('input[role="combobox"]')).not.toBeNull();
    expect(window.location.hash).toBe('#/coberturas');
  });

  it('la recepción puede afiliar desde la página', async () => {
    const cobertura = await abrirYElegir('99000002', 'Dos, Paciente · DNI 99000002');

    expect(boton(cobertura, 'Afiliar').hidden).toBe(false);
  });
});

describe('Página "Coberturas" para administración y recepción', () => {
  it('Menú de la recepción con Coberturas', async () => {
    conSesion(SESION_RECEPCION);
    app = abrirApp(servidor, '#/agenda');

    await vi.waitFor(() => expect(enlacesVisibles()).toContain('Coberturas'));
    const enlaces = enlacesVisibles();
    expect(enlaces.indexOf('Coberturas')).toBe(enlaces.indexOf('Agenda') + 1);
    expect(enlaces.indexOf('Reportes')).toBe(enlaces.indexOf('Coberturas') + 1);
  });

  it('Menú del administrador con Coberturas', async () => {
    conSesion(SESION_ADMIN);
    app = abrirApp(servidor, '#/agenda');

    await vi.waitFor(() => expect(enlacesVisibles()).toContain('Coberturas'));
    const enlaces = enlacesVisibles();
    expect(enlaces.indexOf('Coberturas')).toBe(enlaces.indexOf('Pacientes') + 1);
    expect(enlaces.indexOf('Reportes')).toBe(enlaces.indexOf('Coberturas') + 1);
  });

  it('El odontólogo abre la dirección', async () => {
    conSesion(SESION_ANA);
    app = abrirApp(servidor, '#/coberturas');

    await vi.waitFor(() => expect(texto(main())).toContain('Esta sección es solo para administración y recepción.'));
    expect(enlacesVisibles()).not.toContain('Coberturas');
    expect(servidor.a('GET', '/pacientes')).toHaveLength(0);
  });

  it('Coberturas sin sesión', async () => {
    servidor.sinSesion();
    app = abrirApp(servidor, '#/coberturas');

    await vi.waitFor(() => expect(main().querySelector('h1')?.textContent).toBe('Mi cuenta'));
    expect(window.location.hash).toBe('#/mi-cuenta');
  });
});
