import type { ResultadoPrevio } from './modelo.ts';

/**
 * Lo que la firma le deja a la ficha cuando el profesional desbloquea (C-48, design D12 y D13): el aviso de "firmada",
 * de "sesión vencida" o los datos para comprobar un envío dudoso. Vive solo en memoria: nunca en la dirección ni en el
 * almacenamiento del navegador. Leerlo lo borra, así que al recargar la página no queda nada.
 */

let pendiente: ResultadoPrevio | null = null;

export function dejarResultado(resultado: ResultadoPrevio | null): void {
  pendiente = resultado;
}

/** Devuelve lo que dejó la firma y lo borra. */
export function tomarResultado(): ResultadoPrevio | null {
  const resultado = pendiente;
  pendiente = null;
  return resultado;
}
