import type { ClienteApi } from '../shared/api/cliente.ts';

/** Prestación del catálogo público (`GET /public/prestaciones`). */
export interface PrestacionPublica {
  readonly id: number;
  readonly nombre: string;
  readonly duracionMinutos: number;
}

/** Profesional del catálogo público, sin matrícula ni otros datos. */
export interface ProfesionalPublico {
  readonly id: number;
  readonly nombre: string;
}

function lista(cuerpo: unknown, ruta: string): unknown[] {
  if (!Array.isArray(cuerpo)) {
    throw new Error(`Respuesta inesperada de ${ruta}`);
  }
  return cuerpo;
}

function leerPrestacion(elemento: unknown): PrestacionPublica | null {
  if (typeof elemento !== 'object' || elemento === null) {
    return null;
  }
  const id: unknown = Reflect.get(elemento, 'id');
  const nombre: unknown = Reflect.get(elemento, 'nombre');
  const duracionMinutos: unknown = Reflect.get(elemento, 'duracionMinutos');
  if (typeof id !== 'number' || typeof nombre !== 'string' || typeof duracionMinutos !== 'number') {
    return null;
  }
  return { id, nombre, duracionMinutos };
}

function leerProfesional(elemento: unknown): ProfesionalPublico | null {
  if (typeof elemento !== 'object' || elemento === null) {
    return null;
  }
  const id: unknown = Reflect.get(elemento, 'id');
  const nombre: unknown = Reflect.get(elemento, 'nombre');
  return typeof id === 'number' && typeof nombre === 'string' ? { id, nombre } : null;
}

export async function cargarPrestaciones(api: ClienteApi): Promise<PrestacionPublica[]> {
  const ruta = '/public/prestaciones';
  return lista(await api.obtener<unknown>(ruta), ruta)
    .map(leerPrestacion)
    .filter((p): p is PrestacionPublica => p !== null);
}

export async function cargarProfesionales(api: ClienteApi, prestacionId: number): Promise<ProfesionalPublico[]> {
  const ruta = `/public/profesionales?prestacionId=${prestacionId}`;
  return lista(await api.obtener<unknown>(ruta), ruta)
    .map(leerProfesional)
    .filter((p): p is ProfesionalPublico => p !== null);
}
