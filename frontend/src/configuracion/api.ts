import type { ClienteApi } from '../shared/api/cliente.ts';

/** Registros completos de los catálogos de C-04 (design D1): a diferencia de `agenda/catalogos.ts`, no se resumen. */
export interface Profesional {
  readonly id: number;
  readonly nombre: string;
  readonly apellido: string;
  readonly matricula: string;
  readonly activo: boolean;
  readonly sillonHabitualId: number | null;
}

export interface Sillon {
  readonly id: number;
  readonly nombre: string;
  readonly activo: boolean;
}

export interface Prestacion {
  readonly id: number;
  readonly nombre: string;
  readonly duracionMinutos: number;
  readonly activo: boolean;
}

function campo(objeto: object, nombre: string): unknown {
  return Reflect.get(objeto, nombre);
}

function esObjeto(valor: unknown): valor is object {
  return typeof valor === 'object' && valor !== null;
}

export function leerProfesional(valor: unknown): Profesional | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const id = campo(valor, 'id');
  const nombre = campo(valor, 'nombre');
  const apellido = campo(valor, 'apellido');
  const matricula = campo(valor, 'matricula');
  const sillon = campo(valor, 'sillonHabitualId');
  if (typeof id !== 'number' || typeof nombre !== 'string' || typeof apellido !== 'string') {
    return null;
  }
  return {
    id,
    nombre,
    apellido,
    matricula: typeof matricula === 'string' ? matricula : '',
    activo: campo(valor, 'activo') !== false,
    sillonHabitualId: typeof sillon === 'number' ? sillon : null,
  };
}

export function leerSillon(valor: unknown): Sillon | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const id = campo(valor, 'id');
  const nombre = campo(valor, 'nombre');
  if (typeof id !== 'number' || typeof nombre !== 'string') {
    return null;
  }
  return { id, nombre, activo: campo(valor, 'activo') !== false };
}

export function leerPrestacion(valor: unknown): Prestacion | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const id = campo(valor, 'id');
  const nombre = campo(valor, 'nombre');
  const duracion = campo(valor, 'duracionMinutos');
  if (typeof id !== 'number' || typeof nombre !== 'string' || typeof duracion !== 'number') {
    return null;
  }
  return { id, nombre, duracionMinutos: duracion, activo: campo(valor, 'activo') !== false };
}

/** Lee una lista con `leer`, descartando los elementos con forma inesperada. */
export function leerLista<T>(cuerpo: unknown, leer: (valor: unknown) => T | null, ruta: string): T[] {
  if (!Array.isArray(cuerpo)) {
    throw new Error(`Respuesta inesperada de ${ruta}`);
  }
  return cuerpo.map(leer).filter((item): item is T => item !== null);
}

export async function cargarProfesionales(api: ClienteApi): Promise<Profesional[]> {
  return leerLista(await api.obtener<unknown>('/profesionales'), leerProfesional, '/profesionales');
}

export async function cargarSillones(api: ClienteApi): Promise<Sillon[]> {
  return leerLista(await api.obtener<unknown>('/sillones'), leerSillon, '/sillones');
}

export async function cargarPrestaciones(api: ClienteApi): Promise<Prestacion[]> {
  return leerLista(await api.obtener<unknown>('/prestaciones'), leerPrestacion, '/prestaciones');
}

/** Prestaciones habilitadas de un profesional (`GET /profesionales/{id}/prestaciones`). */
export async function cargarEspecialidades(api: ClienteApi, profesionalId: number): Promise<Prestacion[]> {
  const ruta = `/profesionales/${profesionalId}/prestaciones`;
  return leerLista(await api.obtener<unknown>(ruta), leerPrestacion, ruta);
}

export function nombreCompleto(profesional: Profesional): string {
  return `${profesional.nombre} ${profesional.apellido}`;
}
