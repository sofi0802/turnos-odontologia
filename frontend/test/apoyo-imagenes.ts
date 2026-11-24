import { expect, vi } from 'vitest';
import { dejarSeleccion } from '../src/ficha/seleccion.ts';
import { abrirApp, json, ServidorFalso, type AppDePrueba } from './apoyo.ts';

/**
 * Apoyo de los tests de C-47 (frontend-imagenes-y-radiografias): la ficha de "Paciente Uno" contra el servidor
 * falso, con imágenes ficticias. El día actual es el 2026-12-01, a las 11:00 en el consultorio.
 */

export const SESION_ANA = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };
export const SESION_ADMIN = { autenticado: true, username: 'admin', rol: 'ADMIN' };
const FICHA = { pacienteId: 1, antecedentes: '', alergias: '', medicacion: '', actualizadoPor: null, actualizadoEn: null, ausencias: [] };

export interface ImagenFalsa {
  id: number;
  categoria: string;
  pieza: number | null;
  tomadaEl: string;
  descripcion: string | null;
  tipoContenido: string;
  tamanoBytes: number;
  sha256: string;
  profesionalId: number;
  profesional: string;
  registradaEn: string;
  anulacion: { profesionalId: number; profesional: string; anuladaEn: string; motivo: string } | null;
}

export function imagen(datos: Partial<ImagenFalsa> & { id: number }): ImagenFalsa {
  return {
    categoria: 'RADIOGRAFIA',
    pieza: 36,
    tomadaEl: '2026-11-30',
    descripcion: 'Control',
    tipoContenido: 'image/jpeg',
    tamanoBytes: 319_488,
    sha256: 'a'.repeat(64),
    profesionalId: 1,
    profesional: 'Ana Ejemplo',
    registradaEn: '2026-12-01T09:00:00-03:00',
    anulacion: null,
    ...datos,
  };
}

/** Radiografía de la pieza 36 tomada el 2026-11-30, descripción "Control", cargada por Ana Ejemplo. */
export const RX36 = imagen({ id: 5 });
/** Fotografía de la pieza 11 (PNG). */
export const FOTO11 = imagen({ id: 6, categoria: 'FOTOGRAFIA', pieza: 11, tomadaEl: '2026-11-15', descripcion: null, tipoContenido: 'image/png', tamanoBytes: 1_258_291 });
/** Estudio en PDF sin pieza. */
export const ESTUDIO_PDF = imagen({ id: 7, categoria: 'ESTUDIO', pieza: null, tomadaEl: '2026-10-01', descripcion: null, tipoContenido: 'application/pdf', tamanoBytes: 860_160 });
/** Fotografía de la pieza 36 anulada por Bruno Prueba. */
export const ANULADA = imagen({
  id: 8,
  categoria: 'FOTOGRAFIA',
  pieza: 36,
  descripcion: null,
  tipoContenido: 'image/png',
  anulacion: { profesionalId: 2, profesional: 'Bruno Prueba', anuladaEn: '2026-11-30T17:20:00-03:00', motivo: 'Duplicada' },
});

export interface Entorno {
  readonly servidor: ServidorFalso;
  readonly app: () => AppDePrueba | undefined;
  /** La lista que responde la API; el test la cambia para simular otras cargas. */
  imagenes: ImagenFalsa[];
  main(): HTMLElement;
  seccion(): HTMLElement;
  abrirFicha(): Promise<void>;
  /** Espera a que la sección muestre algo distinto de "Cargando". */
  seccionLista(): Promise<void>;
  cerrar(): void;
}

/** Arma el servidor falso, la sesión y la lista de imágenes; `abrirFicha` abre `#/pacientes` con "Paciente Uno". */
export function prepararEntorno(sesion: object, inicial: ImagenFalsa[] = [RX36, ESTUDIO_PDF]): Entorno {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00-03:00'));
  const servidor = new ServidorFalso();
  let app: AppDePrueba | undefined;
  const entorno: Entorno = {
    servidor,
    app: () => app,
    imagenes: inicial,
    main() {
      const raiz = app?.raiz;
      if (raiz === undefined) {
        throw new Error('La aplicación no está abierta');
      }
      return raiz.querySelector('main') ?? raiz;
    },
    seccion() {
      const encontrada = entorno.main().querySelector<HTMLElement>('.img-seccion');
      if (encontrada === null) {
        throw new Error('No está la sección de imágenes');
      }
      return encontrada;
    },
    async abrirFicha() {
      dejarSeleccion({ paciente: { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: null }, turno: null });
      app = abrirApp(servidor, '#/pacientes');
      await vi.waitFor(() => expect(entorno.main().querySelector('.ficha-columnas')).not.toBeNull());
    },
    async seccionLista() {
      await vi.waitFor(() => {
        const seccion = entorno.seccion();
        expect(seccion.querySelector('.img-lista, .estado-vacio, [role="alert"]:not(:empty)')).not.toBeNull();
        expect(seccion.querySelector('.cargando')).toBeNull();
      });
    },
    cerrar() {
      app?.router.detener();
      app = undefined;
      document.querySelectorAll('dialog').forEach((d) => d.remove());
      vi.useRealTimers();
      vi.restoreAllMocks();
      localStorage.clear();
      sessionStorage.clear();
    },
  };
  servidor.responder('GET /public/sesion', () => json(200, sesion));
  servidor.responder('GET /pacientes/{id}/ficha', () => json(200, FICHA));
  servidor.responder('GET /pacientes/{id}/odontograma', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/periodontograma', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/imagenes', (pedido) => {
    const pieza = pedido.query.get('pieza');
    const conAnuladas = pedido.query.get('incluirAnuladas') === 'true';
    return json(
      200,
      entorno.imagenes.filter(
        (i) => (conAnuladas || i.anulacion === null) && (pieza === null || i.pieza === Number(pieza)),
      ),
    );
  });
  return entorno;
}

/** Simula `URL.createObjectURL` y `URL.revokeObjectURL`, que jsdom no trae. */
export function simularDireccionesTemporales(): {
  readonly crear: ReturnType<typeof vi.fn<(objeto: Blob | MediaSource) => string>>;
  readonly revocar: ReturnType<typeof vi.fn<(direccion: string) => void>>;
} {
  let n = 0;
  const crear = vi.fn<(objeto: Blob | MediaSource) => string>(() => {
    n += 1;
    return `blob:ficticio-${n}`;
  });
  const revocar = vi.fn<(direccion: string) => void>();
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, writable: true, value: crear });
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, writable: true, value: revocar });
  return { crear, revocar };
}

/** Responde el archivo de una imagen con bytes ficticios del tipo dado. */
export function responderArchivo(servidor: ServidorFalso, tipo = 'image/jpeg', estado = 200): void {
  servidor.responder('GET /pacientes/{id}/imagenes/{id}/archivo', () =>
    estado === 200
      ? new Response(new Uint8Array([255, 216, 255, 1, 2, 3]), { status: 200, headers: { 'Content-Type': tipo } })
      : json(estado, {}),
  );
}
