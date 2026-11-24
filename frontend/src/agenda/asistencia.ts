import type { ClienteApi } from '../shared/api/cliente.ts';
import { fechaDeInstante, formatearHora } from '../shared/fecha/zona.ts';
import type { Rol } from '../shared/sesion/sesion.ts';
import type { TurnoAgenda } from './consulta.ts';

/** C-43: la ausencia se marca recién a los 15 minutos del inicio (RN-AG-17); el backend aplica la misma regla. */
const TOLERANCIA_MS = 15 * 60 * 1000;

const ESTADOS_ACTIVOS: ReadonlySet<string> = new Set(['RESERVADO', 'CONFIRMADO']);

export interface EtiquetaAsistencia {
  readonly texto: string;
  /** Clase de la etiqueta (`.etiqueta-*`). */
  readonly clase: string;
}

/** Acción de asistencia y su ruta: `POST /turnos/{id}/<accion>`. */
export type AccionAsistencia = 'llegada' | 'ausencia' | 'deshacer-ausencia';

export interface AccionesDeAsistencia {
  readonly llego: boolean;
  /** `null`: no se ofrece. Si todavía no pasó la tolerancia, deshabilitado con la hora desde la que se puede. */
  readonly ausente: { readonly habilitado: boolean; readonly desde: string } | null;
  readonly deshacer: boolean;
}

const SIN_ACCIONES: AccionesDeAsistencia = { llego: false, ausente: null, deshacer: false };

function esActivo(turno: TurnoAgenda): boolean {
  return ESTADOS_ACTIVOS.has(turno.estado);
}

/**
 * Etiqueta de asistencia del turno, la misma para todos los roles del personal (design D10). La diferencia no
 * depende solo del color: cada caso tiene su texto. `null` para un turno activo que todavía no terminó y sin llegada.
 */
export function etiquetaDeAsistencia(turno: TurnoAgenda, ahora: Date): EtiquetaAsistencia | null {
  if (turno.estado === 'ATENDIDO') {
    return { texto: 'Atendido', clase: 'etiqueta-atendido' };
  }
  if (turno.estado === 'AUSENTE') {
    return { texto: 'Ausente', clase: 'etiqueta-ausente' };
  }
  if (!esActivo(turno)) {
    return null;
  }
  if (turno.llegadaEn !== null) {
    return { texto: `Llegó ${formatearHora(turno.llegadaEn)}`, clase: 'etiqueta-llegada' };
  }
  if (Date.parse(turno.fin) <= ahora.getTime()) {
    return { texto: 'Sin registrar', clase: 'etiqueta-sin-registrar' };
  }
  return null;
}

/**
 * Botones de asistencia (design D10). Solo para la recepción: "Llegó" en un turno activo sin llegada desde el día del
 * turno, "Ausente" en un turno activo sin llegada (habilitado a los 15 minutos del inicio) y "Deshacer ausencia" en un
 * turno ausente que no terminó.
 */
export function accionesDeAsistencia(turno: TurnoAgenda, rol: Rol | null, ahora: Date): AccionesDeAsistencia {
  if (rol !== 'RECEPCIONISTA') {
    return SIN_ACCIONES;
  }
  if (turno.estado === 'AUSENTE') {
    return { ...SIN_ACCIONES, deshacer: ahora.getTime() < Date.parse(turno.fin) };
  }
  if (!esActivo(turno) || turno.llegadaEn !== null) {
    return SIN_ACCIONES;
  }
  const desdeAusente = Date.parse(turno.inicio) + TOLERANCIA_MS;
  return {
    llego: fechaDeInstante(ahora) >= fechaDeInstante(turno.inicio),
    ausente: { habilitado: ahora.getTime() >= desdeAusente, desde: formatearHora(new Date(desdeAusente)) },
    deshacer: false,
  };
}

/** Los turnos atendidos y ausentes ya no se cancelan ni se reprograman. */
export function permiteCancelarYReprogramar(turno: TurnoAgenda): boolean {
  return esActivo(turno);
}

/** `POST /turnos/{id}/llegada`, `/ausencia` o `/deshacer-ausencia`, sin cuerpo. */
export async function registrarAsistencia(api: ClienteApi, turnoId: number, accion: AccionAsistencia): Promise<void> {
  await api.crear<unknown>(`/turnos/${turnoId}/${accion}`);
}
