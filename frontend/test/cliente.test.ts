import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { crearClienteApi, type ClienteApi } from '../src/shared/api/cliente.ts';
import {
  ErrorGenerico,
  ErrorNegocio,
  ErrorNoEncontrado,
  ErrorPermiso,
  ErrorSesion,
  ErrorValidacion,
} from '../src/shared/api/errores.ts';

const URL_BASE = 'http://localhost:8080/api/v1';

type FetchSimulado = ReturnType<typeof vi.fn<typeof fetch>>;

function responder(estado: number, cuerpo?: unknown): FetchSimulado {
  const texto = cuerpo === undefined ? null : typeof cuerpo === 'string' ? cuerpo : JSON.stringify(cuerpo);
  return vi.fn<typeof fetch>().mockResolvedValue(new Response(texto, { status: estado }));
}

function pedido(fetchSimulado: FetchSimulado): { url: string; opciones: RequestInit; encabezados: Headers } {
  const llamada = fetchSimulado.mock.calls[0];
  if (llamada === undefined) {
    throw new Error('No se envió ningún pedido');
  }
  const [entrada, opciones = {}] = llamada;
  return { url: String(entrada), opciones, encabezados: new Headers(opciones.headers) };
}

function fijarCookie(valor: string): void {
  document.cookie = `XSRF-TOKEN=${valor}; path=/`;
}

function borrarCookie(): void {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
}

async function capturarError(promesa: Promise<unknown>): Promise<unknown> {
  try {
    await promesa;
  } catch (error) {
    return error;
  }
  throw new Error('Se esperaba un error');
}

describe('URL base configurable', () => {
  it('Pedido a un recurso de la API', async () => {
    const fetchSimulado = responder(200, []);
    await crearClienteApi(URL_BASE, fetchSimulado).obtener('/turnos');
    expect(pedido(fetchSimulado).url).toBe('http://localhost:8080/api/v1/turnos');
  });

  it('URL base con barra final', async () => {
    const fetchSimulado = responder(200, []);
    await crearClienteApi(`${URL_BASE}/`, fetchSimulado).obtener('/turnos');
    expect(pedido(fetchSimulado).url).toBe('http://localhost:8080/api/v1/turnos');
  });
});

describe('Envío de la sesión', () => {
  it('Pedido con la cookie de sesión', async () => {
    const fetchSimulado = responder(200, []);
    await crearClienteApi(URL_BASE, fetchSimulado).obtener('/turnos');
    expect(pedido(fetchSimulado).opciones.credentials).toBe('include');
  });
});

describe('Token CSRF en los pedidos que modifican datos', () => {
  beforeEach(borrarCookie);
  afterEach(borrarCookie);

  it('POST con token CSRF', async () => {
    fijarCookie('token-ficticio-123');
    const fetchSimulado = responder(201, { id: 1 });
    await crearClienteApi(URL_BASE, fetchSimulado).crear('/turnos', { profesionalId: 1 });
    expect(pedido(fetchSimulado).encabezados.get('X-XSRF-TOKEN')).toBe('token-ficticio-123');
  });

  it('GET sin token CSRF', async () => {
    fijarCookie('token-ficticio-123');
    const fetchSimulado = responder(200, []);
    await crearClienteApi(URL_BASE, fetchSimulado).obtener('/turnos');
    expect(pedido(fetchSimulado).encabezados.has('X-XSRF-TOKEN')).toBe(false);
  });

  it('POST sin cookie CSRF', async () => {
    const fetchSimulado = responder(201, { id: 1 });
    await crearClienteApi(URL_BASE, fetchSimulado).crear('/turnos', { profesionalId: 1 });
    expect(pedido(fetchSimulado).encabezados.has('X-XSRF-TOKEN')).toBe(false);
  });
});

describe('Cuerpo JSON', () => {
  it('Respuesta exitosa con datos', async () => {
    const fetchSimulado = responder(201, { id: 1, estado: 'RESERVADO' });
    const turnoFicticio = { pacienteId: 7, profesionalId: 1, sillonId: 1, prestacionId: 1, inicio: '2026-12-01T10:00' };

    const resultado = await crearClienteApi(URL_BASE, fetchSimulado).crear('/turnos', turnoFicticio);

    const { opciones, encabezados } = pedido(fetchSimulado);
    expect(opciones.body).toBe(JSON.stringify(turnoFicticio));
    expect(encabezados.get('Content-Type')).toBe('application/json');
    expect(resultado).toEqual({ id: 1, estado: 'RESERVADO' });
  });

  it('Respuesta exitosa sin cuerpo', async () => {
    const fetchSimulado = responder(204);
    const resultado = await crearClienteApi(URL_BASE, fetchSimulado).crear('/turnos/1/cancelar');
    expect(resultado).toBeUndefined();
  });
});

describe('Error de negocio ante un 409', () => {
  let cliente: ClienteApi;

  it('Solapamiento por profesional', async () => {
    cliente = crearClienteApi(
      URL_BASE,
      responder(409, {
        status: 409,
        title: 'Conflict',
        detail: 'El profesional ya tiene un turno en ese horario',
        codigo: 'SOLAPAMIENTO_PROFESIONAL',
      }),
    );

    const error = await capturarError(cliente.crear('/turnos', {}));

    expect(error).toBeInstanceOf(ErrorNegocio);
    expect(error).toMatchObject({
      codigo: 'SOLAPAMIENTO_PROFESIONAL',
      detalle: 'El profesional ya tiene un turno en ese horario',
    });
  });

  it('Conflicto sin código', async () => {
    cliente = crearClienteApi(URL_BASE, responder(409, { status: 409, title: 'Conflict' }));

    const error = await capturarError(cliente.crear('/turnos', {}));

    expect(error).toBeInstanceOf(ErrorNegocio);
    expect(error).toMatchObject({ codigo: 'CONFLICTO_SIN_CODIGO' });
  });
});

describe('Horarios alternativos en un error de negocio', () => {
  it('Rechazo con alternativas', async () => {
    const cliente = crearClienteApi(
      URL_BASE,
      responder(409, {
        status: 409,
        codigo: 'SIN_DISPONIBILIDAD',
        alternativas: [{ inicio: '2026-12-01T11:00:00-03:00', fin: '2026-12-01T11:30:00-03:00' }],
      }),
    );

    const error = await capturarError(cliente.crear('/paciente/reservas', {}));

    expect(error).toBeInstanceOf(ErrorNegocio);
    expect(error).toMatchObject({
      codigo: 'SIN_DISPONIBILIDAD',
      alternativas: [{ inicio: '2026-12-01T11:00:00-03:00', fin: '2026-12-01T11:30:00-03:00' }],
    });
  });

  it('Rechazo sin alternativas', async () => {
    const cliente = crearClienteApi(URL_BASE, responder(409, { status: 409, codigo: 'SOLAPAMIENTO_PROFESIONAL' }));

    const error = await capturarError(cliente.crear('/turnos', {}));

    expect(error).toBeInstanceOf(ErrorNegocio);
    expect((error as ErrorNegocio).alternativas).toEqual([]);
  });

  it('Alternativas incompletas', async () => {
    const cliente = crearClienteApi(
      URL_BASE,
      responder(409, {
        status: 409,
        codigo: 'SIN_DISPONIBILIDAD',
        alternativas: [
          { inicio: '2026-12-01T11:00:00-03:00' },
          { inicio: '2026-12-01T11:30:00-03:00', fin: '2026-12-01T12:00:00-03:00' },
        ],
      }),
    );

    const error = await capturarError(cliente.crear('/paciente/reservas', {}));

    expect((error as ErrorNegocio).alternativas).toEqual([
      { inicio: '2026-12-01T11:30:00-03:00', fin: '2026-12-01T12:00:00-03:00' },
    ]);
  });
});

describe('Error de validación ante un 422', () => {
  it('Inicio en el pasado', async () => {
    const cliente = crearClienteApi(
      URL_BASE,
      responder(422, { status: 422, detail: 'El turno empieza en el pasado', codigo: 'INICIO_EN_EL_PASADO' }),
    );

    const error = await capturarError(cliente.crear('/turnos', {}));

    expect(error).toBeInstanceOf(ErrorValidacion);
    expect(error).not.toBeInstanceOf(ErrorNegocio);
    expect(error).toMatchObject({ codigo: 'INICIO_EN_EL_PASADO', detalle: 'El turno empieza en el pasado' });
    expect((error as ErrorValidacion).errores).toEqual([]);
  });

  it('Datos inválidos con errores de campo', async () => {
    const cliente = crearClienteApi(
      URL_BASE,
      responder(422, {
        status: 422,
        detail: 'Hay datos inválidos en el pedido',
        codigo: 'DATOS_INVALIDOS',
        errores: [{ campo: 'inicio', mensaje: 'es obligatorio' }, { campo: 'sinMensaje' }, 'basura'],
      }),
    );

    const error = await capturarError(cliente.crear('/turnos', {}));

    expect(error).toBeInstanceOf(ErrorValidacion);
    expect(error).toMatchObject({ codigo: 'DATOS_INVALIDOS' });
    expect((error as ErrorValidacion).errores).toEqual([{ campo: 'inicio', mensaje: 'es obligatorio' }]);
  });
});

describe('Errores de sesión, permiso y recurso inexistente', () => {
  it('Sin sesión', async () => {
    const cliente = crearClienteApi(URL_BASE, responder(401, { status: 401, codigo: 'NO_AUTENTICADO' }));

    const error = await capturarError(cliente.obtener('/turnos'));

    expect(error).toBeInstanceOf(ErrorSesion);
    expect(error).toMatchObject({ codigo: 'NO_AUTENTICADO', estado: 401 });
  });

  it('Credenciales inválidas', async () => {
    const cliente = crearClienteApi(
      URL_BASE,
      responder(401, { status: 401, detail: 'Usuario o contraseña inválidos', codigo: 'CREDENCIALES_INVALIDAS' }),
    );

    const error = await capturarError(cliente.crear('/public/login', { username: 'recepcion', password: 'x' }));

    expect(error).toBeInstanceOf(ErrorSesion);
    expect(error).toMatchObject({ codigo: 'CREDENCIALES_INVALIDAS' });
    expect((error as ErrorSesion).message).not.toContain('inválidos');
  });

  it('Acceso denegado', async () => {
    const cliente = crearClienteApi(URL_BASE, responder(403, { status: 403, codigo: 'ACCESO_DENEGADO' }));

    const error = await capturarError(cliente.crear('/turnos', {}));

    expect(error).toBeInstanceOf(ErrorPermiso);
    expect(error).not.toBeInstanceOf(ErrorSesion);
    expect(error).toMatchObject({ codigo: 'ACCESO_DENEGADO', estado: 403 });
  });

  it('Turno inexistente', async () => {
    const cliente = crearClienteApi(URL_BASE, responder(404, { status: 404, codigo: 'TURNO_INEXISTENTE' }));

    const error = await capturarError(cliente.crear('/turnos/999999/cancelar', {}));

    expect(error).toBeInstanceOf(ErrorNoEncontrado);
    expect(error).toMatchObject({ codigo: 'TURNO_INEXISTENTE', estado: 404 });
  });
});

describe('Envío de un formulario con archivos', () => {
  beforeEach(borrarCookie);
  afterEach(borrarCookie);

  function formularioFicticio(): FormData {
    const datos = new FormData();
    datos.append('archivo', new File([new Uint8Array([1, 2, 3])], 'rx.jpg', { type: 'image/jpeg' }));
    datos.append('categoria', 'RADIOGRAFIA');
    return datos;
  }

  it('Carga de un archivo con token CSRF', async () => {
    fijarCookie('token-ficticio-123');
    const fetchSimulado = responder(201, { id: 5 });
    const datos = formularioFicticio();

    const resultado = await crearClienteApi(URL_BASE, fetchSimulado).enviarFormulario('/pacientes/1/imagenes', datos);

    const { url, opciones, encabezados } = pedido(fetchSimulado);
    expect(url).toBe('http://localhost:8080/api/v1/pacientes/1/imagenes');
    expect(opciones.method).toBe('POST');
    expect(opciones.body).toBe(datos);
    expect((opciones.body as FormData).get('categoria')).toBe('RADIOGRAFIA');
    expect(((opciones.body as FormData).get('archivo') as File).name).toBe('rx.jpg');
    expect(encabezados.get('X-XSRF-TOKEN')).toBe('token-ficticio-123');
    expect(opciones.credentials).toBe('include');
    expect(encabezados.has('Content-Type')).toBe(false);
    expect(resultado).toEqual({ id: 5 });
  });

  it('Sin cookie CSRF no manda el encabezado', async () => {
    const fetchSimulado = responder(201, { id: 5 });
    await crearClienteApi(URL_BASE, fetchSimulado).enviarFormulario('/pacientes/1/imagenes', formularioFicticio());
    expect(pedido(fetchSimulado).encabezados.has('X-XSRF-TOKEN')).toBe(false);
  });

  it('Error de validación de un formulario', async () => {
    const cliente = crearClienteApi(
      URL_BASE,
      responder(422, { status: 422, detail: 'Solo se aceptan archivos JPEG, PNG o PDF', codigo: 'TIPO_ARCHIVO_NO_PERMITIDO' }),
    );

    const error = await capturarError(cliente.enviarFormulario('/pacientes/1/imagenes', formularioFicticio()));

    expect(error).toBeInstanceOf(ErrorValidacion);
    expect(error).toMatchObject({ codigo: 'TIPO_ARCHIVO_NO_PERMITIDO' });
  });

  it('Un formulario con la red caída da un error genérico', async () => {
    const fetchSimulado = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch'));
    const error = await capturarError(
      crearClienteApi(URL_BASE, fetchSimulado).enviarFormulario('/pacientes/1/imagenes', formularioFicticio()),
    );
    expect(error).toBeInstanceOf(ErrorGenerico);
    expect(error).toMatchObject({ estado: null });
  });
});

describe('Descarga de un archivo', () => {
  it('Descarga de una radiografía', async () => {
    const bytes = new Uint8Array([255, 216, 255, 1, 2, 3]);
    const fetchSimulado = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response(bytes, { status: 200, headers: { 'Content-Type': 'image/jpeg' } }));

    const archivo = await crearClienteApi(URL_BASE, fetchSimulado).obtenerArchivo('/pacientes/1/imagenes/5/archivo');

    const { url, opciones, encabezados } = pedido(fetchSimulado);
    expect(url).toBe('http://localhost:8080/api/v1/pacientes/1/imagenes/5/archivo');
    expect(opciones.method).toBe('GET');
    expect(opciones.credentials).toBe('include');
    expect(encabezados.get('Accept')).not.toBe('application/json');
    expect(archivo.type).toBe('image/jpeg');
    expect(new Uint8Array(await archivo.arrayBuffer())).toEqual(bytes);
  });

  it('Descarga sin permiso', async () => {
    const cliente = crearClienteApi(URL_BASE, responder(403, { status: 403, codigo: 'ACCESO_DENEGADO' }));

    const error = await capturarError(cliente.obtenerArchivo('/pacientes/1/imagenes/5/archivo'));

    expect(error).toBeInstanceOf(ErrorPermiso);
    expect(error).toMatchObject({ codigo: 'ACCESO_DENEGADO', estado: 403 });
  });

  it('Una descarga con un error del servidor da un error genérico', async () => {
    const cliente = crearClienteApi(URL_BASE, responder(500, 'traza interna'));
    const error = await capturarError(cliente.obtenerArchivo('/pacientes/1/imagenes/5/archivo'));
    expect(error).toBeInstanceOf(ErrorGenerico);
    expect(error).toMatchObject({ estado: 500 });
  });

  it('Una descarga con la red caída da un error genérico', async () => {
    const fetchSimulado = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch'));
    const error = await capturarError(crearClienteApi(URL_BASE, fetchSimulado).obtenerArchivo('/x'));
    expect(error).toBeInstanceOf(ErrorGenerico);
    expect(error).toMatchObject({ estado: null });
  });
});

describe('Error de validación ante un 413', () => {
  it('Archivo demasiado grande', async () => {
    const cliente = crearClienteApi(
      URL_BASE,
      responder(413, { status: 413, detail: 'El archivo supera los 10 MB', codigo: 'ARCHIVO_DEMASIADO_GRANDE' }),
    );

    const error = await capturarError(cliente.enviarFormulario('/pacientes/1/imagenes', new FormData()));

    expect(error).toBeInstanceOf(ErrorValidacion);
    expect(error).toMatchObject({ codigo: 'ARCHIVO_DEMASIADO_GRANDE', detalle: 'El archivo supera los 10 MB', estado: 413 });
  });

  it('413 sin cuerpo', async () => {
    const cliente = crearClienteApi(URL_BASE, responder(413));

    const error = await capturarError(cliente.enviarFormulario('/pacientes/1/imagenes', new FormData()));

    expect(error).toBeInstanceOf(ErrorValidacion);
    expect((error as ErrorValidacion).codigo).toBeNull();
    expect((error as ErrorValidacion).estado).toBe(413);
  });
});

describe('Otras fallas sin detalles internos', () => {
  it('Error interno del servidor', async () => {
    const traza = 'java.lang.NullPointerException at ar.turnos.odontologia.agenda.Ficticio.metodo(Ficticio.java:42)';
    const cliente = crearClienteApi(URL_BASE, responder(500, traza));

    const error = await capturarError(cliente.obtener('/turnos'));

    expect(error).toBeInstanceOf(ErrorGenerico);
    expect(error).toMatchObject({ estado: 500 });
    expect((error as ErrorGenerico).message).not.toContain('NullPointerException');
  });

  it('Un 502 sigue siendo un error genérico', async () => {
    const error = await capturarError(crearClienteApi(URL_BASE, responder(502, 'puerta de enlace')).obtener('/turnos'));
    expect(error).toBeInstanceOf(ErrorGenerico);
    expect(error).not.toBeInstanceOf(ErrorValidacion);
    expect(error).toMatchObject({ estado: 502 });
  });

  it('Servidor inalcanzable', async () => {
    const fetchSimulado = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch'));
    const cliente = crearClienteApi(URL_BASE, fetchSimulado);

    const error = await capturarError(cliente.obtener('/turnos'));

    expect(error).toBeInstanceOf(ErrorGenerico);
    expect(error).toMatchObject({ estado: null });
  });
});
