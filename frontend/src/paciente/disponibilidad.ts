import type { ClienteApi } from '../shared/api/cliente.ts';
import type { Fecha } from '../shared/fecha/zona.ts';

/** Horario libre tal como lo da la API; `inicio` se reenvía sin recalcular (design D5). */
export interface Horario {
  readonly inicio: string;
  readonly fin: string;
}

export interface ConsultaDisponibilidad {
  readonly prestacionId: number;
  readonly fecha: Fecha;
  /** `null` es "Sin preferencia": el pedido no lleva `profesionalId`. */
  readonly profesionalId: number | null;
}

export function rutaDisponibilidad(consulta: ConsultaDisponibilidad): string {
  const parametros = new URLSearchParams({ prestacionId: String(consulta.prestacionId), fecha: consulta.fecha });
  if (consulta.profesionalId !== null) {
    parametros.set('profesionalId', String(consulta.profesionalId));
  }
  return `/public/disponibilidad?${parametros.toString()}`;
}

function leerHorario(elemento: unknown): Horario | null {
  if (typeof elemento !== 'object' || elemento === null) {
    return null;
  }
  const inicio: unknown = Reflect.get(elemento, 'inicio');
  const fin: unknown = Reflect.get(elemento, 'fin');
  return typeof inicio === 'string' && typeof fin === 'string' ? { inicio, fin } : null;
}

/** Horarios libres del día, en el orden de la API. */
export async function consultarDisponibilidad(api: ClienteApi, consulta: ConsultaDisponibilidad): Promise<Horario[]> {
  const cuerpo = await api.obtener<unknown>(rutaDisponibilidad(consulta));
  if (!Array.isArray(cuerpo)) {
    throw new Error('Respuesta inesperada de /public/disponibilidad');
  }
  return cuerpo.map(leerHorario).filter((h): h is Horario => h !== null);
}
