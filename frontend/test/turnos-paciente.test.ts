import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { iniciarApp } from '../src/app.ts';
import { traducirEntrada } from '../src/shared/ui/entrada.ts';
import type { Router } from '../src/shared/ui/router.ts';
import { boton, campo, json, ServidorFalso, texto } from './apoyo.ts';

// Datos ficticios: hay una sesión de "Paciente Uno"; el momento actual es el martes 2026-12-01 a las 08:00
// en la zona del consultorio; "Consulta" (id 1) la atienden Ana Ejemplo (id 1) y Bruno Prueba (id 2).
const SESION_PACIENTE = { autenticado: true, username: 'paciente.uno@example.com', rol: 'PACIENTE' };

function turnoPaciente(datos: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    numeroReserva: 'R-0002',
    inicio: '2026-12-03T10:00:00-03:00',
    fin: '2026-12-03T10:30:00-03:00',
    estado: 'RESERVADO',
    prestacion: { id: 1, nombre: 'Consulta' },
    profesional: { id: 1, nombre: 'Ana Ejemplo' },
    modificableOnline: true,
    ...datos,
  };
}

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

function titulo(): string | null {
  return raiz.querySelector('main h1')?.textContent ?? null;
}

function tarjeta(numero: string): HTMLElement {
  const encontrada = [...raiz.querySelectorAll<HTMLElement>('.turno-paciente')].find(
    (t) => t.dataset['reserva'] === numero,
  );
  if (encontrada === undefined) {
    throw new Error(`No está el turno ${numero}`);
  }
  return encontrada;
}

function seccion(nombre: string): HTMLElement {
  const encontrada = raiz.querySelector<HTMLElement>(`section[aria-label="${nombre}"]`);
  if (encontrada === null) {
    throw new Error(`No está la sección ${nombre}`);
  }
  return encontrada;
}

function avisos(): string {
  return [...raiz.querySelectorAll('main [role="alert"], main [role="status"]')]
    .map((e) => e.textContent ?? '')
    .join(' ');
}

function botones(elemento: HTMLElement): string[] {
  return [...elemento.querySelectorAll('button')].map((b) => b.textContent ?? '');
}

async function abrirMisTurnos(turnos: Record<string, unknown>[] = [turnoPaciente()]): Promise<void> {
  servidor.responder('GET /paciente/turnos', () => json(200, turnos));
  abrirEn('/#/mis-turnos');
  await vi.waitFor(() => expect(raiz.querySelector('main .pila[aria-busy]')).toBeNull());
  await vi.waitFor(() => expect(titulo()).toBe('Mis turnos'));
}

async function abrirCambio(): Promise<void> {
  await abrirMisTurnos();
  boton(tarjeta('R-0002'), 'Cambiar horario').click();
  await vi.waitFor(() => expect(tarjeta('R-0002').querySelectorAll('.chip').length).toBeGreaterThan(0));
}

function chip(hora: string): HTMLButtonElement {
  const encontrado = [...tarjeta('R-0002').querySelectorAll<HTMLButtonElement>('.chip')].find(
    (b) => b.textContent === hora,
  );
  if (encontrado === undefined) {
    throw new Error(`No está el horario ${hora}`);
  }
  return encontrado;
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00Z'));
  servidor = new ServidorFalso();
  servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
  servidor.responder('GET /public/prestaciones', () => json(200, []));
  servidor.responder('GET /public/profesionales', () =>
    json(200, [
      { id: 1, nombre: 'Ana Ejemplo' },
      { id: 2, nombre: 'Bruno Prueba' },
    ]),
  );
  servidor.responder('GET /public/disponibilidad', () =>
    json(200, [
      { inicio: '2026-12-03T11:00:00-03:00', fin: '2026-12-03T11:30:00-03:00' },
      { inicio: '2026-12-03T11:30:00-03:00', fin: '2026-12-03T12:00:00-03:00' },
    ]),
  );
  document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
});

afterEach(() => {
  router?.detener();
  router = undefined;
  vi.useRealTimers();
  history.replaceState(null, '', '/');
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
});

describe('Lista de mis turnos', () => {
  it('Próximos y anteriores', async () => {
    await abrirMisTurnos([
      turnoPaciente(),
      turnoPaciente({
        numeroReserva: 'R-0003',
        inicio: '2026-12-02T09:00:00-03:00',
        fin: '2026-12-02T09:30:00-03:00',
        estado: 'CONFIRMADO',
      }),
      turnoPaciente({
        numeroReserva: 'R-0001',
        inicio: '2026-11-20T11:00:00-03:00',
        fin: '2026-11-20T11:30:00-03:00',
        estado: 'CANCELADO',
        modificableOnline: false,
      }),
    ]);

    expect(servidor.a('GET', '/paciente/turnos')).toHaveLength(1);
    const proximos = [...seccion('Próximos turnos').querySelectorAll<HTMLElement>('.turno-paciente')].map((t) =>
      texto(t),
    );
    expect(proximos).toHaveLength(2);
    expect(proximos[0]).toContain('miércoles 2 de diciembre · 09:00');
    expect(proximos[0]).toContain('Confirmado');
    expect(proximos[1]).toContain('jueves 3 de diciembre · 10:00');
    expect(proximos[1]).toContain('Reservado');
    expect(proximos[1]).toContain('R-0002');
    const anteriores = texto(seccion('Turnos anteriores'));
    expect(anteriores).toContain('20 de noviembre');
    expect(anteriores).toContain('Cancelado');
  });

  it('Sin turnos', async () => {
    await abrirMisTurnos([]);

    expect(texto(raiz)).toContain('Todavía no tenés turnos.');
    const enlace = [...raiz.querySelectorAll<HTMLAnchorElement>('main a')].find((a) => a.textContent === 'Reservar turno');
    expect(enlace?.getAttribute('href')).toBe('#/reserva');
  });

  // C-42: el momento actual es el 2026-12-01 a las 08:00 en Buenos Aires.
  it('Turno pasado sin registrar', async () => {
    await abrirMisTurnos([
      turnoPaciente({
        numeroReserva: 'R-0001',
        inicio: '2026-11-28T10:00:00-03:00',
        fin: '2026-11-28T10:30:00-03:00',
        modificableOnline: false,
      }),
      turnoPaciente({
        numeroReserva: 'R-0004',
        inicio: '2026-11-27T09:00:00-03:00',
        fin: '2026-11-27T09:30:00-03:00',
        estado: 'CONFIRMADO',
        modificableOnline: false,
      }),
    ]);

    const anteriores = seccion('Turnos anteriores');
    for (const numero of ['R-0001', 'R-0004']) {
      const t = texto(tarjeta(numero));
      expect(t).toContain('Sin registrar');
      expect(t).toContain('El consultorio todavía no registró la asistencia.');
      expect(tarjeta(numero).closest('section')).toBe(anteriores);
    }
    expect(texto(anteriores)).not.toContain('Reservado');
    expect(texto(anteriores)).not.toContain('Confirmado');
    // Ningún pedido por turno: solo la lista de turnos y, desde C-15, la lista de espera del paciente.
    expect(
      servidor.pedidos.map((p) => `${p.metodo} ${p.ruta}`).filter((p) => p !== 'GET /public/sesion').sort(),
    ).toEqual(['GET /paciente/lista-espera', 'GET /paciente/turnos']);
  });

  it('Turno en curso', async () => {
    await abrirMisTurnos([
      turnoPaciente({
        numeroReserva: 'R-0005',
        inicio: '2026-12-01T07:45:00-03:00',
        fin: '2026-12-01T08:15:00-03:00',
        estado: 'CONFIRMADO',
        modificableOnline: false,
      }),
    ]);

    const t = texto(tarjeta('R-0005'));
    expect(tarjeta('R-0005').closest('section')).toBe(seccion('Turnos anteriores'));
    expect(t).toContain('Confirmado');
    expect(t).not.toContain('Sin registrar');
  });

  it('Turnos registrados se muestran igual', async () => {
    await abrirMisTurnos([
      turnoPaciente({ numeroReserva: 'R-0006', inicio: '2026-11-25T10:00:00-03:00', fin: '2026-11-25T10:30:00-03:00', estado: 'ATENDIDO', modificableOnline: false }),
      turnoPaciente({ numeroReserva: 'R-0007', inicio: '2026-11-26T10:00:00-03:00', fin: '2026-11-26T10:30:00-03:00', estado: 'AUSENTE', modificableOnline: false }),
      turnoPaciente({ numeroReserva: 'R-0008', inicio: '2026-11-20T11:00:00-03:00', fin: '2026-11-20T11:30:00-03:00', estado: 'CANCELADO', modificableOnline: false }),
    ]);

    expect(texto(tarjeta('R-0006'))).toContain('Atendido');
    expect(texto(tarjeta('R-0007'))).toContain('Ausente');
    expect(texto(tarjeta('R-0008'))).toContain('Cancelado');
    expect(texto(seccion('Turnos anteriores'))).not.toContain('Sin registrar');
  });
});

describe('Acciones según el plazo de 24 horas', () => {
  it('Turno dentro y fuera del plazo', async () => {
    await abrirMisTurnos([
      turnoPaciente(),
      turnoPaciente({
        numeroReserva: 'R-0004',
        inicio: '2026-12-01T18:00:00-03:00',
        fin: '2026-12-01T18:30:00-03:00',
        modificableOnline: false,
      }),
    ]);

    expect(botones(tarjeta('R-0002')).sort()).toEqual(['Cambiar horario', 'Cancelar turno']);
    expect(botones(tarjeta('R-0004'))).toEqual([]);
    expect(texto(tarjeta('R-0004'))).toContain(
      'Faltan menos de 24 h. Para cancelarlo o cambiarlo, contactá al consultorio.',
    );
  });
});

describe('Cancelar un turno', () => {
  it('Cancelación confirmada', async () => {
    servidor.responder('POST /paciente/reservas/R-0002/cancelar', () =>
      json(200, turnoPaciente({ estado: 'CANCELADO' })),
    );
    await abrirMisTurnos();

    boton(tarjeta('R-0002'), 'Cancelar turno').click();
    const dialogo = raiz.querySelector<HTMLDialogElement>('dialog');
    expect(dialogo?.open).toBe(true);
    expect(texto(dialogo ?? raiz)).toContain('¿Cancelar el turno?');
    expect(texto(dialogo ?? raiz)).toContain('jueves 3 de diciembre a las 10:00 con Ana Ejemplo');
    boton(raiz, 'Sí, cancelar turno').click();

    await vi.waitFor(() => expect(avisos()).toContain('Tu turno se canceló.'));
    expect(servidor.a('POST', '/paciente/reservas/R-0002/cancelar')).toHaveLength(1);
    await vi.waitFor(() => expect(servidor.a('GET', '/paciente/turnos')).toHaveLength(2));
  });

  it('Cancelación desistida', async () => {
    await abrirMisTurnos();

    boton(tarjeta('R-0002'), 'Cancelar turno').click();
    boton(raiz, 'Volver').click();

    expect(raiz.querySelector('dialog')).toBeNull();
    expect(servidor.a('POST', '/paciente/reservas/R-0002/cancelar')).toHaveLength(0);
    expect(texto(seccion('Próximos turnos'))).toContain('R-0002');
  });
});

describe('Cambiar el horario de un turno', () => {
  it('Horarios para cambiar con el mismo profesional', async () => {
    await abrirCambio();

    expect(servidor.ultimo('GET', '/public/disponibilidad').query.toString()).toBe(
      'prestacionId=1&fecha=2026-12-03&profesionalId=1',
    );
    const selector = campo<HTMLSelectElement>(tarjeta('R-0002'), 'Profesional');
    expect(selector.selectedOptions[0]?.textContent).toBe('Ana Ejemplo');
    expect([...selector.options].map((o) => o.textContent)).toContain('Sin preferencia');
  });

  it('Cambio confirmado', async () => {
    servidor.responder('POST /paciente/reservas/R-0002/reprogramar', () =>
      json(200, turnoPaciente({ numeroReserva: 'R-0003', inicio: '2026-12-03T11:00:00-03:00' })),
    );
    await abrirCambio();

    chip('11:00').click();
    boton(tarjeta('R-0002'), 'Cambiar a las 11:00').click();

    await vi.waitFor(() =>
      expect(avisos()).toContain(
        'Cambiamos tu turno al jueves 3 de diciembre a las 11:00. Tu nuevo número de reserva es R-0003.',
      ),
    );
    expect(servidor.ultimo('POST', '/paciente/reservas/R-0002/reprogramar').cuerpo).toEqual({
      inicio: '2026-12-03T11:00:00-03:00',
      profesionalId: 1,
    });
    await vi.waitFor(() => expect(servidor.a('GET', '/paciente/turnos')).toHaveLength(2));
  });

  it('Cambio sin preferencia', async () => {
    servidor.responder('POST /paciente/reservas/R-0002/reprogramar', () =>
      json(200, turnoPaciente({ numeroReserva: 'R-0003' })),
    );
    await abrirCambio();
    const selector = campo<HTMLSelectElement>(tarjeta('R-0002'), 'Profesional');

    selector.value = '';
    selector.dispatchEvent(new Event('change', { bubbles: true }));
    await vi.waitFor(() => expect(servidor.a('GET', '/public/disponibilidad')).toHaveLength(2));
    await vi.waitFor(() => expect(tarjeta('R-0002').querySelectorAll('.chip').length).toBeGreaterThan(0));
    chip('11:30').click();
    boton(tarjeta('R-0002'), 'Cambiar a las 11:30').click();

    await vi.waitFor(() => expect(servidor.a('POST', '/paciente/reservas/R-0002/reprogramar')).toHaveLength(1));
    expect(servidor.ultimo('POST', '/paciente/reservas/R-0002/reprogramar').cuerpo).toEqual({
      inicio: '2026-12-03T11:30:00-03:00',
    });
  });
});

describe('Rechazos al cancelar o cambiar', () => {
  async function confirmarCancelacion(): Promise<void> {
    await abrirMisTurnos();
    boton(tarjeta('R-0002'), 'Cancelar turno').click();
    boton(raiz, 'Sí, cancelar turno').click();
  }

  it('Plazo vencido mientras la página estaba abierta', async () => {
    servidor.responder('POST /paciente/reservas/R-0002/cancelar', () =>
      json(409, { codigo: 'CANCELACION_FUERA_DE_PLAZO', detail: 'detalle interno' }),
    );

    await confirmarCancelacion();

    await vi.waitFor(() =>
      expect(avisos()).toContain('Faltan menos de 24 h. Para cancelarlo o cambiarlo, contactá al consultorio.'),
    );
    await vi.waitFor(() => expect(servidor.a('GET', '/paciente/turnos')).toHaveLength(2));
  });

  it('Turno que ya no existe', async () => {
    servidor.responder('POST /paciente/reservas/R-0002/cancelar', () =>
      json(404, { codigo: 'RESERVA_INEXISTENTE' }),
    );

    await confirmarCancelacion();

    await vi.waitFor(() => expect(avisos()).toContain('No encontramos ese turno.'));
  });

  it('Nuevo horario ocupado', async () => {
    servidor.responder('POST /paciente/reservas/R-0002/reprogramar', () =>
      json(409, {
        codigo: 'SIN_DISPONIBILIDAD',
        alternativas: [{ inicio: '2026-12-03T11:30:00-03:00', fin: '2026-12-03T12:00:00-03:00' }],
      }),
    );
    await abrirCambio();

    chip('11:00').click();
    boton(tarjeta('R-0002'), 'Cambiar a las 11:00').click();

    await vi.waitFor(() => expect(texto(tarjeta('R-0002'))).toContain('Ese horario se ocupó recién. Elegí otro.'));
    expect([...tarjeta('R-0002').querySelectorAll('.chip')].map((b) => b.textContent)).toEqual(['11:30']);
    expect(servidor.a('GET', '/paciente/turnos')).toHaveLength(1);
    expect(texto(tarjeta('R-0002'))).toContain('jueves 3 de diciembre · 10:00');
  });
});

describe('Entrada desde los links de los avisos', () => {
  it('Link de cancelar sin sesión', async () => {
    servidor.sinSesion();
    servidor.responder('GET /paciente/turnos', () => json(200, []));
    const entradasPrevias = history.length;

    abrirEn('/mis-turnos/cancelar');

    expect(`${window.location.pathname}${window.location.hash}`).toBe('/#/mis-turnos');
    await vi.waitFor(() => expect(window.location.hash).toBe('#/mi-cuenta'));
    expect(`${window.location.pathname}${window.location.hash}`).toBe('/#/mi-cuenta');
    expect(history.length).toBe(entradasPrevias);

    servidor.responder('POST /public/login', () => {
      servidor.responder('GET /public/sesion', () => json(200, SESION_PACIENTE));
      return json(200, SESION_PACIENTE);
    });
    const email = campo(raiz, 'Email');
    email.value = 'paciente.uno@example.com';
    campo(raiz, 'Contraseña').value = 'clave-ficticia-1';
    raiz.querySelector<HTMLFormElement>('form[aria-label="Ingresar"]')?.requestSubmit();

    await vi.waitFor(() => expect(titulo()).toBe('Mis turnos'));
  });

  it('Link de reprogramar con sesión', async () => {
    servidor.responder('GET /paciente/turnos', () => json(200, [turnoPaciente()]));
    const entradasPrevias = history.length;

    abrirEn('/mis-turnos/reprogramar');

    await vi.waitFor(() => expect(titulo()).toBe('Mis turnos'));
    expect(`${window.location.pathname}${window.location.hash}`).toBe('/#/mis-turnos');
    expect(history.length).toBe(entradasPrevias);
  });
});

// ---------- C-23: la seña en "Mis turnos" (spec frontend-senas) ----------

const SENA_PENDIENTE = {
  estado: 'PENDIENTE',
  importe: 4500,
  venceEn: '2026-12-01T10:30:00-03:00',
  linkPago: 'https://mp.test/pagar/1',
};

function enlacePagar(elemento: HTMLElement): HTMLAnchorElement | undefined {
  return [...elemento.querySelectorAll('a')].find((a) => a.textContent === 'Pagar seña');
}

describe('Seña en Mis turnos', () => {
  it('Seña pendiente en mis turnos', async () => {
    await abrirMisTurnos([turnoPaciente({ sena: SENA_PENDIENTE })]);

    expect(texto(tarjeta('R-0002'))).toContain('Seña pendiente: $ 4.500,00. Vence a las 10:30.');
    expect(enlacePagar(tarjeta('R-0002'))?.getAttribute('href')).toBe('https://mp.test/pagar/1');
  });

  it('Seña pagada en mis turnos', async () => {
    await abrirMisTurnos([
      turnoPaciente({ sena: { estado: 'PAGADA', importe: 4500, venceEn: '2026-12-01T10:30:00-03:00', linkPago: null } }),
    ]);

    expect(texto(tarjeta('R-0002'))).toContain('Seña pagada');
    expect(enlacePagar(tarjeta('R-0002'))).toBeUndefined();
    expect(botones(tarjeta('R-0002'))).not.toContain('Pagar seña');
  });

  it('Seña devuelta en mis turnos', async () => {
    await abrirMisTurnos([
      turnoPaciente({
        estado: 'CANCELADO',
        modificableOnline: false,
        sena: { estado: 'DEVUELTA', importe: 4500, venceEn: '2026-12-01T10:30:00-03:00', linkPago: null },
      }),
    ]);

    expect(texto(tarjeta('R-0002'))).toContain('Seña devuelta');
  });

  it('los estados vencida y retenida se dicen con texto', async () => {
    await abrirMisTurnos([
      turnoPaciente({
        numeroReserva: 'R-0003',
        estado: 'CANCELADO',
        modificableOnline: false,
        sena: { estado: 'VENCIDA', importe: 4500, venceEn: '2026-12-01T10:30:00-03:00', linkPago: null },
      }),
      turnoPaciente({
        numeroReserva: 'R-0004',
        estado: 'AUSENTE',
        modificableOnline: false,
        sena: { estado: 'RETENIDA', importe: 4500, venceEn: '2026-12-01T10:30:00-03:00', linkPago: null },
      }),
    ]);

    expect(texto(tarjeta('R-0003'))).toContain('Seña vencida: el turno se canceló');
    expect(texto(tarjeta('R-0004'))).toContain('Seña retenida por el consultorio');
  });

  it('una seña pendiente sin link pide uno nuevo al pagar', async () => {
    servidor.responder('POST /paciente/reservas/R-0002/sena/link', () =>
      json(200, { linkPago: 'https://mp.test/pagar/nuevo' }),
    );
    const abrir = vi.spyOn(window, 'open').mockReturnValue(null);
    await abrirMisTurnos([turnoPaciente({ sena: { ...SENA_PENDIENTE, linkPago: null } })]);

    boton(tarjeta('R-0002'), 'Pagar seña').click();

    await vi.waitFor(() => expect(abrir).toHaveBeenCalledWith('https://mp.test/pagar/nuevo', '_self'));
    expect(servidor.a('POST', '/paciente/reservas/R-0002/sena/link')).toHaveLength(1);
    abrir.mockRestore();
  });
});

describe('Vuelta desde Mercado Pago', () => {
  it('Vuelta antes de la notificación', async () => {
    servidor.responder('GET /paciente/turnos', () => json(200, [turnoPaciente({ sena: SENA_PENDIENTE })]));
    abrirEn('/#/mis-turnos?pago=aprobado');
    await vi.waitFor(() => expect(titulo()).toBe('Mis turnos'));
    await vi.waitFor(() => expect(raiz.querySelector('.turno-paciente')).not.toBeNull());

    expect(avisos()).toContain('Estamos confirmando tu pago. Puede tardar unos minutos.');
    expect(texto(tarjeta('R-0002'))).toContain('Seña pendiente');
    expect(window.location.hash).toBe('#/mis-turnos');
  });

  it('Vuelta sin recargar la página (link de la pasarela simulada)', async () => {
    servidor.responder('GET /paciente/turnos', () => json(200, [turnoPaciente({ sena: SENA_PENDIENTE })]));
    abrirEn('/#/mis-turnos');
    await vi.waitFor(() => expect(raiz.querySelector('.turno-paciente')).not.toBeNull());
    expect(avisos()).not.toContain('Estamos confirmando tu pago');

    window.location.hash = '#/mis-turnos?pago=simulado&ref=S-FICTICIA0000001';
    await vi.waitFor(() => expect(avisos()).toContain('Estamos confirmando tu pago. Puede tardar unos minutos.'));
    expect(titulo()).toBe('Mis turnos');
    expect(window.location.hash).toBe('#/mis-turnos');
  });
});
