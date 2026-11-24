/**
 * Reglas de las imágenes clínicas del lado del navegador (C-47, design D2 y D6): validación del archivo, la pieza,
 * la fecha y los textos; nombres accesibles y formato del peso. Son funciones puras, sin DOM: las prueban tests
 * unitarios. Repiten límites fijos del backend (C-26); sus errores se siguen mostrando.
 */

export type Categoria = 'RADIOGRAFIA' | 'FOTOGRAFIA' | 'ESTUDIO';

export const CATEGORIAS: readonly { readonly valor: Categoria; readonly texto: string }[] = [
  { valor: 'RADIOGRAFIA', texto: 'Radiografía' },
  { valor: 'FOTOGRAFIA', texto: 'Fotografía' },
  { valor: 'ESTUDIO', texto: 'Estudio' },
];

const NOMBRE_CATEGORIA: Readonly<Record<Categoria, string>> = {
  RADIOGRAFIA: 'Radiografía',
  FOTOGRAFIA: 'Fotografía',
  ESTUDIO: 'Estudio',
};

export function nombreDeCategoria(categoria: Categoria): string {
  return NOMBRE_CATEGORIA[categoria];
}

/** 10 MB, el límite de C-26: un archivo de exactamente 10 485 760 bytes se acepta. */
export const TAMANO_MAXIMO = 10_485_760;
export const LIMITE_DESCRIPCION = 200;
export const LIMITE_MOTIVO = 200;

export const MENSAJE_TIPO_NO_PERMITIDO = 'Solo se aceptan archivos JPEG, PNG o PDF.';
export const MENSAJE_ARCHIVO_GRANDE = 'El archivo supera los 10 MB.';
export const MENSAJE_PIEZA_INVALIDA = 'La pieza no existe en la notación FDI.';

export type TipoArchivo = 'JPEG' | 'PNG' | 'PDF';

const POR_MIME: Readonly<Record<string, TipoArchivo>> = {
  'image/jpeg': 'JPEG',
  'image/png': 'PNG',
  'application/pdf': 'PDF',
};

const POR_EXTENSION: Readonly<Record<string, TipoArchivo>> = {
  jpg: 'JPEG',
  jpeg: 'JPEG',
  png: 'PNG',
  pdf: 'PDF',
};

/** Tipo por el MIME del archivo; si el sistema no lo informa, por la extensión. `null` si no está permitido. */
export function tipoDeArchivo(archivo: File): TipoArchivo | null {
  if (archivo.type !== '') {
    return POR_MIME[archivo.type.toLowerCase()] ?? null;
  }
  const punto = archivo.name.lastIndexOf('.');
  return punto < 0 ? null : (POR_EXTENSION[archivo.name.slice(punto + 1).toLowerCase()] ?? null);
}

/** Mensaje del campo del archivo, o `null` si se puede enviar. */
export function validarArchivo(archivo: File | null): string | null {
  if (archivo === null) {
    return 'Elegí un archivo.';
  }
  if (tipoDeArchivo(archivo) === null) {
    return MENSAJE_TIPO_NO_PERMITIDO;
  }
  if (archivo.size > TAMANO_MAXIMO) {
    return MENSAJE_ARCHIVO_GRANDE;
  }
  if (archivo.size === 0) {
    return 'El archivo está vacío.';
  }
  return null;
}

/** Misma regla que `PiezaDental`: permanentes 11 a 48 y temporales 51 a 85. */
export function esPiezaFdi(pieza: number): boolean {
  if (!Number.isInteger(pieza)) {
    return false;
  }
  const cuadrante = Math.floor(pieza / 10);
  const diente = pieza % 10;
  if (cuadrante >= 1 && cuadrante <= 4) {
    return diente >= 1 && diente <= 8;
  }
  if (cuadrante >= 5 && cuadrante <= 8) {
    return diente >= 1 && diente <= 5;
  }
  return false;
}

/** La pieza es opcional: vacío es válido. Solo se aceptan dos cifras, sin ceros a la izquierda. */
export function validarPieza(texto: string): string | null {
  const limpio = texto.trim();
  if (limpio === '') {
    return null;
  }
  return /^[1-8][1-8]$/.test(limpio) && esPiezaFdi(Number(limpio)) ? null : MENSAJE_PIEZA_INVALIDA;
}

/** Fechas `AAAA-MM-DD`, que se comparan como texto; `hoy` es el día de la zona del consultorio. */
export function validarFechaDeToma(fecha: string, hoy: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha)) {
    return 'Elegí la fecha de la toma.';
  }
  return fecha > hoy ? 'La fecha de la toma no puede ser futura.' : null;
}

/** "30/11/2026" a partir de `2026-11-30`. */
export function fechaDeToma(fecha: string): string {
  const [anio, mes, dia] = fecha.split('-');
  return `${dia ?? ''}/${mes ?? ''}/${anio ?? ''}`;
}

export function validarDescripcion(texto: string): string | null {
  return texto.length > LIMITE_DESCRIPCION ? `La descripción puede tener hasta ${LIMITE_DESCRIPCION} caracteres.` : null;
}

export function validarMotivo(texto: string): string | null {
  if (texto.trim() === '') {
    return 'Escribí el motivo de la anulación.';
  }
  return texto.trim().length > LIMITE_MOTIVO ? `El motivo puede tener hasta ${LIMITE_MOTIVO} caracteres.` : null;
}

export interface DatosDeNombre {
  readonly categoria: Categoria;
  readonly pieza: number | null;
  readonly tomadaEl: string;
}

function descripcionCorta(imagen: DatosDeNombre, anulada: boolean): string {
  const categoria = nombreDeCategoria(imagen.categoria).toLowerCase();
  const pieza = imagen.pieza === null ? '' : ` de la pieza ${imagen.pieza}`;
  return `${categoria}${anulada ? ' anulada' : ''}${pieza} del ${fechaDeToma(imagen.tomadaEl)}`;
}

/** "Radiografía de la pieza 36 del 30/11/2026": el texto alternativo del visor y el nombre del archivo en pantalla. */
export function nombreAccesible(imagen: DatosDeNombre): string {
  const texto = descripcionCorta(imagen, false);
  return `${texto.charAt(0).toUpperCase()}${texto.slice(1)}`;
}

/** "Ver radiografía de la pieza 36 del 30/11/2026": el nombre accesible de cada botón de la fila. */
export function nombreDeBoton(accion: string, imagen: DatosDeNombre, anulada: boolean): string {
  return `${accion} ${descripcionCorta(imagen, anulada)}`;
}

/** "312 KB", "1,2 MB". */
export function formatearPeso(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const kb = bytes / 1024;
  if (Math.round(kb) < 1024) {
    return `${Math.round(kb)} KB`;
  }
  const mb = Math.round((bytes / 1_048_576) * 10) / 10;
  return `${String(mb).replace('.', ',')} MB`;
}

const FORMATO_POR_TIPO: Readonly<Record<string, TipoArchivo>> = POR_MIME;

/** "JPEG", "PNG" o "PDF" según el tipo de contenido que guarda la API; vacío si es otro. */
export function etiquetaDeFormato(tipoContenido: string): string {
  return FORMATO_POR_TIPO[tipoContenido] ?? '';
}

/** Solo JPEG y PNG se muestran en el visor; un PDF solo se descarga (design D5). */
export function puedeVerse(tipoContenido: string): boolean {
  return tipoContenido === 'image/jpeg' || tipoContenido === 'image/png';
}

const EXTENSION_POR_TIPO: Readonly<Record<string, string>> = { 'image/jpeg': 'jpg', 'image/png': 'png', 'application/pdf': 'pdf' };

/** `imagen-<id>.<ext>`: el nombre genérico de la API, sin el nombre del paciente ni la pieza (design D5). */
export function nombreDeArchivo(imagenId: number, tipoContenido: string): string {
  const extension = EXTENSION_POR_TIPO[tipoContenido];
  return extension === undefined ? `imagen-${imagenId}` : `imagen-${imagenId}.${extension}`;
}
