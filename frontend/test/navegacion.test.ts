import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Router } from '../src/shared/ui/router.ts';
import { abrirApp, ServidorFalso } from './apoyo.ts';

let raiz: HTMLElement;
let router: Router | undefined;

/** Abre la aplicación con una sesión ficticia de `recepcion` (la Agenda es privada desde C-09). */
function abrirEn(hash: string): void {
  const app = abrirApp(new ServidorFalso(), hash);
  raiz = app.raiz;
  router = app.router;
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

afterEach(() => {
  router?.detener();
  router = undefined;
});

describe('Páginas disponibles', () => {
  it('Abrir la página de reserva por su dirección', () => {
    abrirEn('#/reserva');
    expect(titulo()).toBe('Reserva');
  });

  it('Abrir la página de ingreso por su dirección', () => {
    abrirEn('#/ingreso');
    expect(titulo()).toBe('Ingresar');
    const textos = [...raiz.querySelectorAll('nav a')].map((a) => a.textContent);
    expect(textos).not.toContain('Ingresar');
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
});

describe('Menú adaptable a pantallas chicas', () => {
  it('Abrir el menú en el celular', () => {
    abrirEn('#/agenda');
    expect(botonMenu().getAttribute('aria-expanded')).toBe('false');
    expect(menu().classList.contains('abierto')).toBe(false);

    botonMenu().click();

    expect(menu().classList.contains('abierto')).toBe(true);
    expect(menu().querySelectorAll('a')).toHaveLength(3);
    expect(botonMenu().getAttribute('aria-expanded')).toBe('true');
  });

  it('Elegir una página en el celular', async () => {
    abrirEn('#/agenda');
    botonMenu().click();

    await elegir('Reserva');

    expect(titulo()).toBe('Reserva');
    expect(menu().classList.contains('abierto')).toBe(false);
    expect(botonMenu().getAttribute('aria-expanded')).toBe('false');
  });

  it('Menú siempre visible en la computadora', () => {
    // jsdom no evalúa media queries: acá se comprueba que los tres enlaces están
    // en el menú sin tocar el botón. Lo visual se verifica con capturas (design D8).
    abrirEn('#/agenda');
    const textos = [...menu().querySelectorAll('a')].map((a) => a.textContent);
    expect(textos).toEqual(['Agenda', 'Reserva', 'Configuración']);
    expect(menu().hidden).toBe(false);
  });
});
