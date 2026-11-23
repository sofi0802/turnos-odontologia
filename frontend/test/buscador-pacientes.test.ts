import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { crearBuscadorPacientes, type BuscadorPacientes } from '../src/agenda/buscador-pacientes.ts';
import type { ClienteApi } from '../src/shared/api/cliente.ts';
import { ErrorGenerico, ErrorSesion } from '../src/shared/api/errores.ts';
import { abrirApp, boton, campo, errorDeCampo, escribir, json, ServidorFalso, texto, type AppDePrueba } from './apoyo.ts';

// Datos ficticios, iguales al seed de la KB.
const UNO = { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: '99000001' };
const DOS = { id: 2, nombre: 'Paciente', apellido: 'Dos', dni: '99000002' };
const CARLA = { id: 3, nombre: 'Carla', apellido: 'Ficticia', dni: '99000003' };

let servidor: ServidorFalso;
let buscador: BuscadorPacientes;
let sesionVencida: ReturnType<typeof vi.fn<(causa: unknown) => boolean>>;
let app: AppDePrueba | undefined;

function montar(api: ClienteApi): void {
  sesionVencida = vi.fn<(causa: unknown) => boolean>(() => false);
  buscador = crearBuscadorPacientes(api, { sesionVencida });
  document.body.replaceChildren(buscador.contenedor);
}

function estado(): string {
  return texto(buscador.contenedor.querySelector<HTMLElement>('[role="status"]') ?? document.body);
}

function opciones(): HTMLElement[] {
  return [...buscador.contenedor.querySelectorAll<HTMLElement>('[role="option"]')];
}

function textosDeOpciones(): string[] {
  return opciones().map((o) => texto(o).trim());
}

function escribirTexto(valor: string): void {
  escribir(buscador.control, valor);
}

function teclear(tecla: string): KeyboardEvent {
  const evento = new KeyboardEvent('keydown', { key: tecla, bubbles: true, cancelable: true });
  buscador.control.dispatchEvent(evento);
  return evento;
}

/** Escribe, espera los 300 ms y deja pasar las respuestas pendientes. */
async function buscarYEsperar(valor: string): Promise<void> {
  escribirTexto(valor);
  await vi.advanceTimersByTimeAsync(300);
}

function consultas(): string[] {
  return servidor.a('GET', '/pacientes').map((p) => p.query.get('q') ?? '');
}

/** API cuyas respuestas se resuelven a mano, para probar el orden de llegada. */
function apiControlada(): { api: ClienteApi; pedir: string[]; responder(ruta: string, cuerpo: unknown): void } {
  const pendientes = new Map<string, (cuerpo: unknown) => void>();
  const pedir: string[] = [];
  const noUsada = (): Promise<never> => Promise.reject(new Error('no se usa en este test'));
  const api: ClienteApi = {
    solicitar: noUsada,
    crear: noUsada,
    actualizar: noUsada,
    borrar: noUsada,
    obtener: <T>(ruta: string) =>
      new Promise<T | undefined>((resolver) => {
        pedir.push(ruta);
        pendientes.set(ruta, (cuerpo) => resolver(cuerpo as T));
      }),
  };
  return {
    api,
    pedir,
    responder(ruta, cuerpo) {
      const resolver = pendientes.get(ruta);
      if (resolver === undefined) {
        throw new Error(`No hay un pedido pendiente a ${ruta}`);
      }
      resolver(cuerpo);
    },
  };
}

beforeEach(() => {
  vi.useFakeTimers();
  servidor = new ServidorFalso();
  servidor.responder('GET /pacientes', () => json(200, []));
  history.replaceState(null, '', '#/agenda');
  montar(servidor.api);
});

afterEach(() => {
  app?.router.detener();
  app = undefined;
  for (const abierto of document.querySelectorAll('dialog')) {
    abierto.remove();
  }
  vi.useRealTimers();
  localStorage.clear();
  sessionStorage.clear();
  document.body.replaceChildren();
});

describe('Búsqueda mientras se escribe', () => {
  it('Busca después de la espera', async () => {
    escribirTexto('fict');
    await vi.advanceTimersByTimeAsync(299);
    expect(consultas()).toEqual([]);

    await vi.advanceTimersByTimeAsync(1);

    expect(consultas()).toEqual(['fict']);
  });

  it('Escritura rápida', async () => {
    for (const parcial of ['f', 'fi', 'fic', 'fict']) {
      escribirTexto(parcial);
      await vi.advanceTimersByTimeAsync(100);
    }
    expect(consultas()).toEqual([]);

    await vi.advanceTimersByTimeAsync(300);

    expect(consultas()).toEqual(['fict']);
  });

  it('Texto demasiado corto', async () => {
    await buscarYEsperar('f');
    expect(estado()).toContain('Escribí al menos 2 letras o 3 números del DNI.');
    await buscarYEsperar('99');

    expect(consultas()).toEqual([]);
    expect(estado()).toContain('Escribí al menos 2 letras o 3 números del DNI.');
  });

  it('el texto se recorta y se codifica para la dirección', async () => {
    await buscarYEsperar('  paciente dos ');

    expect(servidor.a('GET', '/pacientes')[0]?.query.get('q')).toBe('paciente dos');
    expect(consultas()).toEqual(['paciente dos']);
  });
});

describe('Estado de búsqueda en curso', () => {
  it('Buscando', async () => {
    const controlada = apiControlada();
    montar(controlada.api);

    await buscarYEsperar('fict');

    expect(controlada.pedir).toEqual(['/pacientes?q=fict']);
    expect(estado()).toContain('Buscando…');
    expect(buscador.contenedor.querySelector('[role="status"]')).not.toBeNull();
  });
});

describe('Lista de coincidencias', () => {
  it('Ver las coincidencias', async () => {
    servidor.responder('GET /pacientes', () => json(200, [DOS, UNO]));

    await buscarYEsperar('paciente');

    expect(textosDeOpciones()).toEqual(['Dos, Paciente · DNI 99000002', 'Uno, Paciente · DNI 99000001']);
    expect(estado()).toContain('2 pacientes encontrados.');
    expect(buscador.contenedor.querySelector('[role="listbox"]')).not.toBeNull();
    expect(buscador.control.getAttribute('aria-expanded')).toBe('true');
  });

  it('Límite de resultados alcanzado', async () => {
    const diez = Array.from({ length: 10 }, (_, i) => ({
      id: 100 + i,
      nombre: `Nombre${i}`,
      apellido: 'Prueba',
      dni: `9910000${i}`,
    }));
    servidor.responder('GET /pacientes', () => json(200, diez));

    await buscarYEsperar('prueba');

    expect(opciones()).toHaveLength(10);
    expect(texto(buscador.contenedor)).toContain('Se muestran las primeras 10 coincidencias. Escribí más para acotar.');
  });
});

describe('Búsqueda sin resultados', () => {
  it('Sin coincidencias', async () => {
    await buscarYEsperar('zzz');

    expect(texto(buscador.contenedor)).toContain('No encontramos pacientes con ese DNI o nombre.');
    expect(opciones()).toHaveLength(0);
    expect(buscador.control.getAttribute('aria-expanded')).toBe('false');
  });
});

describe('Elegir un paciente', () => {
  beforeEach(async () => {
    servidor.responder('GET /pacientes', () => json(200, [CARLA]));
    document.body.replaceChildren(buscador.contenedor);
    await buscarYEsperar('fict');
  });

  it('Elegir con el mouse', () => {
    opciones()[0]?.click();

    expect(texto(buscador.contenedor)).toContain('Ficticia, Carla · DNI 99000003');
    expect(boton(buscador.contenedor, 'Cambiar paciente')).toBeDefined();
    expect(opciones()).toHaveLength(0);
    expect(buscador.pacienteId()).toBe(3);
  });

  it('Cambiar el paciente elegido', () => {
    opciones()[0]?.click();

    boton(buscador.contenedor, 'Cambiar paciente').click();

    expect(buscador.control.value).toBe('');
    expect(document.activeElement).toBe(buscador.control);
    expect(buscador.pacienteId()).toBeNull();
  });

  it('al editar el texto se borra la elección', () => {
    opciones()[0]?.click();
    expect(buscador.pacienteId()).toBe(3);

    escribirTexto('fictic');

    expect(buscador.pacienteId()).toBeNull();
  });
});

describe('Buscador manejable con el teclado', () => {
  beforeEach(async () => {
    servidor.responder('GET /pacientes', () => json(200, [DOS, UNO]));
    await buscarYEsperar('paciente');
  });

  it('el campo sigue el patrón combobox', () => {
    expect(buscador.control.getAttribute('role')).toBe('combobox');
    expect(buscador.control.getAttribute('aria-autocomplete')).toBe('list');
    expect(buscador.control.getAttribute('aria-expanded')).toBe('true');
    const lista = buscador.contenedor.querySelector('[role="listbox"]');
    expect(lista?.id).toBeTruthy();
    expect(buscador.control.getAttribute('aria-controls')).toBe(lista?.id);
  });

  it('Elegir con el teclado', () => {
    const formulario = document.createElement('form');
    const alEnviar = vi.fn((evento: Event) => evento.preventDefault());
    formulario.addEventListener('submit', alEnviar);
    formulario.append(buscador.contenedor);
    document.body.replaceChildren(formulario);

    teclear('ArrowDown');
    expect(buscador.control.getAttribute('aria-activedescendant')).toBe(opciones()[0]?.id);
    teclear('ArrowDown');
    expect(buscador.control.getAttribute('aria-activedescendant')).toBe(opciones()[1]?.id);
    expect(opciones()[1]?.getAttribute('aria-selected')).toBe('true');
    const enter = teclear('Enter');

    expect(buscador.pacienteId()).toBe(1);
    expect(texto(buscador.contenedor)).toContain('Uno, Paciente · DNI 99000001');
    expect(enter.defaultPrevented).toBe(true);
    expect(alEnviar).not.toHaveBeenCalled();
  });

  it('Escape cierra la lista y no el diálogo', () => {
    const dialogo = document.createElement('dialog');
    dialogo.append(buscador.contenedor);
    document.body.replaceChildren(dialogo);
    dialogo.showModal();
    const alCancelar = vi.fn();
    document.addEventListener('keydown', alCancelar);

    const escape = teclear('Escape');
    document.removeEventListener('keydown', alCancelar);

    expect(escape.defaultPrevented).toBe(true);
    expect(alCancelar).not.toHaveBeenCalled();
    expect(buscador.control.getAttribute('aria-expanded')).toBe('false');
    expect(opciones()).toHaveLength(0);
    expect(dialogo.open).toBe(true);
    expect(buscador.control.value).toBe('paciente');
  });

  it('con la lista cerrada Escape no se intercepta', () => {
    teclear('Escape');

    expect(teclear('Escape').defaultPrevented).toBe(false);
  });
});

describe('Solo cuenta la última búsqueda', () => {
  it('Respuestas fuera de orden', async () => {
    const controlada = apiControlada();
    montar(controlada.api);
    await buscarYEsperar('pac');
    await buscarYEsperar('paciente dos');
    expect(controlada.pedir).toEqual(['/pacientes?q=pac', '/pacientes?q=paciente%20dos']);

    controlada.responder('/pacientes?q=paciente%20dos', [DOS]);
    await vi.advanceTimersByTimeAsync(0);
    controlada.responder('/pacientes?q=pac', [DOS, UNO]);
    await vi.advanceTimersByTimeAsync(0);

    expect(textosDeOpciones()).toEqual(['Dos, Paciente · DNI 99000002']);
    expect(estado()).toContain('1 paciente encontrado.');
  });
});

describe('Falla de la búsqueda', () => {
  async function abrirNuevoTurnoConSesion(): Promise<HTMLDialogElement> {
    vi.useRealTimers();
    document.cookie = 'XSRF-TOKEN=token-ficticio-123; path=/';
    app = abrirApp(servidor, '#/agenda');
    await vi.waitFor(() => expect(servidor.a('GET', '/turnos')).toHaveLength(1));
    await vi.waitFor(() => expect(campo<HTMLSelectElement>(app?.raiz ?? document.body, 'Profesional').options.length).toBeGreaterThan(1));
    boton(app?.raiz ?? document.body, 'Nuevo turno').click();
    const dialogo = document.querySelector<HTMLDialogElement>('dialog[open]');
    if (dialogo === null) {
      throw new Error('No se abrió el formulario');
    }
    return dialogo;
  }

  it('Error del servidor', async () => {
    servidor.responder('GET /pacientes', () => json(500));
    const dialogo = await abrirNuevoTurnoConSesion();
    escribir(campo<HTMLSelectElement>(dialogo, 'Profesional'), '1');

    escribir(campo(dialogo, 'Paciente'), 'fict');

    await vi.waitFor(() => expect(errorDeCampo(dialogo, 'Paciente')).toBe('No pudimos buscar pacientes. Probá de nuevo.'));
    expect(dialogo.open).toBe(true);
    expect(campo<HTMLSelectElement>(dialogo, 'Profesional').value).toBe('1');
    expect(texto(dialogo)).toContain('Ana Ejemplo');
  });

  it('Sesión vencida durante la búsqueda', async () => {
    servidor.responder('GET /pacientes', () => json(401, { status: 401, codigo: 'NO_AUTENTICADO' }));
    const dialogo = await abrirNuevoTurnoConSesion();

    escribir(campo(dialogo, 'Paciente'), 'fict');

    await vi.waitFor(() => expect(texto(document.body)).toContain('Tu sesión terminó. Ingresá de nuevo.'));
    expect(window.location.hash).toBe('#/mi-cuenta');
    expect(document.querySelector('dialog[open]')).toBeNull();
  });

  it('Búsqueda sin permiso', async () => {
    servidor.responder('GET /pacientes', () => json(403, { status: 403, codigo: 'ACCESO_DENEGADO' }));

    await buscarYEsperar('fict');

    expect(errorDeCampo(buscador.contenedor, 'Paciente')).toBe('No tenés permiso para esta acción.');
  });

  it('un 401 se le deja a la regla de sesión vencida y no muestra el aviso de búsqueda', async () => {
    const falla = new ErrorSesion('NO_AUTENTICADO');
    montar({
      solicitar: () => Promise.reject(falla),
      obtener: () => Promise.reject(falla),
      crear: () => Promise.reject(falla),
      actualizar: () => Promise.reject(falla),
      borrar: () => Promise.reject(falla),
    });
    sesionVencida.mockReturnValue(true);

    await buscarYEsperar('fict');

    expect(sesionVencida).toHaveBeenCalledWith(falla);
    expect(errorDeCampo(buscador.contenedor, 'Paciente')).toBe('');
  });

  it('un corte de red muestra el aviso de búsqueda sin borrar lo escrito', async () => {
    const falla = new ErrorGenerico(null);
    montar({
      solicitar: () => Promise.reject(falla),
      obtener: () => Promise.reject(falla),
      crear: () => Promise.reject(falla),
      actualizar: () => Promise.reject(falla),
      borrar: () => Promise.reject(falla),
    });

    await buscarYEsperar('fict');

    expect(errorDeCampo(buscador.contenedor, 'Paciente')).toBe('No pudimos buscar pacientes. Probá de nuevo.');
    expect(buscador.control.value).toBe('fict');
  });
});

describe('El texto buscado no se guarda', () => {
  it('Búsqueda sin rastros en el navegador', async () => {
    servidor.responder('GET /pacientes', () => json(200, [CARLA]));

    await buscarYEsperar('99000003');
    opciones()[0]?.click();

    expect(JSON.stringify({ ...localStorage })).not.toMatch(/99000003|Ficticia/);
    expect(JSON.stringify({ ...sessionStorage })).not.toMatch(/99000003|Ficticia/);
    expect(document.cookie).not.toMatch(/99000003|Ficticia/);
    expect(window.location.hash).toBe('#/agenda');
  });
});
