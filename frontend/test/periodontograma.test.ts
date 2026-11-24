import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { dejarSeleccion } from '../src/ficha/seleccion.ts';
import { abrirApp, boton, campo, escribir, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';

/**
 * C-46 frontend-periodontograma: un test por escenario del delta `frontend-ficha-clinica` (sección
 * "Periodontograma" de la ficha). Datos ficticios del seed: "Paciente Uno" (id 1), `ana.ejemplo`, `bruno.prueba`
 * y `admin`.
 */

const SESION_ANA = { autenticado: true, username: 'ana.ejemplo', rol: 'ODONTOLOGO', profesionalId: 1 };
const SESION_ADMIN = { autenticado: true, username: 'admin', rol: 'ADMIN' };
const FICHA = { pacienteId: 1, antecedentes: '', alergias: '', medicacion: '', actualizadoPor: null, actualizadoEn: null, ausencias: [] };
const CODIGOS = ['MV', 'V', 'DV', 'ML', 'L', 'DL'] as const;

type Medida = readonly [number, number, boolean?, boolean?];

function sitios(medidas: readonly Medida[]): object[] {
  return medidas.map(([profundidadSondaje, recesion, sangrado = false, placa = false], i) => ({
    sitio: CODIGOS[i],
    profundidadSondaje,
    recesion,
    sangrado,
    placa,
  }));
}

const SANOS: Medida[] = Array.from({ length: 6 }, () => [2, -2] as const);

function pieza(numero: number, estado: string, medidas: readonly Medida[] = SANOS, extra: object = {}): object {
  return { pieza: numero, ausente: false, movilidad: 0, furcacion: null, estado, sitios: sitios(medidas), ...extra };
}

function ausente(numero: number): object {
  return { pieza: numero, ausente: true, movilidad: null, furcacion: null, estado: null, sitios: [] };
}

const P16: Medida[] = [[4, 0, true], [4, 0], [3, -1, false, true], [3, 0, true], [2, -1], [3, 0, false, true]];

const V9 = {
  id: 9,
  profesionalId: 2,
  profesional: 'Bruno Prueba',
  registradoEn: '2026-12-01T09:00:00-03:00',
  piezas: [
    pieza(16, 'MODERADO', P16, { movilidad: 1, furcacion: 1 }),
    pieza(21, 'SEVERO', SANOS.map(() => [5, 2] as const)),
    ausente(18),
    pieza(11, 'SANO'),
    pieza(46, 'MODERADO', SANOS.map(() => [3, 0] as const), { furcacion: 2 }),
  ],
};

const V4 = {
  id: 4,
  profesionalId: 1,
  profesional: 'Ana Ejemplo',
  registradoEn: '2026-11-20T10:30:00-03:00',
  piezas: [pieza(16, 'LEVE')],
};

function resumen(version: { id: number; profesionalId: number; profesional: string; registradoEn: string }): object {
  return { id: version.id, profesionalId: version.profesionalId, profesional: version.profesional, registradoEn: version.registradoEn };
}

let servidor: ServidorFalso;
let app: AppDePrueba | undefined;

function main(): HTMLElement {
  const raiz = app?.raiz;
  if (raiz === undefined) {
    throw new Error('La aplicación no está abierta');
  }
  return raiz.querySelector('main') ?? raiz;
}

function seccion(): HTMLElement {
  const encontrada = main().querySelector<HTMLElement>('.perio-seccion');
  if (encontrada === null) {
    throw new Error('No está la sección del periodontograma');
  }
  return encontrada;
}

function conSesion(sesion: object): void {
  servidor.responder('GET /public/sesion', () => json(200, sesion));
}

function responderPerio(versiones: ReadonlyArray<typeof V9> = [V9, V4]): void {
  servidor.responder('GET /pacientes/{id}/periodontograma', () => json(200, versiones.map(resumen)));
  servidor.responder('GET /pacientes/{id}/periodontograma/{id}', (pedido) => {
    const id = Number(pedido.ruta.split('/').pop());
    const version = versiones.find((v) => v.id === id);
    return version === undefined ? json(404, { status: 404, codigo: 'RECURSO_NO_ENCONTRADO' }) : json(200, version);
  });
}

async function abrirFicha(): Promise<void> {
  dejarSeleccion({ paciente: { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: null }, turno: null });
  app = abrirApp(servidor, '#/pacientes');
  await vi.waitFor(() => expect(main().querySelector('.ficha-columnas')).not.toBeNull());
}

/** Espera a que la sección muestre la versión (o el estado vacío). */
async function seccionLista(): Promise<void> {
  await vi.waitFor(() => expect(seccion().querySelector('.perio-mapa, .estado-vacio, [role="alert"]:not(:empty)')).not.toBeNull());
}

function botonPieza(numero: number): HTMLButtonElement {
  const encontrado = seccion().querySelector<HTMLButtonElement>(`.perio-pieza[data-pieza="${numero}"]`);
  if (encontrado === null) {
    throw new Error(`No está la pieza ${numero}`);
  }
  return encontrado;
}

function elegir(numero: number): void {
  botonPieza(numero).click();
}

function control<T extends HTMLElement = HTMLInputElement>(nombre: string): T {
  const encontrado = seccion().querySelector<T>(`[aria-label="${nombre}"]`);
  if (encontrado === null) {
    throw new Error(`No está el control ${nombre}`);
  }
  return encontrado;
}

function errorDe(elemento: HTMLElement): string {
  const ids = (elemento.getAttribute('aria-describedby') ?? '').split(/\s+/).filter((id) => id !== '');
  return ids.map((id) => document.getElementById(id)).find((e) => e?.classList.contains('campo-error'))?.textContent ?? '';
}

function avisos(rol: 'alert' | 'status'): string {
  return [...seccion().querySelectorAll(`[role="${rol}"]`)].map((a) => a.textContent ?? '').join('');
}

function pedidosAlPeriodontograma(): number {
  return servidor.pedidos.filter((p) => p.ruta.includes('/periodontograma')).length;
}

async function nuevaVersion(): Promise<void> {
  await abrirFicha();
  await seccionLista();
  boton(seccion(), 'Nueva versión').click();
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-12-01T11:00:00-03:00'));
  servidor = new ServidorFalso();
  conSesion(SESION_ANA);
  servidor.responder('GET /pacientes/{id}/ficha', () => json(200, FICHA));
  servidor.responder('GET /pacientes/{id}/odontograma', () => json(200, []));
  responderPerio();
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  document.querySelectorAll('dialog').forEach((d) => d.remove());
  vi.useRealTimers();
  vi.restoreAllMocks();
  localStorage.clear();
  sessionStorage.clear();
});

describe('Periodontograma en la ficha', () => {
  it('Abrir la ficha con periodontograma', async () => {
    await abrirFicha();
    await seccionLista();

    expect(servidor.a('GET', '/pacientes/1/periodontograma')).toHaveLength(1);
    expect(servidor.a('GET', '/pacientes/1/periodontograma/9')).toHaveLength(1);
    expect(servidor.a('GET', '/pacientes/1/periodontograma/4')).toHaveLength(0);
    expect(texto(seccion())).toContain('Versión del 01/12/2026 a las 09:00 · Bruno Prueba');
    // Debajo del odontograma y arriba del resto de la ficha.
    const odontograma = main().querySelector('.odontograma')?.closest('section') ?? null;
    expect(odontograma?.nextElementSibling).toBe(seccion());
  });

  it('Paciente sin periodontograma', async () => {
    responderPerio([]);
    await abrirFicha();
    await seccionLista();

    expect(texto(seccion())).toContain('Todavía no hay periodontogramas de este paciente.');
    expect(servidor.pedidos.filter((p) => /\/periodontograma\/\d+$/.test(p.ruta))).toEqual([]);
  });

  it('Falla la carga del periodontograma', async () => {
    servidor.responder('GET /pacientes/{id}/periodontograma', () => {
      throw new TypeError('Failed to fetch');
    });
    await abrirFicha();

    const reintentar = await vi.waitFor(() => boton(seccion(), 'Reintentar'));
    expect(avisos('alert')).toBe('No pudimos cargar el periodontograma. Revisá la conexión e intentá de nuevo.');
    expect(main().querySelector('.ficha-columnas')).not.toBeNull();
    expect(main().querySelector('.odontograma')).not.toBeNull();

    responderPerio();
    reintentar.click();

    await vi.waitFor(() => expect(seccion().querySelector('.perio-mapa')).not.toBeNull());
    expect(servidor.a('GET', '/pacientes/1/periodontograma')).toHaveLength(2);
    expect(servidor.a('GET', '/pacientes/1/ficha')).toHaveLength(1);
    expect(servidor.a('GET', '/pacientes/1/odontograma')).toHaveLength(1);
  });
});

describe('Mapa de piezas con su estado', () => {
  it('Estados dibujados', async () => {
    responderPerio([{ ...V9, piezas: [pieza(16, 'SANO'), pieza(21, 'SEVERO'), ausente(18)] }]);
    await abrirFicha();
    await seccionLista();

    expect(seccion().querySelectorAll('.perio-pieza')).toHaveLength(32);
    expect(texto(botonPieza(16))).toContain('Sano');
    expect(texto(botonPieza(21))).toContain('Severo');
    expect(texto(botonPieza(18))).toContain('Ausente');
    expect(botonPieza(16).dataset['estado']).toBe('SANO');
    const otras = [...seccion().querySelectorAll<HTMLButtonElement>('.perio-pieza')].filter(
      (b) => !['16', '21', '18'].includes(b.dataset['pieza'] ?? ''),
    );
    expect(otras.every((b) => texto(b).includes('Sin cargar'))).toBe(true);
  });
});

describe('Dibujo de las piezas', () => {
  it('Líneas del margen y de la bolsa', async () => {
    await abrirFicha();
    await seccionLista();

    const dibujo = botonPieza(16).querySelector('svg');
    // Pieza 16 (cuadrante 1): mesial a la derecha del dibujo (x 32), vestibular en el medio (x 20).
    expect(dibujo?.querySelector('.perio-linea-margen')?.getAttribute('points')).toBe('32,55 20,55 8,58.2');
    expect(dibujo?.querySelector('.perio-linea-fondo')?.getAttribute('points')).toBe('32,42.2 20,42.2 8,48.6');
    expect(dibujo?.getAttribute('aria-hidden')).toBe('true');
    // Superior con la raíz arriba; inferior invertido.
    expect(dibujo?.querySelector('g')?.getAttribute('transform')).toBeNull();
    expect(botonPieza(46).querySelector('svg g')?.getAttribute('transform')).toBe('translate(0 100) scale(1 -1)');
    expect(botonPieza(16).querySelectorAll('.perio-raiz')).toHaveLength(3);
    // Ausente tachada y sin líneas; sin cargar sin líneas.
    expect(botonPieza(18).querySelector('.perio-cruz')).not.toBeNull();
    expect(botonPieza(18).querySelector('.perio-linea-margen')).toBeNull();
    expect(botonPieza(28).querySelector('.perio-linea-margen')).toBeNull();
  });
});

describe('Detalle de una pieza', () => {
  it('Ver los sitios de una pieza superior', async () => {
    await abrirFicha();
    await seccionLista();

    elegir(16);

    const filas = [...seccion().querySelectorAll<HTMLTableRowElement>('.perio-tabla tbody tr')];
    expect(filas.map((f) => f.querySelector('th')?.firstChild?.textContent)).toEqual([
      'Mesiovestibular',
      'Vestibular',
      'Distovestibular',
      'Mesiopalatino',
      'Palatino',
      'Distopalatino',
    ]);
    const vestibular = [...(filas[1]?.querySelectorAll('td') ?? [])].map((td) => td.textContent);
    expect(vestibular.slice(0, 3)).toEqual(['4 mm', '0 mm', '4 mm']);
    expect(texto(seccion())).toContain('Movilidad 1');
    expect(texto(seccion())).toContain('Furcación 1');
    expect(seccion().querySelector('.perio-chip')?.textContent).toBe('Moderado');
    expect(botonPieza(16).getAttribute('aria-pressed')).toBe('true');
  });
});

describe('Vista grande de la pieza', () => {
  it('Pieza inferior en grande', async () => {
    await abrirFicha();
    await seccionLista();

    elegir(46);

    const vistas = [...seccion().querySelectorAll('.perio-vista')];
    expect(vistas.map((v) => v.querySelector('figcaption')?.textContent)).toEqual(['Vestibular', 'Lingual']);
    for (const vista of vistas) {
      expect(vista.querySelector('svg g')?.getAttribute('transform')).toBe('translate(0 100) scale(1 -1)');
      expect(vista.querySelectorAll('.perio-grilla').length).toBeGreaterThan(10);
      expect(vista.querySelector('.perio-linea-margen')).not.toBeNull();
    }
  });

  it('Cara con un sitio vacío', async () => {
    await nuevaVersion();
    elegir(36);
    boton(seccion(), 'Cargar pieza').click();
    for (const [i, codigo] of ['mesiovestibular', 'vestibular', 'distovestibular', 'mesiolingual', 'lingual'].entries()) {
      escribir(control(`Pieza 36, ${codigo}: profundidad de sondaje en mm`), String(2 + (i % 2)));
      escribir(control(`Pieza 36, ${codigo}: recesión en mm`), '-1');
    }

    const [vestibular, lingual] = [...seccion().querySelectorAll('.perio-vista')];
    expect(vestibular?.querySelector('.perio-linea-margen')).not.toBeNull();
    expect(lingual?.querySelector('figcaption')?.textContent).toBe('Lingual');
    expect(lingual?.querySelector('.perio-linea-margen')).toBeNull();
    expect(lingual?.querySelector('.perio-linea-fondo')).toBeNull();
  });
});

describe('Versiones anteriores', () => {
  it('Abrir una versión anterior', async () => {
    await abrirFicha();
    await seccionLista();
    const selector = campo<HTMLSelectElement>(seccion(), 'Versión');
    expect([...selector.options].map((o) => o.textContent)).toEqual([
      '01/12/2026 09:00 · Bruno Prueba (la más nueva)',
      '20/11/2026 10:30 · Ana Ejemplo',
    ]);

    escribir(selector, '4');

    await vi.waitFor(() => expect(texto(seccion())).toContain('Versión del 20/11/2026 a las 10:30 · Ana Ejemplo'));
    expect(servidor.a('GET', '/pacientes/1/periodontograma/4')).toHaveLength(1);
    expect(texto(botonPieza(16))).toContain('Leve');
    elegir(16);
    expect(seccion().querySelectorAll('.perio-tabla input')).toHaveLength(0);
    const textos = [...seccion().querySelectorAll('button')].map((b) => b.textContent ?? '');
    expect(textos.some((t) => /editar|borrar|eliminar/i.test(t))).toBe(false);
  });
});

describe('Nueva versión precargada', () => {
  it('La nueva versión copia la última', async () => {
    await abrirFicha();
    await seccionLista();
    // Aunque se esté mirando una anterior, la copia sale de la más nueva.
    escribir(campo<HTMLSelectElement>(seccion(), 'Versión'), '4');
    await vi.waitFor(() => expect(texto(seccion())).toContain('20/11/2026 a las 10:30'));
    const antes = pedidosAlPeriodontograma();

    boton(seccion(), 'Nueva versión').click();

    expect(texto(seccion())).toContain('Periodontograma · nueva versión');
    elegir(16);
    expect(control('Pieza 16, mesiovestibular: profundidad de sondaje en mm').value).toBe('4');
    expect(control('Pieza 16, distovestibular: recesión en mm').value).toBe('-1');
    expect(campo<HTMLSelectElement>(seccion(), 'Movilidad').value).toBe('1');
    elegir(21);
    expect(control('Pieza 21, vestibular: recesión en mm').value).toBe('2');
    expect(pedidosAlPeriodontograma()).toBe(antes);
  });

  it('Primera versión', async () => {
    responderPerio([]);
    await nuevaVersion();

    expect(texto(seccion())).toContain('Elegí una pieza para cargarla.');
    expect([...seccion().querySelectorAll('.perio-pieza')].every((b) => b.getAttribute('data-estado') === 'SIN_CARGAR')).toBe(true);
  });
});

describe('Cargar, marcar ausente y quitar piezas', () => {
  it('Cargar una pieza nueva', async () => {
    await nuevaVersion();
    elegir(36);
    expect(texto(seccion())).toContain('Esta pieza no está en la versión.');

    boton(seccion(), 'Cargar pieza').click();

    const campos = [...seccion().querySelectorAll<HTMLInputElement>('.perio-tabla input[type="number"]')];
    expect(campos).toHaveLength(12);
    expect(campos.every((c) => c.value === '')).toBe(true);
    expect(campo<HTMLSelectElement>(seccion(), 'Movilidad').value).toBe('');
    expect(campo<HTMLSelectElement>(seccion(), 'Furcación').value).toBe('');
    expect(texto(botonPieza(36))).toContain('En carga');
  });

  it('Una pieza sin furcación', async () => {
    await nuevaVersion();

    elegir(11);

    expect(() => campo(seccion(), 'Movilidad')).not.toThrow();
    expect(() => campo(seccion(), 'Furcación')).toThrow();
  });

  it('Marcar una pieza ausente', async () => {
    await nuevaVersion();
    elegir(16);
    const casilla = campo(seccion(), 'Pieza ausente');

    casilla.click();

    expect(seccion().querySelector('.perio-tabla')).toBeNull();
    expect(() => campo(seccion(), 'Movilidad')).toThrow();
    expect(() => campo(seccion(), 'Furcación')).toThrow();
    expect(botonPieza(16).getAttribute('aria-label')).toBe('Pieza 16: Ausente');
  });

  it('Quitar una pieza de la carga', async () => {
    servidor.responder('POST /pacientes/{id}/periodontograma', (pedido) => json(201, { ...V9, id: 12, piezas: (pedido.cuerpo as { piezas: object[] }).piezas }));
    responderPerio([{ ...V9, piezas: [pieza(16, 'SANO'), pieza(21, 'LEVE')] }]);
    await nuevaVersion();
    elegir(21);

    boton(seccion(), 'Quitar de esta versión').click();

    expect(botonPieza(21).getAttribute('aria-label')).toBe('Pieza 21: Sin cargar');
    boton(seccion(), 'Guardar versión').click();
    await vi.waitFor(() => expect(servidor.a('POST', '/pacientes/1/periodontograma')).toHaveLength(1));
    const enviado = servidor.ultimo('POST', '/pacientes/1/periodontograma').cuerpo as { piezas: Array<{ pieza: number }> };
    expect(enviado.piezas.map((p) => p.pieza)).toEqual([16]);
  });
});

describe('Convención de la recesión', () => {
  it('Ayuda visible', async () => {
    await nuevaVersion();
    elegir(16);

    const recesion = control('Pieza 16, mesiovestibular: recesión en mm');
    const ayuda = (recesion.getAttribute('aria-describedby') ?? '')
      .split(/\s+/)
      .map((id) => document.getElementById(id))
      .find((e) => e?.classList.contains('perio-ayuda'));
    expect(ayuda?.textContent).toBe(
      'Recesión: del límite amelocementario al margen de la encía. Cargala en negativo si el margen está por encima del límite, como en una encía sana (por ejemplo, −2).',
    );
    expect(recesion.min).toBe('-5');
    expect(recesion.max).toBe('15');
  });
});

describe('Validación antes de guardar', () => {
  it('Falta completar una pieza', async () => {
    await nuevaVersion();
    elegir(36);
    boton(seccion(), 'Cargar pieza').click();
    for (const codigo of ['mesiovestibular', 'vestibular', 'distovestibular', 'mesiolingual', 'lingual']) {
      escribir(control(`Pieza 36, ${codigo}: profundidad de sondaje en mm`), '3');
      escribir(control(`Pieza 36, ${codigo}: recesión en mm`), '-1');
    }
    escribir(campo<HTMLSelectElement>(seccion(), 'Movilidad'), '0');
    elegir(11);
    const antes = pedidosAlPeriodontograma();

    boton(seccion(), 'Guardar versión').click();

    expect(pedidosAlPeriodontograma()).toBe(antes);
    expect(avisos('alert')).toContain('Completá los 6 sitios y la movilidad de la pieza 36.');
    expect(document.activeElement).toBe(botonPieza(36));
    expect(botonPieza(36).hasAttribute('data-error')).toBe(true);
    expect(errorDe(control('Pieza 36, distolingual: profundidad de sondaje en mm'))).toBe('Completá este dato.');
    expect(control('Pieza 36, distolingual: recesión en mm').getAttribute('aria-invalid')).toBe('true');
  });

  it('Valor fuera de rango', async () => {
    await nuevaVersion();
    elegir(11);
    escribir(control('Pieza 11, mesiovestibular: profundidad de sondaje en mm'), '16');
    const antes = pedidosAlPeriodontograma();

    boton(seccion(), 'Guardar versión').click();

    expect(pedidosAlPeriodontograma()).toBe(antes);
    expect(errorDe(control('Pieza 11, mesiovestibular: profundidad de sondaje en mm'))).toBe('Entre 0 y 15 mm.');
  });

  it('Versión sin piezas', async () => {
    responderPerio([]);
    await nuevaVersion();
    const antes = pedidosAlPeriodontograma();

    boton(seccion(), 'Guardar versión').click();

    expect(pedidosAlPeriodontograma()).toBe(antes);
    expect(avisos('alert')).toContain('Cargá al menos una pieza.');
  });
});

describe('Guardar la versión', () => {
  const V12 = {
    id: 12,
    profesionalId: 1,
    profesional: 'Ana Ejemplo',
    registradoEn: '2026-12-01T11:00:00-03:00',
    piezas: [pieza(16, 'LEVE', SANOS.map(() => [3, -1] as const)), ausente(18)],
  };

  it('Guardar una versión', async () => {
    responderPerio([{ ...V9, piezas: [pieza(16, 'MODERADO', P16, { movilidad: 1, furcacion: 1 }), ausente(18)] }]);
    servidor.responder('POST /pacientes/{id}/periodontograma', () => json(201, V12));
    await nuevaVersion();
    elegir(16);
    escribir(control('Pieza 16, mesiovestibular: recesión en mm'), '-1');

    boton(seccion(), 'Guardar versión').click();

    await vi.waitFor(() => expect(avisos('status')).toContain('Periodontograma guardado.'));
    const enviado = servidor.ultimo('POST', '/pacientes/1/periodontograma').cuerpo as { piezas: Array<Record<string, unknown>> };
    expect(enviado.piezas.map((p) => p['pieza'])).toEqual([16, 18]);
    expect(enviado.piezas[1]).toEqual({ pieza: 18, ausente: true });
    expect((enviado.piezas[0]?.['sitios'] as Array<Record<string, unknown>>)[0]).toEqual({
      sitio: 'MV',
      profundidadSondaje: 4,
      recesion: -1,
      sangrado: true,
      placa: false,
    });
    expect(texto(seccion())).toContain('Versión del 01/12/2026 a las 11:00 · Ana Ejemplo');
    expect(campo<HTMLSelectElement>(seccion(), 'Versión').options[0]?.textContent).toBe('01/12/2026 11:00 · Ana Ejemplo (la más nueva)');
    expect(texto(botonPieza(16))).toContain('Leve');
    expect(seccion().querySelectorAll('.perio-tabla input')).toHaveLength(0);
  });

  it('El servidor rechaza una furcación', async () => {
    servidor.responder('POST /pacientes/{id}/periodontograma', () =>
      json(422, { status: 422, detail: 'Furcación', codigo: 'FURCACION_NO_APLICA' }),
    );
    await nuevaVersion();
    elegir(11);
    escribir(control('Pieza 11, vestibular: profundidad de sondaje en mm'), '3');

    boton(seccion(), 'Guardar versión').click();

    await vi.waitFor(() => expect(avisos('alert')).toContain('La furcación solo se carga en molares y en las piezas 14 y 24.'));
    expect(control('Pieza 11, vestibular: profundidad de sondaje en mm').value).toBe('3');
    expect(texto(seccion())).toContain('Periodontograma · nueva versión');
  });

  it('El servidor rechaza sitios incompletos', async () => {
    servidor.responder('POST /pacientes/{id}/periodontograma', () =>
      json(422, { status: 422, detail: 'Sitios', codigo: 'SITIOS_INCOMPLETOS' }),
    );
    await nuevaVersion();

    boton(seccion(), 'Guardar versión').click();

    await vi.waitFor(() => expect(avisos('alert')).toContain('Cada pieza cargada necesita sus 6 sitios y la movilidad.'));
    expect(texto(seccion())).toContain('Periodontograma · nueva versión');
    elegir(16);
    expect(control('Pieza 16, mesiovestibular: profundidad de sondaje en mm').value).toBe('4');
  });
});

describe('Descartar una carga sin guardar', () => {
  it('Cambiar de paciente con una carga sin guardar', async () => {
    await nuevaVersion();
    elegir(16);
    escribir(control('Pieza 16, mesiovestibular: profundidad de sondaje en mm'), '6');

    boton(main(), 'Cambiar de paciente').click();

    const dialogo = await vi.waitFor(() => {
      const abierto = document.querySelector<HTMLDialogElement>('dialog');
      expect(abierto).not.toBeNull();
      return abierto as HTMLDialogElement;
    });
    expect(texto(dialogo)).toContain('Hay un periodontograma sin guardar. ¿Descartarlo?');

    boton(dialogo, 'Seguir editando').click();

    expect(document.querySelector('dialog')).toBeNull();
    expect(control('Pieza 16, mesiovestibular: profundidad de sondaje en mm').value).toBe('6');
    expect(main().querySelector('.ficha-columnas')).not.toBeNull();
  });

  it('descartar en el diálogo vuelve al buscador', async () => {
    await nuevaVersion();
    elegir(16);
    escribir(control('Pieza 16, mesiovestibular: profundidad de sondaje en mm'), '6');
    boton(main(), 'Cambiar de paciente').click();
    const dialogo = await vi.waitFor(() => document.querySelector<HTMLDialogElement>('dialog') as HTMLDialogElement);

    boton(dialogo, 'Descartar').click();

    await vi.waitFor(() => expect(texto(main())).toContain('Buscá un paciente para ver su ficha.'));
  });

  it('Cancelar sin cambios', async () => {
    await nuevaVersion();

    boton(seccion(), 'Cancelar').click();

    expect(document.querySelector('dialog')).toBeNull();
    expect(texto(seccion())).toContain('Versión del 01/12/2026 a las 09:00 · Bruno Prueba');
    expect(() => boton(seccion(), 'Nueva versión')).not.toThrow();
  });

  it('cancelar con cambios pregunta antes', async () => {
    await nuevaVersion();
    elegir(16);
    escribir(control('Pieza 16, mesiovestibular: profundidad de sondaje en mm'), '6');

    boton(seccion(), 'Cancelar').click();

    const dialogo = await vi.waitFor(() => document.querySelector<HTMLDialogElement>('dialog') as HTMLDialogElement);
    expect(texto(dialogo)).toContain('Hay un periodontograma sin guardar. ¿Descartarlo?');
    boton(dialogo, 'Descartar').click();
    await vi.waitFor(() => expect(texto(seccion())).toContain('Versión del 01/12/2026 a las 09:00 · Bruno Prueba'));
  });
});

describe('Periodontograma accesible', () => {
  it('Nombre accesible de una pieza', async () => {
    await abrirFicha();
    await seccionLista();

    const p21 = botonPieza(21);
    p21.focus();

    expect(document.activeElement).toBe(p21);
    expect(p21.tagName).toBe('BUTTON');
    expect(p21.getAttribute('aria-label')).toBe('Pieza 21: Severo');
  });

  it('Nombre accesible de un campo', async () => {
    await nuevaVersion();
    elegir(16);

    const profundidad = control('Pieza 16, mesiovestibular: profundidad de sondaje en mm');
    profundidad.focus();

    expect(document.activeElement).toBe(profundidad);
    expect(profundidad.type).toBe('number');
  });
});

describe('Solo lectura para el administrador', () => {
  it('El administrador consulta el periodontograma', async () => {
    conSesion(SESION_ADMIN);
    await abrirFicha();
    await seccionLista();

    expect(seccion().querySelector('.perio-mapa')).not.toBeNull();
    expect(() => campo(seccion(), 'Versión')).not.toThrow();
    expect(() => boton(seccion(), 'Nueva versión')).toThrow();
    elegir(16);
    expect(seccion().querySelectorAll('.perio-tabla input')).toHaveLength(0);
  });
});

describe('Datos de salud solo en la pantalla', () => {
  it('Una carga del periodontograma no queda en el navegador', async () => {
    const guardados = vi.spyOn(Storage.prototype, 'setItem');
    const consola = (['log', 'info', 'warn', 'error', 'debug'] as const).map((metodo) =>
      vi.spyOn(console, metodo).mockImplementation(() => undefined),
    );
    await nuevaVersion();
    elegir(21);
    escribir(control('Pieza 21, mesiovestibular: profundidad de sondaje en mm'), '7');

    const enConsola = JSON.stringify(consola.flatMap((espia) => espia.mock.calls));
    const enAlmacenamiento = JSON.stringify(guardados.mock.calls) + JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage });
    expect(window.location.hash).toBe('#/pacientes');
    for (const revisado of [window.location.href, enConsola, enAlmacenamiento]) {
      for (const dato of ['Severo', 'SEVERO', 'profundidad', 'recesion', 'periodonto']) {
        expect(revisado).not.toContain(dato);
      }
    }
  });
});
