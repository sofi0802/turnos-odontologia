import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, boton, campo, escribir, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';
import { COBERTURA_UNO, PARTICULAR } from './apoyo-coberturas.ts';

/**
 * C-49, grupo 5: "Mi cobertura" en "Mi cuenta" del paciente (spec `frontend-coberturas`), el delta de
 * `frontend-cuenta-paciente` y "Mi cuenta" en el menú del paciente (delta de `frontend-navegacion`). Datos ficticios.
 */

const SESION_UNO = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };
const SESION_DOS = { autenticado: true, username: 'paciente.dos@example.com', rol: 'PACIENTE' };
const SESION_RECEPCION = { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' };

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

beforeEach(() => {
  servidor = new ServidorFalso();
});

afterEach(() => {
  app = undefined;
});

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

function main(): HTMLElement {
  return raiz().querySelector('main') ?? raiz();
}

function miCobertura(): HTMLElement | null {
  return main().querySelector<HTMLElement>('.mi-cobertura');
}

function enlacesVisibles(): string[] {
  return [...raiz().querySelectorAll<HTMLAnchorElement>('aside nav[aria-label="Principal"] li:not([hidden]) a')].map(
    (a) => a.textContent ?? '',
  );
}

async function abrirCuenta(sesion: object): Promise<HTMLElement> {
  servidor.responder('GET /public/sesion', () => json(200, sesion));
  app = abrirApp(servidor, '#/mi-cuenta');
  await vi.waitFor(() => expect(miCobertura()?.getAttribute('aria-busy')).toBeNull());
  return miCobertura() as HTMLElement;
}

describe('Mi cobertura del paciente', () => {
  it('Paciente con cobertura', async () => {
    servidor.responder('GET /paciente/cobertura', () => json(200, COBERTURA_UNO));
    const seccion = await abrirCuenta(SESION_UNO);

    await vi.waitFor(() => expect(texto(seccion)).toContain('Obra Social Ejemplo (OSE)'));
    const pedido = servidor.ultimo('GET', '/paciente/cobertura');
    expect([...pedido.query.keys()]).toEqual([]);
    expect(texto(seccion)).toContain('Plan Base');
    expect(texto(seccion)).toContain('OSE-000001');
    expect(texto(seccion)).toContain('Desde 01/01/2026, sin fecha de fin');
    expect(seccion.querySelectorAll('button')).toHaveLength(0);
    expect(seccion.querySelector('h2')?.textContent).toBe('Mi cobertura');
  });

  it('Paciente particular', async () => {
    servidor.responder('GET /paciente/cobertura', () => json(200, PARTICULAR));
    const seccion = await abrirCuenta(SESION_DOS);

    await vi.waitFor(() => expect(texto(seccion)).toContain('Te atendés como particular.'));
  });

  it('Falla de Mi cobertura', async () => {
    let pedidos = 0;
    servidor.responder('GET /paciente/cobertura', () => {
      pedidos += 1;
      return pedidos === 1 ? json(500) : json(200, PARTICULAR);
    });
    const seccion = await abrirCuenta(SESION_UNO);

    await vi.waitFor(() => expect(seccion.querySelector('[role="alert"]')?.textContent).not.toBe(''));
    boton(seccion, 'Reintentar').click();

    await vi.waitFor(() => expect(texto(miCobertura() as HTMLElement)).toContain('Te atendés como particular.'));
    expect(servidor.a('GET', '/paciente/cobertura')).toHaveLength(2);
  });
});

describe('Cuenta del paciente con sesión iniciada', () => {
  it('El paciente ingresa y va a Mis turnos', async () => {
    servidor.sinSesion();
    servidor.responder('POST /public/login', () => {
      servidor.responder('GET /public/sesion', () => json(200, SESION_UNO));
      return json(200, SESION_UNO);
    });
    servidor.responder('GET /paciente/turnos', () => json(200, []));
    app = abrirApp(servidor, '#/mi-cuenta');
    await vi.waitFor(() => expect(raiz().querySelector('form[aria-label="Ingresar"]')).not.toBeNull());

    escribir(campo(raiz(), 'Email'), 'paciente.uno@example.com');
    escribir(campo(raiz(), 'Contraseña'), 'clave-ficticia-1');
    raiz().querySelector<HTMLFormElement>('form[aria-label="Ingresar"]')?.requestSubmit();

    await vi.waitFor(() => expect(main().querySelector('h1')?.textContent).toBe('Mis turnos'));
    expect(window.location.hash).toBe('#/mis-turnos');
  });
});

describe('"Mi cuenta" en el menú del paciente', () => {
  it('El paciente ve Mi cuenta en el menú', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_UNO));
    servidor.responder('GET /paciente/turnos', () => json(200, []));
    app = abrirApp(servidor, '#/mis-turnos');

    await vi.waitFor(() => expect(enlacesVisibles()).toEqual(['Reservar turno', 'Mis turnos', 'Mi cuenta']));
  });

  it('"Mi cuenta" queda marcada como página actual', async () => {
    servidor.responder('GET /paciente/cobertura', () => json(200, PARTICULAR));
    await abrirCuenta(SESION_UNO);

    await vi.waitFor(() =>
      expect([...raiz().querySelectorAll('aside nav a[aria-current="page"]')].map((a) => a.textContent)).toEqual(['Mi cuenta']),
    );
  });

  it('El personal no ve Mi cuenta en el menú', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_RECEPCION));
    app = abrirApp(servidor, '#/agenda');

    await vi.waitFor(() => expect(enlacesVisibles()).toContain('Agenda'));
    expect(enlacesVisibles()).not.toContain('Mi cuenta');
  });

  it('el visitante no ve Mi cuenta en la barra lateral', async () => {
    servidor.sinSesion();
    app = abrirApp(servidor, '#/mi-cuenta');

    await vi.waitFor(() => expect(raiz().querySelector('form[aria-label="Ingresar"]')).not.toBeNull());
    expect(enlacesVisibles()).not.toContain('Mi cuenta');
  });
});
