import { expect, vi } from 'vitest';
import { dejarSeleccion } from '../src/ficha/seleccion.ts';
import { abrirApp, json, ServidorFalso, type AppDePrueba } from './apoyo.ts';
import { SESION_ADMIN, SESION_ODONTOLOGA, servidorCon } from './apoyo-configuracion.ts';

/**
 * Apoyo de los tests de C-48 (frontend-consentimientos): plantillas y consentimientos ficticios, y la ficha de
 * "Paciente Uno" (id 1, DNI 99000001) contra el servidor falso. El día actual es el 2026-11-24, a las 11:00 en el
 * consultorio. `ana.ejemplo` es `ODONTOLOGO` de la Dra. Ana Ejemplo (profesional 1) y `admin` es `ADMIN`.
 */

export { SESION_ADMIN, SESION_ODONTOLOGA };
export const SESION_ANA = SESION_ODONTOLOGA;

export const TITULO_EXTRACCION = 'Consentimiento para extracción dentaria';
export const TITULO_BLANQUEAMIENTO = 'Consentimiento para blanqueamiento';
export const TEXTO_V3 = 'Primer párrafo del consentimiento.\n\nSegundo párrafo con <b>riesgos</b> en la misma línea.';

export const PLANTILLAS = [
  { id: 4, activa: true, versionActual: 3, titulo: TITULO_EXTRACCION, actualizadaEn: '2026-11-24T09:15:00-03:00' },
  { id: 5, activa: false, versionActual: 1, titulo: TITULO_BLANQUEAMIENTO, actualizadaEn: '2026-10-02T10:00:00-03:00' },
];

export const TITULO_CONDUCTO = 'Consentimiento para tratamiento de conducto';

/** Lo que ve el odontólogo: solo las plantillas activas. */
export const ACTIVAS = [
  PLANTILLAS[0] as (typeof PLANTILLAS)[number],
  { id: 6, activa: true, versionActual: 1, titulo: TITULO_CONDUCTO, actualizadaEn: '2026-11-20T09:00:00-03:00' },
];

export function version(numero: number, datos: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    numero,
    titulo: TITULO_EXTRACCION,
    texto: numero === 3 ? TEXTO_V3 : `Texto de la versión ${numero}.`,
    creadaEn: `2026-11-${String(20 + numero).padStart(2, '0')}T09:15:00-03:00`,
    creadaPor: 'admin',
    ...datos,
  };
}

export function plantillaVista(id: number, activa: boolean, numero: number, datos: Record<string, unknown> = {}): Record<string, unknown> {
  return { id, activa, version: version(numero, datos) };
}

/** Texto visible, con los espacios colapsados. */
export function visible(elemento: Element): string {
  return elemento.textContent?.replace(/\s+/g, ' ').trim() ?? '';
}

/** Cambia el valor de un `textarea` o un `input` y avisa como lo haría el teclado. */
export function escribirTexto(elemento: HTMLInputElement | HTMLTextAreaElement, valor: string): void {
  elemento.value = valor;
  elemento.dispatchEvent(new Event('input', { bubbles: true }));
}

/** Todos los `<dialog>` abiertos, del más viejo al más nuevo. */
export function dialogosAbiertos(): HTMLDialogElement[] {
  return [...document.querySelectorAll<HTMLDialogElement>('dialog[open]')];
}

/** El diálogo abierto más nuevo, esperándolo si todavía no está. */
export async function ultimoDialogo(): Promise<HTMLDialogElement> {
  return vi.waitFor(() => {
    const abiertos = dialogosAbiertos();
    expect(abiertos.length).toBeGreaterThan(0);
    return abiertos[abiertos.length - 1] as HTMLDialogElement;
  });
}

/** Simula el Escape del navegador: el evento `cancel` y, si nadie lo frena, el cierre del diálogo. */
export function presionarEscape(dialogo: HTMLDialogElement): Event {
  const evento = new Event('cancel', { cancelable: true });
  dialogo.dispatchEvent(evento);
  if (!evento.defaultPrevented) {
    dialogo.close();
  }
  return evento;
}

export function limpiarDialogos(): void {
  document.querySelectorAll('dialog').forEach((d) => d.remove());
}

/** Servidor falso del administrador con las dos plantillas del escenario. */
export function servidorAdmin(): ServidorFalso {
  const servidor = servidorCon(SESION_ADMIN);
  servidor.responder('GET /consentimientos/plantillas', () => json(200, PLANTILLAS));
  return servidor;
}

// --- La ficha de "Paciente Uno" ---------------------------------------------------------------------------------

const FICHA = { pacienteId: 1, antecedentes: '', alergias: '', medicacion: '', actualizadoPor: null, actualizadoEn: null, ausencias: [] };

export interface ResumenFalso {
  id: number;
  plantillaId: number;
  versionNumero: number;
  titulo: string;
  firmadoEn: string;
  profesionalId: number;
  profesional: string;
  estado: 'VIGENTE' | 'REVOCADO';
}

export function resumen(datos: Partial<ResumenFalso> & { id: number }): ResumenFalso {
  return {
    plantillaId: 4,
    versionNumero: 3,
    titulo: TITULO_EXTRACCION,
    firmadoEn: '2026-11-24T10:30:00-03:00',
    profesionalId: 1,
    profesional: 'Ana Ejemplo',
    estado: 'VIGENTE',
    ...datos,
  };
}

/** Consentimiento vigente del escenario: extracción, versión 3, firmado el 2026-11-24 a las 10:30. */
export const VIGENTE = resumen({ id: 12 });
/** Consentimiento revocado: blanqueamiento, versión 1, firmado el 2026-10-01. */
export const REVOCADO = resumen({
  id: 11,
  plantillaId: 5,
  versionNumero: 1,
  titulo: TITULO_BLANQUEAMIENTO,
  firmadoEn: '2026-10-01T09:00:00-03:00',
  estado: 'REVOCADO',
});

export function vistaDe(base: ResumenFalso, datos: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    ...base,
    texto: TEXTO_V3,
    firmante: { nombre: 'Paciente Uno', dni: '99000001' },
    evidencia: { ip: '192.0.2.10', dispositivo: 'Navegador de prueba 1.0' },
    tipoFirma: 'ELECTRONICA',
    hash: { algoritmo: 'SHA-256', formato: 1, valor: 'a'.repeat(64) },
    revocacion:
      base.estado === 'REVOCADO'
        ? { profesionalId: 1, profesional: 'Ana Ejemplo', revocadoEn: '2026-11-20T11:00:00-03:00', motivo: 'El paciente decidió no hacer el tratamiento' }
        : null,
    ...datos,
  };
}

/** Verificación correcta del consentimiento 12: el hash guardado y el calculado coinciden. */
export const VERIFICACION_INTEGRA = {
  consentimientoId: 12,
  integro: true,
  algoritmo: 'SHA-256',
  hashGuardado: 'a'.repeat(64),
  hashCalculado: 'a'.repeat(64),
  verificadoEn: '2026-11-24T11:05:00-03:00',
};

/** Verificación con dos hash distintos: el documento fue alterado. */
export const VERIFICACION_ALTERADA = {
  ...VERIFICACION_INTEGRA,
  integro: false,
  hashGuardado: '3f9a'.repeat(16),
  hashCalculado: 'b7e1'.repeat(16),
};

/** Responde el documento y su verificación (`Response` o cuerpo JSON) de cualquier consentimiento. */
export function responderDocumento(
  servidor: ServidorFalso,
  vista: Record<string, unknown> = vistaDe(VIGENTE),
  verificacion: Response | object = VERIFICACION_INTEGRA,
): void {
  servidor.responder('GET /pacientes/{id}/consentimientos/{id}', () => json(200, vista));
  servidor.responder('GET /pacientes/{id}/consentimientos/{id}/verificacion', () =>
    verificacion instanceof Response ? verificacion : json(200, verificacion),
  );
}

export interface Entorno {
  readonly servidor: ServidorFalso;
  readonly app: () => AppDePrueba | undefined;
  /** El listado que responde la API; el test lo cambia para simular otras cargas. */
  consentimientos: ResumenFalso[];
  main(): HTMLElement;
  seccion(): HTMLElement;
  abrirFicha(): Promise<void>;
  /** Espera a que la sección muestre algo distinto de "Cargando". */
  seccionLista(): Promise<void>;
  cerrar(): void;
}

/** Arma el servidor falso, la sesión y el listado; `abrirFicha` abre `#/pacientes` con "Paciente Uno". */
export function prepararEntorno(sesion: object, inicial: ResumenFalso[] = [VIGENTE, REVOCADO]): Entorno {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-24T11:00:00-03:00'));
  const servidor = servidorCon(sesion);
  servidor.responder('GET /pacientes/{id}/ficha', () => json(200, FICHA));
  servidor.responder('GET /pacientes/{id}/odontograma', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/periodontograma', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/imagenes', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/cobertura', () => json(200, {}));
  servidor.responder('GET /pacientes/{id}/afiliaciones', () => json(200, []));
  servidor.responder('GET /consentimientos/plantillas', () => json(200, ACTIVAS));
  servidor.responder('GET /consentimientos/plantillas/{id}', () => json(200, plantillaVista(4, true, 3)));
  servidor.responder('POST /logout', () => json(204));
  let app: AppDePrueba | undefined;
  const entorno: Entorno = {
    servidor,
    app: () => app,
    consentimientos: inicial,
    main() {
      const raiz = app?.raiz;
      if (raiz === undefined) {
        throw new Error('La aplicación no está abierta');
      }
      return raiz.querySelector('main') ?? raiz;
    },
    seccion() {
      const encontrada = entorno.main().querySelector<HTMLElement>('.cons-seccion');
      if (encontrada === null) {
        throw new Error('No está la sección de consentimientos');
      }
      return encontrada;
    },
    async abrirFicha() {
      dejarSeleccion({ paciente: { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: '99000001' }, turno: null });
      app = abrirApp(servidor, '#/pacientes');
      await vi.waitFor(() => expect(entorno.main().querySelector('.ficha-columnas')).not.toBeNull());
    },
    async seccionLista() {
      await vi.waitFor(() => {
        const seccion = entorno.seccion();
        expect(seccion.querySelector('.cons-lista, .estado-vacio, [role="alert"]:not(:empty)')).not.toBeNull();
        expect(seccion.querySelector('.cargando')).toBeNull();
      });
    },
    cerrar() {
      app?.router.detener();
      app = undefined;
      limpiarDialogos();
      document.querySelectorAll('.bloqueo-pantalla').forEach((b) => b.remove());
      vi.useRealTimers();
      vi.restoreAllMocks();
      localStorage.clear();
      sessionStorage.clear();
    },
  };
  servidor.responder('GET /pacientes/{id}/consentimientos', () => json(200, entorno.consentimientos));
  return entorno;
}

// --- La vista de firma ------------------------------------------------------------------------------------------

/**
 * Abre la ficha, elige "Consentimiento para extracción dentaria" (versión 3) y "Continuar", y espera la vista de firma.
 * Con `consentimientos` vacío de firmas de hoy, para que no pregunte por la doble firma.
 */
export async function abrirVistaDeFirma(entorno: Entorno): Promise<HTMLDialogElement> {
  entorno.consentimientos = entorno.consentimientos.filter((c) => c.estado === 'REVOCADO');
  await entorno.abrirFicha();
  await entorno.seccionLista();
  const firmar = [...entorno.seccion().querySelectorAll('button')].find((b) => b.textContent === 'Firmar consentimiento');
  firmar?.click();
  const eleccion = await ultimoDialogo();
  await vi.waitFor(() => expect(eleccion.querySelector('input[type="radio"]')).not.toBeNull());
  [...eleccion.querySelectorAll('button')].find((b) => b.textContent === 'Continuar')?.click();
  return vi.waitFor(() => {
    const pantalla = document.querySelector<HTMLDialogElement>('dialog.firma-pantalla');
    expect(pantalla).not.toBeNull();
    return pantalla as HTMLDialogElement;
  });
}

export interface DatosDeFirma {
  nombre?: string;
  dni?: string;
  acepto?: boolean;
}

/** Escribe el nombre y el DNI y marca la casilla, como lo haría el paciente. */
export function completarFirma(pantalla: HTMLElement, datos: DatosDeFirma = {}): void {
  const { nombre = 'Paciente Uno', dni = '99.000.001', acepto = true } = datos;
  const campos = pantalla.querySelectorAll<HTMLInputElement>('input[type="text"]');
  escribirTexto(campos[0] as HTMLInputElement, nombre);
  escribirTexto(campos[1] as HTMLInputElement, dni);
  const casilla = pantalla.querySelector<HTMLInputElement>('input[type="checkbox"]') as HTMLInputElement;
  casilla.checked = acepto;
  casilla.dispatchEvent(new Event('change', { bubbles: true }));
}

/** Envía el formulario de la firma como lo haría "Firmar". */
export function enviarFirma(pantalla: HTMLElement): void {
  (pantalla.querySelector('form') as HTMLFormElement).requestSubmit();
}
