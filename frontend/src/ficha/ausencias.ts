import { TOLERANCIA_MS } from '../agenda/asistencia.ts';
import { formatearHora } from '../shared/fecha/zona.ts';
import type { Rol } from '../shared/sesion/sesion.ts';
import type { AusenciaFicha } from './api.ts';
import { fechaNumerica, fechaYHora } from './formato.ts';
import type { TurnoDeLaFicha } from './seleccion.ts';

/** Ausencias del paciente en la ficha y las condiciones de "Marcar ausente" (C-44, design D7, RN-AG-17). */

export const MENSAJE_SIN_AUSENCIAS = 'Sin ausencias registradas.';

const ESTADOS_ACTIVOS: ReadonlySet<string> = new Set(['RESERVADO', 'CONFIRMADO']);

export interface MarcarAusenteDisponible {
  readonly habilitado: boolean;
  /** "HH:MM" desde la que se habilita. */
  readonly desde: string;
}

/**
 * "Marcar ausente" solo para el odontólogo del turno desde el que se abrió la ficha, con el turno activo y sin
 * llegada. Se habilita a los 15 minutos del inicio. Devuelve `null` si no se ofrece.
 */
export function marcarAusenteDisponible(
  turno: TurnoDeLaFicha | null,
  rol: Rol | null,
  profesionalId: number | undefined,
  ahora: Date,
): MarcarAusenteDisponible | null {
  if (turno === null || rol !== 'ODONTOLOGO' || profesionalId !== turno.profesionalId) {
    return null;
  }
  if (!ESTADOS_ACTIVOS.has(turno.estado) || turno.llegadaEn !== null) {
    return null;
  }
  const desde = Date.parse(turno.inicio) + TOLERANCIA_MS;
  return { habilitado: ahora.getTime() >= desde, desde: formatearHora(new Date(desde)) };
}

export function crearAusencias(ausencias: readonly AusenciaFicha[]): HTMLElement {
  const seccion = document.createElement('section');
  seccion.className = 'panel ficha-seccion';
  const titulo = document.createElement('h2');
  titulo.textContent = 'Ausencias';
  seccion.append(titulo);
  if (ausencias.length === 0) {
    const vacio = document.createElement('p');
    vacio.className = 'firma';
    vacio.textContent = MENSAJE_SIN_AUSENCIAS;
    seccion.append(vacio);
    return seccion;
  }
  const lista = document.createElement('ul');
  lista.className = 'lista-ficha';
  for (const ausencia of ausencias) {
    const item = document.createElement('li');
    const cuando = document.createElement('span');
    const profesional = document.createElement('span');
    profesional.className = 'detalle';
    profesional.textContent = ausencia.profesional;
    cuando.append(`${fechaNumerica(ausencia.inicio)} · ${formatearHora(ausencia.inicio)}`, document.createElement('br'), profesional);
    const estado = document.createElement('span');
    if (ausencia.deshechaEn !== null) {
      item.classList.add('deshecha');
      estado.className = 'detalle';
      estado.textContent = `Deshecha por ${ausencia.deshechaPor ?? ''} el ${fechaYHora(ausencia.deshechaEn)}`;
    } else {
      estado.className = 'etiqueta etiqueta-ausente';
      estado.textContent = 'Ausente';
    }
    item.append(cuando, estado);
    lista.append(item);
  }
  seccion.append(lista);
  return seccion;
}
