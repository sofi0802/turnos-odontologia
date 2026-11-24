import type { ClienteApi } from '../shared/api/cliente.ts';
import { desdeIso, hastaIso, type Periodo } from './periodo.ts';

export interface Filtros {
  readonly profesionalId: number | null;
  readonly sillonId: number | null;
}

/** Turno de `GET /turnos` (capacidad `agenda-api`): sin número de reserva ni datos de salud. */
export interface TurnoAgenda {
  readonly id: number;
  readonly pacienteNombre: string;
  readonly pacienteApellido: string;
  readonly profesionalId: number;
  readonly sillonId: number;
  readonly prestacionId: number;
  readonly inicio: string;
  readonly fin: string;
  readonly estado: string;
  readonly esSobreturno: boolean;
  /** C-43: hora de llegada del paciente; `null` si no se registró. */
  readonly llegadaEn: string | null;
}

export function rutaDeAgenda(periodo: Periodo, filtros: Filtros): string {
  const query = new URLSearchParams({ desde: desdeIso(periodo), hasta: hastaIso(periodo) });
  if (filtros.profesionalId !== null) {
    query.set('profesionalId', String(filtros.profesionalId));
  }
  if (filtros.sillonId !== null) {
    query.set('sillonId', String(filtros.sillonId));
  }
  return `/turnos?${query.toString()}`;
}

function numero(objeto: object, clave: string): number | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : null;
}

function cadena(objeto: object, clave: string): string | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'string' ? valor : null;
}

function leerTurno(elemento: unknown): TurnoAgenda | null {
  if (typeof elemento !== 'object' || elemento === null) {
    return null;
  }
  const id = numero(elemento, 'id');
  const profesionalId = numero(elemento, 'profesionalId');
  const sillonId = numero(elemento, 'sillonId');
  const prestacionId = numero(elemento, 'prestacionId');
  const inicio = cadena(elemento, 'inicio');
  const fin = cadena(elemento, 'fin');
  const estado = cadena(elemento, 'estado');
  if (
    id === null ||
    profesionalId === null ||
    sillonId === null ||
    prestacionId === null ||
    inicio === null ||
    fin === null ||
    estado === null
  ) {
    return null;
  }
  return {
    id,
    pacienteNombre: cadena(elemento, 'pacienteNombre') ?? '',
    pacienteApellido: cadena(elemento, 'pacienteApellido') ?? '',
    profesionalId,
    sillonId,
    prestacionId,
    inicio,
    fin,
    estado,
    esSobreturno: Reflect.get(elemento, 'esSobreturno') === true,
    llegadaEn: cadena(elemento, 'llegadaEn'),
  };
}

/** Pide la agenda del período y la devuelve ordenada por inicio. */
export async function consultarAgenda(api: ClienteApi, periodo: Periodo, filtros: Filtros): Promise<TurnoAgenda[]> {
  const cuerpo = await api.obtener<unknown>(rutaDeAgenda(periodo, filtros));
  const lista = Array.isArray(cuerpo) ? cuerpo : [];
  return lista
    .map(leerTurno)
    .filter((t): t is TurnoAgenda => t !== null)
    .sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio));
}
