import { beforeEach, describe, expect, it } from 'vitest';
import {
  cambiarMiConsentimiento,
  fechaDelCambio,
  leerConsentimiento,
  obtenerConsentimiento,
  registrarConsentimiento,
} from '../src/consentimiento/api.ts';
import { ErrorNoEncontrado, ErrorValidacion } from '../src/shared/api/errores.ts';
import { json, ServidorFalso } from './apoyo.ts';

// C-17, tarea 6.1: los pedidos de la API del consentimiento y sus rechazos. Datos ficticios.

let servidor: ServidorFalso;

beforeEach(() => {
  servidor = new ServidorFalso();
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
});

describe('API del consentimiento', () => {
  it('el paciente cambia el suyo sin mandar ningún id', async () => {
    servidor.responder('PUT /paciente/consentimiento-comunicaciones', () =>
      json(200, { otorgado: true, actualizadoEn: '2026-12-01T13:00:00Z' }),
    );

    const estado = await cambiarMiConsentimiento(servidor.api, true);

    expect(estado).toEqual({ otorgado: true, actualizadoEn: '2026-12-01T13:00:00Z' });
    const pedido = servidor.ultimo('PUT', '/paciente/consentimiento-comunicaciones');
    expect(pedido.cuerpo).toEqual({ otorgado: true });
    expect(pedido.encabezados.get('X-XSRF-TOKEN')).toBe('token-ficticio-123');
  });

  it('la recepción registra con el medio en el recurso del paciente', async () => {
    servidor.responder('PUT /pacientes/{id}/consentimiento-comunicaciones', () =>
      json(200, { otorgado: false, actualizadoEn: '2026-12-01T13:00:00Z', historial: [] }),
    );

    const estado = await registrarConsentimiento(servidor.api, 2, false, 'PRESENCIAL');

    expect(estado.otorgado).toBe(false);
    expect(servidor.ultimo('PUT', '/pacientes/2/consentimiento-comunicaciones').cuerpo).toEqual({
      otorgado: false,
      medio: 'PRESENCIAL',
    });
  });

  it('un 422 llega como error de validación', async () => {
    servidor.responder('PUT /pacientes/{id}/consentimiento-comunicaciones', () =>
      json(422, { codigo: 'DATOS_INVALIDOS', errores: [{ campo: 'medio', mensaje: 'obligatorio' }] }),
    );

    await expect(registrarConsentimiento(servidor.api, 2, true, 'TELEFONO')).rejects.toBeInstanceOf(ErrorValidacion);
  });

  it('un 404 llega como no encontrado', async () => {
    servidor.responder('GET /pacientes/{id}/consentimiento-comunicaciones', () =>
      json(404, { codigo: 'RECURSO_NO_ENCONTRADO' }),
    );

    await expect(obtenerConsentimiento(servidor.api, 999)).rejects.toBeInstanceOf(ErrorNoEncontrado);
  });

  it('una respuesta incompleta se lee como sin consentimiento', () => {
    expect(leerConsentimiento(undefined)).toEqual({ otorgado: false, actualizadoEn: null });
    expect(leerConsentimiento({ otorgado: 'true', actualizadoEn: 5 })).toEqual({ otorgado: false, actualizadoEn: null });
  });

  it('la fecha del cambio va en la hora del consultorio', () => {
    expect(fechaDelCambio('2026-12-01T13:00:00Z')).toBe('01/12/2026');
    // 01:30 en UTC todavía es el 30/11 en Buenos Aires.
    expect(fechaDelCambio('2026-12-01T01:30:00Z')).toBe('30/11/2026');
  });
});
