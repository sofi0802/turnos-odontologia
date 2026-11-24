import type { ClienteApi } from '../shared/api/cliente.ts';
import { CATEGORIAS, type Categoria } from './imagenes-modelo.ts';

/**
 * Pedidos de las imágenes clínicas (endpoints de C-26) y lectura defensiva de sus respuestas (C-47, design D2).
 * Nada de este módulo escribe en la consola ni en el almacenamiento del navegador (Ley 26.529).
 */

export interface AnulacionImagen {
  readonly profesional: string;
  /** Instante ISO con zona; vacío si la API no lo mandó bien. */
  readonly anuladaEn: string;
  readonly motivo: string;
}

export interface ImagenClinica {
  readonly id: number;
  readonly categoria: Categoria;
  readonly pieza: number | null;
  /** Fecha de calendario `AAAA-MM-DD`. */
  readonly tomadaEl: string;
  readonly descripcion: string;
  readonly tipoContenido: string;
  readonly tamanoBytes: number;
  readonly profesional: string;
  readonly anulacion: AnulacionImagen | null;
}

export interface FiltroImagenes {
  readonly pieza?: number;
  readonly incluirAnuladas?: boolean;
}

export interface CargaImagen {
  readonly archivo: File;
  readonly categoria: Categoria;
  readonly tomadaEl: string;
  readonly pieza: number | null;
  readonly descripcion: string;
}

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

function esCategoria(valor: unknown): valor is Categoria {
  return CATEGORIAS.some((c) => c.valor === valor);
}

function leerAnulacion(valor: unknown): AnulacionImagen | null {
  if (!esObjeto(valor)) {
    return null;
  }
  return {
    profesional: texto(valor, 'profesional') ?? '',
    anuladaEn: texto(valor, 'anuladaEn') ?? '',
    motivo: texto(valor, 'motivo') ?? '',
  };
}

/** Lee una imagen; `null` si le falta el id, la categoría, la fecha de la toma, el tipo o el tamaño. */
export function leerImagen(elemento: unknown): ImagenClinica | null {
  if (!esObjeto(elemento)) {
    return null;
  }
  const id = entero(elemento, 'id');
  const categoria: unknown = Reflect.get(elemento, 'categoria');
  const tomadaEl = texto(elemento, 'tomadaEl');
  const tipoContenido = texto(elemento, 'tipoContenido');
  const tamanoBytes = entero(elemento, 'tamanoBytes');
  if (
    id === null ||
    !esCategoria(categoria) ||
    tomadaEl === null ||
    !/^\d{4}-\d{2}-\d{2}$/.test(tomadaEl) ||
    tipoContenido === null ||
    tamanoBytes === null
  ) {
    return null;
  }
  return {
    id,
    categoria,
    pieza: entero(elemento, 'pieza'),
    tomadaEl,
    descripcion: texto(elemento, 'descripcion') ?? '',
    tipoContenido,
    tamanoBytes,
    profesional: texto(elemento, 'profesional') ?? '',
    anulacion: leerAnulacion(Reflect.get(elemento, 'anulacion')),
  };
}

/** Lee el listado en el orden de la API (de la toma más nueva a la más vieja); descarta lo que no sirve. */
export function leerImagenes(cuerpo: unknown): ImagenClinica[] {
  return (Array.isArray(cuerpo) ? cuerpo : []).map(leerImagen).filter((i): i is ImagenClinica => i !== null);
}

function base(pacienteId: number): string {
  return `/pacientes/${pacienteId}/imagenes`;
}

export async function listarImagenes(api: ClienteApi, pacienteId: number, filtro: FiltroImagenes): Promise<ImagenClinica[]> {
  const parametros = new URLSearchParams();
  if (filtro.pieza !== undefined) {
    parametros.set('pieza', String(filtro.pieza));
  }
  if (filtro.incluirAnuladas === true) {
    parametros.set('incluirAnuladas', 'true');
  }
  const consulta = parametros.toString();
  return leerImagenes(await api.obtener<unknown>(consulta === '' ? base(pacienteId) : `${base(pacienteId)}?${consulta}`));
}

/** El archivo de una imagen; con `descarga` la API lo entrega como adjunto (`imagen-<id>.<ext>`). */
export function descargarArchivo(api: ClienteApi, pacienteId: number, imagenId: number, descarga: boolean): Promise<Blob> {
  return api.obtenerArchivo(`${base(pacienteId)}/${imagenId}/archivo${descarga ? '?descarga=true' : ''}`);
}

/** Carga una imagen; con `201` la carga ya se hizo, así que una respuesta ilegible da `null` y no un error. */
export async function cargarImagen(api: ClienteApi, pacienteId: number, carga: CargaImagen): Promise<ImagenClinica | null> {
  const datos = new FormData();
  datos.append('archivo', carga.archivo);
  datos.append('categoria', carga.categoria);
  datos.append('tomadaEl', carga.tomadaEl);
  if (carga.pieza !== null) {
    datos.append('pieza', String(carga.pieza));
  }
  if (carga.descripcion.trim() !== '') {
    datos.append('descripcion', carga.descripcion.trim());
  }
  return leerImagen(await api.enviarFormulario<unknown>(base(pacienteId), datos));
}

/** Anula una imagen con su motivo; igual que la carga, una respuesta ilegible da `null`. */
export async function anularImagen(
  api: ClienteApi,
  pacienteId: number,
  imagenId: number,
  motivo: string,
): Promise<ImagenClinica | null> {
  return leerImagen(await api.crear<unknown>(`${base(pacienteId)}/${imagenId}/anulacion`, { motivo: motivo.trim() }));
}
