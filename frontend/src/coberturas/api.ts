import type { ClienteApi } from '../shared/api/cliente.ts';

/**
 * Pedidos de las coberturas (API de C-18) y lectura defensiva de sus respuestas (C-49, design D4): un valor con otro
 * tipo se descarta. Nada de este módulo escribe en la consola ni en el almacenamiento del navegador (RN-OS-07).
 */

export type TipoCobertura = 'OBRA_SOCIAL' | 'PREPAGA';

export interface PlanCatalogo {
  readonly id: number;
  readonly nombre: string;
  readonly activo: boolean;
}

export interface ObraSocial {
  readonly id: number;
  readonly nombre: string;
  readonly sigla: string | null;
  readonly tipo: TipoCobertura;
  readonly activa: boolean;
  readonly planes: readonly PlanCatalogo[];
}

export interface CodigoNomenclador {
  readonly id: number;
  readonly codigo: string;
  readonly descripcion: string;
  readonly prestacion: { readonly id: number; readonly nombre: string } | null;
  readonly activo: boolean;
}

export interface Afiliacion {
  readonly id: number;
  /** `id` arma la ruta de la cobertura del plan (C-50, design D5); `null` si la respuesta no lo trae. */
  readonly obraSocial: { readonly id: number | null; readonly nombre: string; readonly sigla: string | null };
  readonly plan: { readonly id: number; readonly nombre: string };
  readonly numeroAfiliado: string;
  readonly vigenteDesde: string;
  readonly vigenteHasta: string | null;
  readonly vigente: boolean;
}

export interface Cobertura {
  readonly particular: boolean;
  readonly afiliacion: Afiliacion | null;
}

export interface DatosObraSocial {
  readonly nombre: string;
  /** Vacía se manda como `null` (design D5). */
  readonly sigla: string;
  readonly tipo: TipoCobertura;
}

export interface DatosCodigo {
  readonly codigo: string;
  readonly descripcion: string;
  readonly prestacionId: number | null;
}

export interface DatosCorreccion {
  readonly planId: number;
  readonly numeroAfiliado: string;
  readonly vigenteDesde: string;
}

export interface DatosAfiliacion extends DatosCorreccion {
  readonly vigenteHasta: string | null;
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

function logico(objeto: object, clave: string): boolean | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'boolean' ? valor : null;
}

function lista(cuerpo: unknown): unknown[] {
  return Array.isArray(cuerpo) ? cuerpo : [];
}

function leerPlan(elemento: unknown): PlanCatalogo | null {
  if (!esObjeto(elemento)) {
    return null;
  }
  const id = entero(elemento, 'id');
  const nombre = texto(elemento, 'nombre');
  const activo = logico(elemento, 'activo');
  return id === null || nombre === null || activo === null ? null : { id, nombre, activo };
}

function leerObraSocial(elemento: unknown): ObraSocial | null {
  if (!esObjeto(elemento)) {
    return null;
  }
  const id = entero(elemento, 'id');
  const nombre = texto(elemento, 'nombre');
  const tipo = texto(elemento, 'tipo');
  const activa = logico(elemento, 'activa');
  if (id === null || nombre === null || activa === null || (tipo !== 'OBRA_SOCIAL' && tipo !== 'PREPAGA')) {
    return null;
  }
  const sigla = texto(elemento, 'sigla');
  return {
    id,
    nombre,
    sigla: sigla === null || sigla.trim() === '' ? null : sigla,
    tipo,
    activa,
    planes: lista(Reflect.get(elemento, 'planes')).map(leerPlan).filter((p): p is PlanCatalogo => p !== null),
  };
}

export function leerObrasSociales(cuerpo: unknown): ObraSocial[] {
  return lista(cuerpo).map(leerObraSocial).filter((o): o is ObraSocial => o !== null);
}

function leerCodigo(elemento: unknown): CodigoNomenclador | null {
  if (!esObjeto(elemento)) {
    return null;
  }
  const id = entero(elemento, 'id');
  const codigo = texto(elemento, 'codigo');
  const descripcion = texto(elemento, 'descripcion');
  const activo = logico(elemento, 'activo');
  if (id === null || codigo === null || descripcion === null || activo === null) {
    return null;
  }
  const prestacion: unknown = Reflect.get(elemento, 'prestacion');
  const prestacionId = esObjeto(prestacion) ? entero(prestacion, 'id') : null;
  const prestacionNombre = esObjeto(prestacion) ? texto(prestacion, 'nombre') : null;
  return {
    id,
    codigo,
    descripcion,
    prestacion: prestacionId === null || prestacionNombre === null ? null : { id: prestacionId, nombre: prestacionNombre },
    activo,
  };
}

export function leerNomenclador(cuerpo: unknown): CodigoNomenclador[] {
  return lista(cuerpo).map(leerCodigo).filter((c): c is CodigoNomenclador => c !== null);
}

function leerAfiliacion(elemento: unknown): Afiliacion | null {
  if (!esObjeto(elemento)) {
    return null;
  }
  const id = entero(elemento, 'id');
  const obra: unknown = Reflect.get(elemento, 'obraSocial');
  const plan: unknown = Reflect.get(elemento, 'plan');
  const numeroAfiliado = texto(elemento, 'numeroAfiliado');
  const vigenteDesde = texto(elemento, 'vigenteDesde');
  const vigente = logico(elemento, 'vigente');
  if (id === null || !esObjeto(obra) || !esObjeto(plan) || numeroAfiliado === null || vigenteDesde === null || vigente === null) {
    return null;
  }
  const obraNombre = texto(obra, 'nombre');
  const planId = entero(plan, 'id');
  const planNombre = texto(plan, 'nombre');
  if (obraNombre === null || planId === null || planNombre === null) {
    return null;
  }
  const sigla = texto(obra, 'sigla');
  return {
    id,
    obraSocial: { id: entero(obra, 'id'), nombre: obraNombre, sigla: sigla === null || sigla.trim() === '' ? null : sigla },
    plan: { id: planId, nombre: planNombre },
    numeroAfiliado,
    vigenteDesde,
    vigenteHasta: texto(elemento, 'vigenteHasta'),
    vigente,
  };
}

export function leerAfiliaciones(cuerpo: unknown): Afiliacion[] {
  return lista(cuerpo).map(leerAfiliacion).filter((a): a is Afiliacion => a !== null);
}

/** Sin una afiliación legible, el paciente se atiende como particular. */
export function leerCobertura(cuerpo: unknown): Cobertura {
  const afiliacion = esObjeto(cuerpo) ? leerAfiliacion(Reflect.get(cuerpo, 'afiliacion')) : null;
  return afiliacion === null ? { particular: true, afiliacion: null } : { particular: false, afiliacion };
}

// --- Catálogo (solo ADMIN escribe) ---

export async function listarObrasSociales(api: ClienteApi): Promise<ObraSocial[]> {
  return leerObrasSociales(await api.obtener<unknown>('/obras-sociales'));
}

export async function guardarObraSocial(api: ClienteApi, id: number | null, datos: DatosObraSocial): Promise<void> {
  const sigla = datos.sigla.trim();
  const cuerpo = { nombre: datos.nombre.trim(), sigla: sigla === '' ? null : sigla, tipo: datos.tipo };
  if (id === null) {
    await api.crear<unknown>('/obras-sociales', cuerpo);
  } else {
    await api.actualizar<unknown>(`/obras-sociales/${id}`, cuerpo);
  }
}

function accion(activar: boolean): string {
  return activar ? 'activar' : 'desactivar';
}

export async function cambiarEstadoObraSocial(api: ClienteApi, id: number, activar: boolean): Promise<void> {
  await api.crear<unknown>(`/obras-sociales/${id}/${accion(activar)}`);
}

export async function guardarPlan(api: ClienteApi, obraSocialId: number, planId: number | null, nombre: string): Promise<void> {
  const cuerpo = { nombre: nombre.trim() };
  if (planId === null) {
    await api.crear<unknown>(`/obras-sociales/${obraSocialId}/planes`, cuerpo);
  } else {
    await api.actualizar<unknown>(`/obras-sociales/${obraSocialId}/planes/${planId}`, cuerpo);
  }
}

export async function cambiarEstadoPlan(api: ClienteApi, obraSocialId: number, planId: number, activar: boolean): Promise<void> {
  await api.crear<unknown>(`/obras-sociales/${obraSocialId}/planes/${planId}/${accion(activar)}`);
}

export async function listarNomenclador(api: ClienteApi): Promise<CodigoNomenclador[]> {
  return leerNomenclador(await api.obtener<unknown>('/nomenclador'));
}

export async function guardarCodigo(api: ClienteApi, id: number | null, datos: DatosCodigo): Promise<void> {
  const cuerpo = { codigo: datos.codigo.trim(), descripcion: datos.descripcion.trim(), prestacionId: datos.prestacionId };
  if (id === null) {
    await api.crear<unknown>('/nomenclador', cuerpo);
  } else {
    await api.actualizar<unknown>(`/nomenclador/${id}`, cuerpo);
  }
}

export async function cambiarEstadoCodigo(api: ClienteApi, id: number, activar: boolean): Promise<void> {
  await api.crear<unknown>(`/nomenclador/${id}/${accion(activar)}`);
}

// --- Afiliaciones (ADMIN y RECEPCIONISTA escriben; ODONTOLOGO lee) ---

function afiliaciones(pacienteId: number): string {
  return `/pacientes/${pacienteId}/afiliaciones`;
}

export async function obtenerCobertura(api: ClienteApi, pacienteId: number): Promise<Cobertura> {
  return leerCobertura(await api.obtener<unknown>(`/pacientes/${pacienteId}/cobertura`));
}

export async function listarAfiliaciones(api: ClienteApi, pacienteId: number): Promise<Afiliacion[]> {
  return leerAfiliaciones(await api.obtener<unknown>(afiliaciones(pacienteId)));
}

export async function afiliar(api: ClienteApi, pacienteId: number, datos: DatosAfiliacion): Promise<void> {
  await api.crear<unknown>(afiliaciones(pacienteId), {
    planId: datos.planId,
    numeroAfiliado: datos.numeroAfiliado,
    vigenteDesde: datos.vigenteDesde,
    vigenteHasta: datos.vigenteHasta,
  });
}

export async function corregirAfiliacion(
  api: ClienteApi,
  pacienteId: number,
  afiliacionId: number,
  datos: DatosCorreccion,
): Promise<void> {
  await api.actualizar<unknown>(`${afiliaciones(pacienteId)}/${afiliacionId}`, {
    planId: datos.planId,
    numeroAfiliado: datos.numeroAfiliado,
    vigenteDesde: datos.vigenteDesde,
  });
}

export async function cerrarAfiliacion(api: ClienteApi, pacienteId: number, afiliacionId: number, vigenteHasta: string): Promise<void> {
  await api.crear<unknown>(`${afiliaciones(pacienteId)}/${afiliacionId}/cerrar`, { vigenteHasta });
}

/** La cobertura del paciente con sesión: la API lo identifica por la sesión, sin ids (RN-OS-07). */
export async function obtenerMiCobertura(api: ClienteApi): Promise<Cobertura> {
  return leerCobertura(await api.obtener<unknown>('/paciente/cobertura'));
}
