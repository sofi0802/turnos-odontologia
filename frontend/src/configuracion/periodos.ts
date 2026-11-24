import { aIsoConZona, fechaDeInstante, fechaLarga, formatearHora } from '../shared/fecha/zona.ts';
import { crearCampo, crearEntrada, type Campo } from '../shared/ui/campos.ts';
import { usarSelectorFecha } from '../shared/ui/selector-fecha.ts';
import type { ClienteApi } from '../shared/api/cliente.ts';
import type { Profesional } from './api.ts';
import { crearSelectorHora, type SelectorHora } from './ui.ts';

/** Ausencia, licencia o bloqueo tal como lo devuelve la API de C-07 (`PeriodoRespuesta`). */
export interface Periodo {
  readonly id: number;
  readonly profesionalId: number;
  /** `AUSENCIA`, `LICENCIA` o `BLOQUEO`. */
  readonly tipo: string;
  readonly inicio: string;
  readonly fin: string;
  readonly motivo: string | null;
}

/** Turno que cae dentro de un período recién registrado (`TurnoAfectado`). */
export interface TurnoAfectado {
  readonly turnoId: number;
  readonly inicio: string;
  readonly fin: string;
  readonly prestacionId: number;
}

/** Respuesta del alta de una ausencia o un bloqueo (`PeriodoRegistradoRespuesta`). */
export interface PeriodoRegistrado {
  readonly periodo: Periodo;
  readonly turnosAfectados: readonly TurnoAfectado[];
}

function esObjeto(valor: unknown): valor is object {
  return typeof valor === 'object' && valor !== null;
}

export function leerPeriodo(valor: unknown): Periodo | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const id: unknown = Reflect.get(valor, 'id');
  const profesionalId: unknown = Reflect.get(valor, 'profesionalId');
  const tipo: unknown = Reflect.get(valor, 'tipo');
  const inicio: unknown = Reflect.get(valor, 'inicio');
  const fin: unknown = Reflect.get(valor, 'fin');
  const motivo: unknown = Reflect.get(valor, 'motivo');
  if (typeof id !== 'number' || typeof inicio !== 'string' || typeof fin !== 'string') {
    return null;
  }
  return {
    id,
    profesionalId: typeof profesionalId === 'number' ? profesionalId : 0,
    tipo: typeof tipo === 'string' ? tipo : '',
    inicio,
    fin,
    motivo: typeof motivo === 'string' && motivo.trim() !== '' ? motivo : null,
  };
}

/** Clase de período en la API: `/profesionales/{id}/ausencias` o `/profesionales/{id}/bloqueos`. */
export type ClasePeriodo = 'ausencias' | 'bloqueos';

/** Lee la lista de `GET /profesionales/{id}/{ausencias|bloqueos}`, descartando lo de forma inesperada. */
export function leerPeriodos(cuerpo: unknown, ruta: string): Periodo[] {
  if (!Array.isArray(cuerpo)) {
    throw new Error(`Respuesta inesperada de ${ruta}`);
  }
  return cuerpo.map(leerPeriodo).filter((p): p is Periodo => p !== null);
}

/**
 * Vista "Todos los profesionales" (design D12): pide los períodos de cada profesional activo en paralelo
 * y los une ordenados por inicio. Si falla un pedido, rechaza: no se muestra una lista parcial.
 */
export async function cargarPeriodosDeTodos(
  api: ClienteApi,
  clase: ClasePeriodo,
  profesionales: readonly Profesional[],
): Promise<Periodo[]> {
  const listas = await Promise.all(
    profesionales
      .filter((p) => p.activo)
      .map(async (profesional) => {
        const ruta = `/profesionales/${profesional.id}/${clase}`;
        return leerPeriodos(await api.obtener<unknown>(ruta), ruta).map((p) => ({ ...p, profesionalId: profesional.id }));
      }),
  );
  return listas.flat().sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio));
}

export function leerTurnoAfectado(valor: unknown): TurnoAfectado | null {
  if (!esObjeto(valor)) {
    return null;
  }
  const turnoId: unknown = Reflect.get(valor, 'turnoId');
  const inicio: unknown = Reflect.get(valor, 'inicio');
  const fin: unknown = Reflect.get(valor, 'fin');
  const prestacionId: unknown = Reflect.get(valor, 'prestacionId');
  if (typeof turnoId !== 'number' || typeof inicio !== 'string' || typeof prestacionId !== 'number') {
    return null;
  }
  return { turnoId, inicio, fin: typeof fin === 'string' ? fin : inicio, prestacionId };
}

/** Lee la respuesta del alta; si no tiene la forma esperada, devuelve `null`. */
export function leerPeriodoRegistrado(cuerpo: unknown): PeriodoRegistrado | null {
  if (!esObjeto(cuerpo)) {
    return null;
  }
  const periodo = leerPeriodo(Reflect.get(cuerpo, 'periodo'));
  if (periodo === null) {
    return null;
  }
  const afectados: unknown = Reflect.get(cuerpo, 'turnosAfectados');
  const turnosAfectados = Array.isArray(afectados)
    ? afectados.map(leerTurnoAfectado).filter((t): t is TurnoAfectado => t !== null)
    : [];
  return { periodo, turnosAfectados };
}

function capitalizar(texto: string): string {
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/**
 * Texto de un período en la zona del consultorio:
 * - el mismo día: "Lunes 7 de diciembre, 08:00 a 13:00";
 * - días distintos: "Lunes 14 de diciembre 08:00 al viernes 18 de diciembre 13:00".
 */
export function textoDePeriodo(inicio: string, fin: string): string {
  if (fechaDeInstante(inicio) === fechaDeInstante(fin)) {
    return `${capitalizar(fechaLarga(inicio))}, ${formatearHora(inicio)} a ${formatearHora(fin)}`;
  }
  return `${capitalizar(fechaLarga(inicio))} ${formatearHora(inicio)} al ${fechaLarga(fin)} ${formatearHora(fin)}`;
}

/** Texto de un turno: "Lunes 7 de diciembre · 09:00". */
export function textoDeTurno(inicio: string): string {
  return `${capitalizar(fechaLarga(inicio))} · ${formatearHora(inicio)}`;
}

/** Campos de un período (design D6): fecha y hora de inicio y de fin, y motivo opcional. */
export interface CamposPeriodo {
  readonly fechaInicio: Campo<HTMLInputElement>;
  readonly horaInicio: SelectorHora;
  readonly fechaFin: Campo<HTMLInputElement>;
  readonly horaFin: SelectorHora;
  readonly motivo: Campo<HTMLInputElement>;
  /** Todos los campos, para `mostrarErrorEnFormulario`. Los `name` son `inicio`, `fin` y `motivo` donde aplica. */
  readonly todos: readonly (Campo<HTMLInputElement> | SelectorHora)[];
  /** Los nodos a insertar en el formulario, ya agrupados de a dos. */
  readonly nodos: readonly HTMLElement[];
}

export function crearCamposPeriodo(): CamposPeriodo {
  const fechaInicio = crearCampo('Desde el día', crearEntrada('date', 'inicio'));
  usarSelectorFecha(fechaInicio);
  const horaInicio = crearSelectorHora('horaInicio', 'Hora de inicio', {
    valor: '08:00',
    textoVisible: 'a las',
    ariaMinutos: 'Minutos del inicio',
  });
  const fechaFin = crearCampo('Hasta el día', crearEntrada('date', 'fin'));
  usarSelectorFecha(fechaFin);
  const horaFin = crearSelectorHora('horaFin', 'Hora de fin', {
    valor: '13:00',
    textoVisible: 'a las',
    ariaMinutos: 'Minutos del fin',
  });
  const motivo = crearCampo('Motivo (opcional)', crearEntrada('text', 'motivo'));
  motivo.control.maxLength = 255;
  const ayuda = document.createElement('p');
  ayuda.className = 'ayuda';
  ayuda.textContent = 'No anotes datos de salud en el motivo.';
  const grupo = document.createElement('div');
  grupo.className = 'dos-campos';
  grupo.append(fechaInicio.contenedor, horaInicio.contenedor, fechaFin.contenedor, horaFin.contenedor);
  const todos = [fechaInicio, horaInicio, fechaFin, horaFin, motivo];
  return { fechaInicio, horaInicio, fechaFin, horaFin, motivo, todos, nodos: [grupo, motivo.contenedor, ayuda] };
}

/** Lo que se envía a la API; `null` si falta un dato obligatorio (los marca en los campos). */
export function leerCamposPeriodo(campos: CamposPeriodo): { inicio: string; fin: string; motivo: string | null } | null {
  let completo = true;
  for (const campo of [campos.fechaInicio, campos.fechaFin]) {
    const vacio = campo.control.value === '';
    campo.mostrarError(vacio ? 'Completá este dato.' : '');
    completo &&= !vacio;
  }
  if (!completo) {
    return null;
  }
  const motivo = campos.motivo.control.value.trim();
  return {
    inicio: aIsoConZona(campos.fechaInicio.control.value, campos.horaInicio.valor()),
    fin: aIsoConZona(campos.fechaFin.control.value, campos.horaFin.valor()),
    motivo: motivo === '' ? null : motivo,
  };
}
