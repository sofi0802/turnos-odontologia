import { describe, expect, it } from 'vitest';
import {
  ErrorGenerico,
  ErrorNegocio,
  ErrorNoEncontrado,
  ErrorPermiso,
  ErrorValidacion,
  MENSAJE_ERROR_GENERICO,
} from '../src/shared/api/errores.ts';
import { MENSAJES_POR_CODIGO, mensajeDeError } from '../src/shared/ui/mensajes.ts';

describe('Mensajes por código', () => {
  it.each([
    ['SOLAPAMIENTO_PROFESIONAL', 'El profesional ya tiene un turno en ese horario.'],
    ['SOLAPAMIENTO_SILLON', 'El sillón ya está ocupado en ese horario.'],
    ['MARGEN_INSUFICIENTE', 'No queda el margen mínimo entre turnos.'],
    ['INICIO_EN_EL_PASADO', 'El horario elegido ya pasó.'],
    ['RECURSO_INACTIVO', 'El profesional, el sillón o la prestación está inactivo.'],
    ['TURNO_NO_ACTIVO', 'El turno ya no está activo.'],
    ['FUERA_DE_HORARIO', 'El profesional no atiende en ese horario.'],
    ['PROFESIONAL_AUSENTE', 'El profesional tiene registrada una ausencia en ese horario.'],
    ['AGENDA_BLOQUEADA', 'La agenda del profesional está bloqueada en ese horario.'],
  ])('%s tiene su mensaje propio', (codigo, mensaje) => {
    expect(mensajeDeError(new ErrorNegocio(codigo, 'detalle del servidor'))).toBe(mensaje);
    expect(MENSAJES_POR_CODIGO[codigo]).toBe(mensaje);
  });

  it('un código desconocido usa el detail de la API', () => {
    expect(mensajeDeError(new ErrorNegocio('OTRO_CODIGO', 'Regla ficticia'))).toBe('Regla ficticia');
  });

  it('un código desconocido sin detail usa el mensaje genérico', () => {
    expect(mensajeDeError(new ErrorNegocio('OTRO_CODIGO', ''))).toBe(MENSAJE_ERROR_GENERICO);
  });

  it('permiso, inexistente, validación y fallas genéricas', () => {
    expect(mensajeDeError(new ErrorPermiso('ACCESO_DENEGADO'))).toBe('No tenés permiso para esta acción.');
    expect(mensajeDeError(new ErrorNoEncontrado('TURNO_INEXISTENTE'))).toBe('El turno ya no existe.');
    expect(mensajeDeError(new ErrorValidacion('Hay datos inválidos en el pedido', 'DATOS_INVALIDOS'))).toBe(
      'Hay datos inválidos en el pedido',
    );
    expect(mensajeDeError(new ErrorGenerico(500))).toBe(MENSAJE_ERROR_GENERICO);
    expect(mensajeDeError(new TypeError('interno'))).toBe(MENSAJE_ERROR_GENERICO);
  });
});
