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
  const encontrado = [...raiz.querySelectorAll<HTMLAnchorElement>('aside nav a')].find((a) => a.textContent === texto);
  if (encontrado === undefined) {
    throw new Error(`No existe el enlace ${texto}`);
  }
  return encontrado;
}

/** Enlaces del menú que se ven: los del otro grupo quedan ocultos (design D3 de C-12). */
/**
 * Enlaces visibles del menú principal de la barra lateral. Desde C-41 el personal también tiene "Mi contraseña" en
 * una navegación aparte ("Tu cuenta"), que prueba `cuentas-personal.test.ts`.
 */
function enlacesVisibles(): string[] {
  return [...raiz.querySelectorAll<HTMLAnchorElement>('aside nav[aria-label="Principal"] li:not([hidden]) a')].map(
    (a) => a.textContent ?? '',
  );
}

/** Enlace "Ingresar" del encabezado, fuera del menú. */
function enlaceIngresar(): HTMLAnchorElement | undefined {
  return [...raiz.querySelectorAll<HTMLAnchorElement>('header a')].find(
    (a) => a.textContent === 'Ingresar' && a.closest('aside') === null,
  );
}

function enlaceDelContenido(texto: string): HTMLAnchorElement | undefined {
  return [...raiz.querySelectorAll<HTMLAnchorElement>('main a')].find((a) => a.textContent === texto);
}

function enlacesActivos(): string[] {
  return [...raiz.querySelectorAll('aside nav a[aria-current="page"]')].map((a) => a.textContent ?? '');
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

  it('Abrir la portada por su dirección', async () => {
    abrirEn('#/inicio', sinSesion());
    await vi.waitFor(() => expect(document.title).toBe('Turnos odontología'));
    const textos = [...raiz.querySelectorAll('nav a')].map((a) => a.textContent);
    expect(textos).not.toContain('Inicio');
    expect(textos).not.toContain('Turnos odontología');
  });

  it('Abrir la página de ingreso por su dirección', async () => {
    abrirEn('#/ingreso', sinSesion());
    await esperarTitulo('Mi cuenta');
    expect(window.location.hash).toBe('#/mi-cuenta');
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
    abrirEn('', sinSesion());
    await vi.waitFor(() => expect(document.title).toBe('Turnos odontología'));
    expect(window.location.hash).toBe('#/inicio');
  });

  it('Dirección desconocida', async () => {
    abrirEn('#/no-existe', sinSesion());
    await vi.waitFor(() => expect(document.title).toBe('Turnos odontología'));
    expect(window.location.hash).toBe('#/inicio');
  });

  it('Abrir la aplicación sin dirección con sesión del personal', async () => {
    abrirEn('');
    await vi.waitFor(() => expect(document.title).toBe('Turnos odontología'));
    expect(window.location.hash).toBe('#/inicio');
    await vi.waitFor(() => expect(enlaceDelContenido('Ir a la agenda')?.getAttribute('href')).toBe('#/agenda'));
  });
});

describe('Navegación sin recargar', () => {
  it('Ir de Agenda a Configuración', async () => {
    abrirEn('#/agenda');
    const marcaDelDocumento = Symbol('mismo-documento');
    Reflect.set(window, 'marcaDelDocumento', marcaDelDocumento);

    await elegir('Configuración');

    // Desde C-34, Configuración verifica la sesión del personal antes de dibujarse, como Agenda.
    await esperarTitulo('Configuración');
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

/** Espera a que el encabezado muestre al usuario de la sesión (la barra lateral aparece con él). */
async function esperarUsuario(): Promise<void> {
  await vi.waitFor(() => expect(raiz.querySelector<HTMLElement>('.usuario-nombre')?.hidden).toBe(false));
}

function barraLateral(): HTMLElement {
  const aside = raiz.querySelector<HTMLElement>('aside');
  if (aside === null) {
    throw new Error('No existe la barra lateral');
  }
  return aside;
}

describe('Menú adaptable a pantallas chicas', () => {
  it('Abrir el menú en el celular', async () => {
    abrirEn('#/reserva', conPaciente());
    await esperarCargaDeReserva();
    await esperarUsuario();
    expect(botonMenu().getAttribute('aria-expanded')).toBe('false');
    expect(menu().classList.contains('abierto')).toBe(false);

    botonMenu().click();

    expect(menu().classList.contains('abierto')).toBe(true);
    expect(enlacesVisibles()).toEqual(['Reservar turno', 'Mis turnos']);
    expect(botonMenu().getAttribute('aria-expanded')).toBe('true');
  });

  it('Elegir una página en el celular', async () => {
    abrirEn('#/reserva', conPaciente());
    await esperarCargaDeReserva();
    await esperarUsuario();
    botonMenu().click();

    await elegir('Mis turnos');

    await esperarTitulo('Mis turnos');
    expect(menu().classList.contains('abierto')).toBe(false);
    expect(botonMenu().getAttribute('aria-expanded')).toBe('false');
  });

  it('Cerrar el cajón con Escape o tocando fuera', async () => {
    abrirEn('#/reserva', conPaciente());
    await esperarCargaDeReserva();
    await esperarUsuario();
    botonMenu().click();
    expect(menu().classList.contains('abierto')).toBe(true);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(menu().classList.contains('abierto')).toBe(false);
    expect(botonMenu().getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(botonMenu());

    botonMenu().click();
    expect(menu().classList.contains('abierto')).toBe(true);
    raiz.querySelector<HTMLElement>('.velo')?.click();
    expect(menu().classList.contains('abierto')).toBe(false);
  });

  it('el foco pasa al cajón al abrirlo', async () => {
    abrirEn('#/reserva', conPaciente());
    await esperarCargaDeReserva();
    await esperarUsuario();

    botonMenu().click();

    expect(barraLateral().contains(document.activeElement)).toBe(true);
  });

  it('Menú siempre visible en la computadora', async () => {
    // jsdom no evalúa media queries: acá se comprueba que, con una sesión del personal, la barra lateral
    // está en el documento con sus dos enlaces sin tocar el botón. Lo visual se verifica con capturas.
    abrirEn('#/agenda');
    await esperarTitulo('Agenda');
    // C-32: la recepción también ve "Reportes" (delta de frontend-navegacion).
    expect(enlacesVisibles()).toEqual(['Agenda', 'Reportes', 'Configuración']);
    expect(menu().hidden).toBe(false);
  });
});

describe('Menú según la sesión', () => {
  it('Menú de un visitante', async () => {
    abrirEn('#/reserva', sinSesion());
    await esperarCargaDeReserva();

    expect(barraLateral().hidden).toBe(true);
    const reservar = [...raiz.querySelectorAll<HTMLAnchorElement>('header a')].find((a) => a.textContent === 'Reservar turno');
    expect(reservar?.getAttribute('href')).toBe('#/reserva');
    const ingresar = enlaceIngresar();
    expect(ingresar?.hidden).toBe(false);
    expect(ingresar?.getAttribute('href')).toBe('#/mi-cuenta');
  });

  it('Menú del personal', async () => {
    abrirEn('#/agenda');
    await esperarTitulo('Agenda');

    // C-32: la recepción también ve "Reportes" (delta de frontend-navegacion).
    expect(enlacesVisibles()).toEqual(['Agenda', 'Reportes', 'Configuración']);
    expect(enlacesActivos()).toEqual(['Agenda']);
    expect(barraLateral().hidden).toBe(false);
  });

  it('El menú cambia al ingresar como paciente', async () => {
    document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
    const servidor = abrirEn('#/mi-cuenta', sinSesion());
    await esperarTitulo('Mi cuenta');
    expect(barraLateral().hidden).toBe(true);
    servidor.responder('POST /public/login', () => json(200, SESION_PACIENTE));
    servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
    servidor.responder('GET /paciente/turnos', () => json(200, []));

    escribir(campo(raiz, 'Email'), 'paciente.uno@example.com');
    escribir(campo(raiz, 'Contraseña'), 'clave-ficticia-1');
    raiz.querySelector<HTMLFormElement>('form[aria-label="Ingresar"]')?.requestSubmit();

    await esperarTitulo('Mis turnos');
    expect(barraLateral().hidden).toBe(false);
    expect(enlacesVisibles()).toEqual(['Reservar turno', 'Mis turnos']);
    const usuario = raiz.querySelector<HTMLElement>('.usuario-nombre');
    expect(usuario?.hidden).toBe(false);
    expect(usuario?.textContent).toBe('paciente.uno@example.com');
    const salir = [...raiz.querySelectorAll<HTMLButtonElement>('header button')].find((b) => b.textContent === 'Salir');
    expect(salir?.hidden).toBe(false);
    expect(enlaceIngresar()?.hidden).toBe(true);
  });
});

describe('Página "Reportes" para administración y recepción (C-32)', () => {
  const SESION_ADMIN = { autenticado: true, username: 'admin', rol: 'ADMIN' };
  const SESION_ODONTOLOGO = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };

  function conSesion(sesion: Record<string, unknown>): ServidorFalso {
    const servidor = new ServidorFalso();
    servidor.responder('GET /public/sesion', () => json(200, sesion));
    return servidor;
  }

  it('Menú del administrador', async () => {
    abrirEn('#/agenda', conSesion(SESION_ADMIN));

    await vi.waitFor(() =>
      expect(enlacesVisibles()).toEqual(['Agenda', 'Pacientes', 'Reportes', 'Configuración']),
    );
  });

  it('El odontólogo no ve Reportes', async () => {
    abrirEn('#/agenda', conSesion(SESION_ODONTOLOGO));

    await vi.waitFor(() => expect(enlacesVisibles()).toEqual(['Agenda', 'Pacientes', 'Configuración']));
    expect(enlacesVisibles()).not.toContain('Reportes');
  });

  it('El odontólogo abre la dirección', async () => {
    const servidor = abrirEn('#/reportes', conSesion(SESION_ODONTOLOGO));

    await vi.waitFor(() =>
      expect(raiz.querySelector('main [role="alert"]')?.textContent).toBe(
        'Esta sección es solo para administración y recepción.',
      ),
    );
    expect(servidor.a('GET', '/reportes')).toEqual([]);
  });

  it('Sin sesión en Reportes', async () => {
    abrirEn('#/reportes', sinSesion());

    await esperarTitulo('Mi cuenta');
  });
});

describe('Página "Pacientes" para odontólogos y administradores (C-44)', () => {
  const SESION_ODONTOLOGO = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };

  /** Pedidos de datos de pacientes o de su ficha clínica. */
  function pedidosDePacientes(servidor: ServidorFalso): string[] {
    return servidor.pedidos.map((p) => p.ruta).filter((ruta) => ruta.startsWith('/pacientes'));
  }

  it('Menú del odontólogo', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /public/sesion', () => json(200, SESION_ODONTOLOGO));
    abrirEn('#/agenda', servidor);

    await vi.waitFor(() => expect(enlacesVisibles()).toEqual(['Agenda', 'Pacientes', 'Configuración']));
  });

  it('Menú de la recepción', async () => {
    abrirEn('#/agenda');
    await esperarTitulo('Agenda');

    expect(enlacesVisibles()).not.toContain('Pacientes');
  });

  it('La recepción abre la dirección', async () => {
    const servidor = abrirEn('#/pacientes');

    await vi.waitFor(() =>
      expect(raiz.querySelector('main [role="alert"]')?.textContent).toBe(
        'Esta sección es solo para odontólogos y administradores.',
      ),
    );
    expect(pedidosDePacientes(servidor)).toEqual([]);
  });

  it('Sin sesión', async () => {
    abrirEn('#/pacientes', sinSesion());

    await esperarTitulo('Mi cuenta');
  });
});
