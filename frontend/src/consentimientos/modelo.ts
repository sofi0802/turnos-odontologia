import { fechaNumerica } from '../ficha/formato.ts';
import { fechaDeInstante, formatearHora } from '../shared/fecha/zona.ts';

/**
 * Modelo de los consentimientos informados (C-48, design D3): tipos de la API de C-27 y funciones puras, sin DOM.
 * Las validaciones se aplican sobre el valor recortado y normalizado a NFC, igual que el backend.
 */

export const LIMITE_TITULO = 120;
export const LIMITE_TEXTO = 20_000;
export const MINIMO_NOMBRE = 3;
export const LIMITE_NOMBRE = 200;
export const LIMITE_MOTIVO = 200;

export const MENSAJE_DNI_INVALIDO = 'Escribí el DNI sin letras: 7 u 8 números.';
export const MENSAJE_NOMBRE_CORTO = 'Escribí tu nombre completo.';
export const MENSAJE_SIN_ACEPTAR = 'Para firmar, marcá que leíste y aceptás el consentimiento.';

export type EstadoConsentimiento = 'VIGENTE' | 'REVOCADO';

export interface PlantillaResumen {
  readonly id: number;
  readonly activa: boolean;
  readonly versionActual: number;
  readonly titulo: string;
  readonly actualizadaEn: string;
}

export interface VersionPlantilla {
  readonly numero: number;
  readonly titulo: string;
  readonly texto: string;
  readonly creadaEn: string;
  readonly creadaPor: string;
}

export interface PlantillaVista {
  readonly id: number;
  readonly activa: boolean;
  readonly version: VersionPlantilla;
}

export interface ConsentimientoResumen {
  readonly id: number;
  readonly plantillaId: number;
  readonly versionNumero: number;
  readonly titulo: string;
  readonly firmadoEn: string;
  readonly profesionalId: number;
  readonly profesional: string;
  readonly estado: EstadoConsentimiento;
}

export interface Revocacion {
  readonly profesional: string;
  readonly revocadoEn: string;
  readonly motivo: string;
}

export interface ConsentimientoVista extends ConsentimientoResumen {
  readonly texto: string;
  readonly firmante: { readonly nombre: string; readonly dni: string };
  readonly evidencia: { readonly ip: string; readonly dispositivo: string };
  readonly tipoFirma: string;
  readonly hash: { readonly algoritmo: string; readonly formato: number | null; readonly valor: string };
  readonly revocacion: Revocacion | null;
}

export interface Verificacion {
  readonly integro: boolean;
  readonly algoritmo: string;
  readonly hashGuardado: string;
  readonly hashCalculado: string;
  readonly verificadoEn: string;
}

/** Recorta y normaliza a NFC, igual que el backend (C-27, D3). */
export function normalizar(valor: string): string {
  return valor.trim().normalize('NFC');
}

/** Número con separador de miles de `es-AR`, sin depender de los datos regionales del navegador. */
function conMiles(numero: number): string {
  return String(numero).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/** "1.234/20.000": lo escrito sobre el límite. */
export function formatoContador(cantidad: number, limite: number): string {
  return `${conMiles(cantidad)}/${conMiles(limite)}`;
}

export function validarTitulo(valor: string): string {
  const titulo = normalizar(valor);
  if (titulo === '') {
    return 'Escribí el título.';
  }
  return titulo.length > LIMITE_TITULO ? `El título supera los ${conMiles(LIMITE_TITULO)} caracteres.` : '';
}

export function validarTexto(valor: string): string {
  const texto = normalizar(valor);
  if (texto === '') {
    return 'Escribí el texto del consentimiento.';
  }
  return texto.length > LIMITE_TEXTO ? `El texto supera los ${conMiles(LIMITE_TEXTO)} caracteres.` : '';
}

export function validarNombre(valor: string): string {
  const nombre = normalizar(valor);
  if (nombre.length < MINIMO_NOMBRE) {
    return MENSAJE_NOMBRE_CORTO;
  }
  return nombre.length > LIMITE_NOMBRE ? `El nombre supera los ${LIMITE_NOMBRE} caracteres.` : '';
}

/** Un DNI sin puntos ni espacios tiene 7 u 8 dígitos. */
export function dniValido(valor: string): boolean {
  return /^\d{7,8}$/.test(valor.replace(/[.\s]/g, ''));
}

export function validarDni(valor: string): string {
  return dniValido(valor) ? '' : MENSAJE_DNI_INVALIDO;
}

export function validarMotivo(valor: string): string {
  const motivo = normalizar(valor);
  if (motivo === '') {
    return 'Escribí el motivo de la revocación.';
  }
  return motivo.length > LIMITE_MOTIVO ? `El motivo supera los ${LIMITE_MOTIVO} caracteres.` : '';
}

/** "24/11/2026", en la zona del consultorio. */
export function fechaDeFirma(instante: string): string {
  return fechaNumerica(instante);
}

/** "10:30", en la zona del consultorio. */
export function horaDeFirma(instante: string): string {
  return formatearHora(instante);
}

/** "Ver Consentimiento para extracción dentaria del 24/11/2026". */
export function nombreAccesible(accion: string, titulo: string, firmadoEn: string): string {
  return `${accion} ${titulo} del ${fechaDeFirma(firmadoEn)}`;
}

/** Lo que se sabe de una firma antes de enviarla, para comprobarla si el envío queda dudoso (design D13). */
export interface IntentoDeFirma {
  readonly plantillaId: number;
  readonly versionNumero: number;
  readonly profesionalId: number;
  /** Ids del listado del paciente pedido al elegir la plantilla. */
  readonly antes: readonly number[];
}

/** Lo que la ficha muestra al reabrirse después del bloqueo (design D12 y D13). */
export type ResultadoPrevio =
  | { readonly tipo: 'firmada'; readonly titulo: string; readonly versionNumero: number; readonly firmadoEn: string }
  | { readonly tipo: 'sesion-vencida' }
  | { readonly tipo: 'comprobar'; readonly titulo: string; readonly intento: IntentoDeFirma };

/**
 * La firma que apareció después del intento: misma plantilla y versión, del profesional de la sesión y con un id que
 * no estaba "antes". No compara relojes del navegador y del servidor.
 */
export function firmaNueva(intento: IntentoDeFirma, despues: readonly ConsentimientoResumen[]): ConsentimientoResumen | null {
  return (
    despues.find(
      (c) =>
        c.plantillaId === intento.plantillaId &&
        c.versionNumero === intento.versionNumero &&
        c.profesionalId === intento.profesionalId &&
        !intento.antes.includes(c.id),
    ) ?? null
  );
}

/**
 * El consentimiento vigente de la misma plantilla y versión firmado hoy (fecha de Buenos Aires), si lo hay: avisa
 * antes de firmar dos veces (design D14). `hoy` es `AAAA-MM-DD`.
 */
export function firmadoHoy(
  lista: readonly ConsentimientoResumen[],
  plantillaId: number,
  versionNumero: number,
  hoy: string,
): ConsentimientoResumen | null {
  return (
    lista.find(
      (c) =>
        c.estado === 'VIGENTE' &&
        c.plantillaId === plantillaId &&
        c.versionNumero === versionNumero &&
        fechaDeInstante(c.firmadoEn) === hoy,
    ) ?? null
  );
}
