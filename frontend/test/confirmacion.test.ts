import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { iniciarApp } from '../src/app.ts';
import { olvidarTokenDeConfirmacion, traducirEntrada } from '../src/shared/ui/entrada.ts';
import type { Router } from '../src/shared/ui/router.ts';
import { boton, json, ServidorFalso, texto } from './apoyo.ts';

// Datos ficticios: el recordatorio de un turno RESERVADO del 2026-12-02 a las 07:30 con "Ana Ejemplo"
// lleva el link /confirmar/abc123.
let servidor: ServidorFalso;
let raiz: HTMLElement;
let router: Router | undefined;

function abrirEn(direccion: string): void {
  raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  history.replaceState(null, '', direccion);
  traducirEntrada();
  router = iniciarApp(raiz, servidor.api);
}

function titulo(): string | null {
  return raiz.querySelector('main h1')?.textContent ?? null;
}

function botonConfirmar(): HTMLButtonElement | undefined {
  return [...raiz.querySelectorAll('button')].find((b) => b.textContent === 'Confirmar mi asistencia');
}

async function confirmarCon(respuesta: () => Response): Promise<void> {
  servidor.responder('POST /public/confirmaciones/abc123', respuesta);
  abrirEn('/confirmar/abc123');
  await vi.waitFor(() => expect(titulo()).toBe('Confirmar asistencia'));
  boton(raiz, 'Confirmar mi asistencia').click();
}

beforeEach(() => {
  servidor = new ServidorFalso();
  servidor.sinSesion();
  olvidarTokenDeConfirmacion();
});

afterEach(() => {
  router?.detener();
  router = undefined;
  history.replaceState(null, '', '/');
  olvidarTokenDeConfirmacion();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});

describe('Pantalla de confirmación sin confirmar sola', () => {
  it('Abrir el link del recordatorio', async () => {
    abrirEn('/confirmar/abc123');

    await vi.waitFor(() => expect(titulo()).toBe('Confirmar asistencia'));
    expect(`${window.location.pathname}${window.location.hash}`).toBe('/#/confirmar');
    expect(botonConfirmar()).toBeDefined();
    expect(servidor.pedidos.filter((p) => p.ruta.startsWith('/public/confirmaciones'))).toHaveLength(0);
    expect(document.body.textContent).not.toContain('abc123');
  });

  it('sin token muestra el aviso de link inválido sin botón', async () => {
    abrirEn('/#/confirmar');

    await vi.waitFor(() => expect(titulo()).toBe('Confirmar asistencia'));
    expect(texto(raiz)).toContain('El link no es válido. Revisá que esté completo.');
    expect(botonConfirmar()).toBeUndefined();
  });
});

describe('Asistencia confirmada', () => {
  it('Confirmación correcta', async () => {
    await confirmarCon(() =>
      json(200, { fecha: '2026-12-02', hora: '07:30', profesional: 'Ana Ejemplo', estado: 'CONFIRMADO' }),
    );

    await vi.waitFor(() =>
      expect(texto(raiz)).toContain('Listo, confirmaste tu turno del miércoles 2 de diciembre a las 07:30 con Ana Ejemplo.'),
    );
    expect(servidor.a('POST', '/public/confirmaciones/abc123')).toHaveLength(1);
    expect(botonConfirmar()).toBeUndefined();
  });

  it('envía el token CSRF de la cookie y nunca confirma con GET', async () => {
    document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';

    await confirmarCon(() =>
      json(200, { fecha: '2026-12-02', hora: '07:30', profesional: 'Ana Ejemplo', estado: 'CONFIRMADO' }),
    );

    await vi.waitFor(() => expect(servidor.a('POST', '/public/confirmaciones/abc123')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/public/confirmaciones/abc123').encabezados.get('X-XSRF-TOKEN')).toBe(
      'token-ficticio-123',
    );
    expect(servidor.pedidos.filter((p) => p.metodo === 'GET' && p.ruta.startsWith('/public/confirmaciones'))).toHaveLength(0);
  });

  it('sin cookie CSRF pide la sesión antes del POST', async () => {
    await confirmarCon(() =>
      json(200, { fecha: '2026-12-02', hora: '07:30', profesional: 'Ana Ejemplo', estado: 'CONFIRMADO' }),
    );

    await vi.waitFor(() => expect(servidor.a('POST', '/public/confirmaciones/abc123')).toHaveLength(1));
    const orden = servidor.pedidos.map((p) => `${p.metodo} ${p.ruta}`);
    expect(orden.lastIndexOf('GET /public/sesion')).toBeLessThan(orden.indexOf('POST /public/confirmaciones/abc123'));
  });
});

describe('Confirmación rechazada', () => {
  it('Link ya usado', async () => {
    await confirmarCon(() => json(409, { codigo: 'TOKEN_USADO' }));

    await vi.waitFor(() => expect(texto(raiz)).toContain('Este turno ya estaba confirmado.'));
    expect(botonConfirmar()).toBeUndefined();
  });

  it('Link inválido', async () => {
    await confirmarCon(() => json(404, { codigo: 'TOKEN_INEXISTENTE' }));

    await vi.waitFor(() => expect(texto(raiz)).toContain('El link no es válido. Revisá que esté completo.'));
    expect(botonConfirmar()).toBeUndefined();
  });

  it('Turno que ya no se puede confirmar', async () => {
    await confirmarCon(() => json(409, { codigo: 'TURNO_NO_CONFIRMABLE' }));

    await vi.waitFor(() =>
      expect(texto(raiz)).toContain(
        'Este turno ya no se puede confirmar. Si tenés dudas, contactá al consultorio.',
      ),
    );
    expect(botonConfirmar()).toBeUndefined();
  });
});
