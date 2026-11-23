import type { ClienteApi } from '../shared/api/cliente.ts';

/**
 * Lo elegido en la reserva mientras el paciente ingresa o crea su cuenta (design D4).
 * Solo identificadores y el horario: nunca datos del paciente ni de salud.
 */
export interface ReservaPendiente {
  readonly prestacionId: number;
  readonly profesionalId: number | null;
  readonly fecha: string;
  readonly inicio: string;
}

export const CLAVE_RESERVA_PENDIENTE = 'turnos.reservaPendiente';

function esEntero(valor: unknown): valor is number {
  return typeof valor === 'number' && Number.isInteger(valor) && valor > 0;
}

function leerForma(valor: unknown): ReservaPendiente | null {
  if (typeof valor !== 'object' || valor === null) {
    return null;
  }
  const prestacionId: unknown = Reflect.get(valor, 'prestacionId');
  const profesionalId: unknown = Reflect.get(valor, 'profesionalId');
  const fecha: unknown = Reflect.get(valor, 'fecha');
  const inicio: unknown = Reflect.get(valor, 'inicio');
  if (
    !esEntero(prestacionId) ||
    !(profesionalId === null || esEntero(profesionalId)) ||
    typeof fecha !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(fecha) ||
    typeof inicio !== 'string' ||
    Number.isNaN(Date.parse(inicio))
  ) {
    return null;
  }
  return { prestacionId, profesionalId, fecha, inicio };
}

/** La reserva pendiente de esta pestaña; cualquier error de lectura o de forma es "no hay". */
export function leerReservaPendiente(): ReservaPendiente | null {
  try {
    const texto = sessionStorage.getItem(CLAVE_RESERVA_PENDIENTE);
    return texto === null ? null : leerForma(JSON.parse(texto) as unknown);
  } catch {
    return null;
  }
}

export function guardarReservaPendiente(reserva: ReservaPendiente): void {
  try {
    sessionStorage.setItem(CLAVE_RESERVA_PENDIENTE, JSON.stringify(reserva));
  } catch {
    // Sin almacenamiento, el paciente vuelve a elegir después de ingresar.
  }
}

export function borrarReservaPendiente(): void {
  try {
    sessionStorage.removeItem(CLAVE_RESERVA_PENDIENTE);
  } catch {
    // Nada que borrar.
  }
}

/** Referencia a un catálogo en la respuesta del turno del paciente. */
export interface Referencia {
  readonly id: number | null;
  readonly nombre: string;
}

/** Un turno visto por su paciente (`TurnoPacienteResponse`). */
export interface TurnoPaciente {
  readonly numeroReserva: string;
  readonly inicio: string;
  readonly fin: string;
  readonly estado: string;
  readonly prestacion: Referencia;
  readonly profesional: Referencia;
  readonly modificableOnline: boolean;
}

function leerReferencia(valor: unknown): Referencia {
  if (typeof valor !== 'object' || valor === null) {
    return { id: null, nombre: '' };
  }
  const id: unknown = Reflect.get(valor, 'id');
  const nombre: unknown = Reflect.get(valor, 'nombre');
  return { id: typeof id === 'number' ? id : null, nombre: typeof nombre === 'string' ? nombre : '' };
}

/** Interpreta un turno del paciente; `null` si le falta el número, el horario o el estado. */
export function leerTurnoPaciente(valor: unknown): TurnoPaciente | null {
  if (typeof valor !== 'object' || valor === null) {
    return null;
  }
  const numeroReserva: unknown = Reflect.get(valor, 'numeroReserva');
  const inicio: unknown = Reflect.get(valor, 'inicio');
  const fin: unknown = Reflect.get(valor, 'fin');
  const estado: unknown = Reflect.get(valor, 'estado');
  if (
    typeof numeroReserva !== 'string' ||
    typeof inicio !== 'string' ||
    typeof fin !== 'string' ||
    typeof estado !== 'string'
  ) {
    return null;
  }
  return {
    numeroReserva,
    inicio,
    fin,
    estado,
    prestacion: leerReferencia(Reflect.get(valor, 'prestacion')),
    profesional: leerReferencia(Reflect.get(valor, 'profesional')),
    modificableOnline: Reflect.get(valor, 'modificableOnline') === true,
  };
}

/** Pedido de la reserva: el `inicio` va tal como lo dio la API (design D5). */
export interface PedidoReserva {
  readonly prestacionId: number;
  readonly inicio: string;
  readonly profesionalId: number | null;
}

export async function reservar(api: ClienteApi, pedido: PedidoReserva): Promise<TurnoPaciente> {
  const cuerpo: Record<string, unknown> = { prestacionId: pedido.prestacionId, inicio: pedido.inicio };
  if (pedido.profesionalId !== null) {
    cuerpo['profesionalId'] = pedido.profesionalId;
  }
  const turno = leerTurnoPaciente(await api.crear<unknown>('/paciente/reservas', cuerpo));
  if (turno === null) {
    throw new Error('Respuesta inesperada de /paciente/reservas');
  }
  return turno;
}
