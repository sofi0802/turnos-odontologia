import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { crearClienteApi } from '../src/shared/api/cliente.ts';
import { cerrarSesion, consultarSesion, esPersonal, iniciarSesion } from '../src/shared/sesion/sesion.ts';

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
