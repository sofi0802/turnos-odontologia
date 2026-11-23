/**
 * Fechas y horas en la zona del consultorio (design D2). Las fechas de calendario
 * viajan como texto `AAAA-MM-DD`; el desplazamiento horario sale de `Intl`, nunca
 * se escribe a mano.
 */
export const ZONA = 'America/Argentina/Buenos_Aires';

/** Fecha de calendario `AAAA-MM-DD`. */
export type Fecha = string;

const PARTES_FECHA = new Intl.DateTimeFormat('en-CA', {
  timeZone: ZONA,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const HORA = new Intl.DateTimeFormat('es-AR', {
  timeZone: ZONA,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

const DESPLAZAMIENTO = new Intl.DateTimeFormat('en-US', {
  timeZone: ZONA,
  timeZoneName: 'longOffset',
});

const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'] as const;

const MS_POR_MINUTO = 60_000;
const MS_POR_DIA = 86_400_000;

function partes(fecha: Fecha): [number, number, number] {
  const [anio, mes, dia] = fecha.split('-').map(Number);
  return [anio ?? Number.NaN, mes ?? Number.NaN, dia ?? Number.NaN];
}

function desdeUtc(milisegundos: number): Fecha {
  return new Date(milisegundos).toISOString().slice(0, 10);
}

/** Fecha en la zona del consultorio del instante dado. */
export function fechaDeInstante(instante: Date | string): Fecha {
  return PARTES_FECHA.format(new Date(instante));
}

/** Fecha de hoy en la zona del consultorio. */
export function hoy(): Fecha {
  return fechaDeInstante(new Date());
}

export function sumarDias(fecha: Fecha, dias: number): Fecha {
  const [anio, mes, dia] = partes(fecha);
  return desdeUtc(Date.UTC(anio, mes - 1, dia) + dias * MS_POR_DIA);
}

/** Lunes de la semana (lunes a domingo) que contiene la fecha. */
export function lunesDeLaSemana(fecha: Fecha): Fecha {
  const [anio, mes, dia] = partes(fecha);
  const diaSemana = new Date(Date.UTC(anio, mes - 1, dia)).getUTCDay();
  return sumarDias(fecha, -((diaSemana + 6) % 7));
}

export function diaDeLaSemana(fecha: Fecha): string {
  const [anio, mes, dia] = partes(fecha);
  return DIAS[new Date(Date.UTC(anio, mes - 1, dia)).getUTCDay()] ?? '';
}

/** Desplazamiento de la zona en minutos para un instante (por ejemplo, -180). */
function minutosDeDesplazamiento(milisegundos: number): number {
  const nombre = DESPLAZAMIENTO.formatToParts(new Date(milisegundos)).find((p) => p.type === 'timeZoneName');
  const coincidencia = /GMT([+-])(\d{2}):(\d{2})/.exec(nombre?.value ?? '');
  if (coincidencia === null) {
    return 0;
  }
  const [, signo, horas, minutos] = coincidencia;
  const total = Number(horas) * 60 + Number(minutos);
  return signo === '-' ? -total : total;
}

function formatoDesplazamiento(minutos: number): string {
  const signo = minutos < 0 ? '-' : '+';
  const absoluto = Math.abs(minutos);
  const horas = String(Math.floor(absoluto / 60)).padStart(2, '0');
  return `${signo}${horas}:${String(absoluto % 60).padStart(2, '0')}`;
}

/** ISO-8601 con zona para una fecha y hora (`HH:MM`) del consultorio. */
export function aIsoConZona(fecha: Fecha, hora: string): string {
  const [anio, mes, dia] = partes(fecha);
  const [horas, minutos] = hora.split(':').map(Number);
  const comoSiFueraUtc = Date.UTC(anio, mes - 1, dia, horas ?? 0, minutos ?? 0);
  const primerIntento = minutosDeDesplazamiento(comoSiFueraUtc);
  const desplazamiento = minutosDeDesplazamiento(comoSiFueraUtc - primerIntento * MS_POR_MINUTO);
  return `${fecha}T${hora}:00${formatoDesplazamiento(desplazamiento)}`;
}

/** Hora `HH:MM` en la zona del consultorio. */
export function formatearHora(instante: Date | string): string {
  return HORA.format(new Date(instante));
}

const FECHA_LARGA = new Intl.DateTimeFormat('es-AR', {
  timeZone: ZONA,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

/** Fecha larga en la zona del consultorio, por ejemplo "martes 1 de diciembre". */
export function fechaLarga(instante: Date | string): string {
  const partesFecha = FECHA_LARGA.formatToParts(new Date(instante));
  const valor = (tipo: Intl.DateTimeFormatPartTypes): string => partesFecha.find((p) => p.type === tipo)?.value ?? '';
  return `${valor('weekday')} ${valor('day')} de ${valor('month')}`;
}
