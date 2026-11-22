import {
  CODIGO_CONFLICTO_SIN_CODIGO,
  ErrorGenerico,
  ErrorNegocio,
  ErrorNoEncontrado,
  ErrorPermiso,
  ErrorSesion,
  ErrorValidacion,
  leerProblemDetail,
} from './errores.ts';

/** Nombres que usa Spring Security con CookieCsrfTokenRepository (supuesto con C-05). */
export const COOKIE_CSRF = 'XSRF-TOKEN';
export const ENCABEZADO_CSRF = 'X-XSRF-TOKEN';

export type MetodoHttp = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

const METODOS_QUE_MODIFICAN: ReadonlySet<MetodoHttp> = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export interface ClienteApi {
  solicitar<T>(metodo: MetodoHttp, ruta: string, cuerpo?: unknown): Promise<T | undefined>;
  obtener<T>(ruta: string): Promise<T | undefined>;
  crear<T>(ruta: string, cuerpo?: unknown): Promise<T | undefined>;
  actualizar<T>(ruta: string, cuerpo?: unknown): Promise<T | undefined>;
  borrar<T>(ruta: string): Promise<T | undefined>;
}

function normalizarBase(urlBase: string): string {
  return urlBase.replace(/\/+$/, '');
}

function normalizarRuta(ruta: string): string {
  return ruta.startsWith('/') ? ruta : `/${ruta}`;
}

/** Indica si el navegador ya tiene el token CSRF que entrega el servidor. */
export function hayTokenCsrf(): boolean {
  return leerCookie(COOKIE_CSRF) !== null;
}

function leerCookie(nombre: string): string | null {
  for (const parte of document.cookie.split(';')) {
    const [clave, ...resto] = parte.trim().split('=');
    if (clave === nombre) {
      return decodeURIComponent(resto.join('='));
    }
  }
  return null;
}

async function leerCuerpo(respuesta: Response): Promise<unknown> {
  const texto = await respuesta.text();
  if (texto === '') {
    return undefined;
  }
  try {
    return JSON.parse(texto) as unknown;
  } catch {
    return undefined;
  }
}

async function traducirError(respuesta: Response): Promise<Error> {
  switch (respuesta.status) {
    case 401:
    case 403:
    case 404:
    case 409:
    case 422:
      break;
    default:
      return new ErrorGenerico(respuesta.status);
  }
  const problema = leerProblemDetail(await leerCuerpo(respuesta));
  switch (respuesta.status) {
    case 401:
      return new ErrorSesion(problema.codigo);
    case 403:
      return new ErrorPermiso(problema.codigo);
    case 404:
      return new ErrorNoEncontrado(problema.codigo);
    case 409:
      return new ErrorNegocio(problema.codigo ?? CODIGO_CONFLICTO_SIN_CODIGO, problema.detail);
    default:
      return new ErrorValidacion(problema.detail, problema.codigo, problema.errores);
  }
}

/** Crea un cliente con una URL base dada; las pantallas usan `api`. */
export function crearClienteApi(
  urlBase: string,
  fetchImpl: typeof fetch = (entrada, opciones) => fetch(entrada, opciones),
): ClienteApi {
  const base = normalizarBase(urlBase);

  async function solicitar<T>(metodo: MetodoHttp, ruta: string, cuerpo?: unknown): Promise<T | undefined> {
    const encabezados = new Headers({ Accept: 'application/json' });
    if (cuerpo !== undefined) {
      encabezados.set('Content-Type', 'application/json');
    }
    if (METODOS_QUE_MODIFICAN.has(metodo)) {
      const token = leerCookie(COOKIE_CSRF);
      if (token !== null) {
        encabezados.set(ENCABEZADO_CSRF, token);
      }
    }

    let respuesta: Response;
    try {
      respuesta = await fetchImpl(`${base}${normalizarRuta(ruta)}`, {
        method: metodo,
        credentials: 'include',
        headers: encabezados,
        body: cuerpo === undefined ? null : JSON.stringify(cuerpo),
      });
    } catch {
      throw new ErrorGenerico(null);
    }

    if (!respuesta.ok) {
      throw await traducirError(respuesta);
    }
    return (await leerCuerpo(respuesta)) as T | undefined;
  }

  return {
    solicitar,
    obtener: <T>(ruta: string) => solicitar<T>('GET', ruta),
    crear: <T>(ruta: string, cuerpo?: unknown) => solicitar<T>('POST', ruta, cuerpo),
    actualizar: <T>(ruta: string, cuerpo?: unknown) => solicitar<T>('PUT', ruta, cuerpo),
    borrar: <T>(ruta: string) => solicitar<T>('DELETE', ruta),
  };
}

/** Cliente de la aplicación, configurado con VITE_API_BASE_URL. */
export const api: ClienteApi = crearClienteApi(import.meta.env.VITE_API_BASE_URL ?? '');
