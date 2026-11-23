import type { ClienteApi } from '../shared/api/cliente.ts';
import { leerTurnoPaciente, type TurnoPaciente } from './reserva.ts';

const ESTADOS_ACTIVOS: ReadonlySet<string> = new Set(['RESERVADO', 'CONFIRMADO']);

/** Estado del turno en español y su clase del design system (`.estado .e-*`). */
export const ESTADOS: Readonly<Record<string, { readonly texto: string; readonly clase: string }>> = {
  RESERVADO: { texto: 'Reservado', clase: 'e-reservado' },
  CONFIRMADO: { texto: 'Confirmado', clase: 'e-confirmado' },
  CANCELADO: { texto: 'Cancelado', clase: 'e-cancelado' },
  ATENDIDO: { texto: 'Atendido', clase: 'e-atendido' },
  AUSENTE: { texto: 'Ausente', clase: 'e-ausente' },
};

export async function listarMisTurnos(api: ClienteApi): Promise<TurnoPaciente[]> {
  const cuerpo = await api.obtener<unknown>('/paciente/turnos');
  if (!Array.isArray(cuerpo)) {
    throw new Error('Respuesta inesperada de /paciente/turnos');
  }
  return cuerpo.map(leerTurnoPaciente).filter((t): t is TurnoPaciente => t !== null);
}

export interface TurnosSeparados {
  /** Activos que no empezaron, del más cercano al más lejano. */
  readonly proximos: readonly TurnoPaciente[];
  /** El resto, del más reciente al más viejo. */
  readonly anteriores: readonly TurnoPaciente[];
}

export function separarTurnos(turnos: readonly TurnoPaciente[], ahora: Date): TurnosSeparados {
  const momento = ahora.getTime();
  const esProximo = (t: TurnoPaciente): boolean => ESTADOS_ACTIVOS.has(t.estado) && Date.parse(t.inicio) > momento;
  const porInicio = (a: TurnoPaciente, b: TurnoPaciente): number => Date.parse(a.inicio) - Date.parse(b.inicio);
  return {
    proximos: turnos.filter(esProximo).sort(porInicio),
    anteriores: turnos.filter((t) => !esProximo(t)).sort((a, b) => porInicio(b, a)),
  };
}

function rutaReserva(numeroReserva: string, accion: 'cancelar' | 'reprogramar'): string {
  return `/paciente/reservas/${encodeURIComponent(numeroReserva)}/${accion}`;
}

export async function cancelarTurno(api: ClienteApi, numeroReserva: string): Promise<void> {
  await api.crear<unknown>(rutaReserva(numeroReserva, 'cancelar'));
}

/** Reprograma; `profesionalId` `null` es "Sin preferencia". Devuelve el turno nuevo si llegó con su forma. */
export async function reprogramarTurno(
  api: ClienteApi,
  numeroReserva: string,
  inicio: string,
  profesionalId: number | null,
): Promise<TurnoPaciente | null> {
  const cuerpo: Record<string, unknown> = { inicio };
  if (profesionalId !== null) {
    cuerpo['profesionalId'] = profesionalId;
  }
  return leerTurnoPaciente(await api.crear<unknown>(rutaReserva(numeroReserva, 'reprogramar'), cuerpo));
}
