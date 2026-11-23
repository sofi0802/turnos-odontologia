/**
 * Lleva el número de reserva que escribió el personal al formato que exige la API, `XXXX-XXXX`
 * (el backend compara sin distinguir mayúsculas, pero exige el guion). Quita espacios y guiones,
 * pasa a mayúsculas y, si quedan 8 símbolos, vuelve a poner el guion en la mitad. Si no quedan 8,
 * devuelve lo ingresado sin tocar y deja que la API decida (design D5).
 */
export function normalizarNumeroReserva(ingresado: string): string {
  const simbolos = ingresado.replace(/[\s-]/g, '').toUpperCase();
  return /^[A-Z0-9]{8}$/.test(simbolos) ? `${simbolos.slice(0, 4)}-${simbolos.slice(4)}` : ingresado;
}
