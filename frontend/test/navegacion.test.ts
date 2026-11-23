import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Router } from '../src/shared/ui/router.ts';
import { abrirApp, campo, escribir, json, ServidorFalso } from './apoyo.ts';

let raiz: HTMLElement;
let router: Router | undefined;

const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };

/** Abre la aplicación; por defecto con una sesión ficticia de `recepcion` (la Agenda es privada desde C-09). */
function abrirEn(hash: string, servidor = new ServidorFalso()): ServidorFalso {
  const app = abrirApp(servidor, hash);
  raiz = app.raiz;
  router = app.router;
  return servidor;
}

/** Servidor sin sesión, con el catálogo público vacío. */
function sinSesion(): ServidorFalso {
  const servidor = new ServidorFalso();
  servidor.sinSesion();
  servidor.responder('GET /public/prestaciones', () => json(200, []));
  return servidor;
}

/** Servidor con la sesión ficticia de "Paciente Uno". */
function conPaciente(): ServidorFalso {
  const servidor = sinSesion();
  servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
  servidor.responder('GET /paciente/turnos', () => json(200, []));
  return servidor;
}

async function esperarTitulo(esperado: string): Promise<void> {
  await vi.waitFor(() => expect(titulo()).toBe(esperado));
}

function esperarHashchange(): Promise<void> {
  return new Promise((resolver) => window.addEventListener('hashchange', () => resolver(), { once: true }));
}

function titulo(): string | null {
  return raiz.querySelector('main h1')?.textContent ?? null;
}

function enlace(texto: string): HTMLAnchorElement {
  const encontrado = [...raiz.querySelectorAll<HTMLAnchorElement>('nav a')].find((a) => a.textContent === texto);
  if (encontrado === undefined) {
    throw new Error(`No existe el enlace ${texto}`);
  }
  return encontrado;
}

/** Enlaces del menú que se ven: los del otro grupo quedan ocultos (design D3 de C-12). */
function enlacesVisibles(): string[] {
  return [...raiz.querySelectorAll<HTMLAnchorElement>('nav li:not([hidden]) a')].map((a) => a.textContent ?? '');
}

/** Enlace "Ingresar" del encabezado, fuera del menú. */
function enlaceIngresar(): HTMLAnchorElement | undefined {
  return [...raiz.querySelectorAll<HTMLAnchorElement>('header a')].find(
    (a) => a.textContent === 'Ingresar' && a.closest('nav') === null,
  );
}

function enlacesActivos(): string[] {
  return [...raiz.querySelectorAll('nav a[aria-current="page"]')].map((a) => a.textContent ?? '');
}

function botonMenu(): HTMLButtonElement {
  const boton = raiz.querySelector<HTMLButtonElement>('button[aria-controls="menu-principal"]');
  if (boton === null) {
    throw new Error('No existe el botón del menú');
  }
  return boton;
}

function menu(): HTMLElement {
  const nav = raiz.querySelector<HTMLElement>('#menu-principal');
  if (nav === null) {
    throw new Error('No existe el menú');
  }
  return nav;
}

async function elegir(texto: string): Promise<void> {
  const cambio = esperarHashchange();
  enlace(texto).click();
  await cambio;
}

async function esperarCargaDeReserva(): Promise<void> {
  await vi.waitFor(() => expect(raiz.querySelector('main [aria-busy]')).toBeNull());
}

afterEach(() => {
  router?.detener();
  router = undefined;
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});

describe('Páginas disponibles', () => {
  it('Abrir la página de reserva por su dirección', () => {
    abrirEn('#/reserva', sinSesion());
    expect(titulo()).toBe('Reservar turno');
  });

  it('Abrir la página de ingreso por su dirección', () => {
    abrirEn('#/ingreso');
    expect(titulo()).toBe('Ingresar');
    const textos = [...raiz.querySelectorAll('nav a')].map((a) => a.textContent);
    expect(textos).not.toContain('Ingresar');
  });

  it('Abrir la cuenta del paciente por su dirección', async () => {
    abrirEn('#/mi-cuenta', sinSesion());
    await esperarTitulo('Mi cuenta');
    const textos = [...raiz.querySelectorAll('nav a')].map((a) => a.textContent);
    expect(textos).not.toContain('Mi cuenta');
  });
});

describe('Página inicial y direcciones desconocidas', () => {
  it('Abrir la aplicación sin dirección', async () => {
    abrirEn('');
    await esperarTitulo('Agenda');
    expect(window.location.hash).toBe('#/agenda');
  });

  it('Dirección desconocida', async () => {
    abrirEn('#/no-existe');
    await esperarTitulo('Agenda');
    expect(window.location.hash).toBe('#/agenda');
  });
});

describe('Navegación sin recargar', () => {
  it('Ir de Agenda a Configuración', async () => {
    abrirEn('#/agenda');
    const marcaDelDocumento = Symbol('mismo-documento');
    Reflect.set(window, 'marcaDelDocumento', marcaDelDocumento);

    await elegir('Configuración');

    expect(titulo()).toBe('Configuración');
    expect(Reflect.get(window, 'marcaDelDocumento')).toBe(marcaDelDocumento);
    expect(enlacesActivos()).toEqual(['Configuración']);
  });

  it('Volver con el botón Atrás', async () => {
    abrirEn('#/agenda');
    await elegir('Configuración');

    const cambio = esperarHashchange();
    history.back();
    await cambio;

    await esperarTitulo('Agenda');
  });

  it('Ir de Reservar turno a Mis turnos', async () => {
    abrirEn('#/reserva', conPaciente());
    await esperarCargaDeReserva();
    const marcaDelDocumento = Symbol('mismo-documento');
    Reflect.set(window, 'marcaDelDocumento', marcaDelDocumento);

    await elegir('Mis turnos');

    await esperarTitulo('Mis turnos');
    expect(Reflect.get(window, 'marcaDelDocumento')).toBe(marcaDelDocumento);
    expect(enlacesActivos()).toEqual(['Mis turnos']);
  });
});

describe('Menú adaptable a pantallas chicas', () => {
  it('Abrir el menú en el celular', async () => {
    abrirEn('#/reserva', sinSesion());
    await esperarCargaDeReserva();
    expect(botonMenu().getAttribute('aria-expanded')).toBe('false');
    expect(menu().classList.contains('abierto')).toBe(false);

    botonMenu().click();

    expect(menu().classList.contains('abierto')).toBe(true);
    expect(enlacesVisibles()).toEqual(['Reservar turno']);
    expect(botonMenu().getAttribute('aria-expanded')).toBe('true');
  });

  it('Elegir una página en el celular', async () => {
    abrirEn('#/mi-cuenta', sinSesion());
    await esperarTitulo('Mi cuenta');
    botonMenu().click();

    await elegir('Reservar turno');

    expect(titulo()).toBe('Reservar turno');
    expect(menu().classList.contains('abierto')).toBe(false);
    expect(botonMenu().getAttribute('aria-expanded')).toBe('false');
  });

  it('Menú siempre visible en la computadora', async () => {
    // jsdom no evalúa media queries: acá se comprueba que, con una sesión del personal,
    // sus dos enlaces están en el menú sin tocar el botón. Lo visual se verifica con capturas.
    abrirEn('#/agenda');
    await esperarTitulo('Agenda');
    expect(enlacesVisibles()).toEqual(['Agenda', 'Configuración']);
    expect(menu().hidden).toBe(false);
  });
});

describe('Menú según la sesión', () => {
  it('Menú de un visitante', async () => {
    abrirEn('#/reserva', sinSesion());
    await esperarCargaDeReserva();

    expect(enlacesVisibles()).toEqual(['Reservar turno']);
    const ingresar = enlaceIngresar();
    expect(ingresar?.hidden).toBe(false);
    expect(ingresar?.getAttribute('href')).toBe('#/mi-cuenta');
  });

  it('Menú del personal', async () => {
    abrirEn('#/agenda');
    await esperarTitulo('Agenda');

    expect(enlacesVisibles()).toEqual(['Agenda', 'Configuración']);
  });

  it('El menú cambia al ingresar como paciente', async () => {
    document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
    const servidor = abrirEn('#/mi-cuenta', sinSesion());
    await esperarTitulo('Mi cuenta');
    expect(enlacesVisibles()).toEqual(['Reservar turno']);
    servidor.responder('POST /public/login', () => json(200, SESION_PACIENTE));
    servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
    servidor.responder('GET /paciente/turnos', () => json(200, []));

    escribir(campo(raiz, 'Email'), 'paciente.uno@example.com');
    escribir(campo(raiz, 'Contraseña'), 'clave-ficticia-1');
    raiz.querySelector<HTMLFormElement>('form[aria-label="Ingresar"]')?.requestSubmit();

    await esperarTitulo('Mis turnos');
    expect(enlacesVisibles()).toEqual(['Reservar turno', 'Mis turnos']);
    const usuario = raiz.querySelector<HTMLElement>('.usuario-nombre');
    expect(usuario?.hidden).toBe(false);
    expect(usuario?.textContent).toBe('paciente.uno@example.com');
    const salir = [...raiz.querySelectorAll<HTMLButtonElement>('header button')].find((b) => b.textContent === 'Salir');
    expect(salir?.hidden).toBe(false);
    expect(enlaceIngresar()?.hidden).toBe(true);
  });
});
