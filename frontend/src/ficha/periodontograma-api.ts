import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorGenerico } from '../shared/api/errores.ts';

/**
 * Pedidos del periodontograma (endpoints de C-25) y lectura defensiva de sus respuestas (C-46, design D1). Nada de
 * este módulo escribe en la consola ni en el almacenamiento del navegador (Ley 26.529, D9 de C-44).
 */

export const SITIOS = ['MV', 'V', 'DV', 'ML', 'L', 'DL'] as const;
export type Sitio = (typeof SITIOS)[number];

export const ESTADOS_PERIODONTALES = ['SANO', 'LEVE', 'MODERADO', 'SEVERO'] as const;
export type EstadoPeriodontal = (typeof ESTADOS_PERIODONTALES)[number];

export interface ResumenPeriodontograma {
  readonly id: number;
  readonly profesional: string;
  readonly registradoEn: string;
}

export interface SitioPeriodontal {
  readonly sitio: Sitio;
  readonly profundidadSondaje: number;
  readonly recesion: number;
  readonly sangrado: boolean;
  readonly placa: boolean;
}

export interface PiezaPeriodontograma {
  readonly pieza: number;
  readonly ausente: boolean;
  readonly movilidad: number | null;
  readonly furcacion: number | null;
  /** Lo calcula el backend (RN-CL-06); `null` en una pieza ausente. */
  readonly estado: EstadoPeriodontal | null;
  readonly sitios: readonly SitioPeriodontal[];
}

export interface VersionPeriodontograma extends ResumenPeriodontograma {
  readonly piezas: readonly PiezaPeriodontograma[];
}

export type PiezaPedido =
  | { readonly pieza: number; readonly ausente: true }
  | {
      readonly pieza: number;
      readonly ausente: false;
      readonly movilidad: number;
      readonly furcacion: number | null;
      readonly sitios: readonly SitioPeriodontal[];
    };

export interface PedidoPeriodontograma {
  readonly piezas: readonly PiezaPedido[];
}

function texto(objeto: object, clave: string): string | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'string' ? valor : null;
}

function entero(objeto: object, clave: string): number | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'number' && Number.isInteger(valor) ? valor : null;
}

function logico(objeto: object, clave: string): boolean {
  return Reflect.get(objeto, clave) === true;
}

function esObjeto(valor: unknown): valor is object {
  return typeof valor === 'object' && valor !== null;
}

function esSitio(valor: unknown): valor is Sitio {
  return typeof valor === 'string' && (SITIOS as readonly string[]).includes(valor);
}

function esEstado(valor: unknown): valor is EstadoPeriodontal {
  return typeof valor === 'string' && (ESTADOS_PERIODONTALES as readonly string[]).includes(valor);
}

function leerResumen(elemento: unknown): ResumenPeriodontograma | null {
  if (!esObjeto(elemento)) {
    return null;
  }
  const id = entero(elemento, 'id');
  const registradoEn = texto(elemento, 'registradoEn');
  if (id === null || registradoEn === null) {
    return null;
  }
  return { id, profesional: texto(elemento, 'profesional') ?? '', registradoEn };
}

function masNuevaPrimero(a: ResumenPeriodontograma, b: ResumenPeriodontograma): number {
  return Date.parse(b.registradoEn) - Date.parse(a.registradoEn) || b.id - a.id;
}

/** Lista de versiones, de la más nueva a la más vieja. */
export function leerResumenes(cuerpo: unknown): ResumenPeriodontograma[] {
  return (Array.isArray(cuerpo) ? cuerpo : [])
    .map(leerResumen)
    .filter((r): r is ResumenPeriodontograma => r !== null)
    .sort(masNuevaPrimero);
}

function leerSitio(elemento: unknown): SitioPeriodontal | null {
  if (!esObjeto(elemento)) {
    return null;
  }
  const sitio: unknown = Reflect.get(elemento, 'sitio');
  const profundidadSondaje = entero(elemento, 'profundidadSondaje');
  const recesion = entero(elemento, 'recesion');
  if (!esSitio(sitio) || profundidadSondaje === null || recesion === null) {
    return null;
  }
  return { sitio, profundidadSondaje, recesion, sangrado: logico(elemento, 'sangrado'), placa: logico(elemento, 'placa') };
}

function leerPieza(elemento: unknown): PiezaPeriodontograma | null {
  if (!esObjeto(elemento)) {
    return null;
  }
  const pieza = entero(elemento, 'pieza');
  if (pieza === null) {
    return null;
  }
  const estado: unknown = Reflect.get(elemento, 'estado');
  const sitios: unknown = Reflect.get(elemento, 'sitios');
  return {
    pieza,
    ausente: logico(elemento, 'ausente'),
    movilidad: entero(elemento, 'movilidad'),
    furcacion: entero(elemento, 'furcacion'),
    estado: esEstado(estado) ? estado : null,
    sitios: (Array.isArray(sitios) ? sitios : [])
      .map(leerSitio)
      .filter((s): s is SitioPeriodontal => s !== null)
      .sort((a, b) => SITIOS.indexOf(a.sitio) - SITIOS.indexOf(b.sitio)),
  };
}

export function leerVersion(cuerpo: unknown): VersionPeriodontograma | null {
  const resumen = leerResumen(cuerpo);
  if (resumen === null || !esObjeto(cuerpo)) {
    return null;
  }
  const piezas: unknown = Reflect.get(cuerpo, 'piezas');
  return {
    ...resumen,
    piezas: (Array.isArray(piezas) ? piezas : []).map(leerPieza).filter((p): p is PiezaPeriodontograma => p !== null),
  };
}

function base(pacienteId: number): string {
  return `/pacientes/${pacienteId}/periodontograma`;
}

function versionOError(cuerpo: unknown): VersionPeriodontograma {
  const version = leerVersion(cuerpo);
  if (version === null) {
    throw new ErrorGenerico(200);
  }
  return version;
}

export async function listarVersiones(api: ClienteApi, pacienteId: number): Promise<ResumenPeriodontograma[]> {
  return leerResumenes(await api.obtener<unknown>(base(pacienteId)));
}

export async function obtenerVersion(api: ClienteApi, pacienteId: number, versionId: number): Promise<VersionPeriodontograma> {
  return versionOError(await api.obtener<unknown>(`${base(pacienteId)}/${versionId}`));
}

/** Registra una versión nueva; con `201` la API devuelve la versión completa, con el estado de cada pieza. */
export async function registrarVersion(
  api: ClienteApi,
  pacienteId: number,
  pedido: PedidoPeriodontograma,
): Promise<VersionPeriodontograma> {
  return versionOError(await api.crear<unknown>(base(pacienteId), pedido));
}
