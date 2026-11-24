import type { ClienteApi } from '../shared/api/cliente.ts';

/**
 * Pedidos de la ficha clínica (endpoints de C-13, `clinica/`) y lectura defensiva de sus respuestas. Nada de este
 * módulo escribe en la consola ni en el almacenamiento del navegador (Ley 26.529, design D9).
 */

export const ESTADOS_PIEZA = ['SANO', 'CARIES', 'OBTURADO', 'A_TRATAR', 'AUSENTE', 'CORONA', 'ENDODONCIA'] as const;
export type EstadoPieza = (typeof ESTADOS_PIEZA)[number];

export const CARAS = ['O', 'M', 'D', 'V', 'L', 'P'] as const;
export type Cara = (typeof CARAS)[number];

export const MAXIMO_TEXTO = 4000;
export const MAXIMO_CAMBIOS = 32;

export interface AusenciaFicha {
  readonly turnoId: number;
  readonly inicio: string;
  readonly profesional: string;
  readonly deshechaEn: string | null;
  readonly deshechaPor: string | null;
}

export interface DatosFicha {
  readonly antecedentes: string;
  readonly alergias: string;
  readonly medicacion: string;
}

export interface Ficha extends DatosFicha {
  readonly actualizadoPor: string | null;
  readonly actualizadoEn: string | null;
  readonly ausencias: readonly AusenciaFicha[];
}

export interface RegistroOdontograma {
  readonly id: number;
  readonly pieza: number;
  /** `null` es la pieza completa. */
  readonly cara: Cara | null;
  readonly estado: EstadoPieza;
  readonly profesional: string;
  readonly registradoEn: string;
}

export interface CambioOdontograma {
  readonly pieza: number;
  readonly cara: Cara | null;
  readonly estado: EstadoPieza;
}

function texto(objeto: object, clave: string): string | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'string' ? valor : null;
}

function entero(objeto: object, clave: string): number | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'number' && Number.isInteger(valor) ? valor : null;
}

function esEstado(valor: unknown): valor is EstadoPieza {
  return typeof valor === 'string' && (ESTADOS_PIEZA as readonly string[]).includes(valor);
}

function esCara(valor: unknown): valor is Cara {
  return typeof valor === 'string' && (CARAS as readonly string[]).includes(valor);
}

function leerAusencia(elemento: unknown): AusenciaFicha | null {
  if (typeof elemento !== 'object' || elemento === null) {
    return null;
  }
  const turnoId = entero(elemento, 'turnoId');
  const inicio = texto(elemento, 'inicio');
  if (turnoId === null || inicio === null) {
    return null;
  }
  return {
    turnoId,
    inicio,
    profesional: texto(elemento, 'profesional') ?? '',
    deshechaEn: texto(elemento, 'deshechaEn'),
    deshechaPor: texto(elemento, 'deshechaPor'),
  };
}

export function leerFicha(cuerpo: unknown): Ficha {
  const objeto = typeof cuerpo === 'object' && cuerpo !== null ? cuerpo : {};
  const ausencias: unknown = Reflect.get(objeto, 'ausencias');
  return {
    antecedentes: texto(objeto, 'antecedentes') ?? '',
    alergias: texto(objeto, 'alergias') ?? '',
    medicacion: texto(objeto, 'medicacion') ?? '',
    actualizadoPor: texto(objeto, 'actualizadoPor'),
    actualizadoEn: texto(objeto, 'actualizadoEn'),
    ausencias: (Array.isArray(ausencias) ? ausencias : [])
      .map(leerAusencia)
      .filter((a): a is AusenciaFicha => a !== null)
      .sort((a, b) => Date.parse(b.inicio) - Date.parse(a.inicio)),
  };
}

function leerRegistro(elemento: unknown): RegistroOdontograma | null {
  if (typeof elemento !== 'object' || elemento === null) {
    return null;
  }
  const id = entero(elemento, 'id');
  const pieza = entero(elemento, 'pieza');
  const estado: unknown = Reflect.get(elemento, 'estado');
  const cara: unknown = Reflect.get(elemento, 'cara');
  const registradoEn = texto(elemento, 'registradoEn');
  if (id === null || pieza === null || !esEstado(estado) || registradoEn === null || !(cara === null || esCara(cara))) {
    return null;
  }
  return { id, pieza, cara, estado, profesional: texto(elemento, 'profesional') ?? '', registradoEn };
}

export function leerRegistros(cuerpo: unknown): RegistroOdontograma[] {
  return (Array.isArray(cuerpo) ? cuerpo : []).map(leerRegistro).filter((r): r is RegistroOdontograma => r !== null);
}

function base(pacienteId: number): string {
  return `/pacientes/${pacienteId}`;
}

export async function obtenerFicha(api: ClienteApi, pacienteId: number): Promise<Ficha> {
  return leerFicha(await api.obtener<unknown>(`${base(pacienteId)}/ficha`));
}

export async function guardarFicha(api: ClienteApi, pacienteId: number, datos: DatosFicha): Promise<Ficha> {
  return leerFicha(await api.actualizar<unknown>(`${base(pacienteId)}/ficha`, datos));
}

export async function obtenerOdontograma(api: ClienteApi, pacienteId: number): Promise<RegistroOdontograma[]> {
  return leerRegistros(await api.obtener<unknown>(`${base(pacienteId)}/odontograma`));
}

/** Historial completo, del registro más nuevo al más viejo. */
export async function obtenerHistorial(api: ClienteApi, pacienteId: number): Promise<RegistroOdontograma[]> {
  const registros = leerRegistros(await api.obtener<unknown>(`${base(pacienteId)}/odontograma/historial`));
  return registros.sort((a, b) => Date.parse(b.registradoEn) - Date.parse(a.registradoEn) || b.id - a.id);
}

/** Registra los cambios en un solo pedido; la API responde el estado vigente. */
export async function registrarCambios(
  api: ClienteApi,
  pacienteId: number,
  cambios: readonly CambioOdontograma[],
): Promise<RegistroOdontograma[]> {
  return leerRegistros(await api.actualizar<unknown>(`${base(pacienteId)}/odontograma`, { cambios }));
}

export async function marcarAusente(api: ClienteApi, pacienteId: number, turnoId: number): Promise<void> {
  await api.crear<unknown>(`${base(pacienteId)}/ausencias`, { turnoId });
}
