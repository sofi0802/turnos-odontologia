import { lunesDeLaSemana, sumarDias, type Fecha } from '../shared/fecha/zona.ts';

/** Período de un reporte, con el primer y el último día incluidos (RN-RE-03). */
export interface PeriodoReporte {
  readonly desde: Fecha;
  readonly hasta: Fecha;
}

export const MAXIMO_DIAS = 92;
export const MENSAJE_FIN_ANTERIOR = 'La fecha final tiene que ser igual o posterior a la inicial.';
export const MENSAJE_PERIODO_LARGO = `Elegí un período de hasta ${MAXIMO_DIAS} días.`;
export const MENSAJE_FECHA_OBLIGATORIA = 'Elegí una fecha.';

const MS_POR_DIA = 86_400_000;

/** La semana en curso, de lunes a domingo (spec frontend-reportes, "Semana en curso al abrir"). */
export function semanaEnCurso(hoy: Fecha): PeriodoReporte {
  const desde = lunesDeLaSemana(hoy);
  return { desde, hasta: sumarDias(desde, 6) };
}

function diaUtc(fecha: Fecha): number {
  const [anio, mes, dia] = fecha.split('-').map(Number);
  return Date.UTC(anio ?? 0, (mes ?? 1) - 1, dia ?? 1);
}

/** Error del período para mostrar junto a "Hasta" (o "Desde" si falta), o `null` si se puede pedir. */
export function errorDelPeriodo(periodo: PeriodoReporte): { campo: 'desde' | 'hasta'; mensaje: string } | null {
  if (periodo.desde === '') {
    return { campo: 'desde', mensaje: MENSAJE_FECHA_OBLIGATORIA };
  }
  if (periodo.hasta === '') {
    return { campo: 'hasta', mensaje: MENSAJE_FECHA_OBLIGATORIA };
  }
  const dias = (diaUtc(periodo.hasta) - diaUtc(periodo.desde)) / MS_POR_DIA + 1;
  if (dias < 1) {
    return { campo: 'hasta', mensaje: MENSAJE_FIN_ANTERIOR };
  }
  if (dias > MAXIMO_DIAS) {
    return { campo: 'hasta', mensaje: MENSAJE_PERIODO_LARGO };
  }
  return null;
}
