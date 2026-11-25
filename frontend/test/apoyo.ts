import { expect, vi } from 'vitest';
import { iniciarApp } from '../src/app.ts';
import { crearClienteApi, type ClienteApi } from '../src/shared/api/cliente.ts';
import { hoy } from '../src/shared/fecha/zona.ts';
import type { Router } from '../src/shared/ui/router.ts';

/** Datos ficticios, iguales al seed de la KB. */
export const PROFESIONALES = [
  { id: 1, nombre: 'Ana', apellido: 'Ejemplo', matricula: 'MP-0001', activo: true, sillonHabitualId: 1 },
  { id: 2, nombre: 'Bruno', apellido: 'Prueba', matricula: 'MP-0002', activo: true, sillonHabitualId: 2 },
];
export const SILLONES = [
  { id: 1, nombre: 'Sillón 1', activo: true },
  { id: 2, nombre: 'Sillón 2', activo: true },
];
export const PRESTACIONES = [{ id: 1, nombre: 'Consulta', duracionMinutos: 30, activo: true }];

/** Pacientes ficticios del seed; los usa el `GET /pacientes` del servidor falso. */
export const PACIENTES = [
  { id: 1, nombre: 'Paciente', apellido: 'Uno', dni: '99000001' },
  { id: 2, nombre: 'Paciente', apellido: 'Dos', dni: '99000002' },
  { id: 3, nombre: 'Carla', apellido: 'Ficticia', dni: '99000003' },
];

export const SESION_RECEPCION = { autenticado: true, username: 'recepcion', rol: 'RECEPCIONISTA' };

const URL_BASE = 'http://localhost:8080/api/v1';

export interface Pedido {
  readonly metodo: string;
  readonly ruta: string;
  readonly query: URLSearchParams;
  readonly cuerpo: unknown;
  /** El cuerpo multipart de un pedido con archivos (C-47), o `undefined`. */
  readonly formulario?: FormData;
  readonly encabezados: Headers;
}

type Manejador = (pedido: Pedido) => Response | Promise<Response>;

export function json(estado: number, cuerpo?: unknown): Response {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status: estado });
}

export function turno(datos: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: 7,
    pacienteId: 1,
    pacienteNombre: 'Paciente',
    pacienteApellido: 'Uno',
    profesionalId: 1,
    sillonId: 1,
    prestacionId: 1,
    inicio: '2026-12-01T10:00:00-03:00',
    fin: '2026-12-01T10:30:00-03:00',
    estado: 'RESERVADO',
    esSobreturno: false,
    ...datos,
  };
}

/** API falsa: responde por "MÉTODO /ruta" y guarda cada pedido. */
export class ServidorFalso {
  readonly pedidos: Pedido[] = [];
  readonly api: ClienteApi;
  private readonly manejadores = new Map<string, Manejador>();

  constructor() {
    this.responder('GET /public/sesion', () => json(200, SESION_RECEPCION));
    this.responder('GET /turnos', () => json(200, []));
    this.responder('GET /profesionales', () => json(200, PROFESIONALES));
    this.responder('GET /sillones', () => json(200, SILLONES));
    this.responder('GET /prestaciones', () => json(200, PRESTACIONES));
    this.responder('GET /pacientes', (pedido) => {
      const q = (pedido.query.get('q') ?? '').toLowerCase();
      return json(
        200,
        PACIENTES.filter((p) => `${p.nombre} ${p.apellido} ${p.dni}`.toLowerCase().includes(q)),
      );
    });
    this.responder('POST /logout', () => json(204));
    // C-15: por defecto, la lista de espera está vacía y el plazo es el de fábrica.
    this.responder('GET /lista-espera', () => json(200, []));
    this.responder('GET /paciente/lista-espera', () => json(200, []));
    this.responder('GET /configuracion/lista-espera', () => json(200, { plazoHoras: 2 }));
    // C-17: por defecto, "Paciente Uno" sin teléfono y nadie con el consentimiento de comunicaciones.
    this.responder('GET /paciente/perfil', () =>
      json(200, { nombre: 'Paciente', apellido: 'Uno', dni: '99000001', email: 'paciente.uno@example.com', telefono: null }),
    );
    this.responder('GET /paciente/consentimiento-comunicaciones', () => json(200, { otorgado: false, actualizadoEn: null }));
    this.responder('GET /pacientes/{id}/consentimiento-comunicaciones', () =>
      json(200, { otorgado: false, actualizadoEn: null, historial: [] }),
    );
    const fetchFalso = vi.fn<typeof fetch>(async (entrada, opciones) => {
      const url = new URL(String(entrada));
      const metodo = opciones?.method ?? 'GET';
      const ruta = url.pathname.replace('/api/v1', '');
      const texto = typeof opciones?.body === 'string' ? opciones.body : null;
      const pedido: Pedido = {
        metodo,
        ruta,
        query: url.searchParams,
        cuerpo: texto === null ? undefined : (JSON.parse(texto) as unknown),
        ...(opciones?.body instanceof FormData ? { formulario: opciones.body } : {}),
        encabezados: new Headers(opciones?.headers),
      };
      this.pedidos.push(pedido);
      const manejador = this.manejadores.get(`${metodo} ${ruta}`) ?? this.buscarConId(metodo, ruta);
      return manejador === undefined ? json(404) : manejador(pedido);
    });
    this.api = crearClienteApi(URL_BASE, fetchFalso);
  }

  /** `clave` es "MÉTODO /ruta"; `{id}` sirve para cualquier número. */
  responder(clave: string, manejador: Manejador): void {
    this.manejadores.set(clave, manejador);
  }

  sinSesion(): void {
    this.responder('GET /public/sesion', () => json(200, { autenticado: false }));
  }

  /** Pedidos a una ruta, en orden. */
  a(metodo: string, ruta: string): Pedido[] {
    return this.pedidos.filter((p) => p.metodo === metodo && p.ruta === ruta);
  }

  ultimo(metodo: string, ruta: string): Pedido {
    const lista = this.a(metodo, ruta);
    const pedido = lista[lista.length - 1];
    if (pedido === undefined) {
      throw new Error(`No hubo pedidos ${metodo} ${ruta}`);
    }
    return pedido;
  }

  private buscarConId(metodo: string, ruta: string): Manejador | undefined {
    return this.manejadores.get(`${metodo} ${ruta.replace(/\/\d+(?=\/|$)/g, '/{id}')}`);
  }
}

export interface AppDePrueba {
  readonly raiz: HTMLElement;
  readonly servidor: ServidorFalso;
  readonly router: Router;
}

/** Abre la aplicación en `hash` contra el servidor falso. */
export function abrirApp(servidor: ServidorFalso, hash: string): AppDePrueba {
  const raiz = document.createElement('div');
  document.body.replaceChildren(raiz);
  history.replaceState(null, '', hash === '' ? window.location.pathname : hash);
  const router = iniciarApp(raiz, servidor.api);
  return { raiz, servidor, router };
}

/** Elemento de formulario por el texto de su etiqueta. */
export function campo<T extends HTMLElement = HTMLInputElement>(raiz: ParentNode, etiqueta: string): T {
  const label = [...raiz.querySelectorAll('label')].find((l) => l.textContent?.trim() === etiqueta);
  const elemento = label?.control ?? null;
  if (elemento === null) {
    throw new Error(`No existe el campo ${etiqueta}`);
  }
  return elemento as unknown as T;
}

/** Botón por su texto visible o, si es un ícono, por su `aria-label` (C-35). */
export function boton(raiz: ParentNode, texto: string): HTMLButtonElement {
  const encontrado = [...raiz.querySelectorAll('button')].find(
    (b) => b.textContent?.trim() === texto || b.getAttribute('aria-label') === texto,
  );
  if (encontrado === undefined) {
    throw new Error(`No existe el botón ${texto}`);
  }
  return encontrado;
}

export function escribir(elemento: HTMLInputElement | HTMLSelectElement, valor: string): void {
  elemento.value = valor;
  elemento.dispatchEvent(new Event('input', { bubbles: true }));
  elemento.dispatchEvent(new Event('change', { bubbles: true }));
}

/** Mensaje de error asociado a un campo con `aria-describedby`. */
export function errorDeCampo(raiz: ParentNode, etiqueta: string): string {
  const elemento = campo(raiz, etiqueta);
  // Un campo con ayuda (C-45) se describe por la ayuda y por el error: se lee el que es el error.
  const ids = (elemento.getAttribute('aria-describedby') ?? '').split(/\s+/).filter((id) => id !== '');
  const error = ids.map((id) => document.getElementById(id)).find((e) => e?.classList.contains('campo-error'));
  return error?.textContent ?? '';
}

export function texto(raiz: HTMLElement): string {
  return raiz.textContent?.replace(/\s+/g, ' ') ?? '';
}

function finDeHorario(hora: string, minutos = 30): string {
  const [h, m] = hora.split(':').map(Number);
  const total = (h ?? 0) * 60 + (m ?? 0) + minutos;
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

/**
 * Respuestas que necesita el "Nuevo turno" guiado de C-40: profesionales habilitados, horarios libres
 * de cualquier día y los turnos del sillón (vacíos). `agenda` es lo que devuelve la agenda sin `sillonId`.
 */
export function responderPasosDelNuevoTurno(
  servidor: ServidorFalso,
  opciones: { agenda?: unknown[]; horarios?: string[] } = {},
): void {
  const agenda = opciones.agenda ?? [];
  const horarios = opciones.horarios ?? ['10:00', '10:30', '11:00', '15:00'];
  servidor.responder('GET /public/profesionales', () =>
    json(200, [
      { id: 1, nombre: 'Ana Ejemplo' },
      { id: 2, nombre: 'Bruno Prueba' },
    ]),
  );
  servidor.responder('GET /public/disponibilidad', (pedido) => {
    const fecha = pedido.query.get('fecha') ?? '';
    return json(
      200,
      horarios.map((h) => ({ inicio: `${fecha}T${h}:00-03:00`, fin: `${fecha}T${finDeHorario(h)}:00-03:00` })),
    );
  });
  servidor.responder('GET /turnos', (pedido) => json(200, pedido.query.has('sillonId') ? [] : agenda));
}

/** Recorre el "Nuevo turno" guiado: prestación, profesional, día del calendario (hoy si no se indica) y horario. */
export async function completarPasosDelNuevoTurno(
  dialogo: ParentNode,
  { prestacion = '1', profesional = '1', fecha = hoy(), hora = '10:00' } = {},
): Promise<void> {
  escribir(campo<HTMLSelectElement>(dialogo, 'Prestación'), prestacion);
  await vi.waitFor(() => expect(campo<HTMLSelectElement>(dialogo, 'Profesional').disabled).toBe(false));
  escribir(campo<HTMLSelectElement>(dialogo, 'Profesional'), profesional);
  const dia = await vi.waitFor(() => {
    const boton = dialogo.querySelector<HTMLButtonElement>(`.cal-dia[data-fecha="${fecha}"]`);
    expect(boton?.disabled).toBe(false);
    return boton;
  });
  dia?.click();
  const horario = await vi.waitFor(() => {
    const boton = [...dialogo.querySelectorAll<HTMLButtonElement>('.chips button')].find((b) => b.textContent === hora);
    expect(boton).toBeDefined();
    return boton;
  });
  horario?.click();
}

/** Horario elegido en la grilla del nuevo turno, o `null`. */
export function horarioElegido(dialogo: ParentNode): string | null {
  return dialogo.querySelector('.chips button[aria-pressed="true"]')?.textContent ?? null;
}

/** Busca en el campo "Paciente" y elige la opción con ese texto, como lo hace la recepción. */
export async function elegirPaciente(raiz: ParentNode, busqueda: string, opcion: string): Promise<void> {
  escribir(campo(raiz, 'Paciente'), busqueda);
  const elegida = await vi.waitFor(() => {
    const encontrada = [...raiz.querySelectorAll<HTMLElement>('[role="option"]')].find(
      (o) => texto(o).trim() === opcion,
    );
    expect(encontrada).toBeDefined();
    return encontrada;
  });
  elegida?.click();
}
