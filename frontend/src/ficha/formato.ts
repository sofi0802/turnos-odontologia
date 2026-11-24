import { diaDeLaSemana, fechaDeInstante, formatearHora } from '../shared/fecha/zona.ts';

/** "01/12/2026", en la zona del consultorio. */
export function fechaNumerica(instante: string): string {
  const [anio, mes, dia] = fechaDeInstante(instante).split('-');
  return `${dia ?? ''}/${mes ?? ''}/${anio ?? ''}`;
}

/** "01/12/2026 a las 09:40". */
export function fechaYHora(instante: string): string {
  return `${fechaNumerica(instante)} a las ${formatearHora(instante)}`;
}

/** "martes 01/12 · 10:00 – 10:30". */
export function rangoDelTurno(inicio: string, fin: string): string {
  const fecha = fechaDeInstante(inicio);
  const [, mes, dia] = fecha.split('-');
  return `${diaDeLaSemana(fecha)} ${dia ?? ''}/${mes ?? ''} · ${formatearHora(inicio)} – ${formatearHora(fin)}`;
}
