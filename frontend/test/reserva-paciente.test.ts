import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { abrirApp, boton, campo, escribir, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';

// Datos ficticios: hoy es el martes 2026-12-01 en la zona del consultorio; "Consulta" (id 1) dura 30 minutos
// y la atienden Ana Ejemplo (id 1) y Bruno Prueba (id 2).
const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };
const SESION_RECEPCION = { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' };
const PRESTACIONES_PUBLICAS = [
  { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
  { id: 2, nombre: 'Limpieza dental', duracionMinutos: 45 },
  { id: 3, nombre: 'Extracción', duracionMinutos: 30 },
];
const PROFESIONALES_PUBLICOS = [
  { id: 1, nombre: 'Ana Ejemplo' },
  { id: 2, nombre: 'Bruno Prueba' },
];

function horario(hora: string, fecha = '2026-12-01'): { inicio: string; fin: string } {
  const [h, m] = hora.split(':').map(Number);
  const finMinutos = (h ?? 0) * 60 + (m ?? 0) + 30;
  const fin = `${String(Math.floor(finMinutos / 60)).padStart(2, '0')}:${String(finMinutos % 60).padStart(2, '0')}`;
  return { inicio: `${fecha}T${hora}:00-03:00`, fin: `${fecha}T${fin}:00-03:00` };
}

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

function panel(): HTMLElement {
  const encontrado = raiz().querySelector<HTMLElement>('main .panel');
  if (encontrado === null) {
    throw new Error('No está el panel de la reserva');
  }
  return encontrado;
}

function pasoActual(): string {
  return panel().querySelector('h2')?.textContent ?? '';
}

function alerta(): string {
  return [...raiz().querySelectorAll('main [role="alert"], main [role="status"]')]
    .map((e) => e.textContent ?? '')
    .join(' ');
}

function chips(): HTMLButtonElement[] {
  return [...panel().querySelectorAll<HTMLButtonElement>('.chip')];
}

function chip(hora: string): HTMLButtonElement {
  const encontrado = chips().find((b) => b.textContent === hora);
  if (encontrado === undefined) {
    throw new Error(`No está el horario ${hora}`);
  }
  return encontrado;
}

function opcionesDeProfesional(): string[] {
  return [...panel().querySelectorAll('input[name="profesional"]')].map(
    (entrada) => entrada.closest('label')?.querySelector('.opcion-nombre')?.textContent ?? '',
  );
}

function profesionalElegido(): string {
  const elegido = panel().querySelector<HTMLInputElement>('input[name="profesional"]:checked');
  return elegido?.closest('label')?.querySelector('.opcion-nombre')?.textContent ?? '';
}

function elegirProfesional(nombre: string): void {
  const entrada = [...panel().querySelectorAll<HTMLInputElement>('input[name="profesional"]')].find(
    (e) => e.closest('label')?.querySelector('.opcion-nombre')?.textContent === nombre,
  );
  if (entrada === undefined) {
    throw new Error(`No está el profesional ${nombre}`);
  }
  entrada.click();
}

function selectorDePrestacion(): HTMLInputElement {
  return campo(panel(), 'Prestación');
}

/** Abre el selector "Prestación" y toca la opción con ese texto (por ejemplo, "Consulta · 30 minutos"). */
function elegirPrestacion(opcion: string): void {
  selectorDePrestacion().focus();
  const encontrada = [...panel().querySelectorAll<HTMLElement>('[role="option"]')].find((o) => o.textContent === opcion);
  if (encontrada === undefined) {
    throw new Error(`No está la prestación ${opcion}`);
  }
  encontrada.click();
}

function resumen(): string {
  return texto(panel().querySelector('dl') ?? panel());
}

async function abrirReserva(): Promise<void> {
  app = abrirApp(servidor, '#/reserva');
  await vi.waitFor(() => expect(pasoActual()).toBe('¿Qué necesitás?'));
}

/** Del paso 1 al 2 con "Consulta" y espera los horarios. */
async function irAlPaso2(): Promise<void> {
  await abrirReserva();
  elegirPrestacion('Consulta · 30 minutos');
  boton(panel(), 'Continuar').click();
  await vi.waitFor(() => expect(pasoActual()).toBe('Elegí día y horario'));
  await vi.waitFor(() => expect(panel().querySelector('[aria-busy]')).toBeNull());
}

/** Llega al resumen de "Consulta" del 2026-12-01 a las 10:30 con "Sin preferencia". */
async function irAlResumen(): Promise<void> {
  await irAlPaso2();
  chip('10:30').click();
  boton(panel(), 'Continuar').click();
  expect(pasoActual()).toBe('Confirmá tu turno');
}

function disponibilidadPedida(): URLSearchParams[] {
  return servidor.a('GET', '/public/disponibilidad').map((p) => p.query);
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
  servidor = new ServidorFalso();
  servidor.sinSesion();
  servidor.responder('GET /public/prestaciones', () => json(200, PRESTACIONES_PUBLICAS));
  servidor.responder('GET /public/profesionales', () => json(200, PROFESIONALES_PUBLICOS));
  servidor.responder('GET /public/disponibilidad', () => json(200, [horario('10:00'), horario('10:30')]));
  servidor.responder('GET /paciente/turnos', () => json(200, []));
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
  sessionStorage.clear();
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  vi.useRealTimers();
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  sessionStorage.clear();
});

function conSesionDePaciente(): void {
  servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
}

describe('Reserva en tres pasos', () => {
  it('Pasar del paso 1 al 2', async () => {
    await abrirReserva();
    expect(texto(panel())).toContain('Paso 1 de 3');

    elegirPrestacion('Consulta · 30 minutos');
    boton(panel(), 'Continuar').click();

    expect(pasoActual()).toBe('Elegí día y horario');
    expect(texto(panel())).toContain('Paso 2 de 3');
  });

  it('Continuar sin elegir', async () => {
    await abrirReserva();

    boton(panel(), 'Continuar').click();

    expect(pasoActual()).toBe('¿Qué necesitás?');
    expect(alerta()).toContain('Elegí una prestación.');
  });

  it('Volver sin perder lo elegido', async () => {
    await irAlPaso2();
    await vi.waitFor(() => expect(opcionesDeProfesional()).toContain('Bruno Prueba'));
    elegirProfesional('Bruno Prueba');

    boton(panel(), 'Volver').click();
    expect(selectorDePrestacion().value).toBe('Consulta');
    boton(panel(), 'Continuar').click();

    expect(pasoActual()).toBe('Elegí día y horario');
    expect(profesionalElegido()).toBe('Bruno Prueba');
  });
});

describe('Elección de la prestación', () => {
  it('Prestaciones a elegir', async () => {
    await abrirReserva();

    selectorDePrestacion().focus();

    const ofrecidas = [...panel().querySelectorAll('[role="option"]')].map((o) => o.textContent);
    expect(ofrecidas).toContain('Consulta · 30 minutos');
    expect(panel().querySelector('input[type="radio"][name="prestacion"]')).toBeNull();
  });

  it('el paso 1 explica cómo elegir', async () => {
    await abrirReserva();

    expect(texto(panel())).toContain('Buscá la prestación o elegila de la lista. Después elegís el día y el horario.');
  });

  it('Elegir con el toque', async () => {
    await abrirReserva();
    selectorDePrestacion().focus();

    panel().querySelector<HTMLElement>('[role="option"]')?.click();

    expect(selectorDePrestacion().value).toBe('Consulta');
    boton(panel(), 'Continuar').click();
    expect(pasoActual()).toBe('Elegí día y horario');
    expect(texto(panel())).toContain('Consulta · 30 minutos');
  });

  it('Cambiar el texto quita la elección', async () => {
    await abrirReserva();
    elegirPrestacion('Consulta · 30 minutos');

    escribir(selectorDePrestacion(), 'Consultas');
    boton(panel(), 'Continuar').click();

    expect(pasoActual()).toBe('¿Qué necesitás?');
    expect(alerta()).toContain('Elegí una prestación.');
  });

  it('Lo que se pide en el paso 2 no cambia', async () => {
    await abrirReserva();
    elegirPrestacion('Limpieza dental · 45 minutos');

    boton(panel(), 'Continuar').click();

    await vi.waitFor(() => expect(pasoActual()).toBe('Elegí día y horario'));
    expect(servidor.ultimo('GET', '/public/profesionales').query.get('prestacionId')).toBe('2');
    expect(texto(panel())).toContain('Limpieza dental · 45 minutos');
  });

  it('sin prestaciones avisa y no ofrece Continuar', async () => {
    servidor.responder('GET /public/prestaciones', () => json(500));
    app = abrirApp(servidor, '#/reserva');

    await vi.waitFor(() =>
      expect(alerta()).toContain('No pudimos cargar las prestaciones. Probá de nuevo en unos minutos.'),
    );
    expect(() => boton(panel(), 'Continuar')).toThrow();
  });
});

describe('Elección del profesional y la fecha', () => {
  it('Profesionales de la prestación elegida', async () => {
    await irAlPaso2();

    expect(servidor.ultimo('GET', '/public/profesionales').query.get('prestacionId')).toBe('1');
    await vi.waitFor(() => expect(opcionesDeProfesional()).toEqual(['Sin preferencia', 'Ana Ejemplo', 'Bruno Prueba']));
    expect(profesionalElegido()).toBe('Sin preferencia');
  });

  it('Fecha inicial y días pasados', async () => {
    await irAlPaso2();
    const fecha = campo(panel(), 'Día');

    expect(fecha.value).toBe('2026-12-01');
    expect(fecha.min).toBe('2026-12-01');
    escribir(fecha, '2026-11-30');
    expect(fecha.value).toBe('2026-12-01');
  });
});

describe('Horarios libres del día', () => {
  it('Horarios sin preferencia', async () => {
    await irAlPaso2();

    const [consulta] = disponibilidadPedida();
    expect(consulta?.toString()).toBe('prestacionId=1&fecha=2026-12-01');
    expect(chips().map((b) => b.textContent)).toEqual(['10:00', '10:30']);
  });

  it('Horarios de un profesional elegido', async () => {
    await irAlPaso2();
    await vi.waitFor(() => expect(opcionesDeProfesional()).toContain('Bruno Prueba'));

    elegirProfesional('Bruno Prueba');

    await vi.waitFor(() => expect(disponibilidadPedida()).toHaveLength(2));
    expect(disponibilidadPedida()[1]?.toString()).toBe('prestacionId=1&fecha=2026-12-01&profesionalId=2');
  });

  it('Horario elegido', async () => {
    await irAlPaso2();

    chip('10:30').click();

    expect(chip('10:30').getAttribute('aria-pressed')).toBe('true');
    expect(chip('10:00').getAttribute('aria-pressed')).toBe('false');
  });
});

describe('Día sin horarios libres', () => {
  it('Día completo', async () => {
    servidor.responder('GET /public/disponibilidad', () => json(200, []));

    await irAlPaso2();

    expect(texto(panel())).toContain(
      'No hay horarios libres para ese día. Probá con otra fecha o con otro profesional.',
    );
    expect(boton(panel(), 'Continuar').disabled).toBe(true);
  });
});

describe('Resumen antes de confirmar', () => {
  it('Resumen sin preferencia', async () => {
    await irAlResumen();

    expect(resumen()).toContain('Consulta');
    expect(resumen()).toContain('Sin preferencia (te asignamos uno)');
    expect(resumen()).toContain('martes 1 de diciembre');
    expect(resumen()).toContain('10:30');
    expect(boton(panel(), 'Confirmar reserva')).toBeDefined();
    expect(panel().querySelectorAll('input')).toHaveLength(0);
  });
});

describe('Confirmar sin sesión de paciente', () => {
  it('Confirmar sin sesión', async () => {
    await irAlResumen();

    boton(panel(), 'Confirmar reserva').click();

    await vi.waitFor(() => expect(window.location.hash).toBe('#/mi-cuenta'));
    await vi.waitFor(() => expect(texto(raiz())).toContain('Ingresá o creá tu cuenta para confirmar la reserva.'));
    expect(servidor.a('POST', '/paciente/reservas')).toHaveLength(0);
  });

  it('Vuelta a la reserva después de ingresar', async () => {
    await irAlResumen();
    boton(panel(), 'Confirmar reserva').click();
    await vi.waitFor(() => expect(raiz().querySelector('main h1')?.textContent).toBe('Mi cuenta'));
    servidor.responder('POST /public/login', () => {
      conSesionDePaciente();
      return json(200, SESION_PACIENTE);
    });

    escribir(campo(raiz(), 'Email'), 'paciente.uno@example.com');
    escribir(campo(raiz(), 'Contraseña'), 'clave-ficticia-1');
    raiz().querySelector<HTMLFormElement>('form[aria-label="Ingresar"]')?.requestSubmit();

    await vi.waitFor(() => expect(window.location.hash).toBe('#/reserva'));
    await vi.waitFor(() => expect(pasoActual()).toBe('Confirmá tu turno'));
    expect(resumen()).toContain('Consulta');
    expect(resumen()).toContain('martes 1 de diciembre');
    expect(resumen()).toContain('10:30');
    expect(servidor.a('POST', '/paciente/reservas')).toHaveLength(0);
  });

  it('una reserva pendiente con forma inválida se ignora', async () => {
    sessionStorage.setItem('turnos.reservaPendiente', JSON.stringify({ prestacionId: 'uno', inicio: 3 }));

    await abrirReserva();

    expect(pasoActual()).toBe('¿Qué necesitás?');
  });
});

describe('Reserva confirmada', () => {
  it('Reserva sin preferencia confirmada', async () => {
    conSesionDePaciente();
    servidor.responder('POST /paciente/reservas', () =>
      json(201, {
        numeroReserva: 'R-0001',
        ...horario('10:30'),
        estado: 'RESERVADO',
        prestacion: { id: 1, nombre: 'Consulta' },
        profesional: { id: 2, nombre: 'Bruno Prueba' },
      }),
    );
    await irAlResumen();

    boton(panel(), 'Confirmar reserva').click();

    await vi.waitFor(() => expect(texto(panel())).toContain('¡Turno reservado!'));
    expect(servidor.ultimo('POST', '/paciente/reservas').cuerpo).toEqual({
      prestacionId: 1,
      inicio: '2026-12-01T10:30:00-03:00',
    });
    const comprobante = texto(panel());
    for (const esperado of ['R-0001', 'martes 1 de diciembre', '10:30', 'Consulta', 'Bruno Prueba']) {
      expect(comprobante).toContain(esperado);
    }
    expect(panel().querySelector('a[href="#/mis-turnos"]')).not.toBeNull();
    expect(sessionStorage.getItem('turnos.reservaPendiente')).toBeNull();
  });

  it('Doble envío', async () => {
    conSesionDePaciente();
    servidor.responder('POST /paciente/reservas', () => json(201, { numeroReserva: 'R-0001', ...horario('10:30'), estado: 'RESERVADO' }));
    await irAlResumen();

    const confirmar = boton(panel(), 'Confirmar reserva');
    confirmar.click();
    confirmar.click();

    await vi.waitFor(() => expect(texto(panel())).toContain('¡Turno reservado!'));
    expect(servidor.a('POST', '/paciente/reservas')).toHaveLength(1);
  });
});

describe('Horario ocupado al confirmar', () => {
  function ocupado(alternativas: { inicio: string; fin: string }[]): void {
    servidor.responder('POST /paciente/reservas', () =>
      json(409, { codigo: 'SIN_DISPONIBILIDAD', detail: 'Sin disponibilidad', alternativas }),
    );
  }

  it('Horario tomado con alternativas', async () => {
    conSesionDePaciente();
    ocupado([horario('11:00'), horario('11:30')]);
    await irAlResumen();

    boton(panel(), 'Confirmar reserva').click();

    await vi.waitFor(() => expect(alerta()).toContain('Ese horario se ocupó recién. Elegí otro.'));
    expect(chips().map((b) => b.textContent)).toEqual(['11:00', '11:30']);
  });

  it('Elegir una alternativa', async () => {
    conSesionDePaciente();
    ocupado([horario('11:00'), horario('11:30')]);
    await irAlResumen();
    boton(panel(), 'Confirmar reserva').click();
    await vi.waitFor(() => expect(chips()).toHaveLength(2));

    chip('11:00').click();

    expect(resumen()).toContain('11:00');
    expect(boton(panel(), 'Confirmar reserva')).toBeDefined();
    expect(servidor.a('POST', '/paciente/reservas')).toHaveLength(1);
  });

  it('Horario tomado sin alternativas', async () => {
    conSesionDePaciente();
    ocupado([]);
    await irAlResumen();

    boton(panel(), 'Confirmar reserva').click();

    await vi.waitFor(() => expect(pasoActual()).toBe('Elegí día y horario'));
    expect(alerta()).toContain('No quedan horarios libres ese día. Elegí otra fecha.');
    await vi.waitFor(() => expect(disponibilidadPedida()).toHaveLength(2));
    expect(disponibilidadPedida()[1]?.get('fecha')).toBe('2026-12-01');
  });
});

describe('Otros rechazos de la reserva', () => {
  it('El horario pasó mientras se elegía', async () => {
    conSesionDePaciente();
    servidor.responder('POST /paciente/reservas', () => json(409, { codigo: 'INICIO_EN_EL_PASADO', detail: 'detalle interno' }));
    await irAlResumen();

    boton(panel(), 'Confirmar reserva').click();

    await vi.waitFor(() => expect(pasoActual()).toBe('Elegí día y horario'));
    expect(alerta()).toContain('El horario elegido ya pasó.');
    expect(alerta()).not.toContain('detalle interno');
    await vi.waitFor(() => expect(disponibilidadPedida()).toHaveLength(2));
    await vi.waitFor(() => expect(chips()).toHaveLength(2));
    expect(chips().filter((b) => b.getAttribute('aria-pressed') === 'true')).toHaveLength(0);
  });
});

describe('Reserva con sesión del personal', () => {
  it('Recepción abre la reserva', async () => {
    servidor.responder('GET /public/sesion', () => json(200, SESION_RECEPCION));

    app = abrirApp(servidor, '#/reserva');

    await vi.waitFor(() =>
      expect(alerta()).toContain('La reserva online es para pacientes. Para dar un turno, usá la Agenda.'),
    );
    expect(servidor.a('GET', '/public/prestaciones')).toHaveLength(0);
  });
});
