import { diaDeLaSemana, fechaDeInstante, formatearHora } from '../shared/fecha/zona.ts';
import type { MedioPago } from './api.ts';

/** Textos compartidos de la pantalla Caja. */

export const NOMBRE_MEDIO: Readonly<Record<MedioPago, string>> = {
  EFECTIVO: 'Efectivo',
  DEBITO: 'Débito',
  CREDITO: 'Crédito',
  TRANSFERENCIA: 'Transferencia',
};

/** "lunes 07/12" para una fecha `AAAA-MM-DD`. */
export function diaCorto(fecha: string): string {
  const [, mes, dia] = fecha.split('-');
  return `${diaDeLaSemana(fecha).toLowerCase()} ${dia ?? ''}/${mes ?? ''}`;
}

/** "07/12" para un instante (columna "Turno" de la cuenta corriente, como el mockup). */
export function diaYMes(instante: string): string {
  const [, mes, dia] = fechaDeInstante(instante).split('-');
  return `${dia ?? ''}/${mes ?? ''}`;
}

/** "lunes 07/12 10:00" para un instante. */
export function fechaYHora(instante: string): string {
  return `${diaCorto(fechaDeInstante(instante))} ${formatearHora(instante)}`;
}
