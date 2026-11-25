import type { ClienteApi } from '../shared/api/cliente.ts';

/**
 * Consentimiento de comunicaciones (C-17). El paciente usa su propio recurso, que lo identifica por la sesión; la
 * recepción y el administrador usan el del paciente elegido. Las respuestas no traen datos de contacto.
 */

export type MedioConsentimiento = 'TELEFONO' | 'PRESENCIAL';

export interface EstadoConsentimiento {
  readonly otorgado: boolean;
  /** Instante ISO del último cambio; `null` si nunca cambió. */
  readonly actualizadoEn: string | null;
}

function esObjeto(valor: unknown): valor is object {
  return typeof valor === 'object' && valor !== null;
}

export function leerConsentimiento(cuerpo: unknown): EstadoConsentimiento {
  const otorgado = esObjeto(cuerpo) && Reflect.get(cuerpo, 'otorgado') === true;
  const fecha = esObjeto(cuerpo) ? Reflect.get(cuerpo, 'actualizadoEn') : null;
  return { otorgado, actualizadoEn: typeof fecha === 'string' ? fecha : null };
}

const PROPIO = '/paciente/consentimiento-comunicaciones';

function delPaciente(pacienteId: number): string {
  return `/pacientes/${pacienteId}/consentimiento-comunicaciones`;
}

export async function obtenerMiConsentimiento(api: ClienteApi): Promise<EstadoConsentimiento> {
  return leerConsentimiento(await api.obtener<unknown>(PROPIO));
}

export async function cambiarMiConsentimiento(api: ClienteApi, otorgado: boolean): Promise<EstadoConsentimiento> {
  return leerConsentimiento(await api.actualizar<unknown>(PROPIO, { otorgado }));
}

export async function obtenerConsentimiento(api: ClienteApi, pacienteId: number): Promise<EstadoConsentimiento> {
  return leerConsentimiento(await api.obtener<unknown>(delPaciente(pacienteId)));
}

export async function registrarConsentimiento(
  api: ClienteApi,
  pacienteId: number,
  otorgado: boolean,
  medio: MedioConsentimiento,
): Promise<EstadoConsentimiento> {
  return leerConsentimiento(await api.actualizar<unknown>(delPaciente(pacienteId), { otorgado, medio }));
}

const FORMATO_FECHA = new Intl.DateTimeFormat('es-AR', {
  timeZone: 'America/Argentina/Buenos_Aires',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

/** "01/12/2026" en la hora del consultorio. */
export function fechaDelCambio(instante: string): string {
  return FORMATO_FECHA.format(new Date(instante));
}
