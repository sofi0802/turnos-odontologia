import { describe, expect, it, vi } from 'vitest';
import {
  cambiarEstadoPlantilla,
  crearPlantilla,
  editarPlantilla,
  firmar,
  leerConsentimientos,
  leerPlantillas,
  listarConsentimientos,
  listarPlantillas,
  listarVersiones,
  obtenerConsentimiento,
  obtenerPlantilla,
  revocar,
  verificar,
} from '../src/consentimientos/api.ts';
import { crearClienteApi } from '../src/shared/api/cliente.ts';
import { ErrorGenerico, ErrorNegocio, ErrorNoEncontrado } from '../src/shared/api/errores.ts';
import { json, ServidorFalso } from './apoyo.ts';

const VERSION = {
  numero: 3,
  titulo: 'Consentimiento para extracción dentaria',
  texto: 'Primera línea\n<b>Segunda</b>',
  creadaEn: '2026-11-24T09:00:00-03:00',
  creadaPor: 'admin',
};
const PLANTILLA = { id: 4, activa: true, version: VERSION };
const RESUMEN = {
  id: 12,
  plantillaId: 4,
  versionNumero: 3,
  titulo: 'Consentimiento para extracción dentaria',
  firmadoEn: '2026-11-24T10:30:00-03:00',
  profesionalId: 1,
  profesional: 'Ana Ejemplo',
  estado: 'VIGENTE',
};
const VISTA = {
  ...RESUMEN,
  texto: 'Texto firmado',
  firmante: { nombre: 'Paciente Uno', dni: '99000001' },
  evidencia: { ip: '203.0.113.5', dispositivo: 'Navegador de prueba' },
  tipoFirma: 'ELECTRONICA',
  hash: { algoritmo: 'SHA-256', formato: 1, valor: 'a'.repeat(64) },
  revocacion: null,
};

describe('Plantillas: pedidos y lectura', () => {
  it('lista las activas sin parámetros y, con incluirInactivas, pide también las inactivas', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /consentimientos/plantillas', () =>
      json(200, [{ id: 4, activa: true, versionActual: 3, titulo: 'Extracción', actualizadaEn: '2026-11-24T09:00:00-03:00' }]),
    );
    const activas = await listarPlantillas(servidor.api, false);
    expect(servidor.ultimo('GET', '/consentimientos/plantillas').query.has('incluirInactivas')).toBe(false);
    await listarPlantillas(servidor.api, true);
    expect(servidor.ultimo('GET', '/consentimientos/plantillas').query.get('incluirInactivas')).toBe('true');
    expect(activas).toEqual([
      { id: 4, activa: true, versionActual: 3, titulo: 'Extracción', actualizadaEn: '2026-11-24T09:00:00-03:00' },
    ]);
  });

  it('descarta lo que no tiene el tipo esperado y devuelve una lista vacía si no es una lista', () => {
    expect(
      leerPlantillas([
        { id: 4, activa: true, versionActual: 3, titulo: 'Bien', actualizadaEn: '2026-11-24T09:00:00-03:00' },
        { id: '5', activa: true, versionActual: 1, titulo: 'Id de texto', actualizadaEn: '' },
        { id: 6, activa: 'si', versionActual: 1, titulo: 'Activa de texto', actualizadaEn: '' },
        { id: 7, activa: false, versionActual: 1, titulo: 42, actualizadaEn: '' },
        null,
        'texto',
      ]).map((p) => p.id),
    ).toEqual([4]);
    expect(leerPlantillas({ id: 1 })).toEqual([]);
    expect(leerPlantillas(undefined)).toEqual([]);
  });

  it('pide una plantilla con su última versión', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /consentimientos/plantillas/{id}', () => json(200, PLANTILLA));
    expect(await obtenerPlantilla(servidor.api, 4)).toEqual(PLANTILLA);
    expect(servidor.a('GET', '/consentimientos/plantillas/4')).toHaveLength(1);
  });

  it('con una plantilla ilegible falla como error genérico: no se firma un texto dudoso', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /consentimientos/plantillas/{id}', () => json(200, { id: 4, activa: true, version: { numero: 3 } }));
    await expect(obtenerPlantilla(servidor.api, 4)).rejects.toBeInstanceOf(ErrorGenerico);
  });

  it('una plantilla que ya no está da el error de "no encontrado"', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /consentimientos/plantillas/{id}', () => json(404, { codigo: 'RECURSO_NO_ENCONTRADO' }));
    await expect(obtenerPlantilla(servidor.api, 4)).rejects.toBeInstanceOf(ErrorNoEncontrado);
  });

  it('crea una plantilla con el título y el texto', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /consentimientos/plantillas', () => json(201, { ...PLANTILLA, id: 9 }));
    const creada = await crearPlantilla(servidor.api, { titulo: 'Consentimiento para implante', texto: 'Uno\n\nDos' });
    expect(servidor.ultimo('POST', '/consentimientos/plantillas').cuerpo).toEqual({
      titulo: 'Consentimiento para implante',
      texto: 'Uno\n\nDos',
    });
    expect(creada?.id).toBe(9);
  });

  it('una alta con 201 y respuesta ilegible no es un error: ya se creó', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /consentimientos/plantillas', () => json(201, { raro: true }));
    await expect(crearPlantilla(servidor.api, { titulo: 'T', texto: 'X' })).resolves.toBeNull();
  });

  it('edita con versionBase y distingue la versión nueva (201) de "sin cambios" (200)', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /consentimientos/plantillas/{id}/versiones', () =>
      json(201, { ...PLANTILLA, version: { ...VERSION, numero: 4 } }),
    );
    const nueva = await editarPlantilla(servidor.api, 4, { versionBase: 3, titulo: 'T', texto: 'X' });
    expect(servidor.ultimo('POST', '/consentimientos/plantillas/4/versiones').cuerpo).toEqual({
      versionBase: 3,
      titulo: 'T',
      texto: 'X',
    });
    expect(nueva).toEqual({ numero: 4, sinCambios: false });

    servidor.responder('POST /consentimientos/plantillas/{id}/versiones', () => json(200, PLANTILLA));
    expect(await editarPlantilla(servidor.api, 4, { versionBase: 3, titulo: 'T', texto: 'X' })).toEqual({
      numero: 3,
      sinCambios: true,
    });
  });

  it('un conflicto de versión llega como error de negocio con su código', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /consentimientos/plantillas/{id}/versiones', () =>
      json(409, { codigo: 'VERSION_DESACTUALIZADA', detail: 'x' }),
    );
    await expect(editarPlantilla(servidor.api, 4, { versionBase: 3, titulo: 'T', texto: 'X' })).rejects.toMatchObject({
      codigo: 'VERSION_DESACTUALIZADA',
    });
    await expect(editarPlantilla(servidor.api, 4, { versionBase: 3, titulo: 'T', texto: 'X' })).rejects.toBeInstanceOf(ErrorNegocio);
  });

  it('lista las versiones en el orden de la API', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /consentimientos/plantillas/{id}/versiones', () =>
      json(200, [VERSION, { ...VERSION, numero: 2 }, { ...VERSION, numero: 1 }, { numero: 'x' }]),
    );
    expect((await listarVersiones(servidor.api, 4)).map((v) => v.numero)).toEqual([3, 2, 1]);
  });

  it('cambia el estado con { activa }', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('PUT /consentimientos/plantillas/{id}/estado', () => json(200, { ...PLANTILLA, activa: false }));
    await cambiarEstadoPlantilla(servidor.api, 4, false);
    expect(servidor.ultimo('PUT', '/consentimientos/plantillas/4/estado').cuerpo).toEqual({ activa: false });
    await cambiarEstadoPlantilla(servidor.api, 4, true);
    expect(servidor.ultimo('PUT', '/consentimientos/plantillas/4/estado').cuerpo).toEqual({ activa: true });
  });
});

describe('Consentimientos: pedidos y lectura', () => {
  it('lista los consentimientos del paciente en el orden de la API y descarta los ilegibles', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /pacientes/{id}/consentimientos', () =>
      json(200, [RESUMEN, { ...RESUMEN, id: 13, estado: 'OTRO' }, { ...RESUMEN, id: 14, estado: 'REVOCADO' }, { id: 15 }]),
    );
    const lista = await listarConsentimientos(servidor.api, 1);
    expect(lista.map((c) => [c.id, c.estado])).toEqual([
      [12, 'VIGENTE'],
      [14, 'REVOCADO'],
    ]);
    expect(servidor.a('GET', '/pacientes/1/consentimientos')).toHaveLength(1);
    expect(leerConsentimientos('no es una lista')).toEqual([]);
  });

  it('lee el documento con la evidencia, el formato del hash como número y la revocación', async () => {
    const servidor = new ServidorFalso();
    const revocacion = { profesionalId: 1, profesional: 'Ana Ejemplo', revocadoEn: '2026-11-20T11:00:00-03:00', motivo: 'Cambió de idea' };
    servidor.responder('GET /pacientes/{id}/consentimientos/{id}', () => json(200, { ...VISTA, revocacion }));
    const vista = await obtenerConsentimiento(servidor.api, 1, 12);
    expect(servidor.a('GET', '/pacientes/1/consentimientos/12')).toHaveLength(1);
    expect(vista.firmante).toEqual({ nombre: 'Paciente Uno', dni: '99000001' });
    expect(vista.evidencia).toEqual({ ip: '203.0.113.5', dispositivo: 'Navegador de prueba' });
    expect(vista.hash).toEqual({ algoritmo: 'SHA-256', formato: 1, valor: 'a'.repeat(64) });
    expect(typeof vista.hash.formato).toBe('number');
    expect(vista.revocacion).toEqual({ profesional: 'Ana Ejemplo', revocadoEn: '2026-11-20T11:00:00-03:00', motivo: 'Cambió de idea' });
    expect(vista.estado).toBe('VIGENTE');
  });

  it('sin revocación, el documento la trae en null; un hash con formato de texto no se acepta como número', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /pacientes/{id}/consentimientos/{id}', () =>
      json(200, { ...VISTA, hash: { algoritmo: 'SHA-256', formato: '1', valor: 'b'.repeat(64) } }),
    );
    const vista = await obtenerConsentimiento(servidor.api, 1, 12);
    expect(vista.revocacion).toBeNull();
    expect(vista.hash.formato).toBeNull();
  });

  it('un documento ilegible falla como error genérico', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('GET /pacientes/{id}/consentimientos/{id}', () => json(200, { id: 12 }));
    await expect(obtenerConsentimiento(servidor.api, 1, 12)).rejects.toBeInstanceOf(ErrorGenerico);
  });

  it('pide la verificación y solo acepta un resultado con integro booleano', async () => {
    const servidor = new ServidorFalso();
    const verificacion = {
      consentimientoId: 12,
      integro: true,
      algoritmo: 'SHA-256',
      hashGuardado: 'a'.repeat(64),
      hashCalculado: 'a'.repeat(64),
      verificadoEn: '2026-11-24T11:00:00-03:00',
    };
    servidor.responder('GET /pacientes/{id}/consentimientos/{id}/verificacion', () => json(200, verificacion));
    expect(await verificar(servidor.api, 1, 12)).toEqual({
      integro: true,
      algoritmo: 'SHA-256',
      hashGuardado: 'a'.repeat(64),
      hashCalculado: 'a'.repeat(64),
      verificadoEn: '2026-11-24T11:00:00-03:00',
    });
    expect(servidor.a('GET', '/pacientes/1/consentimientos/12/verificacion')).toHaveLength(1);

    for (const raro of [{ ...verificacion, integro: 'true' }, { ...verificacion, integro: undefined }, {}]) {
      servidor.responder('GET /pacientes/{id}/consentimientos/{id}/verificacion', () => json(200, raro));
      await expect(verificar(servidor.api, 1, 12)).rejects.toBeInstanceOf(ErrorGenerico);
    }
  });

  it('revoca con el motivo recortado', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/{id}/consentimientos/{id}/revocacion', () => json(200, VISTA));
    await revocar(servidor.api, 1, 12, '  El paciente decidió no hacer el tratamiento  ');
    expect(servidor.ultimo('POST', '/pacientes/1/consentimientos/12/revocacion').cuerpo).toEqual({
      motivo: 'El paciente decidió no hacer el tratamiento',
    });
  });
});

describe('Firma', () => {
  const DATOS = { plantillaId: 4, versionNumero: 3, firmanteNombre: 'Paciente Uno', firmanteDni: '99.000.001' };

  it('envía la plantilla, la versión, el nombre, el DNI tal como se escribió y aceptado', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/{id}/consentimientos', () => json(201, VISTA));
    const firmado = await firmar(servidor.api, 1, DATOS, new AbortController().signal);
    expect(servidor.ultimo('POST', '/pacientes/1/consentimientos').cuerpo).toEqual({
      plantillaId: 4,
      versionNumero: 3,
      firmanteNombre: 'Paciente Uno',
      firmanteDni: '99.000.001',
      aceptado: true,
    });
    expect(firmado.id).toBe(12);
  });

  it('un rechazo de negocio o de validación llega con su código', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/{id}/consentimientos', () => json(422, { codigo: 'FIRMANTE_NO_COINCIDE', detail: 'x' }));
    await expect(firmar(servidor.api, 1, DATOS, new AbortController().signal)).rejects.toMatchObject({ codigo: 'FIRMANTE_NO_COINCIDE' });
    servidor.responder('POST /pacientes/{id}/consentimientos', () => json(409, { codigo: 'PLANTILLA_INACTIVA', detail: 'x' }));
    await expect(firmar(servidor.api, 1, DATOS, new AbortController().signal)).rejects.toMatchObject({ codigo: 'PLANTILLA_INACTIVA' });
  });

  it('una respuesta que no se puede leer es un error genérico: el envío queda dudoso', async () => {
    const servidor = new ServidorFalso();
    servidor.responder('POST /pacientes/{id}/consentimientos', () => json(201, { cosa: 1 }));
    await expect(firmar(servidor.api, 1, DATOS, new AbortController().signal)).rejects.toBeInstanceOf(ErrorGenerico);
  });

  it('pasa la señal de cancelación al pedido: al cancelarla, el pedido se corta con un error genérico', async () => {
    let senalRecibida: AbortSignal | null | undefined;
    const fetchColgado = vi.fn<typeof fetch>(
      (_entrada, opciones) =>
        new Promise<Response>((_resolver, rechazar) => {
          senalRecibida = opciones?.signal;
          opciones?.signal?.addEventListener('abort', () => rechazar(new DOMException('cancelado', 'AbortError')));
        }),
    );
    const api = crearClienteApi('http://localhost:8080/api/v1', fetchColgado);
    const control = new AbortController();
    const pedido = firmar(api, 1, DATOS, control.signal);
    const rechazo = expect(pedido).rejects.toBeInstanceOf(ErrorGenerico);
    expect(senalRecibida).toBe(control.signal);
    control.abort();
    await rechazo;
  });

  it('los otros pedidos de la API no llevan señal', async () => {
    const fetchSimple = vi.fn<typeof fetch>(async () => json(200, []));
    const api = crearClienteApi('http://localhost:8080/api/v1', fetchSimple);
    await listarConsentimientos(api, 1);
    expect(fetchSimple.mock.calls[0]?.[1]?.signal ?? null).toBeNull();
  });
});
