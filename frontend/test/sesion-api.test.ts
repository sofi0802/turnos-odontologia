import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { crearClienteApi } from '../src/shared/api/cliente.ts';
import {
  cerrarSesion,
  consultarSesion,
  esPaciente,
  esPersonal,
  iniciarSesion,
  registrarPaciente,
} from '../src/shared/sesion/sesion.ts';

const URL_BASE = 'http://localhost:8080/api/v1';

function borrarCookie(): void {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
}

function respuesta(estado: number, cuerpo?: unknown): Response {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status: estado });
}

function rutas(fetchSimulado: ReturnType<typeof vi.fn<typeof fetch>>): string[] {
  return fetchSimulado.mock.calls.map(([entrada, opciones]) => `${opciones?.method ?? 'GET'} ${String(entrada).replace(URL_BASE, '')}`);
}

describe('Módulo de sesión', () => {
  beforeEach(borrarCookie);
  afterEach(borrarCookie);

  it('consulta la sesión del personal', async () => {
    const fetchSimulado = vi.fn<typeof fetch>().mockResolvedValue(
      respuesta(200, { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' }),
    );

    const sesion = await consultarSesion(crearClienteApi(URL_BASE, fetchSimulado));

    expect(sesion).toEqual({ autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' });
    expect(rutas(fetchSimulado)).toEqual(['GET /public/sesion']);
  });

  it('la sesión del odontólogo trae su profesional (RN-AG-18)', async () => {
    const fetchSimulado = vi.fn<typeof fetch>().mockResolvedValue(
      respuesta(200, { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 }),
    );

    const sesion = await consultarSesion(crearClienteApi(URL_BASE, fetchSimulado));

    expect(sesion).toEqual({ autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 });
  });

  it('solo el odontólogo con un id válido tiene profesional', async () => {
    const casos: unknown[] = [
      { autenticado: true, username: 'admin', rol: 'ADMIN', profesionalId: 2 },
      { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 0 },
      { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: '1' },
      { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1.5 },
    ];
    for (const cuerpo of casos) {
      const fetchSimulado = vi.fn<typeof fetch>().mockResolvedValue(respuesta(200, cuerpo));

      const sesion = await consultarSesion(crearClienteApi(URL_BASE, fetchSimulado));

      expect(sesion.profesionalId).toBeUndefined();
    }
  });

  it('una respuesta sin sesión o con forma desconocida es "sin sesión"', async () => {
    const fetchSimulado = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(respuesta(200, { autenticado: false }))
      .mockResolvedValueOnce(respuesta(200, { autenticado: true, username: 'x', rol: 'DESCONOCIDO' }));
    const api = crearClienteApi(URL_BASE, fetchSimulado);

    expect(await consultarSesion(api)).toEqual({ autenticado: false, username: null, rol: null });
    expect(await consultarSesion(api)).toEqual({ autenticado: false, username: null, rol: null });
  });

  it('el login pide antes la sesión si no existe la cookie CSRF', async () => {
    const fetchSimulado = vi.fn<typeof fetch>().mockImplementation(async (entrada) => {
      if (String(entrada).endsWith('/public/sesion')) {
        document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
        return respuesta(200, { autenticado: false });
      }
      return respuesta(200, { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' });
    });

    const sesion = await iniciarSesion(crearClienteApi(URL_BASE, fetchSimulado), 'recepcion', 'clave-ficticia');

    expect(rutas(fetchSimulado)).toEqual(['GET /public/sesion', 'POST /public/login']);
    const [, opciones] = fetchSimulado.mock.calls[1] ?? [];
    expect(new Headers(opciones?.headers).get('X-XSRF-TOKEN')).toBe('token-ficticio-123');
    expect(opciones?.body).toBe(JSON.stringify({ username: 'recepcion', password: 'clave-ficticia' }));
    expect(sesion.rol).toBe('RECEPCIONISTA');
  });

  it('el login no pide la sesión si la cookie CSRF ya existe', async () => {
    document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
    const fetchSimulado = vi
      .fn<typeof fetch>()
      .mockResolvedValue(respuesta(200, { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' }));

    await iniciarSesion(crearClienteApi(URL_BASE, fetchSimulado), 'recepcion', 'clave-ficticia');

    expect(rutas(fetchSimulado)).toEqual(['POST /public/login']);
  });

  it('cerrar sesión no falla aunque falle el servidor', async () => {
    const fetchSimulado = vi.fn<typeof fetch>().mockResolvedValue(respuesta(500));

    await expect(cerrarSesion(crearClienteApi(URL_BASE, fetchSimulado))).resolves.toBeUndefined();
    expect(rutas(fetchSimulado)).toEqual(['POST /logout']);
  });

  it('solo los roles del personal son personal', () => {
    expect(esPersonal('ADMIN')).toBe(true);
    expect(esPersonal('RECEPCIONISTA')).toBe(true);
    expect(esPersonal('ODONTOLOGO')).toBe(true);
    expect(esPersonal('PACIENTE')).toBe(false);
    expect(esPersonal(null)).toBe(false);
  });
});

describe('Registro del paciente (C-12)', () => {
  beforeEach(borrarCookie);
  afterEach(borrarCookie);

  const datos = {
    nombre: 'Paciente',
    apellido: 'Uno',
    dni: '99000001',
    email: 'paciente.uno@example.com',
    telefono: null,
    password: 'clave-ficticia-1',
  };

  it('pide la sesión antes si no hay cookie CSRF y envía el registro sin la repetición', async () => {
    const fetchSimulado = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(respuesta(200, { autenticado: false }))
      .mockResolvedValueOnce(respuesta(201, { username: 'paciente.uno@example.com', rol: 'PACIENTE' }));

    await registrarPaciente(crearClienteApi(URL_BASE, fetchSimulado), datos);

    expect(rutas(fetchSimulado)).toEqual(['GET /public/sesion', 'POST /public/pacientes/registro']);
    const cuerpo = JSON.parse(String(fetchSimulado.mock.calls[1]?.[1]?.body)) as Record<string, unknown>;
    expect(cuerpo).toEqual({
      nombre: 'Paciente',
      apellido: 'Uno',
      dni: '99000001',
      email: 'paciente.uno@example.com',
      password: 'clave-ficticia-1',
    });
  });

  it('con cookie CSRF envía solo el registro, con el teléfono si lo hay', async () => {
    document.cookie = 'XSRF-TOKEN=token-ficticio; path=/';
    const fetchSimulado = vi.fn<typeof fetch>().mockResolvedValue(respuesta(201, {}));

    await registrarPaciente(crearClienteApi(URL_BASE, fetchSimulado), { ...datos, telefono: '1100000000' });

    expect(rutas(fetchSimulado)).toEqual(['POST /public/pacientes/registro']);
    const cuerpo = JSON.parse(String(fetchSimulado.mock.calls[0]?.[1]?.body)) as Record<string, unknown>;
    expect(cuerpo['telefono']).toBe('1100000000');
  });

  it('esPaciente solo con el rol PACIENTE', () => {
    expect(esPaciente('PACIENTE')).toBe(true);
    expect(esPaciente('RECEPCIONISTA')).toBe(false);
    expect(esPaciente(null)).toBe(false);
  });
});
