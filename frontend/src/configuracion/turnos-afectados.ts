import { fechaLarga } from '../shared/fecha/zona.ts';
import { cargarPrestaciones, nombreCompleto, type Profesional } from './api.ts';
import { textoDeTurno, type ClasePeriodo, type Periodo, type TurnoAfectado } from './periodos.ts';
import { abrirReasignacion, crearRecursosReasignacion } from './reasignacion.ts';
import { botonChico, cargarEn, crearFila, crearLista, type ContextoSeccion } from './ui.ts';

/** Estados de turno que siguen ocupando la agenda (design D7). */
const ESTADOS_DADOS: ReadonlySet<string> = new Set(['RESERVADO', 'CONFIRMADO']);

/**
 * Turno de `GET /turnos` como turno afectado. Se leen solo fecha, hora y prestación: el nombre del
 * paciente que trae la agenda no se usa (design D7, RN-NO-01).
 */
function leerTurnoDado(valor: unknown): TurnoAfectado | null {
  if (typeof valor !== 'object' || valor === null) {
    return null;
  }
  const id: unknown = Reflect.get(valor, 'id');
  const inicio: unknown = Reflect.get(valor, 'inicio');
  const fin: unknown = Reflect.get(valor, 'fin');
  const prestacionId: unknown = Reflect.get(valor, 'prestacionId');
  const estado: unknown = Reflect.get(valor, 'estado');
  if (
    typeof id !== 'number' ||
    typeof inicio !== 'string' ||
    typeof prestacionId !== 'number' ||
    typeof estado !== 'string' ||
    !ESTADOS_DADOS.has(estado)
  ) {
    return null;
  }
  return { turnoId: id, inicio, fin: typeof fin === 'string' ? fin : inicio, prestacionId };
}

/** Recuadro de "Turnos afectados" de una sección de Ausencias o de Bloqueos (design D7 y D8). */
export interface TurnosAfectadosVista {
  /** Donde se dibuja el recuadro; la sección lo agrega a su panel. */
  readonly contenedor: HTMLElement;
  /** "Ver turnos afectados": pide los turnos dados del profesional dentro del período exacto. */
  ver(periodo: Periodo): void;
  /** Muestra los turnos dados que ya se conocen, por ejemplo los de la respuesta del alta. */
  mostrar(periodo: Periodo, turnos: readonly TurnoAfectado[]): void;
  limpiar(): void;
  /** Período cuyos turnos se muestran, para limpiarlos si se lo borra. */
  periodoMostrado(): number | null;
}

/**
 * Turnos afectados por una ausencia o un bloqueo, con fecha, hora y prestación, sin datos de salud
 * (RN-NO-01). Quien reasigna (`permisos.reasigna`) ve "Reasignar" en cada turno; la ruta de la
 * reasignación usa la clase del período (design D16).
 */
export function crearTurnosAfectados(
  contexto: ContextoSeccion,
  clase: ClasePeriodo,
  profesionales: () => readonly Profesional[],
): TurnosAfectadosVista {
  const { api } = contexto;
  const contenedor = document.createElement('div');
  const recursos = crearRecursosReasignacion(api);
  let mostrado: number | null = null;

  let nombresPrestaciones: Promise<ReadonlyMap<number, string>> | null = null;
  const prestaciones = (): Promise<ReadonlyMap<number, string>> => {
    if (nombresPrestaciones === null) {
      nombresPrestaciones = cargarPrestaciones(api).then(
        (lista) => new Map(lista.map((p) => [p.id, p.nombre])),
        () => {
          nombresPrestaciones = null;
          return new Map<number, string>();
        },
      );
    }
    return nombresPrestaciones;
  };

  function limpiar(): void {
    mostrado = null;
    contenedor.replaceChildren();
  }

  /** "La licencia del lunes 14 de diciembre", "El bloqueo del miércoles 9 de diciembre de Ana Ejemplo". */
  function descripcion(periodo: Periodo): string {
    if (clase === 'bloqueos') {
      const profesional = profesionales().find((p) => p.id === periodo.profesionalId);
      const de = profesional === undefined ? '' : ` de ${nombreCompleto(profesional)}`;
      return `El bloqueo del ${fechaLarga(periodo.inicio)}${de}`;
    }
    return `${periodo.tipo === 'LICENCIA' ? 'La licencia' : 'La ausencia'} del ${fechaLarga(periodo.inicio)}`;
  }

  function dibujar(destino: HTMLElement, periodo: Periodo, turnos: readonly TurnoAfectado[]): void {
    mostrado = periodo.id;
    const caja = document.createElement('div');
    caja.className = 'afectados';
    caja.setAttribute('role', 'status');
    destino.replaceChildren(caja);
    void prestaciones().then((nombres) => {
      let restantes = turnos.length;
      const titulo = document.createElement('h3');
      const tituloDe = (n: number): string => `Turnos afectados: ${n}`;
      titulo.textContent = tituloDe(restantes);
      const ayuda = document.createElement('p');
      ayuda.className = 'ayuda';
      const reasigna = contexto.permisos.reasigna;
      ayuda.textContent =
        turnos.length === 0
          ? `${descripcion(periodo)} no se cruza con turnos dados.`
          : `${descripcion(periodo)} se cruza con estos turnos.${reasigna ? ' Reasignalos a otro profesional o reprogramalos desde la agenda.' : ''}`;
      const aviso = document.createElement('p');
      aviso.className = 'aviso aviso-ok';
      aviso.hidden = true;
      caja.append(titulo, ayuda, aviso);
      if (turnos.length === 0) {
        return;
      }
      const ul = crearLista();
      for (const turno of turnos) {
        const prestacion = nombres.get(turno.prestacionId) ?? 'Prestación';
        const fila = crearFila(textoDeTurno(turno.inicio), [prestacion]);
        if (reasigna) {
          const reasignar = botonChico('Reasignar', 'principal');
          reasignar.addEventListener('click', () =>
            abrirReasignacion({
              contexto,
              recursos,
              clase,
              ausenteId: periodo.profesionalId,
              periodoId: periodo.id,
              turno,
              prestacion,
              profesionales: profesionales(),
              alReasignar: () => {
                fila.remove();
                restantes -= 1;
                titulo.textContent = tituloDe(restantes);
                aviso.textContent = 'Turno reasignado.';
                aviso.hidden = false;
              },
            }),
          );
          // El botón se agrega aparte porque `alReasignar` necesita la fila ya creada.
          const cajaAcciones = document.createElement('div');
          cajaAcciones.className = 'item-acciones';
          cajaAcciones.append(reasignar);
          fila.append(cajaAcciones);
        }
        ul.append(fila);
      }
      caja.append(ul);
    });
  }

  return {
    contenedor,
    limpiar,
    periodoMostrado: () => mostrado,
    mostrar(periodo, turnos) {
      const destino = document.createElement('div');
      contenedor.replaceChildren(destino);
      dibujar(destino, periodo, turnos);
    },
    ver(periodo) {
      mostrado = periodo.id;
      const destino = document.createElement('div');
      contenedor.replaceChildren(destino);
      const query = new URLSearchParams({
        desde: periodo.inicio,
        hasta: periodo.fin,
        profesionalId: String(periodo.profesionalId),
      });
      cargarEn(
        destino,
        contexto,
        async () => {
          const cuerpo = await api.obtener<unknown>(`/turnos?${query.toString()}`);
          if (!Array.isArray(cuerpo)) {
            throw new Error('Respuesta inesperada de /turnos');
          }
          return cuerpo.map(leerTurnoDado).filter((t): t is TurnoAfectado => t !== null);
        },
        (turnos, caja) => dibujar(caja, periodo, turnos),
      );
    },
  };
}
