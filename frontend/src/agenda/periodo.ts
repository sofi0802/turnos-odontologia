import { aIsoConZona, lunesDeLaSemana, sumarDias, type Fecha } from '../shared/fecha/zona.ts';

export type Vista = 'dia' | 'semana';

/** Período que muestra la agenda: `[desde, hasta)` en fechas del consultorio. */
export interface Periodo {
  readonly vista: Vista;
  readonly desde: Fecha;
  readonly hasta: Fecha;
}

/** Período de la vista que contiene la fecha (la semana va de lunes a domingo). */
export function periodoDe(vista: Vista, fecha: Fecha): Periodo {
  const desde = vista === 'dia' ? fecha : lunesDeLaSemana(fecha);
  return { vista, desde, hasta: sumarDias(desde, vista === 'dia' ? 1 : 7) };
}

/** Mueve el período un día o una semana hacia adelante (`pasos` > 0) o atrás. */
export function mover(periodo: Periodo, pasos: number): Periodo {
  return periodoDe(periodo.vista, sumarDias(periodo.desde, pasos * (periodo.vista === 'dia' ? 1 : 7)));
}

/** Fechas del período, en orden. */
export function diasDe(periodo: Periodo): Fecha[] {
  const dias: Fecha[] = [];
  for (let dia = periodo.desde; dia < periodo.hasta; dia = sumarDias(dia, 1)) {
    dias.push(dia);
  }
  return dias;
}

export function desdeIso(periodo: Periodo): string {
  return aIsoConZona(periodo.desde, '00:00');
}

export function hastaIso(periodo: Periodo): string {
  return aIsoConZona(periodo.hasta, '00:00');
}
