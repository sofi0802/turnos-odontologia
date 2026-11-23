import { crearBoton, crearCampo, crearSelector } from '../shared/ui/campos.ts';
import { nombreCompleto, type Profesional } from './api.ts';
import {
  cargarPeriodosDeTodos,
  crearCamposPeriodo,
  leerCamposPeriodo,
  leerPeriodoRegistrado,
  leerPeriodos,
  textoDePeriodo,
  type Periodo,
} from './periodos.ts';
import { crearCampoProfesional, leerCampoProfesional, montarProfesionalElegido } from './profesional-elegido.ts';
import { crearTurnosAfectados } from './turnos-afectados.ts';
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

function textoDeTipo(tipo: string): string {
  return tipo === 'LICENCIA' ? 'Licencia' : 'Ausencia';
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

  const registrar = crearBoton('Registrar ausencia');
  panel.append(crearCabecera('Ausencias y licencias', gestiona ? registrar : undefined));

  const lista = document.createElement('div');
  const afectados = crearTurnosAfectados(contexto, 'ausencias', () => profesionales);

  montarProfesionalElegido(
    panel,
    contexto,
    'Tus ausencias',
    (id, cargados) => {
      profesionalId = id;
      profesionales = cargados;
      limpiarAfectados();
      cargarLista(id);
    },
    (cargados) => {
      profesionalId = null;
      profesionales = cargados;
      limpiarAfectados();
      cargarLista(null);
    },
  );
  panel.append(lista, afectados.contenedor);
  if (!gestiona && contexto.permisos.profesionalFijoId !== null) {
    const ayuda = document.createElement('p');
    ayuda.className = 'ayuda';
    ayuda.textContent = 'Las ausencias las registra recepción o el administrador.';
    panel.append(ayuda);
  }

  function limpiarAfectados(): void {
    afectados.limpiar();
  }

  function nombreDe(id: number): string {
    const profesional = profesionales.find((p) => p.id === id);
    return profesional === undefined ? '' : nombreCompleto(profesional);
  }

  /** `id` es el profesional elegido, o `null` con "Todos los profesionales" (design D12). */
  function cargarLista(id: number | null): void {
    // Un contenedor nuevo por carga: si llega tarde la respuesta de otro profesional, se dibuja fuera de la vista.
    const contenedor = document.createElement('div');
    lista.replaceChildren(contenedor);
    recargar = cargarEn(
      contenedor,
      contexto,
      async () => {
        if (id === null) {
          return cargarPeriodosDeTodos(api, 'ausencias', profesionales);
        }
        const ruta = `/profesionales/${id}/ausencias`;
        return leerPeriodos(await api.obtener<unknown>(ruta), ruta).map((a) => ({ ...a, profesionalId: id }));
      },
      (ausencias, destino) => dibujarLista(ausencias, destino, id === null),
    );
  }

  function dibujarLista(ausencias: readonly Periodo[], destino: HTMLElement, conProfesional: boolean): void {
    if (ausencias.length === 0) {
      const vacio = document.createElement('p');
      vacio.className = 'ayuda';
      vacio.textContent = 'No hay ausencias registradas.';
      destino.append(vacio);
      return;
    }
    const ul = crearLista(conProfesional);
    for (const ausencia of ausencias) {
      const acciones: HTMLButtonElement[] = [];
      if (gestiona) {
        const ver = botonChico('Ver turnos afectados');
        ver.addEventListener('click', () => afectados.ver(ausencia));
        const borrar = botonChico('Borrar', 'peligro');
        borrar.addEventListener('click', () => borrarAusencia(ausencia.profesionalId, ausencia));
        acciones.push(ver, borrar);
      }
      const datos = [textoDePeriodo(ausencia.inicio, ausencia.fin), ausencia.motivo ?? 'Sin motivo'];
      ul.append(
        crearFila(
          textoDeTipo(ausencia.tipo),
          datos,
          conProfesional ? { acciones, profesional: nombreDe(ausencia.profesionalId) } : { acciones },
        ),
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
        if (afectados.periodoMostrado() === ausencia.id) {
          limpiarAfectados();
        }
        recargar();
      },
    );
  }

  registrar.addEventListener('click', () => abrirRegistro());

  /** Con un profesional elegido, el alta es para él; con "Todos", el formulario pide el profesional. */
  function abrirRegistro(): void {
    const campoProfesional = profesionalId === null ? crearCampoProfesional(profesionales) : null;
    const tipo = crearCampo('Tipo', crearSelector('tipo', TIPOS));
    const periodo = crearCamposPeriodo();
    const nombre = profesionalId === null ? '' : nombreDe(profesionalId);
    const dialogo = abrirDialogo(
      nombre === '' ? 'Registrar ausencia' : `Registrar ausencia de ${nombre}`,
      [...(campoProfesional === null ? [] : [campoProfesional.contenedor]), tipo.contenedor, ...periodo.nodos],
      'Registrar',
    );
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const id = campoProfesional === null ? profesionalId : leerCampoProfesional(campoProfesional);
      const datos = leerCamposPeriodo(periodo);
      if (id === null || datos === null) {
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
          if (profesionalId !== null && profesionalId !== id) {
            return;
          }
          recargar();
          const registrada = leerPeriodoRegistrado(respuesta);
          if (registrada !== null) {
            afectados.mostrar({ ...registrada.periodo, profesionalId: id }, registrada.turnosAfectados);
          }
        },
        (causa: unknown) => {
          const campos = [...(campoProfesional === null ? [] : [campoProfesional]), tipo, ...periodo.todos];
          if (mostrarErrorEnFormulario(causa, dialogo.general, campos, contexto)) {
            dialogo.cerrar();
          }
        },
      );
    });
  }
}
