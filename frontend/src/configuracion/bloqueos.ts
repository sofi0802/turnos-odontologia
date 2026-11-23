import { crearBoton } from '../shared/ui/campos.ts';
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

/** "El bloqueo se cruza con 2 turnos dados." (o "1 turno dado"). */
export function textoDeCruce(cantidad: number): string {
  return cantidad === 1
    ? 'El bloqueo se cruza con 1 turno dado.'
    : `El bloqueo se cruza con ${cantidad} turnos dados.`;
}

/**
 * Pestaña "Bloqueos" (requisito "Bloqueos de agenda"): lista, registro y borrado con confirmación.
 * `ADMIN` elige el profesional; `ODONTOLOGO` opera sobre el suyo. Si el bloqueo se cruza con turnos
 * dados, se informa cuántos son y se listan; `ADMIN` y `RECEPCIONISTA` los reasignan (design D8).
 */
export function montarBloqueos(panel: HTMLElement, contexto: ContextoSeccion): void {
  const { api, permisos } = contexto;
  const sinProfesional = permisos.profesionalFijo && permisos.profesionalFijoId === null;
  const gestiona = permisos.gestionaBloqueos && !sinProfesional;

  let profesionalId: number | null = null;
  let profesionales: readonly Profesional[] = [];
  let recargar: () => void = () => undefined;

  const bloquear = crearBoton('Bloquear horario');
  panel.append(crearCabecera('Bloqueos de agenda', gestiona ? bloquear : undefined));

  const aviso = document.createElement('div');
  const lista = document.createElement('div');
  const afectados = crearTurnosAfectados(contexto, 'bloqueos', () => profesionales);

  montarProfesionalElegido(
    panel,
    contexto,
    'Tus bloqueos',
    (id, cargados) => {
      profesionalId = id;
      profesionales = cargados;
      aviso.replaceChildren();
      afectados.limpiar();
      cargarLista(id);
    },
    (cargados) => {
      profesionalId = null;
      profesionales = cargados;
      aviso.replaceChildren();
      afectados.limpiar();
      cargarLista(null);
    },
  );
  panel.append(aviso, lista, afectados.contenedor);
  if (!gestiona && permisos.reasigna) {
    const ayuda = document.createElement('p');
    ayuda.className = 'ayuda';
    ayuda.textContent =
      'Los bloqueos los registra cada odontólogo o el administrador. Desde acá podés ver los turnos que afectan y reasignarlos.';
    panel.append(ayuda);
  }

  /** `id` es el profesional elegido, o `null` con "Todos los profesionales" (design D12). */
  function cargarLista(id: number | null): void {
    // Un contenedor nuevo por carga, para que una respuesta tardía de otro profesional no se vea.
    const contenedor = document.createElement('div');
    lista.replaceChildren(contenedor);
    recargar = cargarEn(
      contenedor,
      contexto,
      async () => {
        if (id === null) {
          return cargarPeriodosDeTodos(api, 'bloqueos', profesionales);
        }
        const ruta = `/profesionales/${id}/bloqueos`;
        return leerPeriodos(await api.obtener<unknown>(ruta), ruta).map((b) => ({ ...b, profesionalId: id }));
      },
      (bloqueos, destino) => dibujarLista(bloqueos, destino, id === null),
    );
  }

  function nombreDe(id: number): string {
    const profesional = profesionales.find((p) => p.id === id);
    return profesional === undefined ? '' : nombreCompleto(profesional);
  }

  function dibujarLista(bloqueos: readonly Periodo[], destino: HTMLElement, conProfesional: boolean): void {
    if (bloqueos.length === 0) {
      const vacio = document.createElement('p');
      vacio.className = 'ayuda';
      vacio.textContent = 'No hay bloqueos registrados.';
      destino.append(vacio);
      return;
    }
    const ul = crearLista(conProfesional);
    for (const bloqueo of bloqueos) {
      const acciones: HTMLButtonElement[] = [];
      if (permisos.reasigna) {
        const ver = botonChico('Ver turnos afectados');
        ver.addEventListener('click', () => afectados.ver(bloqueo));
        acciones.push(ver);
      }
      if (gestiona) {
        const borrar = botonChico('Borrar', 'peligro');
        borrar.addEventListener('click', () => borrarBloqueo(bloqueo.profesionalId, bloqueo));
        acciones.push(borrar);
      }
      const datos = [bloqueo.motivo ?? 'Sin motivo'];
      ul.append(
        crearFila(
          textoDePeriodo(bloqueo.inicio, bloqueo.fin),
          datos,
          conProfesional ? { acciones, profesional: nombreDe(bloqueo.profesionalId) } : { acciones },
        ),
      );
    }
    destino.append(ul);
  }

  function borrarBloqueo(id: number, bloqueo: Periodo): void {
    confirmar(
      'Borrar bloqueo',
      textoDePeriodo(bloqueo.inicio, bloqueo.fin),
      'Borrar',
      contexto,
      () => api.borrar(`/profesionales/${id}/bloqueos/${bloqueo.id}`),
      () => {
        aviso.replaceChildren();
        if (afectados.periodoMostrado() === bloqueo.id) {
          afectados.limpiar();
        }
        recargar();
      },
    );
  }

  /** Aviso tras registrar: "Bloqueo registrado." y, si corresponde, cuántos turnos dados se cruzan. */
  function mostrarRegistrado(cruces: number): void {
    const caja = document.createElement('div');
    caja.setAttribute('role', 'status');
    caja.className = cruces > 0 ? 'aviso aviso-alerta' : 'aviso aviso-ok';
    const contenido = document.createElement('div');
    const titulo = document.createElement('strong');
    titulo.textContent = 'Bloqueo registrado.';
    contenido.append(titulo);
    if (cruces > 0) {
      // Quien no puede reasignar (el odontólogo) avisa a la recepción, que sí puede.
      contenido.append(` ${textoDeCruce(cruces)}${permisos.reasigna ? '' : ' Avisale a la recepción para reasignarlos.'}`);
    }
    caja.append(contenido);
    aviso.replaceChildren(caja);
  }

  bloquear.addEventListener('click', () => abrirRegistro());

  /** Con un profesional elegido, el bloqueo es para él; con "Todos", el formulario pide el profesional. */
  function abrirRegistro(): void {
    const campoProfesional = profesionalId === null ? crearCampoProfesional(profesionales) : null;
    const periodo = crearCamposPeriodo();
    const nombre = profesionalId === null ? '' : nombreDe(profesionalId);
    const dialogo = abrirDialogo(
      nombre === '' ? 'Bloquear horario' : `Bloquear horario de ${nombre}`,
      [...(campoProfesional === null ? [] : [campoProfesional.contenedor]), ...periodo.nodos],
      'Bloquear',
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
        respuesta = await api.crear<unknown>(`/profesionales/${id}/bloqueos`, datos);
      }).then(
        (ok) => {
          if (!ok) {
            return;
          }
          dialogo.cerrar();
          if (profesionalId !== null && profesionalId !== id) {
            return;
          }
          const registrado = leerPeriodoRegistrado(respuesta);
          mostrarRegistrado(registrado?.turnosAfectados.length ?? 0);
          afectados.limpiar();
          if (registrado !== null && registrado.turnosAfectados.length > 0) {
            afectados.mostrar({ ...registrado.periodo, profesionalId: id }, registrado.turnosAfectados);
          }
          recargar();
        },
        (causa: unknown) => {
          const campos = [...(campoProfesional === null ? [] : [campoProfesional]), ...periodo.todos];
          if (mostrarErrorEnFormulario(causa, dialogo.general, campos, contexto)) {
            dialogo.cerrar();
          }
        },
      );
    });
  }
}
