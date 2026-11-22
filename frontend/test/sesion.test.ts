import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, boton, campo, errorDeCampo, escribir, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

function titulo(): string | null {
  return app?.raiz.querySelector('main h1')?.textContent ?? null;
}

beforeEach(() => {
  servidor = new ServidorFalso();
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});

describe('Página de ingreso del personal', () => {
  it('Recepción ingresa con credenciales correctas', async () => {
    servidor.sinSesion();
    servidor.responder('POST /public/login', () =>
      json(200, { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' }),
    );
    app = abrirApp(servidor, '#/ingreso');
    await vi.waitFor(() => expect(titulo()).toBe('Ingresar'));
    servidor.responder('GET /public/sesion', () =>
      json(200, { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' }),
    );

    escribir(campo(app.raiz, 'Usuario'), 'recepcion');
    escribir(campo(app.raiz, 'Contraseña'), 'clave-ficticia');
    boton(app.raiz, 'Ingresar').click();

    await vi.waitFor(() => expect(titulo()).toBe('Agenda'));
    expect(servidor.ultimo('POST', '/public/login').cuerpo).toEqual({
      username: 'recepcion',
      password: 'clave-ficticia',
    });
    expect(window.location.hash).toBe('#/agenda');
  });

  it('Campos vacíos', async () => {
    app = abrirApp(servidor, '#/ingreso');
    await vi.waitFor(() => expect(titulo()).toBe('Ingresar'));

    escribir(campo(app.raiz, 'Usuario'), 'recepcion');
    boton(app.raiz, 'Ingresar').click();

    expect(errorDeCampo(app.raiz, 'Contraseña')).toBe('Completá la contraseña.');
    expect(errorDeCampo(app.raiz, 'Usuario')).toBe('');
    expect(servidor.a('POST', '/public/login')).toHaveLength(0);
  });
});

describe('Credenciales rechazadas', () => {
  it('Contraseña incorrecta', async () => {
    servidor.responder('POST /public/login', () =>
      json(401, { status: 401, detail: 'Usuario o contraseña inválidos', codigo: 'CREDENCIALES_INVALIDAS' }),
    );
    app = abrirApp(servidor, '#/ingreso');
    await vi.waitFor(() => expect(titulo()).toBe('Ingresar'));

    escribir(campo(app.raiz, 'Usuario'), 'recepcion');
    escribir(campo(app.raiz, 'Contraseña'), 'clave-incorrecta');
    boton(app.raiz, 'Ingresar').click();

    await vi.waitFor(() => expect(texto(app?.raiz ?? document.body)).toContain('Usuario o contraseña incorrectos.'));
    expect(campo(app.raiz, 'Usuario').value).toBe('recepcion');
    expect(campo(app.raiz, 'Contraseña').value).toBe('');
    expect(titulo()).toBe('Ingresar');
  });
});

async function abrirAgendaConSesion(): Promise<AppDePrueba> {
  const abierta = abrirApp(servidor, '#/agenda');
  await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(1));
  await vi.waitFor(() => expect(abierta.raiz.querySelector('.agenda-lista[aria-busy="true"]')).toBeNull());
  return abierta;
}

describe('Sesión vencida', () => {
  it('La sesión vence mientras se usa la agenda', async () => {
    app = await abrirAgendaConSesion();
    boton(app.raiz, 'Semana').click();
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(2));
    servidor.responder('GET /turnos', () => json(401, { status: 401, codigo: 'NO_AUTENTICADO' }));

    boton(app.raiz, 'Siguiente').click();

    await vi.waitFor(() => expect(titulo()).toBe('Ingresar'));
    expect(texto(app.raiz)).toContain('Tu sesión terminó. Ingresá de nuevo.');
    expect(window.location.hash).toBe('#/ingreso');
  });

  it('Acción sin permiso', async () => {
    servidor.responder('POST /turnos', () => json(403, { status: 403, codigo: 'ACCESO_DENEGADO' }));
    app = await abrirAgendaConSesion();
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(app?.raiz ?? document.body, 'Profesional').options.length).toBe(3));
    boton(app.raiz, 'Nuevo turno').click();
    const dialogo = document.querySelector<HTMLDialogElement>('dialog[open]') ?? document.body;
    escribir(campo(dialogo, 'Id del paciente'), '1');
    escribir(campo<HTMLSelectElement>(dialogo, 'Profesional'), '1');
    escribir(campo<HTMLSelectElement>(dialogo, 'Sillón'), '1');
    escribir(campo<HTMLSelectElement>(dialogo, 'Prestación'), '1');
    escribir(campo(dialogo, 'Hora'), '10:00');

    boton(dialogo, 'Crear turno').click();

    await vi.waitFor(() => expect(texto(document.body)).toContain('No tenés permiso para esta acción.'));
    expect(titulo()).toBe('Agenda');
    expect(window.location.hash).toBe('#/agenda');
    document.querySelector('dialog')?.remove();
  });
});

describe('Cierre de sesión', () => {
  it('Recepción cierra la sesión', async () => {
    app = await abrirAgendaConSesion();
    await vi.waitFor(() => expect(texto(app?.raiz.querySelector('header') ?? document.body)).toContain('recepcion'));
    servidor.sinSesion();

    boton(app.raiz, 'Salir').click();

    await vi.waitFor(() => expect(titulo()).toBe('Ingresar'));
    expect(servidor.ultimo('POST', '/logout').encabezados.get('X-XSRF-TOKEN')).toBe('token-ficticio-123');
    expect(texto(app.raiz.querySelector('header') ?? app.raiz)).not.toContain('recepcion');
  });

  it('sale aunque el servidor falle', async () => {
    servidor.responder('POST /logout', () => json(500));
    app = await abrirAgendaConSesion();

    boton(app.raiz, 'Salir').click();

    await vi.waitFor(() => expect(titulo()).toBe('Ingresar'));
  });
});

describe('Páginas privadas solo con sesión del personal', () => {
  it('Abrir la agenda sin sesión', async () => {
    servidor.sinSesion();

    app = abrirApp(servidor, '#/agenda');

    await vi.waitFor(() => expect(titulo()).toBe('Ingresar'));
    expect(window.location.hash).toBe('#/ingreso');
    expect(servidor.a('GET', '/turnos')).toHaveLength(0);
  });

  it('Un paciente abre la agenda', async () => {
    servidor.responder('GET /public/sesion', () =>
      json(200, { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' }),
    );

    app = abrirApp(servidor, '#/agenda');

    await vi.waitFor(() =>
      expect(texto(app?.raiz ?? document.body)).toContain('Esta sección es solo para el personal del consultorio.'),
    );
    expect(servidor.a('GET', '/turnos')).toHaveLength(0);
  });
});
