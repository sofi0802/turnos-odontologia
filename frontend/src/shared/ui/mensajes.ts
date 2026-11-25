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
  // C-45: sobreturno sin sillón libre y extensión de un sobreturno.
  SIN_SILLON_DISPONIBLE: 'No hay ningún sillón libre en ese horario. Probá con otra hora.',
  TURNO_NO_EXTENSIBLE: 'Un sobreturno no se puede extender.',
  TURNO_INEXISTENTE: 'El turno ya no existe.',
  ACCESO_DENEGADO: 'No tenés permiso para esta acción.',
  SIN_DISPONIBILIDAD: 'Ese horario se ocupó recién. Elegí otro.',
  CANCELACION_FUERA_DE_PLAZO: 'Faltan menos de 24 h. Para cancelarlo o cambiarlo, contactá al consultorio.',
  RESERVA_INEXISTENTE: 'No encontramos ese turno.',
  PROFESIONAL_NO_HABILITADO: 'Ese profesional no atiende esa prestación. Elegí otro.',
  // No dice cuál de los dos datos falló, igual que el backend (C-33).
  DATOS_DE_RESERVA_INVALIDOS: 'El DNI o el número de reserva no son correctos.',
  DNI_YA_REGISTRADO:
    'Ya hay un paciente registrado con ese DNI. Si es tuyo, ingresá con tu cuenta o contactá al consultorio.',
  EMAIL_YA_REGISTRADO: 'Ya existe una cuenta con ese email. Ingresá con tu contraseña.',
  // Configuración del consultorio (C-34, design D10).
  MATRICULA_DUPLICADA: 'Ya existe un profesional con esa matrícula.',
  NOMBRE_DUPLICADO: 'Ya existe otro con ese nombre.',
  DURACION_INVALIDA: 'La duración tiene que ser mayor que 0 y múltiplo de 5.',
  PRESTACION_INACTIVA: 'Hay una prestación inactiva entre las elegidas.',
  SILLON_INACTIVO: 'El sillón elegido está inactivo.',
  MARGEN_INVALIDO: 'El margen tiene que ser de 10 minutos o más.',
  HORARIO_INVALIDO: 'Revisá los días y las horas de las franjas.',
  FRANJAS_SUPERPUESTAS: 'Hay franjas del mismo día que se superponen.',
  PERIODO_INVALIDO: 'El fin tiene que ser posterior al inicio.',
  TURNO_NO_AFECTADO: 'Ese turno ya no está afectado por ese período.',
  SILLON_HABITUAL_OCUPADO: 'Ese sillón ya es el habitual de otro profesional.',
  // Cuentas del personal (C-41).
  USUARIO_EXISTENTE: 'Ya existe una cuenta con ese usuario.',
  PROFESIONAL_CON_CUENTA: 'Ese profesional ya tiene una cuenta.',
  PROFESIONAL_NO_DISPONIBLE: 'El profesional no existe o está inactivo.',
  OPERACION_SOBRE_CUENTA_PROPIA: 'No podés hacer esto con tu propia cuenta.',
  USUARIO_INEXISTENTE: 'Esa cuenta ya no existe.',
  CONTRASENA_ACTUAL_INCORRECTA: 'La contraseña actual no es correcta.',
  CAMBIO_DE_CONTRASENA_PENDIENTE: 'Antes de seguir, elegí una contraseña nueva.',
  // Asistencia en la agenda (C-43).
  LLEGADA_REGISTRADA: 'El paciente ya tiene la llegada registrada.',
  LLEGADA_ANTICIPADA: 'La llegada se registra el día del turno.',
  AUSENCIA_ANTES_DE_TOLERANCIA: 'Todavía no pasaron 15 minutos desde el inicio del turno.',
  TURNO_NO_AUSENTE: 'El turno no está marcado como ausente.',
  TURNO_TERMINADO: 'El turno ya terminó.',
  PIEZA_INVALIDA: 'La pieza no existe en la notación FDI.',
  CARA_INVALIDA: 'Hay una cara que no corresponde a esa pieza.',
  // Periodontograma (C-46).
  SITIOS_INCOMPLETOS: 'Cada pieza cargada necesita sus 6 sitios y la movilidad.',
  FURCACION_NO_APLICA: 'La furcación solo se carga en molares y en las piezas 14 y 24.',
  // Imágenes clínicas (C-47).
  TIPO_ARCHIVO_NO_PERMITIDO: 'Solo se aceptan archivos JPEG, PNG o PDF.',
  ARCHIVO_DEMASIADO_GRANDE: 'El archivo supera los 10 MB.',
  IMAGEN_YA_ANULADA: 'La imagen ya estaba anulada.',
  // Lista de espera (C-15).
  YA_EN_LISTA_DE_ESPERA: 'El paciente ya está en la lista de espera para esta prestación.',
  SIN_CONSENTIMIENTO: 'El paciente no aceptó recibir mensajes: no le podemos avisar las ofertas.',
  INSCRIPCION_NO_ACTIVA: 'Esta inscripción ya no estaba activa.',
  PLAZO_INVALIDO: 'Ingresá un número entero de 1 a 24.',
  OFERTA_NO_DISPONIBLE: 'Ese horario ya se ocupó. Seguís en la lista de espera.',
  OFERTA_VENCIDA: 'Esta oferta venció. Seguís en la lista de espera.',
  OFERTA_YA_RESPONDIDA: 'Ya respondiste esta oferta.',
  OFERTA_INEXISTENTE: 'El link no es válido. Revisá que esté completo.',
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

/** Textos que la reprogramación (C-33) cambia respecto de la creación de turnos. */
const MENSAJES_REPROGRAMACION: Readonly<Record<string, string>> = {
  SOLAPAMIENTO_SILLON: 'El sillón ya está ocupado en ese horario. Elegí otro sillón.',
};

/** Como {@link mensajeDeError}, con la indicación de elegir otro sillón al reprogramar. */
export function mensajeDeReprogramacion(error: unknown): string {
  if (error instanceof ErrorNegocio) {
    return MENSAJES_REPROGRAMACION[error.codigo] ?? mensajeDeError(error);
  }
  return mensajeDeError(error);
}

/** C-43: al deshacer una ausencia, cualquier rechazo por superposición o margen dice lo mismo. */
export const MENSAJE_HORARIO_OCUPADO = 'El horario ya se ocupó. Dale otro turno al paciente.';
const CODIGOS_DE_HORARIO_OCUPADO: ReadonlySet<string> = new Set([
  'SOLAPAMIENTO_PROFESIONAL',
  'SOLAPAMIENTO_SILLON',
  'MARGEN_INSUFICIENTE',
]);

/** Como {@link mensajeDeError}, con {@link MENSAJE_HORARIO_OCUPADO} para los rechazos de horario al deshacer. */
export function mensajeDeDeshacerAusencia(error: unknown): string {
  if (error instanceof ErrorNegocio && CODIGOS_DE_HORARIO_OCUPADO.has(error.codigo)) {
    return MENSAJE_HORARIO_OCUPADO;
  }
  return mensajeDeError(error);
}
