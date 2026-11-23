import { crearAviso, crearCampo, crearSelector } from '../shared/ui/campos.ts';
import { cargarProfesionales, nombreCompleto, type Profesional } from './api.ts';
import { cargarEn, type ContextoSeccion } from './ui.ts';

export const MENSAJE_SIN_PROFESIONAL = 'Tu usuario no está vinculado a un profesional.';

/**
 * Profesional sobre el que trabajan Horarios, Ausencias y Bloqueos (requisito "Profesional de las
 * secciones de agenda", design D4):
 * - `ADMIN` y `RECEPCIONISTA` lo eligen en un selector de profesionales activos;
 * - `ODONTOLOGO` usa el `profesionalId` de su sesión, sin selector, o ve un aviso si no tiene uno.
 *
 * `alElegir` se llama con el profesional elegido (al montar y en cada cambio), junto con la lista de
 * profesionales cargada. Si el odontólogo no tiene profesional, no se llama y no se pide nada.
 */
export function montarProfesionalElegido(
  panel: HTMLElement,
  contexto: ContextoSeccion,
  textoFijo: string,
  alElegir: (profesionalId: number, profesionales: readonly Profesional[]) => void,
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
    const selector = crearCampo(
      'Profesional',
      crearSelector(
        'profesional',
        activos.map((p) => ({ valor: String(p.id), texto: nombreCompleto(p) })),
      ),
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
    selector.control.addEventListener('change', () => alElegir(Number(selector.control.value), lista));
    alElegir(primero.id, lista);
  });
}
