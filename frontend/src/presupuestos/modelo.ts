import { CARAS, type Cara } from '../ficha/api.ts';
import { esPiezaFdi, MENSAJE_PIEZA_INVALIDA } from '../ficha/imagenes-modelo.ts';
import { carasDe } from '../ficha/odontograma-modelo.ts';

/**
 * Reglas puras de los presupuestos y aranceles (C-50, design D4 y D5). Los importes van en **centavos enteros** para
 * no arrastrar errores de punto flotante. No tocan el DOM ni la API, y ningún mensaje repite lo que se escribió.
 */

export type Centavos = number;

export const MENSAJE_CANTIDAD = 'Usá un número entero de 1 a 99.';
export const MENSAJE_CARA_SIN_PIEZA = 'Indicá la pieza para elegir una cara.';
export const MENSAJE_CARA_INVALIDA = 'Esa cara no corresponde a la pieza.';
export const MENSAJE_PRESTACION = 'Elegí la prestación.';
export const MENSAJE_CODIGO = 'Elegí el código del nomenclador.';
export const MENSAJE_PORCENTAJE = 'Usá un número entero de 0 a 100.';
export const MENSAJE_PORCENTAJE_VACIADO = 'Para dejar de cubrirlo, cargá 0.';

/** 9999999999.99, el máximo de C-19. */
const PRECIO_MAXIMO: Centavos = 999_999_999_999;

export function aCentavos(pesos: number): Centavos {
  return Math.round(pesos * 100);
}

export interface Importes {
  readonly importe: Centavos;
  readonly cobertura: Centavos;
  readonly copago: Centavos;
}

/**
 * RN-PR-03: importe = precio × cantidad; cobertura = importe × porcentaje / 100, *half up* a centavos;
 * copago = importe − cobertura.
 */
export function estimarItem(item: { readonly precio: Centavos; readonly cantidad: number; readonly porcentaje: number }): Importes {
  const importe = item.precio * item.cantidad;
  const cobertura = Math.floor((importe * item.porcentaje + 50) / 100);
  return { importe, cobertura, copago: importe - cobertura };
}

export interface Totales {
  readonly precio: Centavos;
  readonly cobertura: Centavos;
  readonly copago: Centavos;
}

export function sumarTotales(items: readonly Importes[]): Totales {
  return items.reduce<Totales>(
    (total, item) => ({
      precio: total.precio + item.importe,
      cobertura: total.cobertura + item.cobertura,
      copago: total.copago + item.copago,
    }),
    { precio: 0, cobertura: 0, copago: 0 },
  );
}

/** "$ 15.000,00": punto de miles y coma decimal, siempre con dos decimales. */
export function formatearPesos(centavos: Centavos): string {
  const negativo = centavos < 0;
  const absoluto = Math.abs(Math.round(centavos));
  const pesos = Math.floor(absoluto / 100).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const resto = String(absoluto % 100).padStart(2, '0');
  return `${negativo ? '-' : ''}$ ${pesos},${resto}`;
}

export type EstadoGuardado = 'PENDIENTE' | 'ACEPTADO' | 'RECHAZADO';
export type EstadoEscrito = 'Pendiente' | 'Vencido' | 'Aceptado' | 'Rechazado';

export function estadoPresupuesto(presupuesto: { readonly estado: EstadoGuardado; readonly vencido: boolean }): EstadoEscrito {
  if (presupuesto.estado === 'ACEPTADO') {
    return 'Aceptado';
  }
  if (presupuesto.estado === 'RECHAZADO') {
    return 'Rechazado';
  }
  return presupuesto.vencido ? 'Vencido' : 'Pendiente';
}

// --- Validación de los ítems ---

export function validarPrestacion(prestacionId: number | null): string {
  return prestacionId === null ? MENSAJE_PRESTACION : '';
}

export function validarCodigo(requerido: boolean, nomencladorId: number | null): string {
  return requerido && nomencladorId === null ? MENSAJE_CODIGO : '';
}

function leerPieza(texto: string): number | null {
  const limpio = texto.trim();
  return /^\d{2}$/.test(limpio) && esPiezaFdi(Number(limpio)) ? Number(limpio) : null;
}

/** La pieza es opcional; si está, tiene que existir en la notación FDI. */
export function validarPieza(texto: string): string {
  return texto.trim() === '' || leerPieza(texto) !== null ? '' : MENSAJE_PIEZA_INVALIDA;
}

export function esCara(valor: string): valor is Cara {
  return (CARAS as readonly string[]).includes(valor);
}

/** La cara es opcional; va con pieza y tiene que ser de esa pieza (las mismas reglas del odontograma). */
export function validarCara(piezaTexto: string, cara: string): string {
  if (cara === '') {
    return '';
  }
  if (piezaTexto.trim() === '') {
    return MENSAJE_CARA_SIN_PIEZA;
  }
  const pieza = leerPieza(piezaTexto);
  if (pieza === null) {
    // El error ya se muestra en "Pieza".
    return '';
  }
  return esCara(cara) && carasDe(pieza).includes(cara) ? '' : MENSAJE_CARA_INVALIDA;
}

export function validarCantidad(texto: string): string {
  const limpio = texto.trim();
  return /^\d{1,2}$/.test(limpio) && Number(limpio) >= 1 ? '' : MENSAJE_CANTIDAD;
}

// --- Lo que escribe el administrador ---

export type LecturaPrecio = { readonly centavos: Centavos } | { readonly error: string };

/** Acepta "15000", "15000,50", "15000.5" y "15.000,00" (punto de miles con coma decimal). */
export function leerPrecio(texto: string): LecturaPrecio {
  const limpio = texto.trim().replace(/\s/g, '');
  if (limpio === '') {
    return { error: 'Ingresá el precio.' };
  }
  const normal = limpio.includes(',') ? limpio.replace(/\./g, '').replace(',', '.') : limpio;
  if (!/^-?\d+(\.\d+)?$/.test(normal)) {
    return { error: 'Ingresá un número, por ejemplo 15000 o 15000,50.' };
  }
  const [entero = '', decimales = ''] = normal.replace('-', '').split('.');
  if (normal.startsWith('-') || /^0*$/.test(entero + decimales)) {
    return { error: 'Ingresá un precio mayor que cero.' };
  }
  if (decimales.length > 2) {
    return { error: 'Usá como máximo dos decimales.' };
  }
  const centavos = Number(entero) * 100 + Number(decimales.padEnd(2, '0'));
  if (centavos > PRECIO_MAXIMO) {
    return { error: 'El precio no puede superar $ 9.999.999.999,99.' };
  }
  return { centavos };
}

export type LecturaPorcentaje = { readonly porcentaje: number } | { readonly vacio: true } | { readonly error: string };

/** Entero de 0 a 100. Vacío es "no cubre" solo si no tenía porcentaje: para dejar de cubrir se carga 0. */
export function leerPorcentaje(texto: string, teniaValor: boolean): LecturaPorcentaje {
  const limpio = texto.trim();
  if (limpio === '') {
    return teniaValor ? { error: MENSAJE_PORCENTAJE_VACIADO } : { vacio: true };
  }
  if (!/^\d{1,3}$/.test(limpio) || Number(limpio) > 100) {
    return { error: MENSAJE_PORCENTAJE };
  }
  return { porcentaje: Number(limpio) };
}

// --- Emisión ---

export interface ItemBorrador {
  readonly prestacionId: number | null;
  readonly nomencladorId: number | null;
  readonly pieza: string;
  readonly cara: string;
  readonly cantidad: string;
}

export interface ItemEmision {
  readonly prestacionId: number;
  readonly nomencladorId: number | null;
  readonly pieza: number | null;
  readonly cara: Cara | null;
  readonly cantidad: number;
}

/**
 * Cuerpo del `POST` de emisión: solo prestación, código, pieza, cara y cantidad. El profesional, los precios y los
 * porcentajes los pone el backend. Se llama después de validar los ítems.
 */
export function cuerpoDeEmision(items: readonly ItemBorrador[]): { items: ItemEmision[] } {
  return {
    items: items.map((item) => ({
      prestacionId: item.prestacionId ?? 0,
      nomencladorId: item.nomencladorId,
      pieza: leerPieza(item.pieza),
      cara: esCara(item.cara) ? item.cara : null,
      cantidad: Number(item.cantidad.trim()),
    })),
  };
}
