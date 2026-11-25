import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { iniciarApp } from '../src/app.ts';
import { olvidarTokenDeOferta, traducirEntrada } from '../src/shared/ui/entrada.ts';
import type { Router } from '../src/shared/ui/router.ts';
import { boton, campo, errorDeCampo, escribir, json, ServidorFalso, texto } from './apoyo.ts';

// Lista de espera del paciente y pantalla de la oferta (spec frontend-lista-espera, C-15). Hoy es el martes
// 2026-12-01 a las 08:00 en el consultorio; "Consulta" (id 1) hace de la "Limpieza" del spec y la atienden
// Ana Ejemplo (1) y Bruno Prueba (2). Todos los datos son ficticios.

const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };

let servidor: ServidorFalso;
let raiz: HTMLElement;
let router: Router | undefined;

function abrirEn(direccion: string): void {
  raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  history.replaceState(null, '', direccion);
  traducirEntrada();
  router = iniciarApp(raiz, servidor.api);
}

function main(): HTMLElement {
  const encontrado = raiz.querySelector<HTMLElement>('main');
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

function titulo(): string {
  return main().querySelector('h1')?.textContent ?? '';
}

function posts(): string[] {
  return servidor.pedidos.filter((p) => p.metodo === 'POST').map((p) => p.ruta);
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
  servidor = new ServidorFalso();
  servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
  servidor.responder('GET /public/prestaciones', () => json(200, [{ id: 1, nombre: 'Consulta', duracionMinutos: 30 }]));
  servidor.responder('GET /public/profesionales', () =>
    json(200, [
      { id: 1, nombre: 'Ana Ejemplo' },
      { id: 2, nombre: 'Bruno Prueba' },
    ]),
  );
  servidor.responder('GET /public/disponibilidad', () => json(200, []));
  servidor.responder('GET /paciente/turnos', () => json(200, []));
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
  sessionStorage.clear();
});

afterEach(() => {
  router?.detener();
  router = undefined;
  olvidarTokenDeOferta();
  document.body.replaceChildren();
  vi.useRealTimers();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  sessionStorage.clear();
});

// ---------- reserva ----------

function panel(): HTMLElement {
  const encontrado = main().querySelector<HTMLElement>('.panel');
  if (encontrado === null) {
    throw new Error('No está el panel de la reserva');
  }
  return encontrado;
}

/** Llega al paso 2 con "Consulta", la Dra. Ana Ejemplo y el jueves 3 de diciembre. */
async function diaSinHorarios(): Promise<void> {
  abrirEn('/#/reserva');
  await vi.waitFor(() => expect(panel().querySelector('h2')?.textContent).toBe('¿Qué necesitás?'));
  campo(panel(), 'Prestación').focus();
  const opcion = [...panel().querySelectorAll<HTMLElement>('[role="option"]')].find(
    (o) => o.textContent === 'Consulta · 30 minutos',
  );
  opcion?.click();
  boton(panel(), 'Continuar').click();
  await vi.waitFor(() => expect(panel().querySelector('h2')?.textContent).toBe('Elegí día y horario'));
  const ana = await vi.waitFor(() => {
    const entrada = [...panel().querySelectorAll<HTMLInputElement>('input[name="profesional"]')].find(
      (e) => e.closest('label')?.querySelector('.opcion-nombre')?.textContent === 'Ana Ejemplo',
    );
    expect(entrada).toBeDefined();
    return entrada;
  });
  ana?.click();
  escribir(campo(panel(), 'Día'), '2026-12-03');
  await vi.waitFor(() => expect(servidor.ultimo('GET', '/public/disponibilidad').query.get('fecha')).toBe('2026-12-03'));
  await vi.waitFor(() => expect(boton(panel(), 'Anotarme en la lista de espera')).toBeDefined());
}

describe('Anotarse desde la reserva', () => {
  it('El paciente se anota desde un día sin horarios', async () => {
    servidor.responder('POST /paciente/lista-espera', () =>
      json(201, {
        id: 5,
        prestacionId: 1,
        prestacionNombre: 'Consulta',
        profesionalId: 1,
        profesionalNombre: 'Ana Ejemplo',
        desde: '2026-12-03',
        hasta: '2026-12-17',
        inscriptaEn: '2026-12-01T08:00:00-03:00',
        estado: 'ACTIVA',
        oferta: null,
      }),
    );
    await diaSinHorarios();
    expect(texto(panel())).toContain('No hay horarios libres para ese día. Probá con otra fecha o con otro profesional.');

    boton(panel(), 'Anotarme en la lista de espera').click();
    await vi.waitFor(() => expect(dialogo().querySelector('h2')?.textContent).toBe('Anotarme en la lista de espera'));
    expect(texto(dialogo())).toContain('Consulta');
    expect(texto(dialogo())).toContain('Ana Ejemplo');
    expect(campo(dialogo(), 'Desde').value).toBe('2026-12-03');
    expect(campo(dialogo(), 'Hasta').value).toBe('2026-12-17');
    boton(dialogo(), 'Anotarme').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/paciente/lista-espera')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/paciente/lista-espera').cuerpo).toEqual({
      prestacionId: 1,
      profesionalId: 1,
      desde: '2026-12-03',
      hasta: '2026-12-17',
    });
    await vi.waitFor(() =>
      expect(texto(dialogo())).toContain('Listo, te anotamos. Si se libera un horario, te avisamos por mensaje.'),
    );
    expect(dialogo().querySelector('a[href="#/mis-turnos"]')).not.toBeNull();
  });

  it('Ya estaba anotado', async () => {
    servidor.responder('POST /paciente/lista-espera', () => json(409, { codigo: 'YA_EN_LISTA_DE_ESPERA' }));
    await diaSinHorarios();

    boton(panel(), 'Anotarme en la lista de espera').click();
    await vi.waitFor(() => expect(boton(dialogo(), 'Anotarme')).toBeDefined());
    boton(dialogo(), 'Anotarme').click();

    await vi.waitFor(() => expect(texto(dialogo())).toContain('Ya estás en la lista de espera para esta prestación.'));
    expect(dialogo().querySelector('a[href="#/mis-turnos"]')).not.toBeNull();
  });

  it('El paciente no dio el consentimiento de comunicaciones', async () => {
    servidor.responder('POST /paciente/lista-espera', () => json(409, { codigo: 'SIN_CONSENTIMIENTO' }));
    await diaSinHorarios();

    boton(panel(), 'Anotarme en la lista de espera').click();
    await vi.waitFor(() => expect(boton(dialogo(), 'Anotarme')).toBeDefined());
    boton(dialogo(), 'Anotarme').click();

    await vi.waitFor(() =>
      expect(texto(dialogo())).toContain(
        'Para anotarte tenés que aceptar recibir mensajes del consultorio: así te avisamos cuando se libere un horario.',
      ),
    );
    expect(dialogo().querySelector('h2')?.textContent).toBe('Anotarme en la lista de espera');
    expect(boton(dialogo(), 'Anotarme').disabled).toBe(false);
  });

  it('Valida el rango como la recepción', async () => {
    await diaSinHorarios();
    boton(panel(), 'Anotarme en la lista de espera').click();
    await vi.waitFor(() => expect(boton(dialogo(), 'Anotarme')).toBeDefined());

    escribir(campo(dialogo(), 'Hasta'), '2027-02-15');
    boton(dialogo(), 'Anotarme').click();

    expect(errorDeCampo(dialogo(), 'Hasta')).toBe('El rango puede ser de hasta 60 días.');
    expect(servidor.a('POST', '/paciente/lista-espera')).toHaveLength(0);
  });

  it('Un día con horarios no ofrece la lista', async () => {
    servidor.responder('GET /public/disponibilidad', () =>
      json(200, [{ inicio: '2026-12-01T10:00:00-03:00', fin: '2026-12-01T10:30:00-03:00' }]),
    );
    abrirEn('/#/reserva');
    await vi.waitFor(() => expect(panel().querySelector('h2')?.textContent).toBe('¿Qué necesitás?'));
    campo(panel(), 'Prestación').focus();
    [...panel().querySelectorAll<HTMLElement>('[role="option"]')].find((o) => o.textContent === 'Consulta · 30 minutos')?.click();
    boton(panel(), 'Continuar').click();

    await vi.waitFor(() => expect(panel().querySelectorAll('.chip').length).toBeGreaterThan(0));
    expect([...panel().querySelectorAll('button')].map((b) => b.textContent)).not.toContain(
      'Anotarme en la lista de espera',
    );
  });

  it('Sin sesión', async () => {
    servidor.sinSesion();
    await diaSinHorarios();

    boton(panel(), 'Anotarme en la lista de espera').click();

    await vi.waitFor(() => expect(titulo()).toBe('Mi cuenta'));
    expect(servidor.a('POST', '/paciente/lista-espera')).toHaveLength(0);
  });
});

// ---------- Mis turnos ----------

const INSCRIPCION_CON_OFERTA = {
  id: 5,
  prestacionId: 1,
  prestacionNombre: 'Consulta',
  profesionalId: null,
  profesionalNombre: null,
  desde: '2026-12-01',
  hasta: '2026-12-15',
  inscriptaEn: '2026-11-30T09:00:00-03:00',
  estado: 'ACTIVA',
  oferta: { inicio: '2026-12-03T10:00:00-03:00', venceEn: '2026-12-01T10:00:00-03:00' },
};

function seccionEspera(): HTMLElement | null {
  return main().querySelector<HTMLElement>('section[aria-label="Lista de espera"]');
}

async function abrirMisTurnos(): Promise<void> {
  abrirEn('/#/mis-turnos');
  await vi.waitFor(() => expect(titulo()).toBe('Mis turnos'));
  await vi.waitFor(() => expect(main().querySelector('.pila[aria-busy]')).toBeNull());
}

describe('Mi lista de espera en Mis turnos', () => {
  it('Una inscripción con oferta pendiente', async () => {
    servidor.responder('GET /paciente/lista-espera', () => json(200, [INSCRIPCION_CON_OFERTA]));

    await abrirMisTurnos();

    const seccion = seccionEspera();
    expect(seccion).not.toBeNull();
    const contenido = texto(seccion ?? main());
    expect(contenido).toContain('Consulta');
    expect(contenido).toContain('Sin preferencia');
    expect(contenido).toContain('del martes 1 de diciembre al martes 15 de diciembre');
    expect(contenido).toContain(
      'Te enviamos un horario: jueves 3 de diciembre a las 10:00. Revisá tu WhatsApp o tu email.',
    );
  });

  it('Va después de "Próximos turnos"', async () => {
    servidor.responder('GET /paciente/turnos', () =>
      json(200, [
        {
          numeroReserva: 'R-0007',
          inicio: '2026-12-09T11:00:00-03:00',
          fin: '2026-12-09T11:30:00-03:00',
          estado: 'RESERVADO',
          prestacion: { id: 1, nombre: 'Consulta' },
          profesional: { id: 2, nombre: 'Bruno Prueba' },
          modificableOnline: true,
        },
      ]),
    );
    servidor.responder('GET /paciente/lista-espera', () => json(200, [INSCRIPCION_CON_OFERTA]));

    await abrirMisTurnos();

    const secciones = [...main().querySelectorAll('section[aria-label]')].map((s) => s.getAttribute('aria-label'));
    expect(secciones).toEqual(['Próximos turnos', 'Lista de espera']);
  });

  it('Salir de la lista', async () => {
    let lista: unknown[] = [INSCRIPCION_CON_OFERTA];
    servidor.responder('GET /paciente/lista-espera', () => json(200, lista));
    servidor.responder('POST /paciente/lista-espera/{id}/baja', () => {
      lista = [];
      return json(204);
    });
    await abrirMisTurnos();

    boton(seccionEspera() ?? main(), 'Salir de la lista').click();
    boton(dialogo(), 'Salir de la lista').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/paciente/lista-espera/5/baja')).toHaveLength(1));
    await vi.waitFor(() => expect(seccionEspera()).toBeNull());
  });

  it('Sin inscripciones no se muestra la sección', async () => {
    await abrirMisTurnos();

    expect(seccionEspera()).toBeNull();
    expect(servidor.a('GET', '/paciente/lista-espera')).toHaveLength(1);
  });

  it('Falla la lista de espera', async () => {
    servidor.responder('GET /paciente/turnos', () =>
      json(200, [
        {
          numeroReserva: 'R-0007',
          inicio: '2026-12-09T11:00:00-03:00',
          fin: '2026-12-09T11:30:00-03:00',
          estado: 'RESERVADO',
          prestacion: { id: 1, nombre: 'Consulta' },
          profesional: { id: 2, nombre: 'Bruno Prueba' },
          modificableOnline: true,
        },
      ]),
    );
    servidor.responder('GET /paciente/lista-espera', () => json(500));

    await abrirMisTurnos();

    expect(main().querySelector('section[aria-label="Próximos turnos"]')).not.toBeNull();
    expect(texto(main())).toContain('No pudimos cargar tu lista de espera.');
  });
});

// ---------- oferta ----------

const OFERTA_PENDIENTE = {
  fecha: '2026-12-03',
  hora: '10:00',
  profesional: 'Ana Ejemplo',
  venceEn: '2026-12-01T10:00:00-03:00',
  estado: 'PENDIENTE',
};

async function abrirOferta(respuesta: () => Response = () => json(200, OFERTA_PENDIENTE)): Promise<void> {
  servidor.sinSesion();
  servidor.responder('GET /public/ofertas/xyz789', respuesta);
  abrirEn('/oferta/xyz789');
  await vi.waitFor(() => expect(titulo()).toBe('Turno disponible'));
  await vi.waitFor(() => expect(main().querySelector('.cargando')).toBeNull());
}

describe('Pantalla de la oferta', () => {
  it('Abrir el link de la oferta', async () => {
    await abrirOferta();

    expect(texto(main())).toContain('Se liberó un turno el jueves 3 de diciembre a las 10:00 con Ana Ejemplo.');
    expect(texto(main())).toContain('Podés aceptarlo hasta el martes 1 de diciembre a las 10:00.');
    expect(window.location.hash).toBe('#/oferta');
    expect(window.location.href).not.toContain('xyz789');
    expect(texto(raiz)).not.toContain('xyz789');
    expect(posts()).toHaveLength(0);
    expect(boton(main(), 'Aceptar el turno')).toBeDefined();
    expect(boton(main(), 'No me interesa')).toBeDefined();
  });

  it('Link inválido', async () => {
    await abrirOferta(() => json(404, { codigo: 'OFERTA_INEXISTENTE' }));

    expect(texto(main())).toContain('El link no es válido. Revisá que esté completo.');
    expect(main().querySelectorAll('.panel button')).toHaveLength(0);
  });

  it('Oferta que ya no está pendiente', async () => {
    await abrirOferta(() => json(200, { ...OFERTA_PENDIENTE, estado: 'VENCIDA' }));

    expect(texto(main())).toContain('Esta oferta venció. Seguís en la lista de espera.');
    expect(main().querySelectorAll('.panel button')).toHaveLength(0);
  });
});

describe('Responder la oferta', () => {
  it('Aceptar', async () => {
    servidor.responder('POST /public/ofertas/xyz789/aceptar', () =>
      json(200, { fecha: '2026-12-03', hora: '10:00', profesional: 'Ana Ejemplo', numeroReserva: 'R-0042' }),
    );
    await abrirOferta();

    boton(main(), 'Aceptar el turno').click();
    boton(main(), 'Aceptar el turno').click();

    await vi.waitFor(() =>
      expect(texto(main())).toContain(
        'Listo, tu turno quedó reservado para el jueves 3 de diciembre a las 10:00 con Ana Ejemplo. Tu número de reserva es R-0042.',
      ),
    );
    expect(servidor.a('POST', '/public/ofertas/xyz789/aceptar')).toHaveLength(1);
    expect(main().querySelectorAll('.panel button')).toHaveLength(0);
  });

  it('Pide el token CSRF si todavía no existe', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
    servidor.responder('POST /public/ofertas/xyz789/rechazar', () => json(200, { estado: 'RECHAZADA' }));
    await abrirOferta();
    const sesionesAntes = servidor.a('GET', '/public/sesion').length;

    boton(main(), 'No me interesa').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/public/ofertas/xyz789/rechazar')).toHaveLength(1));
    expect(servidor.a('GET', '/public/sesion').length).toBe(sesionesAntes + 1);
  });

  it('El horario ya se ocupó', async () => {
    servidor.responder('POST /public/ofertas/xyz789/aceptar', () => json(409, { codigo: 'OFERTA_NO_DISPONIBLE' }));
    await abrirOferta();

    boton(main(), 'Aceptar el turno').click();

    await vi.waitFor(() => expect(texto(main())).toContain('Ese horario ya se ocupó. Seguís en la lista de espera.'));
    expect(main().querySelectorAll('.panel button')).toHaveLength(0);
  });

  it('Ya respondida', async () => {
    servidor.responder('POST /public/ofertas/xyz789/aceptar', () => json(409, { codigo: 'OFERTA_YA_RESPONDIDA' }));
    await abrirOferta();

    boton(main(), 'Aceptar el turno').click();

    await vi.waitFor(() => expect(texto(main())).toContain('Ya respondiste esta oferta.'));
  });

  it('No me interesa', async () => {
    servidor.responder('POST /public/ofertas/xyz789/rechazar', () => json(200, { estado: 'RECHAZADA' }));
    await abrirOferta();

    boton(main(), 'No me interesa').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/public/ofertas/xyz789/rechazar')).toHaveLength(1));
    await vi.waitFor(() =>
      expect(texto(main())).toContain('Listo, se lo ofrecemos a otra persona. Seguís en la lista de espera.'),
    );
    expect(servidor.a('POST', '/public/ofertas/xyz789/aceptar')).toHaveLength(0);
  });
});
