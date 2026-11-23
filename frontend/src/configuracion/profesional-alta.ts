import { crearCampo, crearSelector } from '../shared/ui/campos.ts';
import { mensajeDeError } from '../shared/ui/mensajes.ts';
import { cargarPrestaciones, cargarSillones, nombreCompleto, type Prestacion, type Profesional, type Sillon } from './api.ts';
import { SIN_SILLON_HABITUAL } from './profesional-detalle.ts';
import type { ContextoSeccion } from './ui.ts';

export const AVISO_FALLA_PARCIAL =
  'El profesional se creó, pero no se pudieron guardar sus prestaciones o su sillón.';

/** Lo que el formulario "Agregar profesional" suma a nombre, apellido y matrícula (design D14). */
export interface ExtrasAlta {
  readonly nodos: readonly HTMLElement[];
  /**
   * Envía las prestaciones (si se marcó alguna) y después el sillón (si se eligió uno) del profesional
   * recién creado. Se detiene y rechaza con el primer error.
   */
  completar(profesionalId: number): Promise<void>;
}

/**
 * Casillas de prestaciones activas y selector de sillón habitual del alta. Los datos se cargan al abrir
 * el formulario; si no se pueden cargar, se avisa y el alta sigue siendo posible sin ellos.
 * Un sillón que ya es el habitual de otro profesional aparece deshabilitado "(de X)" (RN-RO-09).
 */
export function crearExtrasAlta(contexto: ContextoSeccion, profesionales: readonly Profesional[]): ExtrasAlta {
  const prestaciones: Array<{ casilla: HTMLInputElement; id: number }> = [];
  let selector: HTMLSelectElement | null = null;

  const cargando = document.createElement('p');
  cargando.className = 'ayuda';
  cargando.textContent = 'Cargando prestaciones y sillones…';
  const contenedor = document.createElement('div');
  contenedor.className = 'grupo-config';
  contenedor.append(cargando);

  void Promise.all([cargarPrestaciones(contexto.api), cargarSillones(contexto.api)]).then(
    ([todasLasPrestaciones, sillones]) => {
      cargando.remove();
      contenedor.append(grupoDePrestaciones(todasLasPrestaciones, prestaciones));
      const campoSillon = campoDeSillon(sillones, profesionales);
      selector = campoSillon.control;
      contenedor.append(campoSillon.contenedor);
    },
    (causa: unknown) => {
      if (contexto.sesionVencida(causa)) {
        return;
      }
      cargando.textContent = `${mensajeDeError(causa)} Podés asignar las prestaciones y el sillón después, desde el detalle.`;
    },
  );

  return {
    nodos: [contenedor],
    async completar(profesionalId) {
      const prestacionIds = prestaciones.filter((p) => p.casilla.checked).map((p) => p.id);
      if (prestacionIds.length > 0) {
        await contexto.api.actualizar(`/profesionales/${profesionalId}/prestaciones`, { prestacionIds });
      }
      const sillonId = selector === null || selector.value === '' ? null : Number(selector.value);
      if (sillonId !== null) {
        await contexto.api.actualizar(`/profesionales/${profesionalId}/sillon-habitual`, { sillonId });
      }
    },
  };
}

function grupoDePrestaciones(
  todas: readonly Prestacion[],
  controles: Array<{ casilla: HTMLInputElement; id: number }>,
): HTMLFieldSetElement {
  const grupo = document.createElement('fieldset');
  const leyenda = document.createElement('legend');
  leyenda.textContent = 'Prestaciones que hace (opcional)';
  const casillas = document.createElement('div');
  casillas.className = 'casillas';
  for (const prestacion of todas.filter((p) => p.activo)) {
    const etiqueta = document.createElement('label');
    etiqueta.className = 'casilla';
    const casilla = document.createElement('input');
    casilla.type = 'checkbox';
    casilla.name = 'prestacionIds';
    casilla.value = String(prestacion.id);
    etiqueta.append(casilla, ` ${prestacion.nombre}`);
    casillas.append(etiqueta);
    controles.push({ casilla, id: prestacion.id });
  }
  grupo.append(leyenda, casillas);
  return grupo;
}

function campoDeSillon(sillones: readonly Sillon[], profesionales: readonly Profesional[]) {
  const ocupante = (sillonId: number): Profesional | undefined =>
    profesionales.find((p) => p.sillonHabitualId === sillonId);
  const selector = crearSelector('sillonId', [
    { valor: '', texto: SIN_SILLON_HABITUAL },
    ...sillones
      .filter((s) => s.activo)
      .map((s) => {
        const otro = ocupante(s.id);
        return { valor: String(s.id), texto: otro === undefined ? s.nombre : `${s.nombre} (de ${nombreCompleto(otro)})` };
      }),
  ]);
  selector.classList.add('control');
  for (const opcion of selector.options) {
    opcion.disabled = opcion.value !== '' && ocupante(Number(opcion.value)) !== undefined;
  }
  const campo = crearCampo('Sillón habitual (opcional)', selector);
  const ayuda = document.createElement('span');
  ayuda.className = 'ayuda';
  ayuda.textContent = 'Los sillones que ya son el habitual de otro profesional no se pueden elegir.';
  campo.contenedor.append(ayuda);
  return campo;
}
