import {
  ErrorApi,
  ErrorNegocio,
  ErrorNoEncontrado,
  ErrorPermiso,
  ErrorValidacion,
  MENSAJE_ERROR_GENERICO,
} from '../api/errores.ts';

/** Texto para cada código estable de rechazo (design D6). Un código nuevo se suma acá. */
export const MENSAJES_POR_CODIGO: Readonly<Record<string, string>> = {
  SOLAPAMIENTO_PROFESIONAL: 'El profesional ya tiene un turno en ese horario.',
  SOLAPAMIENTO_SILLON: 'El sillón ya está ocupado en ese horario.',
  MARGEN_INSUFICIENTE: 'No queda el margen mínimo entre turnos.',
  INICIO_EN_EL_PASADO: 'El horario elegido ya pasó.',
  RECURSO_INACTIVO: 'El profesional, el sillón o la prestación está inactivo.',
  TURNO_NO_ACTIVO: 'El turno ya no está activo.',
  FUERA_DE_HORARIO: 'El profesional no atiende en ese horario.',
  PROFESIONAL_AUSENTE: 'El profesional tiene registrada una ausencia en ese horario.',
  AGENDA_BLOQUEADA: 'La agenda del profesional está bloqueada en ese horario.',
  TURNO_INEXISTENTE: 'El turno ya no existe.',
  ACCESO_DENEGADO: 'No tenés permiso para esta acción.',
  SIN_DISPONIBILIDAD: 'Ese horario se ocupó recién. Elegí otro.',
  CANCELACION_FUERA_DE_PLAZO: 'Faltan menos de 24 h. Para cancelarlo o cambiarlo, contactá al consultorio.',
  RESERVA_INEXISTENTE: 'No encontramos ese turno.',
  PROFESIONAL_NO_HABILITADO: 'Ese profesional no atiende esa prestación. Elegí otro.',
  DNI_YA_REGISTRADO:
    'Ya hay un paciente registrado con ese DNI. Si es tuyo, ingresá con tu cuenta o contactá al consultorio.',
  EMAIL_YA_REGISTRADO: 'Ya existe una cuenta con ese email. Ingresá con tu contraseña.',
};

export const MENSAJE_SIN_PERMISO = 'No tenés permiso para esta acción.';

/** Mensaje en español para mostrar a la persona; nunca expone detalles internos. */
export function mensajeDeError(error: unknown): string {
  if (error instanceof ErrorNegocio) {
    return MENSAJES_POR_CODIGO[error.codigo] ?? (error.detalle || MENSAJE_ERROR_GENERICO);
  }
  if (error instanceof ErrorValidacion) {
    return (error.codigo === null ? undefined : MENSAJES_POR_CODIGO[error.codigo]) ?? error.message;
  }
  if (error instanceof ErrorPermiso) {
    return MENSAJE_SIN_PERMISO;
  }
  if (error instanceof ErrorNoEncontrado) {
    return (error.codigo === null ? undefined : MENSAJES_POR_CODIGO[error.codigo]) ?? error.message;
  }
  if (error instanceof ErrorApi) {
    return error.message;
  }
  return MENSAJE_ERROR_GENERICO;
}
