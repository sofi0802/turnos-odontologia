import type { ClienteApi } from '../shared/api/cliente.ts';
import { ErrorGenerico } from '../shared/api/errores.ts';
import {
  normalizar,
  type ConsentimientoResumen,
  type ConsentimientoVista,
  type PlantillaResumen,
  type PlantillaVista,
  type Revocacion,
  type VersionPlantilla,
  type Verificacion,
} from './modelo.ts';

/**
 * Pedidos de las plantillas y los consentimientos (endpoints de C-27) y lectura defensiva de sus respuestas
 * (C-48, design D3). Descarta los valores de otro tipo. Nada de este módulo escribe en la consola ni en el
 * almacenamiento del navegador (Ley 25.326).
 */

function esObjeto(valor: unknown): valor is object {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor);
}

function texto(objeto: object, clave: string): string | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'string' ? valor : null;
}

function entero(objeto: object, clave: string): number | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'number' && Number.isInteger(valor) ? valor : null;
}

function booleano(objeto: object, clave: string): boolean | null {
  const valor: unknown = Reflect.get(objeto, clave);
  return typeof valor === 'boolean' ? valor : null;
}

/** Una respuesta que no se puede leer no es "vacía": es una falla, y la pantalla no muestra nada dudoso. */
function ilegible(): ErrorGenerico {
  return new ErrorGenerico(null);
}

// --- Plantillas -----------------------------------------------------------------------------------------------

function leerPlantillaResumen(valor: unknown): PlantillaResumen | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const id = entero(valor, 'id');
  const activa = booleano(valor, 'activa');
  const versionActual = entero(valor, 'versionActual');
  const titulo = texto(valor, 'titulo');
  if (id === null || activa === null || versionActual === null || titulo === null) {
    return null;
  }
  return { id, activa, versionActual, titulo, actualizadaEn: texto(valor, 'actualizadaEn') ?? '' };
}

/** Lee el listado en el orden de la API; descarta lo que no sirve. */
export function leerPlantillas(cuerpo: unknown): PlantillaResumen[] {
  return (Array.isArray(cuerpo) ? cuerpo : []).map(leerPlantillaResumen).filter((p): p is PlantillaResumen => p !== null);
}

function leerVersion(valor: unknown): VersionPlantilla | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const numero = entero(valor, 'numero');
  const titulo = texto(valor, 'titulo');
  const textoVersion = texto(valor, 'texto');
  if (numero === null || titulo === null || textoVersion === null) {
    return null;
  }
  return {
    numero,
    titulo,
    texto: textoVersion,
    creadaEn: texto(valor, 'creadaEn') ?? '',
    creadaPor: texto(valor, 'creadaPor') ?? '',
  };
}

function leerPlantillaVista(valor: unknown): PlantillaVista | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const id = entero(valor, 'id');
  const activa = booleano(valor, 'activa');
  const version = leerVersion(Reflect.get(valor, 'version'));
  return id === null || activa === null || version === null ? null : { id, activa, version };
}

const BASE_PLANTILLAS = '/consentimientos/plantillas';

/** Las plantillas; con `incluirInactivas` también las desactivadas (solo las ve el `ADMIN`). */
export async function listarPlantillas(api: ClienteApi, incluirInactivas: boolean): Promise<PlantillaResumen[]> {
  return leerPlantillas(await api.obtener<unknown>(incluirInactivas ? `${BASE_PLANTILLAS}?incluirInactivas=true` : BASE_PLANTILLAS));
}

/** Una plantilla con su última versión; si la respuesta no se puede leer, falla (no se firma un texto dudoso). */
export async function obtenerPlantilla(api: ClienteApi, plantillaId: number): Promise<PlantillaVista> {
  const plantilla = leerPlantillaVista(await api.obtener<unknown>(`${BASE_PLANTILLAS}/${plantillaId}`));
  if (plantilla === null) {
    throw ilegible();
  }
  return plantilla;
}

export interface DatosPlantilla {
  readonly titulo: string;
  readonly texto: string;
}

/** Crea una plantilla; con `201` ya se creó, así que una respuesta ilegible da `null` y no un error. */
export async function crearPlantilla(api: ClienteApi, datos: DatosPlantilla): Promise<PlantillaVista | null> {
  return leerPlantillaVista(
    await api.crear<unknown>(BASE_PLANTILLAS, { titulo: normalizar(datos.titulo), texto: normalizar(datos.texto) }),
  );
}

export interface EdicionPlantilla extends DatosPlantilla {
  /** Número de la versión que se abrió para editar. */
  readonly versionBase: number;
}

export interface ResultadoEdicion {
  /** Número de la versión con la que quedó la plantilla. */
  readonly numero: number;
  /** `true` si la API respondió `200` con la misma versión: no hubo cambios. */
  readonly sinCambios: boolean;
}

/**
 * Edita una plantilla: `201` crea la versión siguiente y `200` devuelve la misma si no hubo cambios. El cliente de la
 * API no entrega el estado, así que se distingue por el número: si no pasó de `versionBase`, no hubo cambios.
 */
export async function editarPlantilla(api: ClienteApi, plantillaId: number, edicion: EdicionPlantilla): Promise<ResultadoEdicion | null> {
  const plantilla = leerPlantillaVista(
    await api.crear<unknown>(`${BASE_PLANTILLAS}/${plantillaId}/versiones`, {
      versionBase: edicion.versionBase,
      titulo: normalizar(edicion.titulo),
      texto: normalizar(edicion.texto),
    }),
  );
  if (plantilla === null) {
    return null;
  }
  return { numero: plantilla.version.numero, sinCambios: plantilla.version.numero <= edicion.versionBase };
}

/** Todas las versiones, de la más nueva a la más vieja. */
export async function listarVersiones(api: ClienteApi, plantillaId: number): Promise<VersionPlantilla[]> {
  const cuerpo = await api.obtener<unknown>(`${BASE_PLANTILLAS}/${plantillaId}/versiones`);
  return (Array.isArray(cuerpo) ? cuerpo : []).map(leerVersion).filter((v): v is VersionPlantilla => v !== null);
}

export async function cambiarEstadoPlantilla(api: ClienteApi, plantillaId: number, activa: boolean): Promise<PlantillaVista | null> {
  return leerPlantillaVista(await api.actualizar<unknown>(`${BASE_PLANTILLAS}/${plantillaId}/estado`, { activa }));
}

// --- Consentimientos del paciente -----------------------------------------------------------------------------

function leerResumen(valor: unknown): ConsentimientoResumen | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const id = entero(valor, 'id');
  const plantillaId = entero(valor, 'plantillaId');
  const versionNumero = entero(valor, 'versionNumero');
  const titulo = texto(valor, 'titulo');
  const firmadoEn = texto(valor, 'firmadoEn');
  const profesionalId = entero(valor, 'profesionalId');
  const estado: unknown = Reflect.get(valor, 'estado');
  if (
    id === null ||
    plantillaId === null ||
    versionNumero === null ||
    titulo === null ||
    firmadoEn === null ||
    profesionalId === null ||
    (estado !== 'VIGENTE' && estado !== 'REVOCADO')
  ) {
    return null;
  }
  return { id, plantillaId, versionNumero, titulo, firmadoEn, profesionalId, profesional: texto(valor, 'profesional') ?? '', estado };
}

/** Lee el listado en el orden de la API (del más nuevo al más viejo); descarta lo que no sirve. */
export function leerConsentimientos(cuerpo: unknown): ConsentimientoResumen[] {
  return (Array.isArray(cuerpo) ? cuerpo : []).map(leerResumen).filter((c): c is ConsentimientoResumen => c !== null);
}

function leerRevocacion(valor: unknown): Revocacion | null {
  if (!esObjeto(valor)) {
    return null;
  }
  return {
    profesional: texto(valor, 'profesional') ?? '',
    revocadoEn: texto(valor, 'revocadoEn') ?? '',
    motivo: texto(valor, 'motivo') ?? '',
  };
}

function leerVista(valor: unknown): ConsentimientoVista | null {
  const resumen = leerResumen(valor);
  if (resumen === null || !esObjeto(valor)) {
    return null;
  }
  const textoFirmado = texto(valor, 'texto');
  const firmante: unknown = Reflect.get(valor, 'firmante');
  if (textoFirmado === null || !esObjeto(firmante)) {
    return null;
  }
  const evidencia: unknown = Reflect.get(valor, 'evidencia');
  const hash: unknown = Reflect.get(valor, 'hash');
  return {
    ...resumen,
    texto: textoFirmado,
    firmante: { nombre: texto(firmante, 'nombre') ?? '', dni: texto(firmante, 'dni') ?? '' },
    evidencia: esObjeto(evidencia)
      ? { ip: texto(evidencia, 'ip') ?? '', dispositivo: texto(evidencia, 'dispositivo') ?? '' }
      : { ip: '', dispositivo: '' },
    tipoFirma: texto(valor, 'tipoFirma') ?? '',
    hash: esObjeto(hash)
      ? { algoritmo: texto(hash, 'algoritmo') ?? '', formato: entero(hash, 'formato'), valor: texto(hash, 'valor') ?? '' }
      : { algoritmo: '', formato: null, valor: '' },
    revocacion: leerRevocacion(Reflect.get(valor, 'revocacion')),
  };
}

function leerVerificacion(valor: unknown): Verificacion | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const integro = booleano(valor, 'integro');
  if (integro === null) {
    return null;
  }
  return {
    integro,
    algoritmo: texto(valor, 'algoritmo') ?? '',
    hashGuardado: texto(valor, 'hashGuardado') ?? '',
    hashCalculado: texto(valor, 'hashCalculado') ?? '',
    verificadoEn: texto(valor, 'verificadoEn') ?? '',
  };
}

function base(pacienteId: number): string {
  return `/pacientes/${pacienteId}/consentimientos`;
}

export async function listarConsentimientos(api: ClienteApi, pacienteId: number): Promise<ConsentimientoResumen[]> {
  return leerConsentimientos(await api.obtener<unknown>(base(pacienteId)));
}

/** El documento firmado; si la respuesta no se puede leer, falla. */
export async function obtenerConsentimiento(api: ClienteApi, pacienteId: number, consentimientoId: number): Promise<ConsentimientoVista> {
  const vista = leerVista(await api.obtener<unknown>(`${base(pacienteId)}/${consentimientoId}`));
  if (vista === null) {
    throw ilegible();
  }
  return vista;
}

/** La verificación del hash; solo un `integro` booleano cuenta: nunca se supone un resultado. */
export async function verificar(api: ClienteApi, pacienteId: number, consentimientoId: number): Promise<Verificacion> {
  const verificacion = leerVerificacion(await api.obtener<unknown>(`${base(pacienteId)}/${consentimientoId}/verificacion`));
  if (verificacion === null) {
    throw ilegible();
  }
  return verificacion;
}

/** Revoca con el motivo; con `200` ya se revocó, así que una respuesta ilegible da `null` y no un error. */
export async function revocar(api: ClienteApi, pacienteId: number, consentimientoId: number, motivo: string): Promise<ConsentimientoVista | null> {
  return leerVista(await api.crear<unknown>(`${base(pacienteId)}/${consentimientoId}/revocacion`, { motivo: normalizar(motivo) }));
}

export interface DatosFirma {
  readonly plantillaId: number;
  readonly versionNumero: number;
  readonly firmanteNombre: string;
  /** Tal como lo escribió el paciente: el backend lo normaliza. */
  readonly firmanteDni: string;
}

/**
 * Firma el consentimiento (`aceptado` siempre es `true`: la casilla se valida antes). `senal` corta el pedido a los
 * 30 segundos. Una respuesta que no se puede leer falla como error genérico: el envío queda dudoso (design D13).
 */
export async function firmar(api: ClienteApi, pacienteId: number, datos: DatosFirma, senal: AbortSignal): Promise<ConsentimientoVista> {
  const vista = leerVista(
    await api.solicitar<unknown>(
      'POST',
      base(pacienteId),
      {
        plantillaId: datos.plantillaId,
        versionNumero: datos.versionNumero,
        firmanteNombre: normalizar(datos.firmanteNombre),
        firmanteDni: datos.firmanteDni.trim(),
        aceptado: true,
      },
      senal,
    ),
  );
  if (vista === null) {
    throw ilegible();
  }
  return vista;
}
