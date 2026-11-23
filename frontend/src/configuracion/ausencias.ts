import { fechaLarga } from '../shared/fecha/zona.ts';
import { crearBoton, crearCampo, crearSelector } from '../shared/ui/campos.ts';
import { cargarPrestaciones, nombreCompleto, type Profesional } from './api.ts';
import {
  crearCamposPeriodo,
  leerCamposPeriodo,
  leerPeriodo,
  leerPeriodoRegistrado,
  textoDePeriodo,
  textoDeTurno,
  type Periodo,
  type TurnoAfectado,
} from './periodos.ts';
import { montarProfesionalElegido } from './profesional-elegido.ts';
import { abrirReasignacion, crearRecursosReasignacion } from './reasignacion.ts';
import {
  abrirDialogo,
  botonChico,
  cargarEn,
  confirmar,
  crearCabecera,
  crearFila,
  crearLista,
  guardando,
  mostrarErrorEnFormulario,
  type ContextoSeccion,
} from './ui.ts';

const TIPOS = [
  { valor: 'AUSENCIA', texto: 'Ausencia' },
  { valor: 'LICENCIA', texto: 'Licencia' },
] as const;

/** Estados de turno que siguen ocupando la agenda (design D7). */
const ESTADOS_DADOS: ReadonlySet<string> = new Set(['RESERVADO', 'CONFIRMADO']);

function textoDeTipo(tipo: string): string {
  return tipo === 'LICENCIA' ? 'Licencia' : 'Ausencia';
}

function leerAusencias(cuerpo: unknown, ruta: string): Periodo[] {
  if (!Array.isArray(cuerpo)) {
    throw new Error(`Respuesta inesperada de ${ruta}`);
  }
  return cuerpo.map(leerPeriodo).filter((p): p is Periodo => p !== null);
}

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

/**
 * Pestaña "Ausencias" (requisitos "Ausencias y licencias", "Registro y borrado de ausencias",
 * "Turnos afectados por una ausencia" y "Reasignación de un turno afectado").
 * `ADMIN` y `RECEPCIONISTA` eligen el profesional, registran, borran y reasignan; `ODONTOLOGO`
 * solo lee las propias.
 */
export function montarAusencias(panel: HTMLElement, contexto: ContextoSeccion): void {
  const gestiona = contexto.permisos.gestionaAusencias;
  const { api } = contexto;

  let profesionalId: number | null = null;
  let profesionales: readonly Profesional[] = [];
  let recargar: () => void = () => undefined;
  const recursos = crearRecursosReasignacion(api);

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

  const registrar = crearBoton('Registrar ausencia');
  panel.append(crearCabecera('Ausencias y licencias', gestiona ? registrar : undefined));

  const lista = document.createElement('div');
  const afectados = document.createElement('div');
  /** Ausencia cuyos turnos afectados se muestran, para limpiarlos si se borra. */
  let ausenciaMostrada: number | null = null;

  montarProfesionalElegido(panel, contexto, 'Tus ausencias', (id, cargados) => {
    profesionalId = id;
    profesionales = cargados;
    limpiarAfectados();
    cargarLista(id);
  });
  panel.append(lista, afectados);
  if (!gestiona && contexto.permisos.profesionalFijoId !== null) {
    const ayuda = document.createElement('p');
    ayuda.className = 'ayuda';
    ayuda.textContent = 'Las ausencias las registra recepción o el administrador.';
    panel.append(ayuda);
  }

  function limpiarAfectados(): void {
    ausenciaMostrada = null;
    afectados.replaceChildren();
  }

  function nombreDe(id: number): string {
    const profesional = profesionales.find((p) => p.id === id);
    return profesional === undefined ? '' : nombreCompleto(profesional);
  }

  function cargarLista(id: number): void {
    // Un contenedor nuevo por carga: si llega tarde la respuesta de otro profesional, se dibuja fuera de la vista.
    const contenedor = document.createElement('div');
    lista.replaceChildren(contenedor);
    const ruta = `/profesionales/${id}/ausencias`;
    recargar = cargarEn(
      contenedor,
      contexto,
      async () => leerAusencias(await api.obtener<unknown>(ruta), ruta),
      (ausencias, destino) => dibujarLista(id, ausencias, destino),
    );
  }

  function dibujarLista(id: number, ausencias: readonly Periodo[], destino: HTMLElement): void {
    if (ausencias.length === 0) {
      const vacio = document.createElement('p');
      vacio.className = 'ayuda';
      vacio.textContent = 'No hay ausencias registradas.';
      destino.append(vacio);
      return;
    }
    const ul = crearLista();
    for (const ausencia of ausencias) {
      const acciones: HTMLButtonElement[] = [];
      if (gestiona) {
        const ver = botonChico('Ver turnos afectados');
        ver.addEventListener('click', () => verAfectados(id, ausencia));
        const borrar = botonChico('Borrar', 'peligro');
        borrar.addEventListener('click', () => borrarAusencia(id, ausencia));
        acciones.push(ver, borrar);
      }
      ul.append(
        crearFila(textoDeTipo(ausencia.tipo), [textoDePeriodo(ausencia.inicio, ausencia.fin), ausencia.motivo ?? 'Sin motivo'], {
          acciones,
        }),
      );
    }
    destino.append(ul);
  }

  function borrarAusencia(id: number, ausencia: Periodo): void {
    confirmar(
      'Borrar ausencia',
      `${textoDeTipo(ausencia.tipo)} · ${textoDePeriodo(ausencia.inicio, ausencia.fin)}`,
      'Borrar',
      contexto,
      () => api.borrar(`/profesionales/${id}/ausencias/${ausencia.id}`),
      () => {
        if (ausenciaMostrada === ausencia.id) {
          limpiarAfectados();
        }
        recargar();
      },
    );
  }

  /** "Ver turnos afectados": los turnos dados del profesional dentro del período exacto (design D7). */
  function verAfectados(id: number, ausencia: Periodo): void {
    ausenciaMostrada = ausencia.id;
    const contenedor = document.createElement('div');
    afectados.replaceChildren(contenedor);
    const query = new URLSearchParams({ desde: ausencia.inicio, hasta: ausencia.fin, profesionalId: String(id) });
    cargarEn(
      contenedor,
      contexto,
      async () => {
        const cuerpo = await api.obtener<unknown>(`/turnos?${query.toString()}`);
        if (!Array.isArray(cuerpo)) {
          throw new Error('Respuesta inesperada de /turnos');
        }
        return cuerpo.map(leerTurnoDado).filter((t): t is TurnoAfectado => t !== null);
      },
      (turnos, destino) => mostrarAfectados(destino, id, ausencia, turnos),
    );
  }

  /** Lista de turnos afectados con fecha, hora y prestación, sin datos de salud (RN-NO-01). */
  function mostrarAfectados(
    destino: HTMLElement,
    id: number,
    ausencia: Periodo,
    turnos: readonly TurnoAfectado[],
  ): void {
    ausenciaMostrada = ausencia.id;
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
      const periodo = `${ausencia.tipo === 'LICENCIA' ? 'La licencia' : 'La ausencia'} del ${fechaLarga(ausencia.inicio)}`;
      ayuda.textContent =
        turnos.length === 0
          ? `${periodo} no se cruza con turnos dados.`
          : `${periodo} se cruza con estos turnos. Reasignalos a otro profesional o reprogramalos desde la agenda.`;
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
        if (gestiona) {
          const reasignar = botonChico('Reasignar', 'principal');
          reasignar.addEventListener('click', () =>
            abrirReasignacion({
              contexto,
              recursos,
              ausenteId: id,
              ausenciaId: ausencia.id,
              turno,
              prestacion,
              profesionales,
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

  registrar.addEventListener('click', () => {
    if (profesionalId !== null) {
      abrirRegistro(profesionalId);
    }
  });

  function abrirRegistro(id: number): void {
    const tipo = crearCampo('Tipo', crearSelector('tipo', TIPOS));
    const periodo = crearCamposPeriodo();
    const nombre = nombreDe(id);
    const dialogo = abrirDialogo(
      nombre === '' ? 'Registrar ausencia' : `Registrar ausencia de ${nombre}`,
      [tipo.contenedor, ...periodo.nodos],
      'Registrar',
    );
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const datos = leerCamposPeriodo(periodo);
      if (datos === null) {
        return;
      }
      let respuesta: unknown;
      void guardando(dialogo.enviar, async () => {
        respuesta = await api.crear<unknown>(`/profesionales/${id}/ausencias`, { tipo: tipo.control.value, ...datos });
      }).then(
        (ok) => {
          if (!ok) {
            return;
          }
          dialogo.cerrar();
          if (profesionalId !== id) {
            return;
          }
          recargar();
          const registrada = leerPeriodoRegistrado(respuesta);
          if (registrada !== null) {
            const contenedor = document.createElement('div');
            afectados.replaceChildren(contenedor);
            mostrarAfectados(contenedor, id, registrada.periodo, registrada.turnosAfectados);
          }
        },
        (causa: unknown) => {
          if (mostrarErrorEnFormulario(causa, dialogo.general, [tipo, ...periodo.todos], contexto)) {
            dialogo.cerrar();
          }
        },
      );
    });
  }
}
