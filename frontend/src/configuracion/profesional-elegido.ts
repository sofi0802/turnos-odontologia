import { crearAviso, crearCampo, crearSelector, type Campo } from '../shared/ui/campos.ts';
import { cargarProfesionales, nombreCompleto, type Profesional } from './api.ts';
import { cargarEn, type ContextoSeccion } from './ui.ts';

/** Valor del selector para "Todos los profesionales". */
const TODOS = 'todos';

export const MENSAJE_SIN_PROFESIONAL = 'Tu usuario no está vinculado a un profesional.';

/**
 * Profesional sobre el que trabajan Horarios, Ausencias y Bloqueos (requisito "Profesional de las
 * secciones de agenda", design D4):
 * - `ADMIN` y `RECEPCIONISTA` lo eligen en un selector de profesionales activos;
 * - `ODONTOLOGO` usa el `profesionalId` de su sesión, sin selector, o ve un aviso si no tiene uno.
 *
 * `alElegir` se llama con el profesional elegido (al montar y en cada cambio), junto con la lista de
 * profesionales cargada. Si el odontólogo no tiene profesional, no se llama y no se pide nada.
 *
 * Con `alElegirTodos`, quienes ven todo (`permisos.veTodos`) tienen además "Todos los profesionales",
 * elegido al abrir la pestaña (design D12). `alElegirTodos` recibe la lista de profesionales.
 */
export function montarProfesionalElegido(
  panel: HTMLElement,
  contexto: ContextoSeccion,
  textoFijo: string,
  alElegir: (profesionalId: number, profesionales: readonly Profesional[]) => void,
  alElegirTodos?: (profesionales: readonly Profesional[]) => void,
): void {
  const encabezado = document.createElement('div');
  panel.append(encabezado);

  if (contexto.permisos.profesionalFijo) {
    const id = contexto.permisos.profesionalFijoId;
    if (id === null) {
      const aviso = crearAviso('status');
      aviso.className = 'aviso aviso-info';
      aviso.textContent = MENSAJE_SIN_PROFESIONAL;
      encabezado.append(aviso);
      return;
    }
    const fijo = document.createElement('p');
    fijo.className = 'profesional-fijo';
    fijo.textContent = textoFijo;
    encabezado.append(fijo);
    // El odontólogo puede leer el catálogo (RBAC): se usa solo para mostrar su nombre.
    void cargarProfesionales(contexto.api).then(
      (lista) => {
        const propio = lista.find((p) => p.id === id);
        if (propio !== undefined) {
          fijo.textContent = `${textoFijo} · ${nombreCompleto(propio)}`;
        }
        alElegir(id, lista);
      },
      () => alElegir(id, []),
    );
    return;
  }

  cargarEn(encabezado, contexto, () => cargarProfesionales(contexto.api), (lista, contenedor) => {
    const activos = lista.filter((p) => p.activo);
    const conTodos = alElegirTodos !== undefined && contexto.permisos.veTodos;
    const selector = crearCampo(
      'Profesional',
      crearSelector('profesional', [
        ...(conTodos ? [{ valor: TODOS, texto: 'Todos los profesionales' }] : []),
        ...activos.map((p) => ({ valor: String(p.id), texto: nombreCompleto(p) })),
      ]),
    );
    selector.contenedor.classList.add('selector-profesional');
    contenedor.append(selector.contenedor);
    const primero = activos[0];
    if (primero === undefined) {
      const vacio = document.createElement('p');
      vacio.className = 'ayuda';
      vacio.textContent = 'No hay profesionales activos.';
      contenedor.append(vacio);
      return;
    }
    const elegir = (): void => {
      if (selector.control.value === TODOS && alElegirTodos !== undefined) {
        alElegirTodos(lista);
      } else {
        alElegir(Number(selector.control.value), lista);
      }
    };
    selector.control.addEventListener('change', elegir);
    elegir();
  });
}

/**
 * Campo "Profesional" del formulario de registro cuando la vista es "Todos los profesionales":
 * no asume ninguno (`value` vacío) y pide elegir uno.
 */
export function crearCampoProfesional(profesionales: readonly Profesional[]): Campo<HTMLSelectElement> {
  const selector = crearSelector('profesionalId', [
    { valor: '', texto: 'Elegí un profesional' },
    ...profesionales.filter((p) => p.activo).map((p) => ({ valor: String(p.id), texto: nombreCompleto(p) })),
  ]);
  selector.classList.add('control');
  return crearCampo('Profesional', selector);
}

/** Profesional elegido en `crearCampoProfesional`, o `null` (con su error en el campo) si falta. */
export function leerCampoProfesional(campo: Campo<HTMLSelectElement>): number | null {
  if (campo.control.value === '') {
    campo.mostrarError('Elegí un profesional.');
    return null;
  }
  campo.mostrarError('');
  return Number(campo.control.value);
}
