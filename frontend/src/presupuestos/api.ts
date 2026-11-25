import type { Cara } from '../ficha/api.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import { aCentavos, esCara, type Centavos, type EstadoGuardado, type ItemEmision, type Totales } from './modelo.ts';

/**
 * Pedidos de los presupuestos (API de C-19) y lectura defensiva de sus respuestas (C-50, design D5): un valor con otro
 * tipo se descarta. Los importes se leen en centavos. Nada de este módulo escribe en la consola (RN-PR-07).
 */

export interface ItemPresupuesto {
  readonly id: number;
  readonly prestacion: string;
  readonly codigo: string | null;
  /** `null` también cuando la API no la manda (recepción, PA-24). */
  readonly pieza: number | null;
  readonly cara: Cara | null;
  readonly cantidad: number;
  readonly precioUnitario: Centavos;
  readonly porcentaje: number;
  readonly importe: Centavos;
  readonly cobertura: Centavos;
  readonly copago: Centavos;
}

export interface Presupuesto {
  readonly id: number;
  readonly fecha: string;
  readonly venceEl: string;
  readonly estado: EstadoGuardado;
  readonly vencido: boolean;
  readonly profesional: string;
  readonly particular: boolean;
  readonly cobertura: { readonly obraSocial: string; readonly plan: string; readonly numeroAfiliado: string } | null;
  readonly items: readonly ItemPresupuesto[];
  readonly totales: Totales;
}

export type EstadoSugerido = 'CARIES' | 'A_TRATAR';

export interface Sugerencia {
  readonly pieza: number;
  readonly cara: Cara | null;
  readonly estado: EstadoSugerido;
}

// --- Lectura defensiva ---

function esObjeto(valor: unknown): valor is object {
  return typeof valor === 'object' && valor !== null;
}

function texto(objeto: object, clave: string): string | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'string' ? valor : null;
}

function entero(objeto: object, clave: string): number | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'number' && Number.isInteger(valor) ? valor : null;
}

function importe(objeto: object, clave: string): Centavos | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'number' && Number.isFinite(valor) ? aCentavos(valor) : null;
}

function logico(objeto: object, clave: string): boolean | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'boolean' ? valor : null;
}

function lista(cuerpo: unknown): unknown[] {
  return Array.isArray(cuerpo) ? cuerpo : [];
}

function caraDe(objeto: object): Cara | null {
  const cara = texto(objeto, 'cara');
  return cara !== null && esCara(cara) ? cara : null;
}

function nombreDe(objeto: object, clave: string): string | null {
  const ref: unknown = Reflect.get(objeto, clave);
  return esObjeto(ref) ? texto(ref, 'nombre') : null;
}

function leerItem(elemento: unknown): ItemPresupuesto | null {
  if (!esObjeto(elemento)) {
    return null;
  }
  const id = entero(elemento, 'id');
  const prestacion = nombreDe(elemento, 'prestacion');
  const cantidad = entero(elemento, 'cantidad');
  const porcentaje = entero(elemento, 'porcentajeCobertura');
  const precioUnitario = importe(elemento, 'precioUnitario');
  const total = importe(elemento, 'importe');
  const cobertura = importe(elemento, 'cobertura');
  const copago = importe(elemento, 'copago');
  if (id === null || prestacion === null || cantidad === null || porcentaje === null || precioUnitario === null || total === null || cobertura === null || copago === null) {
    return null;
  }
  return {
    id,
    prestacion,
    codigo: texto(elemento, 'codigoNomenclador'),
    pieza: entero(elemento, 'pieza'),
    cara: caraDe(elemento),
    cantidad,
    precioUnitario,
    porcentaje,
    importe: total,
    cobertura,
    copago,
  };
}

function leerTotales(valor: unknown): Totales | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const precio = importe(valor, 'precio');
  const cobertura = importe(valor, 'cobertura');
  const copago = importe(valor, 'copago');
  return precio === null || cobertura === null || copago === null ? null : { precio, cobertura, copago };
}

function esEstado(valor: string | null): valor is EstadoGuardado {
  return valor === 'PENDIENTE' || valor === 'ACEPTADO' || valor === 'RECHAZADO';
}

export function leerPresupuesto(cuerpo: unknown): Presupuesto | null {
  if (!esObjeto(cuerpo)) {
    return null;
  }
  const id = entero(cuerpo, 'id');
  const fecha = texto(cuerpo, 'fecha');
  const venceEl = texto(cuerpo, 'venceEl');
  const estado = texto(cuerpo, 'estado');
  const vencido = logico(cuerpo, 'vencido');
  const totales = leerTotales(Reflect.get(cuerpo, 'totales'));
  if (id === null || fecha === null || venceEl === null || !esEstado(estado) || vencido === null || totales === null) {
    return null;
  }
  const cobertura: unknown = Reflect.get(cuerpo, 'cobertura');
  const obraSocial = esObjeto(cobertura) ? texto(cobertura, 'obraSocial') : null;
  const plan = esObjeto(cobertura) ? texto(cobertura, 'plan') : null;
  const numeroAfiliado = esObjeto(cobertura) ? texto(cobertura, 'numeroAfiliado') : null;
  const usada = obraSocial === null || plan === null || numeroAfiliado === null ? null : { obraSocial, plan, numeroAfiliado };
  return {
    id,
    fecha,
    venceEl,
    estado,
    vencido,
    profesional: nombreDe(cuerpo, 'profesional') ?? '',
    particular: usada === null,
    cobertura: usada,
    items: lista(Reflect.get(cuerpo, 'items')).map(leerItem).filter((i): i is ItemPresupuesto => i !== null),
    totales,
  };
}

export function leerPresupuestos(cuerpo: unknown): Presupuesto[] {
  return lista(cuerpo).map(leerPresupuesto).filter((p): p is Presupuesto => p !== null);
}

export function leerSugerencias(cuerpo: unknown): Sugerencia[] {
  const sugerencias: Sugerencia[] = [];
  for (const elemento of lista(cuerpo)) {
    if (!esObjeto(elemento)) {
      continue;
    }
    const pieza = entero(elemento, 'pieza');
    const estado = texto(elemento, 'estado');
    if (pieza !== null && (estado === 'CARIES' || estado === 'A_TRATAR')) {
      sugerencias.push({ pieza, cara: caraDe(elemento), estado });
    }
  }
  return sugerencias;
}

// --- Pedidos del personal ---

function base(pacienteId: number): string {
  return `/pacientes/${pacienteId}/presupuestos`;
}

export async function listarPresupuestos(api: ClienteApi, pacienteId: number): Promise<Presupuesto[]> {
  return leerPresupuestos(await api.obtener<unknown>(base(pacienteId)));
}

/** Solo `ODONTOLOGO` (C-19). */
export async function listarSugerencias(api: ClienteApi, pacienteId: number): Promise<Sugerencia[]> {
  return leerSugerencias(await api.obtener<unknown>(`${base(pacienteId)}/sugerencias`));
}

export async function emitirPresupuesto(api: ClienteApi, pacienteId: number, cuerpo: { items: readonly ItemEmision[] }): Promise<Presupuesto | null> {
  return leerPresupuesto(await api.crear<unknown>(base(pacienteId), cuerpo));
}

export async function aceptarPresupuesto(api: ClienteApi, pacienteId: number, presupuestoId: number): Promise<void> {
  await api.crear<unknown>(`${base(pacienteId)}/${presupuestoId}/aceptar`);
}

export async function rechazarPresupuesto(api: ClienteApi, pacienteId: number, presupuestoId: number): Promise<void> {
  await api.crear<unknown>(`${base(pacienteId)}/${presupuestoId}/rechazar`);
}

// --- Pedidos del paciente ---

/** El paciente con sesión: la API lo identifica por la sesión, sin ids. */
export async function listarMisPresupuestos(api: ClienteApi): Promise<Presupuesto[]> {
  return leerPresupuestos(await api.obtener<unknown>('/paciente/presupuestos'));
}
