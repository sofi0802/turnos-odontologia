/**
 * Paciente (y turno) que "Ver ficha" de la agenda le deja a la página Pacientes (C-44, design D1). Vive solo en
 * memoria: nunca en la dirección ni en el almacenamiento del navegador. Leerlo lo borra, así que al recargar la
 * página se vuelve al buscador.
 */

export interface PacienteDeLaFicha {
  readonly id: number;
  readonly nombre: string;
  readonly apellido: string;
  /** `null` cuando se llega desde un turno, que no trae el DNI. */
  readonly dni: string | null;
}

/** El turno desde el que se abrió la ficha: habilita "Marcar ausente" (RN-AG-17). */
export interface TurnoDeLaFicha {
  readonly id: number;
  readonly profesionalId: number;
  readonly inicio: string;
  readonly fin: string;
  readonly estado: string;
  readonly llegadaEn: string | null;
}

export interface SeleccionFicha {
  readonly paciente: PacienteDeLaFicha;
  readonly turno: TurnoDeLaFicha | null;
}

let pendiente: SeleccionFicha | null = null;

export function dejarSeleccion(seleccion: SeleccionFicha): void {
  pendiente = seleccion;
}

/** Devuelve la selección que dejó la agenda y la borra. */
export function tomarSeleccion(): SeleccionFicha | null {
  const seleccion = pendiente;
  pendiente = null;
  return seleccion;
}
