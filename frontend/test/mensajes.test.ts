import { describe, expect, it } from 'vitest';
import {
  ErrorGenerico,
  ErrorNegocio,
  ErrorNoEncontrado,
  ErrorPermiso,
  ErrorValidacion,
  MENSAJE_ERROR_GENERICO,
} from '../src/shared/api/errores.ts';
import { MENSAJES_POR_CODIGO, mensajeDeError, mensajeDeReprogramacion } from '../src/shared/ui/mensajes.ts';

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
    ['SIN_SILLON_DISPONIBLE', 'No hay ningún sillón libre en ese horario. Probá con otra hora.'],
    ['TURNO_NO_EXTENSIBLE', 'Un sobreturno no se puede extender.'],
    // C-46: rechazos del periodontograma.
    ['SITIOS_INCOMPLETOS', 'Cada pieza cargada necesita sus 6 sitios y la movilidad.'],
    ['FURCACION_NO_APLICA', 'La furcación solo se carga en molares y en las piezas 14 y 24.'],
  ])('%s tiene su mensaje propio', (codigo, mensaje) => {
    expect(mensajeDeError(new ErrorNegocio(codigo, 'detalle del servidor'))).toBe(mensaje);
    expect(MENSAJES_POR_CODIGO[codigo]).toBe(mensaje);
  });

  it.each([
    ['SIN_DISPONIBILIDAD', 'Ese horario se ocupó recién. Elegí otro.'],
    ['CANCELACION_FUERA_DE_PLAZO', 'Faltan menos de 24 h. Para cancelarlo o cambiarlo, contactá al consultorio.'],
    ['RESERVA_INEXISTENTE', 'No encontramos ese turno.'],
    ['PROFESIONAL_NO_HABILITADO', 'Ese profesional no atiende esa prestación. Elegí otro.'],
    [
      'DNI_YA_REGISTRADO',
      'Ya hay un paciente registrado con ese DNI. Si es tuyo, ingresá con tu cuenta o contactá al consultorio.',
    ],
    ['EMAIL_YA_REGISTRADO', 'Ya existe una cuenta con ese email. Ingresá con tu contraseña.'],
  ])('%s de la reserva online tiene su mensaje (C-12)', (codigo, mensaje) => {
    expect(mensajeDeError(new ErrorNegocio(codigo, 'detalle del servidor'))).toBe(mensaje);
  });

  it('RESERVA_INEXISTENTE en un 404 usa la tabla', () => {
    expect(mensajeDeError(new ErrorNoEncontrado('RESERVA_INEXISTENTE'))).toBe('No encontramos ese turno.');
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

describe('Mensajes de rechazo de la reprogramación', () => {
  it('DATOS_DE_RESERVA_INVALIDOS no dice cuál de los dos datos falló', () => {
    const texto = 'El DNI o el número de reserva no son correctos.';
    expect(mensajeDeError(new ErrorNegocio('DATOS_DE_RESERVA_INVALIDOS', 'El DNI no corresponde al turno'))).toBe(texto);
    expect(mensajeDeReprogramacion(new ErrorNegocio('DATOS_DE_RESERVA_INVALIDOS', 'detalle'))).toBe(texto);
  });

  it('SOLAPAMIENTO_SILLON al reprogramar indica elegir otro sillón y la creación conserva su texto', () => {
    const error = new ErrorNegocio('SOLAPAMIENTO_SILLON', 'detalle del servidor');
    expect(mensajeDeReprogramacion(error)).toBe('El sillón ya está ocupado en ese horario. Elegí otro sillón.');
    // la creación de turnos conserva su texto de C-09
    expect(mensajeDeError(error)).toBe('El sillón ya está ocupado en ese horario.');
  });

  it('PROFESIONAL_NO_HABILITADO tiene su texto al reprogramar', () => {
    expect(mensajeDeReprogramacion(new ErrorNegocio('PROFESIONAL_NO_HABILITADO', 'x'))).toBe(
      'Ese profesional no atiende esa prestación. Elegí otro.',
    );
  });

  it.each(['PROFESIONAL_AUSENTE', 'AGENDA_BLOQUEADA', 'FUERA_DE_HORARIO'])(
    '%s tiene su texto propio al reprogramar',
    (codigo) => {
      const texto = mensajeDeReprogramacion(new ErrorNegocio(codigo, ''));
      expect(texto).toBe(MENSAJES_POR_CODIGO[codigo]);
      expect(texto).not.toBe('');
      expect(texto).not.toContain(codigo);
    },
  );

  it('los tres textos de ausencia, bloqueo y horario son distintos entre sí', () => {
    const textos = ['PROFESIONAL_AUSENTE', 'AGENDA_BLOQUEADA', 'FUERA_DE_HORARIO'].map((c) => MENSAJES_POR_CODIGO[c]);
    expect(new Set(textos).size).toBe(3);
  });

  // C-45 le dio texto a SIN_SILLON_DISPONIBLE: el ejemplo de código sin texto pasa a ser otro.
  it.each(['CODIGO_QUE_NO_EXISTE', 'SIN_TEXTO_PROPIO', 'X'])(
    'el código %s sin texto muestra el mensaje genérico, nunca vacío ni el código',
    (codigo) => {
      const texto = mensajeDeReprogramacion(new ErrorNegocio(codigo, ''));
      expect(texto).toBe(MENSAJE_ERROR_GENERICO);
      expect(texto).not.toBe('');
      expect(texto).not.toContain(codigo);
    },
  );
});

describe('Mensajes de configuración del consultorio (C-34)', () => {
  it.each([
    ['MATRICULA_DUPLICADA', 'Ya existe un profesional con esa matrícula.'],
    ['NOMBRE_DUPLICADO', 'Ya existe otro con ese nombre.'],
    ['DURACION_INVALIDA', 'La duración tiene que ser mayor que 0 y múltiplo de 5.'],
    ['PRESTACION_INACTIVA', 'Hay una prestación inactiva entre las elegidas.'],
    ['SILLON_INACTIVO', 'El sillón elegido está inactivo.'],
    ['MARGEN_INVALIDO', 'El margen tiene que ser de 10 minutos o más.'],
    ['HORARIO_INVALIDO', 'Revisá los días y las horas de las franjas.'],
    ['FRANJAS_SUPERPUESTAS', 'Hay franjas del mismo día que se superponen.'],
    ['PERIODO_INVALIDO', 'El fin tiene que ser posterior al inicio.'],
    ['TURNO_NO_AFECTADO', 'Ese turno ya no está afectado por ese período.'],
    ['SILLON_HABITUAL_OCUPADO', 'Ese sillón ya es el habitual de otro profesional.'],
  ])('%s tiene su mensaje propio', (codigo, mensaje) => {
    expect(MENSAJES_POR_CODIGO[codigo]).toBe(mensaje);
    expect(mensajeDeError(new ErrorValidacion('detalle', codigo))).toBe(mensaje);
    expect(mensajeDeError(new ErrorNegocio(codigo, 'detalle'))).toBe(mensaje);
  });
});
