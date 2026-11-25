import type { ClienteApi } from '../shared/api/cliente.ts';
import { sumarDias, type Fecha } from '../shared/fecha/zona.ts';

/** C-15: la oferta pendiente de una inscripción. */
export interface OfertaPendiente {
  readonly inicio: string;
  readonly venceEn: string;
}

/**
 * Una inscripción activa de la lista de espera. El nombre y el DNI del paciente solo llegan en la lista de la
 * recepción; en la del paciente son `null`.
 */
export interface Inscripcion {
  readonly id: number;
  readonly pacienteId: number | null;
  readonly pacienteNombre: string | null;
  readonly pacienteDni: string | null;
  readonly prestacionId: number;
  readonly prestacionNombre: string;
  /** `null`: "Sin preferencia". */
  readonly profesionalId: number | null;
  readonly profesionalNombre: string | null;
  readonly desde: Fecha;
  readonly hasta: Fecha;
  readonly inscriptaEn: string;
  readonly oferta: OfertaPendiente | null;
}

export interface PedidoInscripcion {
  readonly prestacionId: number;
  readonly profesionalId: number | null;
  readonly desde: Fecha;
  readonly hasta: Fecha;
}

/** Lo que muestra el link de la oferta: nunca la prestación. */
export interface Oferta {
  readonly fecha: Fecha;
  readonly hora: string;
  readonly profesional: string;
  readonly venceEn: string;
  readonly estado: string;
}

/** La respuesta de aceptar: el turno reservado. */
export interface TurnoAceptado {
  readonly fecha: Fecha;
  readonly hora: string;
  readonly profesional: string;
  readonly numeroReserva: string;
}

export const MAXIMO_DIAS_DEL_RANGO = 60;
export const DIAS_POR_DEFECTO = 14;
export const PLAZO_MINIMO = 1;
export const PLAZO_MAXIMO = 24;

const RUTA_PERSONAL = '/lista-espera';
const RUTA_PACIENTE = '/paciente/lista-espera';
const RUTA_PLAZO = '/configuracion/lista-espera';

function propiedad(cuerpo: unknown, clave: string): unknown {
  return typeof cuerpo === 'object' && cuerpo !== null ? Reflect.get(cuerpo, clave) : undefined;
}

function textoONulo(valor: unknown): string | null {
  return typeof valor === 'string' ? valor : null;
}

function numeroONulo(valor: unknown): number | null {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null;
}

function esFecha(valor: unknown): valor is Fecha {
  return typeof valor === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(valor);
}

function leerOfertaPendiente(cuerpo: unknown): OfertaPendiente | null {
  const inicio = textoONulo(propiedad(cuerpo, 'inicio'));
  const venceEn = textoONulo(propiedad(cuerpo, 'venceEn'));
  return inicio === null || venceEn === null ? null : { inicio, venceEn };
}

/** Lee una inscripción de la API; `null` si le falta un dato obligatorio. */
export function leerInscripcion(cuerpo: unknown): Inscripcion | null {
  const id = numeroONulo(propiedad(cuerpo, 'id'));
  const prestacionId = numeroONulo(propiedad(cuerpo, 'prestacionId'));
  const prestacionNombre = textoONulo(propiedad(cuerpo, 'prestacionNombre'));
  const desde = propiedad(cuerpo, 'desde');
  const hasta = propiedad(cuerpo, 'hasta');
  const inscriptaEn = textoONulo(propiedad(cuerpo, 'inscriptaEn'));
  if (id === null || prestacionId === null || prestacionNombre === null || !esFecha(desde) || !esFecha(hasta)
    || inscriptaEn === null) {
    return null;
  }
  return {
    id,
    pacienteId: numeroONulo(propiedad(cuerpo, 'pacienteId')),
    pacienteNombre: textoONulo(propiedad(cuerpo, 'pacienteNombre')),
    pacienteDni: textoONulo(propiedad(cuerpo, 'pacienteDni')),
    prestacionId,
    prestacionNombre,
    profesionalId: numeroONulo(propiedad(cuerpo, 'profesionalId')),
    profesionalNombre: textoONulo(propiedad(cuerpo, 'profesionalNombre')),
    desde,
    hasta,
    inscriptaEn,
    oferta: leerOfertaPendiente(propiedad(cuerpo, 'oferta')),
  };
}

function leerLista(cuerpo: unknown, ruta: string): Inscripcion[] {
  if (!Array.isArray(cuerpo)) {
    throw new Error(`Respuesta inesperada de ${ruta}`);
  }
  return cuerpo.map(leerInscripcion).filter((i): i is Inscripcion => i !== null);
}

function inscripcionCreada(cuerpo: unknown, ruta: string): Inscripcion {
  const inscripcion = leerInscripcion(cuerpo);
  if (inscripcion === null) {
    throw new Error(`Respuesta inesperada de ${ruta}`);
  }
  return inscripcion;
}

// ---------- recepción y administración ----------

export async function listarListaDeEspera(api: ClienteApi): Promise<Inscripcion[]> {
  return leerLista(await api.obtener<unknown>(RUTA_PERSONAL), RUTA_PERSONAL);
}

export async function anotarPaciente(
  api: ClienteApi,
  pacienteId: number,
  pedido: PedidoInscripcion,
): Promise<Inscripcion> {
  return inscripcionCreada(await api.crear<unknown>(RUTA_PERSONAL, { pacienteId, ...pedido }), RUTA_PERSONAL);
}

export async function darDeBaja(api: ClienteApi, id: number): Promise<void> {
  await api.crear<unknown>(`${RUTA_PERSONAL}/${id}/baja`);
}

// ---------- paciente ----------

export async function misInscripciones(api: ClienteApi): Promise<Inscripcion[]> {
  return leerLista(await api.obtener<unknown>(RUTA_PACIENTE), RUTA_PACIENTE);
}

export async function anotarme(api: ClienteApi, pedido: PedidoInscripcion): Promise<Inscripcion> {
  return inscripcionCreada(await api.crear<unknown>(RUTA_PACIENTE, pedido), RUTA_PACIENTE);
}

export async function salirDeLaLista(api: ClienteApi, id: number): Promise<void> {
  await api.crear<unknown>(`${RUTA_PACIENTE}/${id}/baja`);
}

// ---------- oferta (pública) ----------

function rutaOferta(token: string): string {
  return `/public/ofertas/${encodeURIComponent(token)}`;
}

export async function verOferta(api: ClienteApi, token: string): Promise<Oferta> {
  const cuerpo = await api.obtener<unknown>(rutaOferta(token));
  const fecha = propiedad(cuerpo, 'fecha');
  const hora = textoONulo(propiedad(cuerpo, 'hora'));
  const venceEn = textoONulo(propiedad(cuerpo, 'venceEn'));
  const estado = textoONulo(propiedad(cuerpo, 'estado'));
  if (!esFecha(fecha) || hora === null || venceEn === null || estado === null) {
    throw new Error('Respuesta inesperada de la oferta');
  }
  return { fecha, hora, profesional: textoONulo(propiedad(cuerpo, 'profesional')) ?? '', venceEn, estado };
}

export async function aceptarOferta(api: ClienteApi, token: string): Promise<TurnoAceptado> {
  const cuerpo = await api.crear<unknown>(`${rutaOferta(token)}/aceptar`);
  const fecha = propiedad(cuerpo, 'fecha');
  const hora = textoONulo(propiedad(cuerpo, 'hora'));
  const numeroReserva = textoONulo(propiedad(cuerpo, 'numeroReserva'));
  if (!esFecha(fecha) || hora === null || numeroReserva === null) {
    throw new Error('Respuesta inesperada al aceptar la oferta');
  }
  return { fecha, hora, profesional: textoONulo(propiedad(cuerpo, 'profesional')) ?? '', numeroReserva };
}

export async function rechazarOferta(api: ClienteApi, token: string): Promise<void> {
  await api.crear<unknown>(`${rutaOferta(token)}/rechazar`);
}

// ---------- plazo ----------

export function leerPlazo(cuerpo: unknown): number | null {
  return numeroONulo(propiedad(cuerpo, 'plazoHoras'));
}

export async function obtenerPlazo(api: ClienteApi): Promise<number> {
  const plazo = leerPlazo(await api.obtener<unknown>(RUTA_PLAZO));
  if (plazo === null) {
    throw new Error(`Respuesta inesperada de ${RUTA_PLAZO}`);
  }
  return plazo;
}

export async function guardarPlazo(api: ClienteApi, horas: number): Promise<number> {
  return leerPlazo(await api.actualizar<unknown>(RUTA_PLAZO, { plazoHoras: horas })) ?? horas;
}

// ---------- validación del rango ----------

export const MENSAJE_FECHA_OBLIGATORIA = 'Elegí una fecha.';
export const MENSAJE_DESDE_PASADO = 'Elegí hoy o un día posterior.';
export const MENSAJE_HASTA_ANTERIOR = 'Tiene que ser el mismo día que "Desde" o uno posterior.';
export const MENSAJE_RANGO_LARGO = 'El rango puede ser de hasta 60 días.';

export interface ErroresRango {
  readonly desde: string;
  readonly hasta: string;
}

/** Valida "Desde" y "Hasta" como el backend: desde hoy, hasta igual o posterior y 60 días como máximo. */
export function validarRango(desde: string, hasta: string, hoy: Fecha): ErroresRango {
  let errorDesde = '';
  let errorHasta = '';
  if (!esFecha(desde)) {
    errorDesde = MENSAJE_FECHA_OBLIGATORIA;
  } else if (desde < hoy) {
    errorDesde = MENSAJE_DESDE_PASADO;
  }
  if (!esFecha(hasta)) {
    errorHasta = MENSAJE_FECHA_OBLIGATORIA;
  } else if (esFecha(desde)) {
    if (hasta < desde) {
      errorHasta = MENSAJE_HASTA_ANTERIOR;
    } else if (hasta > sumarDias(desde, MAXIMO_DIAS_DEL_RANGO)) {
      errorHasta = MENSAJE_RANGO_LARGO;
    }
  }
  return { desde: errorDesde, hasta: errorHasta };
}

/** El plazo como lo valida el backend: un entero de 1 a 24; `null` si no lo es. */
export function plazoValido(texto: string): number | null {
  if (!/^\d+$/.test(texto.trim())) {
    return null;
  }
  const horas = Number(texto.trim());
  return horas >= PLAZO_MINIMO && horas <= PLAZO_MAXIMO ? horas : null;
}
