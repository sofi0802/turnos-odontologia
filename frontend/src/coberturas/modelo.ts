import { sumarDias, type Fecha } from '../shared/fecha/zona.ts';

/**
 * Reglas puras de las coberturas (C-49, design D4): validaciones antes de enviar, el estado escrito de una afiliación
 * y los textos de vigencia. No tocan el DOM ni la API. Ningún mensaje repite lo que escribió la persona (RN-OS-07).
 */

export const MENSAJE_NUMERO_INVALIDO = 'Usá de 1 a 30 letras, números, guiones, barras o puntos.';
export const MENSAJE_FIN_ANTERIOR = 'Tiene que ser igual o posterior a la fecha de inicio.';
export const MENSAJE_DESDE_VACIO = 'Completá la fecha de inicio.';

/** El patrón de C-18 para el número de afiliado, después de quitar los espacios de los extremos. */
const PATRON_NUMERO = /^[A-Za-z0-9./-]{1,30}$/;

export function normalizarNumero(valor: string): string {
  return valor.trim();
}

export function validarNumero(valor: string): string {
  return PATRON_NUMERO.test(normalizarNumero(valor)) ? '' : MENSAJE_NUMERO_INVALIDO;
}

export function validarDesde(desde: Fecha): string {
  return desde === '' ? MENSAJE_DESDE_VACIO : '';
}

/** "Vigente hasta" es opcional; si está, no puede ser anterior al inicio. Las fechas `AAAA-MM-DD` se comparan como texto. */
export function validarFin(desde: Fecha, hasta: Fecha): string {
  return desde !== '' && hasta !== '' && hasta < desde ? MENSAJE_FIN_ANTERIOR : '';
}

/** Texto obligatorio con un largo máximo (columnas de V12). */
function validarTexto(valor: string, maximo: number, vacio: string, largo: string): string {
  const limpio = valor.trim();
  if (limpio === '') {
    return vacio;
  }
  return limpio.length > maximo ? largo : '';
}

export function validarNombreObraSocial(valor: string): string {
  return validarTexto(valor, 120, 'Completá el nombre.', 'El nombre puede tener hasta 120 caracteres.');
}

export function validarSigla(valor: string): string {
  return valor.trim().length > 20 ? 'La sigla puede tener hasta 20 caracteres.' : '';
}

export function validarNombrePlan(valor: string): string {
  return validarTexto(valor, 80, 'Completá el nombre.', 'El nombre puede tener hasta 80 caracteres.');
}

export function validarCodigo(valor: string): string {
  return validarTexto(valor, 20, 'Completá el código.', 'El código puede tener hasta 20 caracteres.');
}

export function validarDescripcion(valor: string): string {
  return validarTexto(valor, 200, 'Completá la descripción.', 'La descripción puede tener hasta 200 caracteres.');
}

export type EstadoAfiliacion = 'Vigente' | 'Próxima' | 'Finalizada';

/** "Vigente" lo dice la API; si no lo es, "Próxima" si empieza después de hoy y "Finalizada" si no. */
export function estadoAfiliacion(afiliacion: { readonly vigente: boolean; readonly vigenteDesde: Fecha }, hoy: Fecha): EstadoAfiliacion {
  if (afiliacion.vigente) {
    return 'Vigente';
  }
  return afiliacion.vigenteDesde > hoy ? 'Próxima' : 'Finalizada';
}

/** `AAAA-MM-DD` → `DD/MM/AAAA`. */
export function formatearFecha(fecha: Fecha): string {
  const [anio, mes, dia] = fecha.split('-');
  return `${dia ?? ''}/${mes ?? ''}/${anio ?? ''}`;
}

export function textoVigencia(desde: Fecha, hasta: Fecha | null): string {
  return hasta === null
    ? `Desde ${formatearFecha(desde)}, sin fecha de fin`
    : `Del ${formatearFecha(desde)} al ${formatearFecha(hasta)}`;
}

/** Hoy, o el día siguiente al cierre más reciente si es posterior (para cambiar de cobertura en dos pasos, design D7). */
export function vigenteDesdePorDefecto(afiliaciones: readonly { readonly vigenteHasta: Fecha | null }[], hoy: Fecha): Fecha {
  let propuesta = hoy;
  for (const { vigenteHasta } of afiliaciones) {
    if (vigenteHasta !== null) {
      const siguiente = sumarDias(vigenteHasta, 1);
      if (siguiente > propuesta) {
        propuesta = siguiente;
      }
    }
  }
  return propuesta;
}
