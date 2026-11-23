import { crearBoton } from '../shared/ui/campos.ts';
import { nombreCompleto, type Profesional } from './api.ts';
import { crearCamposPeriodo, leerCamposPeriodo, leerPeriodo, leerPeriodoRegistrado, textoDePeriodo, type Periodo } from './periodos.ts';
import { montarProfesionalElegido } from './profesional-elegido.ts';
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

function leerBloqueos(cuerpo: unknown, ruta: string): Periodo[] {
  if (!Array.isArray(cuerpo)) {
    throw new Error(`Respuesta inesperada de ${ruta}`);
  }
  return cuerpo.map(leerPeriodo).filter((p): p is Periodo => p !== null);
}

/** "El bloqueo se cruza con 2 turnos dados." (o "1 turno dado"). */
export function textoDeCruce(cantidad: number): string {
  return cantidad === 1
    ? 'El bloqueo se cruza con 1 turno dado.'
    : `El bloqueo se cruza con ${cantidad} turnos dados.`;
}

/**
 * Pestaña "Bloqueos" (requisito "Bloqueos de agenda"): lista, registro y borrado con confirmación.
 * `ADMIN` elige el profesional; `ODONTOLOGO` opera sobre el suyo. Si el bloqueo se cruza con turnos
 * dados, solo se informa cuántos son: no se reasignan.
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

  montarProfesionalElegido(panel, contexto, 'Tus bloqueos', (id, cargados) => {
    profesionalId = id;
    profesionales = cargados;
    aviso.replaceChildren();
    cargarLista(id);
  });
  panel.append(aviso, lista);

  function cargarLista(id: number): void {
    // Un contenedor nuevo por carga, para que una respuesta tardía de otro profesional no se vea.
    const contenedor = document.createElement('div');
    lista.replaceChildren(contenedor);
    const ruta = `/profesionales/${id}/bloqueos`;
    recargar = cargarEn(
      contenedor,
      contexto,
      async () => leerBloqueos(await api.obtener<unknown>(ruta), ruta),
      (bloqueos, destino) => dibujarLista(id, bloqueos, destino),
    );
  }

  function dibujarLista(id: number, bloqueos: readonly Periodo[], destino: HTMLElement): void {
    if (bloqueos.length === 0) {
      const vacio = document.createElement('p');
      vacio.className = 'ayuda';
      vacio.textContent = 'No hay bloqueos registrados.';
      destino.append(vacio);
      return;
    }
    const ul = crearLista();
    for (const bloqueo of bloqueos) {
      const acciones: HTMLButtonElement[] = [];
      if (gestiona) {
        const borrar = botonChico('Borrar', 'peligro');
        borrar.addEventListener('click', () => borrarBloqueo(id, bloqueo));
        acciones.push(borrar);
      }
      ul.append(crearFila(textoDePeriodo(bloqueo.inicio, bloqueo.fin), [bloqueo.motivo ?? 'Sin motivo'], { acciones }));
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
      contenido.append(` ${textoDeCruce(cruces)} Avisale a recepción para reprogramarlos.`);
    }
    caja.append(contenido);
    aviso.replaceChildren(caja);
  }

  bloquear.addEventListener('click', () => {
    if (profesionalId !== null) {
      abrirRegistro(profesionalId);
    }
  });

  function abrirRegistro(id: number): void {
    const periodo = crearCamposPeriodo();
    const profesional = profesionales.find((p) => p.id === id);
    const dialogo = abrirDialogo(
      profesional === undefined ? 'Bloquear horario' : `Bloquear horario de ${nombreCompleto(profesional)}`,
      [...periodo.nodos],
      'Bloquear',
    );
    dialogo.formulario.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const datos = leerCamposPeriodo(periodo);
      if (datos === null) {
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
          if (profesionalId !== id) {
            return;
          }
          mostrarRegistrado(leerPeriodoRegistrado(respuesta)?.turnosAfectados.length ?? 0);
          recargar();
        },
        (causa: unknown) => {
          if (mostrarErrorEnFormulario(causa, dialogo.general, periodo.todos, contexto)) {
            dialogo.cerrar();
          }
        },
      );
    });
  }
}
