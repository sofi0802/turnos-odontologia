import type { Sesion } from '../shared/sesion/sesion.ts';

/**
 * Qué puede hacer cada rol en Configuración, según la matriz RBAC (design D3).
 * Solo decide qué botones se dibujan: la API sigue siendo la que autoriza.
 */
export interface Permisos {
  /** Alta, edición, activar y desactivar de profesionales, sillones y prestaciones, más especialidades y sillón habitual. */
  readonly editaCatalogos: boolean;
  readonly editaMargen: boolean;
  readonly editaHorarios: boolean;
  /** Registrar y borrar ausencias, y reasignar los turnos afectados. */
  readonly gestionaAusencias: boolean;
  readonly veBloqueos: boolean;
  readonly gestionaBloqueos: boolean;
  /** Elegir "Todos los profesionales" en Ausencias y Bloqueos (design D3 y D12). */
  readonly veTodos: boolean;
  /**
   * `true` si las pestañas de agenda trabajan sobre un profesional fijo (el odontólogo).
   * En ese caso el profesional es `profesionalFijoId`, o `null` si el usuario no tiene uno vinculado.
   */
  readonly profesionalFijo: boolean;
  readonly profesionalFijoId: number | null;
}

const NINGUNO: Permisos = {
  editaCatalogos: false,
  editaMargen: false,
  editaHorarios: false,
  gestionaAusencias: false,
  veBloqueos: false,
  gestionaBloqueos: false,
  veTodos: false,
  profesionalFijo: false,
  profesionalFijoId: null,
};

export function permisos(sesion: Sesion): Permisos {
  switch (sesion.rol) {
    case 'ADMIN':
      return {
        editaCatalogos: true,
        editaMargen: true,
        editaHorarios: true,
        gestionaAusencias: true,
        veBloqueos: true,
        gestionaBloqueos: true,
        veTodos: true,
        profesionalFijo: false,
        profesionalFijoId: null,
      };
    case 'RECEPCIONISTA':
      return { ...NINGUNO, gestionaAusencias: true, veTodos: true };
    case 'ODONTOLOGO':
      return {
        ...NINGUNO,
        veBloqueos: true,
        gestionaBloqueos: true,
        profesionalFijo: true,
        profesionalFijoId: sesion.profesionalId ?? null,
      };
    default:
      return NINGUNO;
  }
}
