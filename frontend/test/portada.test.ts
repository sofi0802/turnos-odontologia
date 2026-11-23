import { afterEach, describe, expect, it, vi } from 'vitest';
import { estilos } from './estilos.ts';
import type { Router } from '../src/shared/ui/router.ts';
import { abrirApp, json, ServidorFalso } from './apoyo.ts';

let raiz: HTMLElement;
let router: Router | undefined;

const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };

/** Servidor sin sesión, con el catálogo público vacío. */
function sinSesion(): ServidorFalso {
  const servidor = new ServidorFalso();
  servidor.sinSesion();
  servidor.responder('GET /public/prestaciones', () => json(200, []));
  return servidor;
}

function conPaciente(): ServidorFalso {
  const servidor = sinSesion();
  servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
  servidor.responder('GET /paciente/turnos', () => json(200, []));
  return servidor;
}

/** Servidor con la sesión ficticia de `recepcion` (la que trae `ServidorFalso`). */
function conPersonal(): ServidorFalso {
  return new ServidorFalso();
}

function abrirEn(hash: string, servidor: ServidorFalso): ServidorFalso {
  const app = abrirApp(servidor, hash);
  raiz = app.raiz;
  router = app.router;
  return servidor;
}

function esperarHashchange(): Promise<void> {
  return new Promise((resolver) => window.addEventListener('hashchange', () => resolver(), { once: true }));
}

async function esperarTitulo(esperado: string): Promise<void> {
  await vi.waitFor(() => expect(raiz.querySelector('main h1')?.textContent).toBe(esperado));
}

/** Enlaces y botones de la portada (`main`) o del encabezado, por su texto. */
function enlaceDe(zona: 'main' | 'header', texto: string): HTMLAnchorElement | undefined {
  return [...raiz.querySelectorAll<HTMLAnchorElement>(`${zona} a`)].find(
    (a) => a.textContent === texto && a.closest('nav') === null && !a.hidden,
  );
}

function textosVisibles(zona: 'main' | 'header'): string[] {
  return [...raiz.querySelectorAll<HTMLAnchorElement>(`${zona} a`)]
    .filter((a) => !a.hidden && a.closest('nav') === null)
    .map((a) => a.textContent ?? '');
}

/** Espera a que la sesión del servidor haya llegado al encabezado. */
async function esperarSesionEnEncabezado(): Promise<void> {
  await vi.waitFor(() => expect(raiz.querySelector<HTMLElement>('.usuario-nombre')?.hidden).toBe(false));
}

afterEach(() => {
  router?.detener();
  router = undefined;
});

describe('Portada pública', () => {
  it('Abrir la portada sin sesión', async () => {
    abrirEn('#/inicio', sinSesion());
    await vi.waitFor(() => expect(document.title).toBe('Turnos odontología'));

    expect(raiz.querySelectorAll('h1')).toHaveLength(1);
    expect(raiz.querySelectorAll('main ol li')).toHaveLength(3);
    expect(window.location.hash).toBe('#/inicio');
  });

  it('La portada no envía datos', async () => {
    const servidor = abrirEn('#/inicio', sinSesion());
    await vi.waitFor(() => expect(servidor.a('GET', '/public/sesion')).toHaveLength(1));
    await Promise.resolve();

    expect(servidor.pedidos.map((p) => `${p.metodo} ${p.ruta}`)).toEqual(['GET /public/sesion']);
  });
});

describe('Botones de la portada', () => {
  it('Reservar sin registrarse', async () => {
    const servidor = abrirEn('#/inicio', sinSesion());
    const boton = enlaceDe('main', 'Reservar turno');
    expect(boton?.getAttribute('href')).toBe('#/reserva');

    const cambio = esperarHashchange();
    boton?.click();
    await cambio;

    await esperarTitulo('Reservar turno');
    expect(servidor.a('POST', '/public/login')).toHaveLength(0);
    expect(servidor.a('POST', '/public/pacientes/registro')).toHaveLength(0);
  });

  it('Ingresar a la cuenta', async () => {
    abrirEn('#/inicio', sinSesion());
    const boton = enlaceDe('main', 'Ingresar a mi cuenta');
    expect(boton?.getAttribute('href')).toBe('#/mi-cuenta');

    const cambio = esperarHashchange();
    boton?.click();
    await cambio;

    await esperarTitulo('Mi cuenta');
  });
});

describe('Acceso del personal', () => {
  it('El personal entra desde la portada', async () => {
    abrirEn('#/inicio', sinSesion());
    const boton = enlaceDe('header', 'Acceso personal');
    expect(boton?.getAttribute('href')).toBe('#/mi-cuenta');

    const cambio = esperarHashchange();
    boton?.click();
    await cambio;

    await esperarTitulo('Mi cuenta');
  });

  it('El botón del personal en el celular', async () => {
    // jsdom no calcula el diseño: se comprueba que el botón vive en el encabezado (arriba a la derecha,
    // junto al usuario) y que su regla de CSS da al menos 44 px, también en el celular.
    // El diseño real se verifica con capturas a 390 × 844 (tarea 7.3).
    abrirEn('#/inicio', sinSesion());
    expect(enlaceDe('header', 'Acceso personal')?.closest('.usuario')).not.toBeNull();
    const reglaBoton = /\n\.btn-acceso\s*\{([^}]*)\}/.exec(estilos)?.[1] ?? '';
    expect(reglaBoton).toMatch(/min-height:\s*var\(--ds-alto-control\)/);
  });
});

describe('Portada con sesión iniciada', () => {
  it('Paciente con sesión en la portada', async () => {
    abrirEn('#/inicio', conPaciente());
    await esperarSesionEnEncabezado();

    expect(textosVisibles('main')).toEqual(['Reservar turno', 'Mis turnos']);
    expect(enlaceDe('main', 'Mis turnos')?.getAttribute('href')).toBe('#/mis-turnos');
    expect(textosVisibles('header')).not.toContain('Acceso personal');
    expect(raiz.textContent).not.toContain('Ingresar a mi cuenta');
    expect(window.location.hash).toBe('#/inicio');
  });

  it('Personal con sesión en la portada', async () => {
    abrirEn('#/inicio', conPersonal());
    await esperarSesionEnEncabezado();

    expect(textosVisibles('main')).toEqual(['Ir a la agenda']);
    expect(enlaceDe('main', 'Ir a la agenda')?.getAttribute('href')).toBe('#/agenda');
    expect(textosVisibles('header')).not.toContain('Acceso personal');
    expect(raiz.textContent).not.toContain('Ingresar a mi cuenta');
    expect(window.location.hash).toBe('#/inicio');
  });
});

describe('El logo lleva a la portada', () => {
  it('Volver a la portada desde otra página', async () => {
    abrirEn('#/reserva', sinSesion());
    await vi.waitFor(() => expect(raiz.querySelector('main [aria-busy]')).toBeNull());
    const marca = raiz.querySelector<HTMLAnchorElement>('header a.marca');
    expect(marca?.textContent).toBe('Turnos odontología');
    expect(marca?.getAttribute('href')).toBe('#/inicio');

    const cambio = esperarHashchange();
    marca?.click();
    await cambio;

    await vi.waitFor(() => expect(document.title).toBe('Turnos odontología'));
    expect(window.location.hash).toBe('#/inicio');
  });
});

describe('Solo recursos propios', () => {
  it('Sin recursos de otros orígenes', async () => {
    const servidor = abrirEn('#/inicio', sinSesion());
    await vi.waitFor(() => expect(servidor.a('GET', '/public/sesion')).toHaveLength(1));

    expect(raiz.querySelectorAll('img, link, script, iframe, [src]')).toHaveLength(0);
    for (const enlace of raiz.querySelectorAll('a')) {
      expect(enlace.getAttribute('href')).toMatch(/^#\//);
    }
    expect(estilos).not.toMatch(/url\(\s*['"]?https?:/);
    const ilustraciones = raiz.querySelectorAll('main svg');
    expect(ilustraciones.length).toBeGreaterThan(0);
    for (const ilustracion of ilustraciones) {
      expect(ilustracion.getAttribute('aria-hidden')).toBe('true');
    }
  });
});
