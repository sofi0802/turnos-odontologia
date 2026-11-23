import { consultarDisponibilidad, type Horario } from '../paciente/disponibilidad.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import type { Fecha } from '../shared/fecha/zona.ts';
import { consultarAgenda } from './consulta.ts';
import { periodoDe } from './periodo.ts';
import type { EstadoHorarios } from './reprogramacion.ts';

/** Margen mínimo entre turnos (RN-AG-04); se usa si no se puede leer el de la configuración. */
export const MARGEN_MINIMO_MINUTOS = 10;

export interface ConsultaNuevoTurno {
  readonly prestacionId: number;
  readonly profesionalId: number;
  readonly sillonId: number;
  readonly fecha: Fecha;
}

/** Estado de los horarios del nuevo turno: la disponibilidad sin lo que pisa el sillón habitual (design D4). */
export interface BuscadorHorariosNuevoTurno {
  buscar(consulta: ConsultaNuevoTurno): void;
  /** Vuelve a "sin consulta" y descarta lo elegido y las respuestas pendientes. */
  limpiar(): void;
  elegir(horario: Horario): void;
  elegido(): Horario | null;
  estado(): EstadoHorarios;
}

async function leerMargen(api: ClienteApi): Promise<number> {
  try {
    const cuerpo = await api.obtener<unknown>('/configuracion/margen');
    const minutos: unknown = typeof cuerpo === 'object' && cuerpo !== null ? Reflect.get(cuerpo, 'minutos') : null;
    return typeof minutos === 'number' && minutos >= MARGEN_MINIMO_MINUTOS ? minutos : MARGEN_MINIMO_MINUTOS;
  } catch {
    return MARGEN_MINIMO_MINUTOS;
  }
}

/**
 * Pide en paralelo los horarios libres del profesional, los turnos del sillón habitual ese día y (una vez)
 * el margen. Si fallan los turnos o el margen, muestra los horarios sin filtrar o con el margen mínimo: el
 * backend sigue rechazando con `409 SOLAPAMIENTO_SILLON`. Una respuesta vieja se descarta.
 */
export function crearBuscadorHorariosNuevoTurno(api: ClienteApi, alCambiar: () => void): BuscadorHorariosNuevoTurno {
  let estado: EstadoHorarios = { fase: 'sin-consulta' };
  let elegido: Horario | null = null;
  let consultaActual = 0;
  let margen: Promise<number> | null = null;

  function cambiar(nuevo: EstadoHorarios): void {
    estado = nuevo;
    alCambiar();
  }

  return {
    buscar(consulta) {
      consultaActual += 1;
      const esta = consultaActual;
      elegido = null;
      cambiar({ fase: 'cargando' });
      margen ??= leerMargen(api);
      const turnosDelSillon = consultarAgenda(api, periodoDe('dia', consulta.fecha), {
        profesionalId: null,
        sillonId: consulta.sillonId,
      }).catch(() => []);
      Promise.all([
        consultarDisponibilidad(api, {
          prestacionId: consulta.prestacionId,
          fecha: consulta.fecha,
          profesionalId: consulta.profesionalId,
        }),
        turnosDelSillon,
        margen,
      ])
        .then(([horarios, turnos, minutos]) => {
          if (esta === consultaActual) {
            cambiar({ fase: 'listo', horarios: horariosSinChoqueDeSillon(horarios, turnos, minutos) });
          }
        })
        .catch(() => {
          if (esta === consultaActual) {
            cambiar({ fase: 'error' });
          }
        });
    },
    limpiar() {
      consultaActual += 1;
      elegido = null;
      cambiar({ fase: 'sin-consulta' });
    },
    elegir(horario) {
      elegido = horario;
    },
    elegido: () => elegido,
    estado: () => estado,
  };
}

/** Lo mínimo de un turno del sillón que hace falta para saber si choca con un horario. */
export interface OcupacionSillon {
  readonly inicio: string;
  readonly fin: string;
  readonly estado: string;
}

const MS_POR_MINUTO = 60_000;

/**
 * Horarios que no chocan con ningún turno activo del sillón, con el margen en minutos (design D4).
 * Repite la regla del dominio (RN-AG-02 a RN-AG-05) solo para no ofrecer opciones que el backend va a
 * rechazar: la garantía sigue siendo el `409 SOLAPAMIENTO_SILLON` y la restricción `EXCLUDE`.
 */
export function horariosSinChoqueDeSillon(
  horarios: readonly Horario[],
  turnosDelSillon: readonly OcupacionSillon[],
  margenMinutos: number,
): Horario[] {
  const margen = margenMinutos * MS_POR_MINUTO;
  const ocupados = turnosDelSillon
    .filter((t) => t.estado !== 'CANCELADO')
    .map((t) => ({ inicio: Date.parse(t.inicio), fin: Date.parse(t.fin) }));
  return horarios.filter((horario) => {
    const inicio = Date.parse(horario.inicio);
    const fin = Date.parse(horario.fin);
    return ocupados.every((o) => !(inicio < o.fin + margen && o.inicio < fin + margen));
  });
}
