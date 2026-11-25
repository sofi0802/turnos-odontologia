/** Código que se usa cuando un 409 llega sin la propiedad `codigo`. */
export const CODIGO_CONFLICTO_SIN_CODIGO = 'CONFLICTO_SIN_CODIGO';

/** Mensaje fijo para las fallas que no son de negocio ni de validación. */
export const MENSAJE_ERROR_GENERICO =
  'No pudimos completar la operación. Probá de nuevo en unos minutos.';

/** Base de todos los errores que entrega el cliente de API. */
export class ErrorApi extends Error {
  constructor(mensaje: string) {
    super(mensaje);
    this.name = new.target.name;
  }
}

/** Horario que el servidor ofrece en lugar del rechazado, tal como lo envió. */
export interface HorarioAlternativo {
  readonly inicio: string;
  readonly fin: string;
}

/** Respuesta 409: una regla de negocio rechazó el pedido. */
export class ErrorNegocio extends ErrorApi {
  readonly codigo: string;
  readonly detalle: string;
  /** Horarios que ofrece el servidor (por ejemplo, con `SIN_DISPONIBILIDAD`); vacía si no trae. */
  readonly alternativas: readonly HorarioAlternativo[];

  constructor(codigo: string, detalle: string, alternativas: readonly HorarioAlternativo[] = []) {
    super(detalle || codigo);
    this.codigo = codigo;
    this.detalle = detalle;
    this.alternativas = alternativas;
  }
}

/** Un dato del pedido que el servidor rechazó, con su mensaje. */
export interface ErrorCampo {
  readonly campo: string;
  readonly mensaje: string;
}

/** Respuesta 422 (o 413, archivo demasiado grande): los datos enviados no son válidos. */
export class ErrorValidacion extends ErrorApi {
  readonly codigo: string | null;
  readonly detalle: string;
  readonly errores: readonly ErrorCampo[];
  /** 422, o 413 si el servidor cortó el pedido por el tamaño del archivo (C-47). */
  readonly estado: number;

  constructor(detalle: string, codigo: string | null, errores: readonly ErrorCampo[] = [], estado = 422) {
    super(detalle || 'Los datos enviados no son válidos.');
    this.codigo = codigo;
    this.detalle = detalle;
    this.errores = errores;
    this.estado = estado;
  }
}

/** Base de las respuestas 401, 403 y 404: estado y código, sin el cuerpo del servidor. */
export abstract class ErrorConCodigo extends ErrorApi {
  readonly estado: number;
  readonly codigo: string | null;

  protected constructor(estado: number, codigo: string | null, mensaje: string) {
    super(mensaje);
    this.estado = estado;
    this.codigo = codigo;
  }
}

/** Respuesta 401: no hay sesión o las credenciales no son válidas. */
export class ErrorSesion extends ErrorConCodigo {
  constructor(codigo: string | null) {
    super(401, codigo, 'Tu sesión no está iniciada.');
  }
}

/** Respuesta 403: la sesión no tiene permiso para esta acción. */
export class ErrorPermiso extends ErrorConCodigo {
  constructor(codigo: string | null) {
    super(403, codigo, 'No tenés permiso para esta acción.');
  }
}

/** Respuesta 404: el recurso pedido no existe. */
export class ErrorNoEncontrado extends ErrorConCodigo {
  constructor(codigo: string | null) {
    super(404, codigo, 'Lo que buscás ya no existe.');
  }
}

/** Cualquier otra falla; `estado` es null si no hubo respuesta del servidor. */
export class ErrorGenerico extends ErrorApi {
  readonly estado: number | null;

  constructor(estado: number | null) {
    super(MENSAJE_ERROR_GENERICO);
    this.estado = estado;
  }
}

/** Campos del ProblemDetail (RFC 9457) que usa el frontend. */
export interface ProblemDetail {
  readonly detail: string;
  readonly codigo: string | null;
  readonly errores: readonly ErrorCampo[];
  readonly alternativas: readonly HorarioAlternativo[];
}

function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor);
}

function textoNoVacio(valor: unknown): string | null {
  return typeof valor === 'string' && valor.trim() !== '' ? valor : null;
}

/** Interpreta un cuerpo de error desconocido; nunca falla. */
export function leerProblemDetail(cuerpo: unknown): ProblemDetail {
  if (!esObjeto(cuerpo)) {
    return { detail: '', codigo: null, errores: [], alternativas: [] };
  }
  return {
    detail: textoNoVacio(cuerpo['detail']) ?? '',
    codigo: textoNoVacio(cuerpo['codigo']),
    errores: leerErroresDeCampo(cuerpo['errores']),
    alternativas: leerAlternativas(cuerpo['alternativas']),
  };
}

/** Lee la lista `alternativas`; descarta lo que no tenga `inicio` y `fin`. */
function leerAlternativas(valor: unknown): HorarioAlternativo[] {
  if (!Array.isArray(valor)) {
    return [];
  }
  const alternativas: HorarioAlternativo[] = [];
  for (const elemento of valor) {
    if (!esObjeto(elemento)) {
      continue;
    }
    const inicio = textoNoVacio(elemento['inicio']);
    const fin = textoNoVacio(elemento['fin']);
    if (inicio !== null && fin !== null) {
      alternativas.push({ inicio, fin });
    }
  }
  return alternativas;
}

/** Lee la lista `errores` del backend; descarta lo que no tenga campo y mensaje. */
function leerErroresDeCampo(valor: unknown): ErrorCampo[] {
  if (!Array.isArray(valor)) {
    return [];
  }
  const errores: ErrorCampo[] = [];
  for (const elemento of valor) {
    if (!esObjeto(elemento)) {
      continue;
    }
    const campo = textoNoVacio(elemento['campo']);
    const mensaje = textoNoVacio(elemento['mensaje']);
    if (campo !== null && mensaje !== null) {
      errores.push({ campo, mensaje });
    }
  }
  return errores;
}
