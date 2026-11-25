/** Importes de la caja (design D6 y D10 de C-22): se muestran en pesos de Argentina y se mandan como texto. */

const PESOS = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' });

/** "$ 15.000,00"; un negativo, "-$ 500,00". */
export function formatearPesos(valor: number): string {
  return PESOS.format(valor);
}

/** Para el valor inicial de un campo: "9000,00". */
export function importeParaCampo(valor: number): string {
  return valor.toFixed(2).replace('.', ',');
}

const CON_MILES = /^\d{1,3}(\.\d{3})+(,\d{1,2})?$/;
const SIN_MILES = /^\d+([.,]\d{1,2})?$/;

/**
 * Lee lo que escribe la persona ("4000", "4000,50", "4.000,50" o "4000.50") y devuelve el importe con dos decimales
 * como texto ("4000.50"), o `null` si no es un importe válido mayor o igual a cero.
 */
export function leerImporte(escrito: string): string | null {
  const limpio = escrito.trim();
  let normalizado: string;
  if (CON_MILES.test(limpio)) {
    normalizado = limpio.replace(/\./g, '').replace(',', '.');
  } else if (SIN_MILES.test(limpio)) {
    normalizado = limpio.replace(',', '.');
  } else {
    return null;
  }
  const [enteros = '0', decimales = ''] = normalizado.split('.');
  return `${String(Number(enteros))}.${decimales.padEnd(2, '0')}`;
}

/** Valor numérico de un importe ya leído, para comparar y mostrar. */
export function comoNumero(importe: string): number {
  return Number(importe);
}
