import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { dejarSeleccion, type TurnoDeLaFicha } from '../src/ficha/seleccion.ts';
import { abrirApp, boton, campo, elegirPaciente, escribir, json, ServidorFalso, texto, turno, type AppDePrueba } from './apoyo.ts';

/**
 * C-44 frontend-ficha-clinica-odontograma: un test por escenario del delta `frontend-ficha-clinica` que necesita la
 * página entera (los del dibujo del odontograma están en `odontograma.test.ts`). Datos ficticios del seed: "Paciente
 * Uno" (id 1, DNI 99000001), `ana.ejemplo` (profesional 1), `bruno.prueba` (profesional 2), `admin` y `recepcion`.
 */

const SESION_ANA = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };
const SESION_BRUNO = { autenticado: true, username: 'bruno.prueba', rol: 'ODONTOLOGO', profesionalId: 2 };
const SESION_ADMIN = { autenticado: true, username: 'admin', rol: 'ADMIN' };

const FICHA_VACIA = { pacienteId: 1, antecedentes: '', alergias: '', medicacion: '', actualizadoPor: null, actualizadoEn: null, ausencias: [] };
const FICHA_CARGADA = {
  ...FICHA_VACIA,
  alergias: 'Penicilina',
  actualizadoPor: 'ana.ejemplo',
  actualizadoEn: '2026-12-01T09:40:00-03:00',
};

const TURNO_7: TurnoDeLaFicha = {
  id: 7,
  profesionalId: 1,
  inicio: '2026-12-01T10:00:00-03:00',
  fin: '2026-12-01T10:30:00-03:00',
  estado: 'RESERVADO',
  llegadaEn: null,
};

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

function raiz(): HTMLElement {
  if (app === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return app.raiz;
}

function ahoraEs(hora: string): void {
  vi.setSystemTime(new Date(`2026-12-01T${hora}:00-03:00`));
}

function conSesion(sesion: object): void {
  servidor.responder('GET /public/sesion', () => json(200, sesion));
}

function responderFicha(ficha: object = FICHA_CARGADA, registros: unknown[] = []): void {
  servidor.responder('GET /pacientes/{id}/ficha', () => json(200, ficha));
  servidor.responder('GET /pacientes/{id}/odontograma', () => json(200, registros));
  // C-46: la ficha también pide el periodontograma; acá el paciente no tiene ninguno.
  servidor.responder('GET /pacientes/{id}/periodontograma', () => json(200, []));
  // C-47: y las imágenes; acá el paciente no tiene ninguna.
  servidor.responder('GET /pacientes/{id}/imagenes', () => json(200, []));
  // C-49: y la cobertura; acá el paciente es particular y sin afiliaciones.
  servidor.responder('GET /pacientes/{id}/cobertura', () => json(200, { particular: true, afiliacion: null }));
  // C-48: y los consentimientos; acá el paciente todavía no firmó ninguno.
  servidor.responder('GET /pacientes/{id}/consentimientos', () => json(200, []));
  servidor.responder('GET /pacientes/{id}/afiliaciones', () => json(200, []));
}

function abrir(hash = '#/pacientes'): void {
  app = abrirApp(servidor, hash);
}

/** Abre "Pacientes" con la ficha de Paciente Uno ya elegida, como lo deja "Ver ficha" de la agenda. */
async function abrirFicha(turnoDeLaFicha: TurnoDeLaFicha | null = null): Promise<void> {
  dejarSeleccion({ paciente: { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: null }, turno: turnoDeLaFicha });
  abrir();
  await fichaVisible();
}

async function fichaVisible(): Promise<void> {
  await vi.waitFor(() => expect(raiz().querySelector('.ficha-columnas')).not.toBeNull());
}

function main(): HTMLElement {
  return raiz().querySelector('main') ?? raiz();
}

function aviso(rol: 'alert' | 'status'): string {
  return [...main().querySelectorAll(`[role="${rol}"]`)].map((a) => a.textContent ?? '').join('');
}

function botones(): string[] {
  return [...main().querySelectorAll('button')].map((b) => b.textContent ?? '');
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  ahoraEs('11:00');
  servidor = new ServidorFalso();
  conSesion(SESION_ANA);
  responderFicha();
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  vi.useRealTimers();
  vi.restoreAllMocks();
  localStorage.clear();
  sessionStorage.clear();
});

describe('Elegir al paciente en "Pacientes"', () => {
  it('Abrir la ficha desde el buscador', async () => {
    abrir();
    await vi.waitFor(() => expect(() => campo(main(), 'Paciente')).not.toThrow());

    await elegirPaciente(main(), '99000001', 'Uno, Paciente · DNI 99000001');

    await fichaVisible();
    expect(servidor.a('GET', '/pacientes/1/ficha')).toHaveLength(1);
    expect(servidor.a('GET', '/pacientes/1/odontograma')).toHaveLength(1);
    expect(main().querySelector('.ficha-paciente-nombre')?.textContent).toBe('Paciente Uno');
    expect(texto(main())).toContain('DNI 99000001');
  });

  it('Página sin paciente elegido', async () => {
    conSesion(SESION_ADMIN);
    abrir();

    await vi.waitFor(() => expect(texto(main())).toContain('Buscá un paciente para ver su ficha.'));
    expect(() => campo(main(), 'Paciente')).not.toThrow();
    expect(servidor.pedidos.filter((p) => p.ruta.includes('/ficha'))).toEqual([]);
  });

  it('Recargar la página', async () => {
    await abrirFicha();
    app?.router.detener();

    abrir();

    await vi.waitFor(() => expect(texto(main())).toContain('Buscá un paciente para ver su ficha.'));
    expect(main().querySelector('.ficha-columnas')).toBeNull();
    expect(window.location.hash).toBe('#/pacientes');
    expect(servidor.a('GET', '/pacientes/1/ficha')).toHaveLength(1);
  });

  it('"Cambiar de paciente" vuelve al buscador', async () => {
    await abrirFicha();

    boton(main(), 'Cambiar de paciente').click();

    expect(texto(main())).toContain('Buscá un paciente para ver su ficha.');
    expect(main().querySelector('.ficha-columnas')).toBeNull();
  });
});

describe('Cambiar de paciente con cambios sin guardar', () => {
  it('pide confirmación antes de descartar', async () => {
    await abrirFicha();
    main().querySelector('polygon[data-pieza="16"][data-cara="O"]')?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    const caries = [...main().querySelectorAll('label.opcion')].find((l) => (l.textContent ?? '').endsWith('Caries'))?.querySelector('input');
    if (caries !== null && caries !== undefined) {
      caries.checked = true;
      caries.dispatchEvent(new Event('change', { bubbles: true }));
    }
    boton(main(), 'Sumar cambio').click();

    boton(main(), 'Cambiar de paciente').click();

    const dialogo = await vi.waitFor(() => {
      const abierto = document.querySelector<HTMLDialogElement>('dialog');
      expect(abierto).not.toBeNull();
      return abierto as HTMLDialogElement;
    });
    expect(texto(dialogo)).toContain('Hay cambios sin guardar en el odontograma. ¿Descartarlos?');
    expect(main().querySelector('.ficha-columnas')).not.toBeNull();

    boton(dialogo, 'Descartar').click();

    await vi.waitFor(() => expect(texto(main())).toContain('Buscá un paciente para ver su ficha.'));
  });
});

describe('Abrir la ficha desde un turno de la agenda', () => {
  it('Ver la ficha del paciente del turno', async () => {
    ahoraEs('08:00');
    servidor.responder('GET /turnos', () => json(200, [turno()]));
    abrir('#/agenda');
    const verFicha = await vi.waitFor(() => boton(main(), 'Ver ficha de Paciente Uno'));

    verFicha.click();

    await fichaVisible();
    expect(main().querySelector('h1')?.textContent).toBe('Pacientes');
    expect(main().querySelector('.ficha-paciente-nombre')?.textContent).toBe('Paciente Uno');
    expect(texto(main())).toContain('Turno del martes 01/12 · 10:00 – 10:30');
  });
});

describe('Ficha básica', () => {
  it('Ver una ficha cargada', async () => {
    conSesion(SESION_ADMIN);
    await abrirFicha();

    const alergias = [...main().querySelectorAll('dt')].find((dt) => dt.textContent === 'Alergias');
    expect(alergias?.nextElementSibling?.textContent).toBe('Penicilina');
    expect(texto(main())).not.toContain('Todavía no se cargó la ficha.');
    expect(texto(main())).toContain('Actualizada por ana.ejemplo el 01/12/2026 a las 09:40');
  });

  it('Paciente sin ficha', async () => {
    responderFicha(FICHA_VACIA);
    await abrirFicha();

    expect(texto(main())).toContain('Todavía no se cargó la ficha.');
    for (const etiqueta of ['Antecedentes', 'Alergias', 'Medicación']) {
      expect(campo<HTMLTextAreaElement>(main(), etiqueta).value).toBe('');
    }
    expect(aviso('alert')).toBe('');
  });
});

describe('Editar la ficha básica', () => {
  it('Guardar la ficha', async () => {
    servidor.responder('PUT /pacientes/{id}/ficha', (pedido) =>
      json(200, { ...FICHA_CARGADA, ...(pedido.cuerpo as object), actualizadoEn: '2026-12-01T10:05:00-03:00' }),
    );
    await abrirFicha();

    escribir(campo(main(), 'Alergias') as unknown as HTMLInputElement, 'Penicilina y látex');
    boton(main(), 'Guardar ficha').click();

    await vi.waitFor(() => expect(aviso('status')).toContain('Ficha guardada.'));
    expect(servidor.ultimo('PUT', '/pacientes/1/ficha').cuerpo).toEqual({
      antecedentes: '',
      alergias: 'Penicilina y látex',
      medicacion: '',
    });
    expect(texto(main())).toContain('Actualizada por ana.ejemplo el 01/12/2026 a las 10:05');
  });

  it('los campos muestran un ejemplo y el contador junto al rótulo', async () => {
    await abrirFicha();

    const alergias = campo<HTMLTextAreaElement>(main(), 'Alergias');
    expect(alergias.placeholder).toBe('Ej.: penicilina, látex, anestesia local');
    expect(alergias.closest('.campo-suave')?.querySelector('.campo-suave-cabecera .contador')?.textContent).toBe('10 / 4000');
    const descripciones = (alergias.getAttribute('aria-describedby') ?? '').split(' ').map((id) => document.getElementById(id));
    expect(descripciones.map((d) => d?.className)).toEqual(['campo-error', 'contador']);
  });

  it('Texto demasiado largo', async () => {
    await abrirFicha();
    const antecedentes = campo<HTMLTextAreaElement>(main(), 'Antecedentes');
    antecedentes.removeAttribute('maxlength');

    escribir(antecedentes as unknown as HTMLInputElement, 'a'.repeat(4001));
    boton(main(), 'Guardar ficha').click();

    await vi.waitFor(() => expect(texto(main())).toContain('Máximo 4000 caracteres.'));
    expect(servidor.a('PUT', '/pacientes/1/ficha')).toHaveLength(0);
  });

  it('El servidor rechaza los datos', async () => {
    servidor.responder('PUT /pacientes/{id}/ficha', () =>
      json(422, { status: 422, detail: 'Datos inválidos', codigo: 'DATOS_INVALIDOS' }),
    );
    await abrirFicha();

    escribir(campo(main(), 'Alergias') as unknown as HTMLInputElement, 'Penicilina y látex');
    boton(main(), 'Guardar ficha').click();

    await vi.waitFor(() => expect(aviso('alert')).toContain('Revisá los datos de la ficha.'));
    expect(campo<HTMLTextAreaElement>(main(), 'Alergias').value).toBe('Penicilina y látex');
  });
});

describe('Odontograma en la ficha', () => {
  it('Estado vigente dibujado para el administrador', async () => {
    conSesion(SESION_ADMIN);
    responderFicha(FICHA_CARGADA, [
      { id: 1, pieza: 16, cara: 'O', estado: 'OBTURADO', profesionalId: 1, profesional: 'Ana Ejemplo', registradoEn: '2026-12-01T09:00:00-03:00' },
      { id: 2, pieza: 26, cara: 'M', estado: 'A_TRATAR', profesionalId: 1, profesional: 'Ana Ejemplo', registradoEn: '2026-12-01T09:00:00-03:00' },
    ]);
    await abrirFicha();

    expect(main().querySelector('polygon[data-pieza="16"][data-cara="O"]')?.getAttribute('data-estado')).toBe('OBTURADO');
    expect(main().querySelector('polygon[data-pieza="26"][data-cara="M"]')?.getAttribute('data-estado')).toBe('A_TRATAR');
    expect(main().querySelectorAll('polygon[data-estado]')).toHaveLength(2);
  });

  it('Guardar varios cambios juntos', async () => {
    servidor.responder('PUT /pacientes/{id}/odontograma', (pedido) => {
      const { cambios } = pedido.cuerpo as { cambios: Array<{ pieza: number; cara: string | null; estado: string }> };
      return json(
        200,
        cambios.map((c, i) => ({ id: 10 + i, ...c, profesionalId: 1, profesional: 'Ana Ejemplo', registradoEn: '2026-12-01T11:00:00-03:00' })),
      );
    });
    await abrirFicha();
    const pulsar = (pieza: number): void => {
      main().querySelector(`polygon[data-pieza="${pieza}"][data-cara="O"]`)?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    };
    const elegir = (nombre: string, valor: string): void => {
      const etiqueta = [...main().querySelectorAll('label.opcion')].find(
        (l) => l.querySelector(`input[name="${nombre}"]`) !== null && (l.textContent ?? '').endsWith(valor),
      );
      const entrada = etiqueta?.querySelector('input');
      if (entrada !== null && entrada !== undefined) {
        entrada.checked = true;
        entrada.dispatchEvent(new Event('change', { bubbles: true }));
      }
    };
    pulsar(16);
    elegir('estado', 'Caries');
    boton(main(), 'Sumar cambio').click();
    pulsar(36);
    elegir('cara', 'Pieza completa');
    elegir('estado', 'Ausente');
    boton(main(), 'Sumar cambio').click();

    boton(main(), 'Guardar cambios (2)').click();

    await vi.waitFor(() => expect(aviso('status')).toContain('Odontograma guardado.'));
    expect(servidor.ultimo('PUT', '/pacientes/1/odontograma').cuerpo).toEqual({
      cambios: [
        { pieza: 16, cara: 'O', estado: 'CARIES' },
        { pieza: 36, cara: null, estado: 'AUSENTE' },
      ],
    });
    expect(main().querySelector('polygon[data-pieza="36"][data-cara="L"]')?.getAttribute('data-estado')).toBe('AUSENTE');
    expect(main().querySelector('.pendientes')).toBeNull();
  });
});

describe('Historial del odontograma', () => {
  it('Un cambio posterior no borra el anterior', async () => {
    servidor.responder('GET /pacientes/{id}/odontograma/historial', () =>
      json(200, [
        { id: 1, pieza: 16, cara: 'O', estado: 'CARIES', profesionalId: 1, profesional: 'Ana Ejemplo', registradoEn: '2026-11-20T09:00:00-03:00' },
        { id: 2, pieza: 16, cara: 'O', estado: 'OBTURADO', profesionalId: 2, profesional: 'Bruno Prueba', registradoEn: '2026-12-01T09:00:00-03:00' },
      ]),
    );
    await abrirFicha();
    const desplegable = [...main().querySelectorAll('details')].find((d) => d.querySelector('summary')?.textContent === 'Historial del odontograma');
    expect(desplegable).toBeDefined();
    expect(servidor.a('GET', '/pacientes/1/odontograma/historial')).toHaveLength(0);

    if (desplegable !== undefined) {
      desplegable.open = true;
      desplegable.dispatchEvent(new Event('toggle'));
    }

    const filas = await vi.waitFor(() => {
      const encontradas = [...main().querySelectorAll<HTMLTableRowElement>('.tabla-historial tbody tr')];
      expect(encontradas).toHaveLength(2);
      return encontradas;
    });
    expect(texto(filas[0] ?? main())).toContain('Obturado');
    expect(texto(filas[0] ?? main())).toContain('Bruno Prueba');
    expect(texto(filas[1] ?? main())).toContain('Caries');
    expect(main().querySelectorAll('.tabla-historial button')).toHaveLength(0);
  });
});

describe('Solo lectura para el administrador', () => {
  it('El administrador no ve controles de edición', async () => {
    conSesion(SESION_ADMIN);
    await abrirFicha(TURNO_7);

    expect(main().querySelectorAll('textarea, input:not([type="radio"])')).toHaveLength(0);
    expect(botones()).not.toContain('Guardar ficha');
    expect(botones().some((b) => b.startsWith('Guardar cambios'))).toBe(false);
    expect(botones()).not.toContain('Marcar ausente');
    expect(texto(main())).toContain('Solo lectura: la ficha clínica la edita el odontólogo.');
  });
});

describe('Ausencias en la ficha', () => {
  it('Ausencia vigente y ausencia deshecha', async () => {
    responderFicha({
      ...FICHA_CARGADA,
      ausencias: [
        { turnoId: 3, inicio: '2026-11-20T09:00:00-03:00', profesionalId: 1, profesional: 'Ana Ejemplo', deshechaEn: '2026-11-20T09:22:00-03:00', deshechaPor: 'recepcion' },
        { turnoId: 7, inicio: '2026-12-01T10:00:00-03:00', profesionalId: 1, profesional: 'Ana Ejemplo', deshechaEn: null, deshechaPor: null },
      ],
    });
    await abrirFicha();

    const items = [...main().querySelectorAll<HTMLElement>('.lista-ficha li')].filter((li) => texto(li).includes('Ana Ejemplo'));
    expect(items).toHaveLength(2);
    expect(texto(items[0] ?? main())).toContain('01/12/2026 · 10:00');
    expect(texto(items[0] ?? main())).toContain('Ausente');
    expect(texto(items[1] ?? main())).toContain('Deshecha por recepcion el 20/11/2026 a las 09:22');
  });

  it('sin ausencias', async () => {
    await abrirFicha();

    expect(texto(main())).toContain('Sin ausencias registradas.');
  });
});

describe('Marcar ausente desde la ficha', () => {
  it('Marcar ausente', async () => {
    ahoraEs('10:20');
    servidor.responder('POST /pacientes/{id}/ausencias', () => json(200, {}));
    await abrirFicha(TURNO_7);
    const pedidosAntes = servidor.a('GET', '/pacientes/1/ficha').length;
    responderFicha({
      ...FICHA_CARGADA,
      ausencias: [{ turnoId: 7, inicio: TURNO_7.inicio, profesional: 'Ana Ejemplo', deshechaEn: null, deshechaPor: null }],
    });

    boton(main(), 'Marcar ausente').click();

    await vi.waitFor(() => expect(servidor.a('GET', '/pacientes/1/ficha')).toHaveLength(pedidosAntes + 1));
    expect(servidor.ultimo('POST', '/pacientes/1/ausencias').cuerpo).toEqual({ turnoId: 7 });
    await vi.waitFor(() => expect(texto(main())).toContain('01/12/2026 · 10:00'));
    expect(botones()).not.toContain('Marcar ausente');
  });

  it('Antes de la tolerancia', async () => {
    ahoraEs('10:10');
    await abrirFicha(TURNO_7);

    const marcar = boton(main(), 'Marcar ausente');
    expect(marcar.disabled).toBe(true);
    const pista = document.getElementById(marcar.getAttribute('aria-describedby') ?? '');
    expect(pista?.textContent).toBe('Desde las 10:15');
  });

  it('Turno de otro profesional', async () => {
    ahoraEs('10:20');
    conSesion(SESION_BRUNO);
    await abrirFicha(TURNO_7);

    expect(botones()).not.toContain('Marcar ausente');
  });

  it('Ficha abierta desde el buscador', async () => {
    ahoraEs('10:20');
    await abrirFicha(null);

    expect(botones()).not.toContain('Marcar ausente');
  });

  it('El servidor rechaza la ausencia', async () => {
    ahoraEs('10:20');
    servidor.responder('POST /pacientes/{id}/ausencias', () =>
      json(409, { status: 409, detail: 'Llegada registrada', codigo: 'LLEGADA_REGISTRADA' }),
    );
    await abrirFicha(TURNO_7);
    const pedidosAntes = servidor.a('GET', '/pacientes/1/ficha').length;

    boton(main(), 'Marcar ausente').click();

    await vi.waitFor(() => expect(aviso('alert')).toContain('El paciente ya tiene la llegada registrada.'));
    await vi.waitFor(() => expect(servidor.a('GET', '/pacientes/1/ficha')).toHaveLength(pedidosAntes + 1));
  });
});

describe('Errores al cargar la ficha', () => {
  it('Paciente inexistente', async () => {
    servidor.responder('GET /pacientes/{id}/ficha', () =>
      json(404, { status: 404, detail: 'No existe', codigo: 'RECURSO_NO_ENCONTRADO' }),
    );
    dejarSeleccion({ paciente: { id: 99, nombre: 'Paciente', apellido: 'Uno', dni: null }, turno: null });
    abrir();

    await vi.waitFor(() => expect(aviso('alert')).toContain('No encontramos a ese paciente.'));
    expect(() => campo(main(), 'Paciente')).not.toThrow();
  });

  it('sin permiso', async () => {
    servidor.responder('GET /pacientes/{id}/ficha', () => json(403, { status: 403, codigo: 'ACCESO_DENEGADO' }));
    dejarSeleccion({ paciente: { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: null }, turno: null });
    abrir();

    await vi.waitFor(() => expect(aviso('alert')).toContain('No tenés permiso para ver la ficha clínica.'));
  });

  it('Falla y reintento', async () => {
    servidor.responder('GET /pacientes/{id}/odontograma', () => {
      throw new TypeError('Failed to fetch');
    });
    dejarSeleccion({ paciente: { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: null }, turno: null });
    abrir();

    const reintentar = await vi.waitFor(() => boton(main(), 'Reintentar'));
    expect(aviso('alert')).not.toBe('');
    expect(texto(main())).not.toContain('Ficha básica');
    expect(texto(main())).not.toContain('Penicilina');

    responderFicha();
    reintentar.click();

    await fichaVisible();
    expect(servidor.a('GET', '/pacientes/1/ficha')).toHaveLength(2);
    expect(servidor.a('GET', '/pacientes/1/odontograma')).toHaveLength(2);
  });
});

describe('Datos de salud solo en la pantalla', () => {
  it('Nada queda guardado en el navegador', async () => {
    const guardados = vi.spyOn(Storage.prototype, 'setItem');
    const consola = (['log', 'info', 'warn', 'error', 'debug'] as const).map((metodo) =>
      vi.spyOn(console, metodo).mockImplementation(() => undefined),
    );
    responderFicha(FICHA_CARGADA, [
      { id: 1, pieza: 16, cara: 'O', estado: 'OBTURADO', profesionalId: 1, profesional: 'Ana Ejemplo', registradoEn: '2026-12-01T09:00:00-03:00' },
    ]);
    await abrirFicha();
    expect(campo<HTMLTextAreaElement>(main(), 'Alergias').value).toBe('Penicilina');

    const enConsola = JSON.stringify(consola.flatMap((espia) => espia.mock.calls));
    const enAlmacenamiento = JSON.stringify(guardados.mock.calls) + JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage });
    for (const revisado of [window.location.href, enConsola, enAlmacenamiento]) {
      expect(revisado).not.toContain('Penicilina');
      expect(revisado).not.toContain('OBTURADO');
      expect(revisado).not.toContain('Obturado');
    }
  });

  it('Salir borra la ficha de la pantalla', async () => {
    await abrirFicha();
    expect(campo<HTMLTextAreaElement>(main(), 'Alergias').value).toBe('Penicilina');

    boton(raiz(), 'Salir').click();

    await vi.waitFor(() => expect(raiz().querySelector('.ficha-columnas')).toBeNull());
    expect(raiz().querySelectorAll('textarea')).toHaveLength(0);
    expect(raiz().innerHTML).not.toContain('Penicilina');
  });
});
