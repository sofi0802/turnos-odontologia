import type { ClienteApi } from '../shared/api/cliente.ts';

/** Elemento de un catálogo de `catalogo-consultorio` (C-04), con el nombre para mostrar. */
export interface ItemCatalogo {
  readonly id: number;
  readonly nombre: string;
  readonly activo: boolean;
  /** Solo en profesionales: su sillón habitual (C-40 lo muestra y lo envía al crear un turno). */
  readonly sillonHabitualId: number | null;
}

export interface Catalogos {
  readonly profesionales: readonly ItemCatalogo[];
  readonly sillones: readonly ItemCatalogo[];
  readonly prestaciones: readonly ItemCatalogo[];
  /** Avisos por cada catálogo que no se pudo cargar. */
  readonly avisos: readonly string[];
}

export const CATALOGOS_VACIOS: Catalogos = { profesionales: [], sillones: [], prestaciones: [], avisos: [] };

type TipoCatalogo = 'profesionales' | 'sillones' | 'prestaciones';

const RESPALDO: Readonly<Record<TipoCatalogo, string>> = {
  profesionales: 'Profesional',
  sillones: 'Sillón',
  prestaciones: 'Prestación',
};

function leerItem(tipo: TipoCatalogo, elemento: unknown): ItemCatalogo | null {
  if (typeof elemento !== 'object' || elemento === null) {
    return null;
  }
  const id: unknown = Reflect.get(elemento, 'id');
  const nombre: unknown = Reflect.get(elemento, 'nombre');
  const apellido: unknown = Reflect.get(elemento, 'apellido');
  if (typeof id !== 'number' || typeof nombre !== 'string') {
    return null;
  }
  const visible = tipo === 'profesionales' && typeof apellido === 'string' ? `${nombre} ${apellido}` : nombre;
  const sillon: unknown = tipo === 'profesionales' ? Reflect.get(elemento, 'sillonHabitualId') : null;
  return {
    id,
    nombre: visible,
    activo: Reflect.get(elemento, 'activo') !== false,
    sillonHabitualId: typeof sillon === 'number' ? sillon : null,
  };
}

async function cargar(api: ClienteApi, tipo: TipoCatalogo): Promise<ItemCatalogo[]> {
  const cuerpo = await api.obtener<unknown>(`/${tipo}`);
  if (!Array.isArray(cuerpo)) {
    throw new Error(`Respuesta inesperada de /${tipo}`);
  }
  return cuerpo.map((e) => leerItem(tipo, e)).filter((i): i is ItemCatalogo => i !== null);
}

/** Carga los tres catálogos en paralelo; uno que falla queda vacío con su aviso (design D4). */
export async function cargarCatalogos(api: ClienteApi): Promise<Catalogos> {
  const tipos: readonly TipoCatalogo[] = ['profesionales', 'sillones', 'prestaciones'];
  const resultados = await Promise.allSettled(tipos.map((tipo) => cargar(api, tipo)));
  const listas: Record<TipoCatalogo, ItemCatalogo[]> = { profesionales: [], sillones: [], prestaciones: [] };
  const avisos: string[] = [];
  resultados.forEach((resultado, indice) => {
    const tipo = tipos[indice];
    if (tipo === undefined) {
      return;
    }
    if (resultado.status === 'fulfilled') {
      listas[tipo] = resultado.value;
    } else {
      avisos.push(`No pudimos cargar la lista de ${tipo}.`);
    }
  });
  return { ...listas, avisos };
}

/** Nombre para mostrar; si no está en el catálogo, "Profesional 3", "Sillón 2"… */
export function nombreDe(catalogos: Catalogos, tipo: TipoCatalogo, id: number): string {
  return catalogos[tipo].find((item) => item.id === id)?.nombre ?? `${RESPALDO[tipo]} ${id}`;
}
