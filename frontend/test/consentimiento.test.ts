import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, boton, campo, elegirPaciente, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';
import { crearLineaConsentimiento } from '../src/consentimiento/linea-recepcion.ts';

/**
 * C-17: specs `frontend-consentimiento-comunicaciones` (paciente y recepción) y "Datos del paciente en Mi cuenta" de
 * `frontend-cuenta-paciente`. Datos ficticios: "Paciente Uno" (DNI 99000001), "Paciente Dos" (DNI 99000002).
 */

const SESION_UNO = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };
const PERFIL_UNO = {
  nombre: 'Paciente',
  apellido: 'Uno',
  dni: '99000001',
  email: 'paciente.uno@example.com',
  telefono: null,
};
const SIN_CONSENTIMIENTO = { otorgado: false, actualizadoEn: null };
const CON_CONSENTIMIENTO = { otorgado: true, actualizadoEn: '2026-12-01T13:00:00Z' };

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
  servidor = new ServidorFalso();
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
});

afterEach(() => {
  app = undefined;
  vi.useRealTimers();
});

function main(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz.querySelector<HTMLElement>('main') ?? app.raiz;
}

function seccion(titulo: string): HTMLElement {
  const encontrada = [...main().querySelectorAll<HTMLElement>('section')].find(
    (s) => s.querySelector('h2')?.textContent === titulo,
  );
  if (encontrada === undefined) {
    throw new Error(`No está la sección ${titulo}`);
  }
  return encontrada;
}

/** El diálogo abierto más reciente (el de arriba). */
function dialogo(): HTMLDialogElement {
  const abiertos = [...document.querySelectorAll<HTMLDialogElement>('dialog[open]')];
  const ultimo = abiertos[abiertos.length - 1];
  if (ultimo === undefined) {
    throw new Error('No hay un diálogo abierto');
  }
  return ultimo;
}

async function abrirMiCuenta(consentimiento: object = SIN_CONSENTIMIENTO): Promise<void> {
  servidor.responder('GET /public/sesion', () => json(200, SESION_UNO));
  servidor.responder('GET /paciente/perfil', () => json(200, PERFIL_UNO));
  servidor.responder('GET /paciente/consentimiento-comunicaciones', () => json(200, consentimiento));
  app = abrirApp(servidor, '#/mi-cuenta');
  await vi.waitFor(() => expect(texto(seccion('Mensajes del consultorio'))).toMatch(/recibís mensajes|Aceptás/));
  await vi.waitFor(() => expect(texto(seccion('Tus datos'))).toContain('99000001'));
}

describe('Datos del paciente en Mi cuenta', () => {
  it('El paciente ve sus datos', async () => {
    await abrirMiCuenta();

    const datos = seccion('Tus datos');
    expect(texto(datos)).toContain('Paciente Uno');
    expect(texto(datos)).toContain('paciente.uno@example.com');
    expect(texto(datos)).toContain('Sin teléfono');
    expect(datos.querySelector('input, textarea, select')).toBeNull();
    const titulos = [...main().querySelectorAll('section > h2')].map((h) => h.textContent);
    expect(titulos.slice(0, 3)).toEqual(['Tus datos', 'Mensajes del consultorio', 'Mi cobertura']);
  });

  it('Falla al cargar el perfil', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_UNO));
    servidor.responder('GET /paciente/perfil', () => json(500));
    servidor.responder('GET /paciente/consentimiento-comunicaciones', () => json(200, SIN_CONSENTIMIENTO));
    app = abrirApp(servidor, '#/mi-cuenta');

    await vi.waitFor(() => expect(texto(seccion('Tus datos'))).toContain('No pudimos cargar tus datos.'));
    expect(boton(seccion('Tus datos'), 'Reintentar')).toBeDefined();
    await vi.waitFor(() => expect(texto(seccion('Mensajes del consultorio'))).toContain('No recibís mensajes'));
    expect(seccion('Mi cobertura')).toBeDefined();

    servidor.responder('GET /paciente/perfil', () => json(200, PERFIL_UNO));
    boton(seccion('Tus datos'), 'Reintentar').click();
    await vi.waitFor(() => expect(texto(seccion('Tus datos'))).toContain('99000001'));
  });
});

describe('Mensajes del consultorio', () => {
  it('Mi cuenta sin consentimiento', async () => {
    await abrirMiCuenta();

    const mensajes = seccion('Mensajes del consultorio');
    expect(texto(mensajes)).toContain('No recibís mensajes');
    expect(texto(mensajes)).toContain('sin datos de salud');
    expect(boton(mensajes, 'Aceptar mensajes')).toBeDefined();
  });

  it('Mi cuenta con consentimiento', async () => {
    await abrirMiCuenta(CON_CONSENTIMIENTO);

    const mensajes = seccion('Mensajes del consultorio');
    expect(texto(mensajes)).toContain('Aceptás recibir mensajes');
    expect(texto(mensajes)).toContain('Desde el 01/12/2026');
    expect(boton(mensajes, 'Dejar de recibir mensajes')).toBeDefined();
  });

  it('Aceptar mensajes', async () => {
    servidor.responder('PUT /paciente/consentimiento-comunicaciones', () => json(200, CON_CONSENTIMIENTO));
    await abrirMiCuenta();

    const aceptar = boton(seccion('Mensajes del consultorio'), 'Aceptar mensajes');
    aceptar.click();
    expect(aceptar.disabled).toBe(true);

    await vi.waitFor(() =>
      expect(texto(seccion('Mensajes del consultorio'))).toContain('Listo, vas a recibir mensajes del consultorio.'),
    );
    expect(servidor.ultimo('PUT', '/paciente/consentimiento-comunicaciones').cuerpo).toEqual({ otorgado: true });
    expect(texto(seccion('Mensajes del consultorio'))).toContain('Aceptás recibir mensajes');
  });

  it('Dejar de recibir mensajes', async () => {
    servidor.responder('PUT /paciente/consentimiento-comunicaciones', () =>
      json(200, { otorgado: false, actualizadoEn: '2026-12-03T21:00:00Z' }),
    );
    await abrirMiCuenta(CON_CONSENTIMIENTO);

    boton(seccion('Mensajes del consultorio'), 'Dejar de recibir mensajes').click();
    expect(dialogo().querySelector('h2')?.textContent).toBe('¿Dejar de recibir mensajes?');
    expect(servidor.a('PUT', '/paciente/consentimiento-comunicaciones')).toHaveLength(0);
    boton(dialogo(), 'Dejar de recibir').click();

    await vi.waitFor(() => expect(texto(seccion('Mensajes del consultorio'))).toContain('No recibís mensajes'));
    expect(servidor.ultimo('PUT', '/paciente/consentimiento-comunicaciones').cuerpo).toEqual({ otorgado: false });
    expect(texto(seccion('Mensajes del consultorio'))).toContain(
      'Ya no te vamos a recordar tus turnos ni ofrecer horarios de la lista de espera.',
    );
  });

  it('Cancelar la confirmación', async () => {
    await abrirMiCuenta(CON_CONSENTIMIENTO);

    boton(seccion('Mensajes del consultorio'), 'Dejar de recibir mensajes').click();
    boton(dialogo(), 'Volver').click();

    expect(document.querySelector('dialog[open]')).toBeNull();
    expect(servidor.a('PUT', '/paciente/consentimiento-comunicaciones')).toHaveLength(0);
    expect(texto(seccion('Mensajes del consultorio'))).toContain('Aceptás recibir mensajes');
  });

  it('Un error al cambiar se muestra sin cambiar el estado', async () => {
    servidor.responder('PUT /paciente/consentimiento-comunicaciones', () => json(500));
    await abrirMiCuenta();

    boton(seccion('Mensajes del consultorio'), 'Aceptar mensajes').click();

    await vi.waitFor(() => expect(seccion('Mensajes del consultorio').querySelector('[role="alert"]')?.textContent)
      .not.toBe(''));
    expect(texto(seccion('Mensajes del consultorio'))).toContain('No recibís mensajes');
    expect(boton(seccion('Mensajes del consultorio'), 'Aceptar mensajes').disabled).toBe(false);
  });
});

describe('Consentimiento en la recepción', () => {
  const PRESTACIONES_PUBLICAS = [{ id: 1, nombre: 'Consulta', duracionMinutos: 30 }];
  const PROFESIONALES_PUBLICOS = [
    { id: 1, nombre: 'Ana Ejemplo' },
    { id: 2, nombre: 'Bruno Prueba' },
  ];

  async function abrirAnotar(): Promise<void> {
    servidor.responder('GET /public/prestaciones', () => json(200, PRESTACIONES_PUBLICAS));
    servidor.responder('GET /public/profesionales', () => json(200, PROFESIONALES_PUBLICOS));
    app = abrirApp(servidor, '#/lista-espera');
    await vi.waitFor(() => expect(boton(main(), 'Anotar paciente')).toBeDefined());
    boton(main(), 'Anotar paciente').click();
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(dialogo(), 'Prestación').value).toBe('1'));
  }

  function linea(): HTMLElement {
    const encontrada = dialogo().querySelector<HTMLElement>('.consentimiento-linea');
    if (encontrada === null) {
      throw new Error('No está la línea de consentimiento');
    }
    return encontrada;
  }

  it('Paciente elegido sin consentimiento', async () => {
    await abrirAnotar();

    await elegirPaciente(dialogo(), '99000002', 'Dos, Paciente · DNI 99000002');

    await vi.waitFor(() => expect(texto(linea())).toContain('No acepta mensajes'));
    expect(servidor.a('GET', '/pacientes/2/consentimiento-comunicaciones')).toHaveLength(1);
    expect(boton(linea(), 'Registrar consentimiento')).toBeDefined();
  });

  it('El odontólogo no ve la línea', () => {
    const componente = crearLineaConsentimiento({ api: servidor.api, visible: false, sesionVencida: () => false });
    document.body.replaceChildren(componente.contenedor);

    componente.mostrarPaciente(1);

    expect(componente.contenedor.hidden).toBe(true);
    expect(texto(componente.contenedor)).toBe('');
    expect(servidor.a('GET', '/pacientes/1/consentimiento-comunicaciones')).toHaveLength(0);
  });

  it('Registrar un consentimiento por teléfono', async () => {
    servidor.responder('PUT /pacientes/{id}/consentimiento-comunicaciones', () =>
      json(200, { otorgado: true, actualizadoEn: '2026-12-01T11:00:00Z', historial: [] }),
    );
    await abrirAnotar();
    await elegirPaciente(dialogo(), '99000002', 'Dos, Paciente · DNI 99000002');
    await vi.waitFor(() => expect(texto(linea())).toContain('No acepta mensajes'));
    const anotar = dialogo();
    campo<HTMLInputElement>(anotar, 'Hasta').value = '2026-12-10';

    boton(linea(), 'Registrar consentimiento').click();
    expect(dialogo()).not.toBe(anotar);
    expect(dialogo().querySelector('h2')?.textContent).toBe('Registrar consentimiento');
    campo(dialogo(), 'Por teléfono').click();
    boton(dialogo(), 'Registrar').click();

    await vi.waitFor(() => expect(dialogo()).toBe(anotar));
    expect(servidor.ultimo('PUT', '/pacientes/2/consentimiento-comunicaciones').cuerpo).toEqual({
      otorgado: true,
      medio: 'TELEFONO',
    });
    expect(texto(linea())).toContain('Acepta mensajes');
    expect(boton(linea(), 'Revocar')).toBeDefined();
    expect(campo<HTMLInputElement>(anotar, 'Hasta').value).toBe('2026-12-10');
  });

  it('Confirmar sin medio', async () => {
    await abrirAnotar();
    await elegirPaciente(dialogo(), '99000002', 'Dos, Paciente · DNI 99000002');
    await vi.waitFor(() => expect(texto(linea())).toContain('No acepta mensajes'));

    boton(linea(), 'Registrar consentimiento').click();
    boton(dialogo(), 'Registrar').click();

    expect(texto(dialogo())).toContain('Elegí cómo lo pidió el paciente.');
    expect(servidor.a('PUT', '/pacientes/2/consentimiento-comunicaciones')).toHaveLength(0);
  });

  it('Revocar pide el medio y usa el botón de peligro', async () => {
    servidor.responder('GET /pacientes/{id}/consentimiento-comunicaciones', () =>
      json(200, { otorgado: true, actualizadoEn: '2026-11-20T13:00:00Z', historial: [] }),
    );
    servidor.responder('PUT /pacientes/{id}/consentimiento-comunicaciones', () =>
      json(200, { otorgado: false, actualizadoEn: '2026-12-01T11:00:00Z', historial: [] }),
    );
    await abrirAnotar();
    await elegirPaciente(dialogo(), '99000001', 'Uno, Paciente · DNI 99000001');
    await vi.waitFor(() => expect(texto(linea())).toContain('Acepta mensajes'));

    boton(linea(), 'Revocar').click();
    expect(dialogo().querySelector('h2')?.textContent).toBe('Revocar consentimiento');
    expect(boton(dialogo(), 'Revocar').className).toContain('btn-peligro');
    campo(dialogo(), 'En persona').click();
    boton(dialogo(), 'Revocar').click();

    await vi.waitFor(() => expect(texto(linea())).toContain('No acepta mensajes'));
    expect(servidor.ultimo('PUT', '/pacientes/1/consentimiento-comunicaciones').cuerpo).toEqual({
      otorgado: false,
      medio: 'PRESENCIAL',
    });
  });

  it('Registrar y volver a guardar', async () => {
    let anotaciones = 0;
    servidor.responder('POST /lista-espera', () => {
      anotaciones += 1;
      return anotaciones === 1 ? json(409, { codigo: 'SIN_CONSENTIMIENTO' }) : json(201, {
        id: 9,
        pacienteId: 2,
        pacienteNombre: 'Paciente Dos',
        pacienteDni: '99000002',
        prestacionId: 1,
        prestacionNombre: 'Consulta',
        profesionalId: null,
        profesionalNombre: null,
        desde: '2026-12-01',
        hasta: '2026-12-15',
        estado: 'ACTIVA',
        inscriptaEn: '2026-12-01T11:00:00Z',
        oferta: null,
      });
    });
    servidor.responder('PUT /pacientes/{id}/consentimiento-comunicaciones', () =>
      json(200, { otorgado: true, actualizadoEn: '2026-12-01T11:00:00Z', historial: [] }),
    );
    await abrirAnotar();
    await elegirPaciente(dialogo(), '99000002', 'Dos, Paciente · DNI 99000002');
    const anotar = dialogo();
    boton(anotar, 'Anotar').click();
    await vi.waitFor(() =>
      expect(texto(anotar)).toContain('El paciente no aceptó recibir mensajes: no le podemos avisar las ofertas.'),
    );
    await vi.waitFor(() => expect(document.activeElement).toBe(boton(linea(), 'Registrar consentimiento')));

    boton(linea(), 'Registrar consentimiento').click();
    campo(dialogo(), 'Por teléfono').click();
    boton(dialogo(), 'Registrar').click();
    await vi.waitFor(() => expect(texto(linea())).toContain('Acepta mensajes'));
    expect(texto(anotar)).not.toContain('El paciente no aceptó recibir mensajes');
    boton(anotar, 'Anotar').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/lista-espera')).toHaveLength(2));
    const [primero, segundo] = servidor.a('POST', '/lista-espera');
    expect(segundo?.cuerpo).toEqual(primero?.cuerpo);
  });
});
