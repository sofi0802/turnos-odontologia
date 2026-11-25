import { aIsoConZona, fechaDeInstante, fechaLarga, formatearHora, type Fecha } from '../shared/fecha/zona.ts';
import type { Inscripcion, OfertaPendiente } from './api.ts';

export const SIN_PREFERENCIA = 'Sin preferencia';

/** "28/11" de una fecha `YYYY-MM-DD`. */
export function diaYMes(fecha: Fecha): string {
  const [, mes, dia] = fecha.split('-');
  return `${dia ?? ''}/${mes ?? ''}`;
}

/** Fecha larga de un día del consultorio, por ejemplo "martes 1 de diciembre". */
export function fechaLargaDeDia(fecha: Fecha): string {
  return fechaLarga(aIsoConZona(fecha, '12:00'));
}

export function profesionalDe(inscripcion: Inscripcion): string {
  return inscripcion.profesionalNombre ?? SIN_PREFERENCIA;
}

/** "Del 28/11 al 12/12", para la fila de la recepción. */
export function rangoCorto(inscripcion: Inscripcion): string {
  return `Del ${diaYMes(inscripcion.desde)} al ${diaYMes(inscripcion.hasta)}`;
}

/** "del martes 1 de diciembre al martes 15 de diciembre", para Mis turnos. */
export function rangoLargo(inscripcion: Inscripcion): string {
  return `del ${fechaLargaDeDia(inscripcion.desde)} al ${fechaLargaDeDia(inscripcion.hasta)}`;
}

/** "Anotado el 28/11", o "Anotado hoy". */
export function anotadoEl(inscripcion: Inscripcion, hoy: Fecha): string {
  const dia = fechaDeInstante(inscripcion.inscriptaEn);
  return dia === hoy ? 'Anotado hoy' : `Anotado el ${diaYMes(dia)}`;
}

/** "jueves 3 de diciembre a las 10:00". */
export function diaYHora(instante: string): string {
  return `${fechaLarga(instante)} a las ${formatearHora(instante)}`;
}

export function textoOfertaRecepcion(oferta: OfertaPendiente): string {
  return `Oferta enviada: ${diaYHora(oferta.inicio)}, vence a las ${formatearHora(oferta.venceEn)}`;
}

export function textoOfertaPaciente(oferta: OfertaPendiente): string {
  return `Te enviamos un horario: ${diaYHora(oferta.inicio)}. Revisá tu WhatsApp o tu email.`;
}
