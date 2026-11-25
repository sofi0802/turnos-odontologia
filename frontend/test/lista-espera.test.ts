import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  abrirApp,
  boton,
  campo,
  elegirPaciente,
  errorDeCampo,
  escribir,
  json,
  ServidorFalso,
  texto,
  type AppDePrueba,
} from './apoyo.ts';

// Lista de espera del personal (spec frontend-lista-espera, C-15). Hoy es el martes 2026-12-01 a las 08:00 en el
// consultorio. Datos ficticios: "Consulta" (id 1) hace de la "Limpieza" del spec; la atienden Ana Ejemplo (1) y
// Bruno Prueba (2).

const SESION_ADMIN = { autenticado: true, username: 'admin', rol: 'ADMIN' };
const SESION_ODONTOLOGO = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };

function inscripcion(datos: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: 7,
    pacienteId: 1,
    pacienteNombre: 'Paciente Uno',
    pacienteDni: '99000001',
    prestacionId: 1,
    prestacionNombre: 'Consulta',
    profesionalId: 1,
    profesionalNombre: 'Ana Ejemplo',
    desde: '2026-11-30',
    hasta: '2026-12-14',
    inscriptaEn: '2026-11-30T09:00:00-03:00',
    estado: 'ACTIVA',
    oferta: null,
    ...datos,
  };
}

const DOS_CON_OFERTA = inscripcion({
  id: 4,
  pacienteId: 2,
  pacienteNombre: 'Paciente Dos',
  pacienteDni: '99000002',
  profesionalId: null,
  profesionalNombre: null,
  desde: '2026-11-28',
  hasta: '2026-12-12',
  inscriptaEn: '2026-11-28T10:00:00-03:00',
  oferta: { inicio: '2026-12-03T10:00:00-03:00', venceEn: '2026-12-01T10:00:00-03:00' },
});

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

function main(): HTMLElement {
  const encontrado = raiz().querySelector<HTMLElement>('main');
  if (encontrado === null) {
    throw new Error('No está el contenido');
  }
  return encontrado;
}

function dialogo(): HTMLDialogElement {
  const abierto = document.querySelector<HTMLDialogElement>('dialog[open]');
  if (abierto === null) {
    throw new Error('No hay un diálogo abierto');
  }
  return abierto;
}

function filas(): HTMLElement[] {
  return [...raiz().querySelectorAll<HTMLElement>('.le-fila')];
}

function enlacesDelMenu(): string[] {
  return [...raiz().querySelectorAll<HTMLAnchorElement>('aside nav[aria-label="Principal"] li:not([hidden]) a')].map(
    (a) => a.textContent ?? '',
  );
}

function avisos(): string {
  return [...main().querySelectorAll('[role="alert"], [role="status"]')].map((e) => e.textContent ?? '').join(' ');
}

async function abrirLista(lista: Record<string, unknown>[] = [DOS_CON_OFERTA, inscripcion()]): Promise<void> {
  servidor.responder('GET /lista-espera', () => json(200, lista));
  app = abrirApp(servidor, '#/lista-espera');
  await vi.waitFor(() => expect(servidor.a('GET', '/lista-espera').length).toBeGreaterThan(0));
  await vi.waitFor(() => expect(main().querySelector('[aria-busy]')).toBeNull());
}

async function abrirAlta(): Promise<void> {
  await abrirLista();
  boton(main(), 'Anotar paciente').click();
  await vi.waitFor(() => expect(campo<HTMLSelectElement>(dialogo(), 'Prestación').value).toBe('1'));
  await vi.waitFor(() => expect(campo<HTMLSelectElement>(dialogo(), 'Profesional').options.length).toBe(3));
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
  servidor = new ServidorFalso();
  servidor.responder('GET /public/prestaciones', () => json(200, [{ id: 1, nombre: 'Consulta', duracionMinutos: 30 }]));
  servidor.responder('GET /public/profesionales', () =>
    json(200, [
      { id: 1, nombre: 'Ana Ejemplo' },
      { id: 2, nombre: 'Bruno Prueba' },
    ]),
  );
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  document.body.replaceChildren();
  vi.useRealTimers();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});

describe('Página "Lista de espera" para administración y recepción', () => {
  it('Menú de la recepción con Lista de espera', async () => {
    app = abrirApp(servidor, '#/agenda');

    await vi.waitFor(() => expect(enlacesDelMenu()).toContain('Lista de espera'));
    const enlaces = enlacesDelMenu();
    expect(enlaces[enlaces.indexOf('Agenda') + 1]).toBe('Lista de espera');
  });

  it('Orden del menú del administrador', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_ADMIN));
    app = abrirApp(servidor, '#/agenda');

    // "Coberturas" (C-49) todavía no está en master: cuando llegue, va entre "Pacientes" y "Reportes".
    await vi.waitFor(() =>
      expect(enlacesDelMenu()).toEqual(['Agenda', 'Lista de espera', 'Pacientes', 'Reportes', 'Configuración']),
    );
  });

  it('La lista en orden con una oferta pendiente', async () => {
    await abrirLista();

    const [primera, segunda] = filas();
    expect(primera?.querySelector('.le-orden')?.textContent).toBe('1');
    expect(texto(primera ?? main())).toContain('Paciente Dos');
    expect(texto(primera ?? main())).toContain('DNI 99000002');
    expect(texto(primera ?? main())).toContain('Sin preferencia');
    expect(texto(primera ?? main())).toContain('Del 28/11 al 12/12');
    expect(texto(primera ?? main())).toContain('Anotado el 28/11');
    expect(primera?.querySelector('.le-oferta')?.textContent).toBe(
      'Oferta enviada: jueves 3 de diciembre a las 10:00, vence a las 10:00',
    );
    expect(segunda?.querySelector('.le-orden')?.textContent).toBe('2');
    expect(texto(segunda ?? main())).toContain('Paciente Uno');
    expect(texto(segunda ?? main())).toContain('Ana Ejemplo');
    expect(segunda?.querySelector('.le-oferta')).toBeNull();
  });

  it('Lista vacía', async () => {
    await abrirLista([]);

    expect(texto(main())).toContain('No hay pacientes en la lista de espera.');
    expect(main().querySelector('.estado-vacio button')?.textContent).toBe('Anotar paciente');
  });

  it('El odontólogo abre la dirección', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_ODONTOLOGO));
    app = abrirApp(servidor, '#/lista-espera');

    await vi.waitFor(() => expect(texto(main())).toContain('Esta sección es solo para administración y recepción.'));
    expect(enlacesDelMenu()).not.toContain('Lista de espera');
    expect(servidor.a('GET', '/lista-espera')).toHaveLength(0);
  });

  it('Sin sesión lleva a Mi cuenta', async () => {
    servidor.sinSesion();
    app = abrirApp(servidor, '#/lista-espera');

    await vi.waitFor(() => expect(main().querySelector('h1')?.textContent).toBe('Mi cuenta'));
    expect(servidor.a('GET', '/lista-espera')).toHaveLength(0);
  });
});

describe('Anotar a un paciente desde la recepción', () => {
  it('Anotar sin preferencia', async () => {
    servidor.responder('POST /lista-espera', () =>
      json(201, inscripcion({ id: 9, profesionalId: null, profesionalNombre: null, desde: '2026-12-01', hasta: '2026-12-15' })),
    );
    await abrirAlta();
    expect(campo(dialogo(), 'Desde').value).toBe('2026-12-01');
    expect(campo(dialogo(), 'Hasta').value).toBe('2026-12-15');

    await elegirPaciente(dialogo(), '99000001', 'Uno, Paciente · DNI 99000001');
    escribir(campo<HTMLSelectElement>(dialogo(), 'Profesional'), '');
    boton(dialogo(), 'Anotar').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/lista-espera')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/lista-espera').cuerpo).toEqual({
      pacienteId: 1,
      prestacionId: 1,
      profesionalId: null,
      desde: '2026-12-01',
      hasta: '2026-12-15',
    });
    await vi.waitFor(() => expect(document.querySelector('dialog[open]')).toBeNull());
    expect(avisos()).toContain('Anotaste a Paciente Uno en la lista de espera.');
    expect(filas()).toHaveLength(3);
    expect(filas()[2]?.querySelector('.le-orden')?.textContent).toBe('3');
  });

  it('Solo los profesionales habilitados para la prestación', async () => {
    await abrirAlta();

    expect([...campo<HTMLSelectElement>(dialogo(), 'Profesional').options].map((o) => o.textContent)).toEqual([
      'Sin preferencia',
      'Ana Ejemplo',
      'Bruno Prueba',
    ]);
    expect(servidor.ultimo('GET', '/public/profesionales').query.get('prestacionId')).toBe('1');
  });

  it('Rango de más de 60 días', async () => {
    await abrirAlta();
    await elegirPaciente(dialogo(), '99000001', 'Uno, Paciente · DNI 99000001');

    escribir(campo(dialogo(), 'Hasta'), '2027-02-15');
    boton(dialogo(), 'Anotar').click();

    expect(errorDeCampo(dialogo(), 'Hasta')).toBe('El rango puede ser de hasta 60 días.');
    expect(servidor.a('POST', '/lista-espera')).toHaveLength(0);
  });

  it('Sin paciente elegido no se envía', async () => {
    await abrirAlta();

    boton(dialogo(), 'Anotar').click();

    expect(errorDeCampo(dialogo(), 'Paciente')).toBe('Elegí un paciente de la lista.');
    expect(servidor.a('POST', '/lista-espera')).toHaveLength(0);
  });

  it('Paciente sin consentimiento', async () => {
    servidor.responder('POST /lista-espera', () => json(409, { codigo: 'SIN_CONSENTIMIENTO', detail: 'detalle interno' }));
    await abrirAlta();
    await elegirPaciente(dialogo(), '99000003', 'Ficticia, Carla · DNI 99000003');

    boton(dialogo(), 'Anotar').click();

    await vi.waitFor(() =>
      expect(texto(dialogo())).toContain('El paciente no aceptó recibir mensajes: no le podemos avisar las ofertas.'),
    );
    expect(texto(dialogo())).not.toContain('detalle interno');
    expect(dialogo().open).toBe(true);
  });

  it('Ya anotado para la prestación', async () => {
    servidor.responder('POST /lista-espera', () => json(409, { codigo: 'YA_EN_LISTA_DE_ESPERA' }));
    await abrirAlta();
    await elegirPaciente(dialogo(), '99000001', 'Uno, Paciente · DNI 99000001');

    boton(dialogo(), 'Anotar').click();

    await vi.waitFor(() =>
      expect(texto(dialogo())).toContain('El paciente ya está en la lista de espera para esta prestación.'),
    );
  });
});

describe('Dar de baja desde la recepción', () => {
  it('Baja confirmada', async () => {
    servidor.responder('POST /lista-espera/{id}/baja', () => json(204));
    await abrirLista();

    boton(filas()[1] ?? main(), 'Dar de baja').click();
    expect(dialogo().querySelector('h2')?.textContent).toBe('¿Dar de baja a Paciente Uno de la lista de espera?');
    boton(dialogo(), 'Dar de baja').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/lista-espera/7/baja')).toHaveLength(1));
    await vi.waitFor(() => expect(filas()).toHaveLength(1));
    expect(filas().map((f) => texto(f))).not.toContainEqual(expect.stringContaining('Paciente Uno'));
    expect(avisos()).toContain('Diste de baja a Paciente Uno.');
  });

  it('Baja cancelada', async () => {
    await abrirLista();

    boton(filas()[1] ?? main(), 'Dar de baja').click();
    boton(dialogo(), 'Volver').click();

    expect(document.querySelector('dialog[open]')).toBeNull();
    expect(servidor.pedidos.filter((p) => p.metodo === 'POST')).toHaveLength(0);
    expect(filas()).toHaveLength(2);
  });

  it('Con una oferta pendiente avisa que pasa al siguiente', async () => {
    await abrirLista();

    boton(filas()[0] ?? main(), 'Dar de baja').click();

    expect(texto(dialogo())).toContain('Tiene una oferta pendiente: se le ofrece al siguiente de la lista.');
  });

  it('La inscripción ya no estaba activa', async () => {
    servidor.responder('POST /lista-espera/{id}/baja', () => json(409, { codigo: 'INSCRIPCION_NO_ACTIVA' }));
    await abrirLista();

    boton(filas()[1] ?? main(), 'Dar de baja').click();
    boton(dialogo(), 'Dar de baja').click();

    await vi.waitFor(() => expect(filas()).toHaveLength(1));
    expect(avisos()).toContain('Esta inscripción ya no estaba activa.');
  });
});

describe('Plazo para aceptar en Configuración', () => {
  async function abrirMargen(sesion: Record<string, unknown>): Promise<void> {
    servidor.responder('GET /public/sesion', () => json(200, sesion));
    servidor.responder('GET /configuracion/margen', () => json(200, { minutos: 10 }));
    app = abrirApp(servidor, '#/configuracion');
    await vi.waitFor(() => expect(boton(main(), 'Margen')).toBeDefined());
    boton(main(), 'Margen').click();
    await vi.waitFor(() => expect(texto(main())).toContain('Margen vigente'));
  }

  function plazo(): HTMLElement {
    const seccion = main().querySelector<HTMLElement>('.le-plazo');
    if (seccion === null) {
      throw new Error('No está el plazo');
    }
    return seccion;
  }

  it('El administrador cambia el plazo', async () => {
    servidor.responder('PUT /configuracion/lista-espera', () => json(200, { plazoHoras: 4 }));
    await abrirMargen(SESION_ADMIN);
    await vi.waitFor(() => expect(texto(plazo())).toContain('Plazo vigente: 2 horas'));
    expect(texto(plazo())).toContain('Plazo para aceptar una oferta de la lista de espera');

    escribir(campo(plazo(), 'Nuevo plazo'), '4');
    boton(plazo(), 'Guardar plazo').click();

    await vi.waitFor(() => expect(servidor.a('PUT', '/configuracion/lista-espera')).toHaveLength(1));
    expect(servidor.ultimo('PUT', '/configuracion/lista-espera').cuerpo).toEqual({ plazoHoras: 4 });
    await vi.waitFor(() => expect(texto(plazo())).toContain('Guardaste el plazo: 4 horas.'));
    expect(texto(plazo())).toContain('Plazo vigente: 4 horas');
  });

  it('Plazo fuera de rango', async () => {
    await abrirMargen(SESION_ADMIN);
    await vi.waitFor(() => expect(boton(plazo(), 'Guardar plazo')).toBeDefined());

    escribir(campo(plazo(), 'Nuevo plazo'), '30');
    boton(plazo(), 'Guardar plazo').click();

    expect(errorDeCampo(plazo(), 'Nuevo plazo')).toBe('Ingresá un número entero de 1 a 24.');
    expect(servidor.a('PUT', '/configuracion/lista-espera')).toHaveLength(0);
  });

  it('La recepción ve el plazo sin editarlo', async () => {
    await abrirMargen({ autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' });

    await vi.waitFor(() => expect(texto(plazo())).toContain('Plazo vigente: 2 horas'));
    expect([...plazo().querySelectorAll('button')].map((b) => b.textContent)).not.toContain('Guardar plazo');
  });

  it('El odontólogo no lo ve', async () => {
    await abrirMargen(SESION_ODONTOLOGO);

    expect(main().querySelector('.le-plazo')).toBeNull();
    expect(servidor.a('GET', '/configuracion/lista-espera')).toHaveLength(0);
  });
});
