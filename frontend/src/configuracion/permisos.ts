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
  /** Reasignar los turnos afectados por una ausencia o un bloqueo, y verlos (design D3 y D8). */
  readonly reasigna: boolean;
  /** Ver la pestaña Bloqueos: los tres roles (el odontólogo, solo los suyos). */
  readonly veBloqueos: boolean;
  /** Registrar y borrar bloqueos: `ADMIN` y `ODONTOLOGO`. */
  readonly gestionaBloqueos: boolean;
  /** Elegir "Todos los profesionales" en Ausencias y Bloqueos (design D3 y D12). */
  readonly veTodos: boolean;
  /**
   * `true` si las pestañas de agenda trabajan sobre un profesional fijo (el odontólogo).
   * En ese caso el profesional es `profesionalFijoId`, o `null` si el usuario no tiene uno vinculado.
   */
  readonly profesionalFijo: boolean;
  readonly profesionalFijoId: number | null;
  /** Ver y administrar las cuentas del personal (pestaña Usuarios y "Dar acceso"): solo `ADMIN` (C-41). */
  readonly gestionaUsuarios: boolean;
  /** Mantener el catálogo de obras sociales, planes y nomenclador (pestaña "Obras sociales"): solo `ADMIN` (C-49, D5). */
  readonly gestionaCoberturas: boolean;
  /** Configurar las señas de la reserva online: solo `ADMIN` (C-23, RN-SE-01). */
  readonly configuraSenas: boolean;
}

const NINGUNO: Permisos = {
  editaCatalogos: false,
  editaMargen: false,
  editaHorarios: false,
  gestionaAusencias: false,
  reasigna: false,
  veBloqueos: false,
  gestionaBloqueos: false,
  veTodos: false,
  profesionalFijo: false,
  profesionalFijoId: null,
  gestionaUsuarios: false,
  gestionaCoberturas: false,
  configuraSenas: false,
};

export function permisos(sesion: Sesion): Permisos {
  switch (sesion.rol) {
    case 'ADMIN':
      return {
        editaCatalogos: true,
        editaMargen: true,
        editaHorarios: true,
        gestionaAusencias: true,
        reasigna: true,
        veBloqueos: true,
        gestionaBloqueos: true,
        veTodos: true,
        profesionalFijo: false,
        profesionalFijoId: null,
        gestionaUsuarios: true,
        gestionaCoberturas: true,
        configuraSenas: true,
      };
    case 'RECEPCIONISTA':
      return { ...NINGUNO, gestionaAusencias: true, reasigna: true, veBloqueos: true, veTodos: true };
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
