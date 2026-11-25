import type { ClienteApi } from '../shared/api/cliente.ts';
import { aCentavos, type Centavos } from '../presupuestos/modelo.ts';

/**
 * Pedidos de los aranceles y de la cobertura por plan (API de C-19, C-50 design D5). Los precios se leen y se escriben
 * en centavos; la API los recibe en pesos con dos decimales. Solo `ADMIN` escribe.
 */

export interface Arancel {
  readonly prestacionId: number;
  readonly nombre: string;
  readonly activa: boolean;
  /** `null`: sin precio, no se puede presupuestar. */
  readonly precio: Centavos | null;
}

export interface PorcentajeCargado {
  readonly nomencladorId: number;
  readonly porcentaje: number;
}

function esObjeto(valor: unknown): valor is object {
  return typeof valor === 'object' && valor !== null;
}

function entero(objeto: object, clave: string): number | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'number' && Number.isInteger(valor) ? valor : null;
}

export function leerAranceles(cuerpo: unknown): Arancel[] {
  const aranceles: Arancel[] = [];
  for (const elemento of Array.isArray(cuerpo) ? cuerpo : []) {
    if (!esObjeto(elemento)) {
      continue;
    }
    const prestacion: unknown = Reflect.get(elemento, 'prestacion');
    const prestacionId = esObjeto(prestacion) ? entero(prestacion, 'id') : null;
    const nombre: unknown = esObjeto(prestacion) ? Reflect.get(prestacion, 'nombre') : null;
    const activa: unknown = Reflect.get(elemento, 'activa');
    const precio: unknown = Reflect.get(elemento, 'precio');
    if (prestacionId === null || typeof nombre !== 'string' || typeof activa !== 'boolean') {
      continue;
    }
    aranceles.push({
      prestacionId,
      nombre,
      activa,
      precio: typeof precio === 'number' && Number.isFinite(precio) && precio > 0 ? aCentavos(precio) : null,
    });
  }
  return aranceles;
}

export function leerCoberturaPlan(cuerpo: unknown): PorcentajeCargado[] {
  const cargados: PorcentajeCargado[] = [];
  for (const elemento of Array.isArray(cuerpo) ? cuerpo : []) {
    if (!esObjeto(elemento)) {
      continue;
    }
    const nomencladorId = entero(elemento, 'nomencladorId');
    const porcentaje = entero(elemento, 'porcentaje');
    if (nomencladorId !== null && porcentaje !== null) {
      cargados.push({ nomencladorId, porcentaje });
    }
  }
  return cargados;
}

export async function listarAranceles(api: ClienteApi): Promise<Arancel[]> {
  return leerAranceles(await api.obtener<unknown>('/aranceles'));
}

export async function cambiarPrecio(api: ClienteApi, prestacionId: number, precio: Centavos): Promise<void> {
  await api.actualizar<unknown>(`/aranceles/${prestacionId}`, { precio: precio / 100 });
}

function coberturas(obraSocialId: number, planId: number): string {
  return `/obras-sociales/${obraSocialId}/planes/${planId}/coberturas`;
}

export async function listarCoberturaPlan(api: ClienteApi, obraSocialId: number, planId: number): Promise<PorcentajeCargado[]> {
  return leerCoberturaPlan(await api.obtener<unknown>(coberturas(obraSocialId, planId)));
}

export async function cambiarPorcentaje(
  api: ClienteApi,
  obraSocialId: number,
  planId: number,
  nomencladorId: number,
  porcentaje: number,
): Promise<void> {
  await api.actualizar<unknown>(`${coberturas(obraSocialId, planId)}/${nomencladorId}`, { porcentaje });
}
