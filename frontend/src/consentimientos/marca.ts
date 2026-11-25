/**
 * Marca de "firma en curso" (C-48, design D12): un `"1"` en `localStorage`, sin ningún dato del paciente ni del
 * consentimiento. Se pone al abrir la vista de firma y se borra solo al desbloquear con éxito o al ir al ingreso. Si la
 * aplicación arranca con la marca (recarga u otra pestaña), cierra la sesión antes de dibujar cualquier página.
 */

export const CLAVE_DE_LA_MARCA = 'turnos.firmaEnCurso';

/** Aviso del ingreso cuando la aplicación arranca con la marca (recarga u otra pestaña durante la firma o el bloqueo). */
export const MENSAJE_SESION_CERRADA_AL_ARRANCAR =
  'Por seguridad, la sesión se cerró. Si estabas haciendo firmar un consentimiento, revisá la ficha del paciente para ver si quedó guardado.';

/** El almacenamiento puede fallar (ventana privada, datos bloqueados): la marca es una ayuda, nunca un requisito. */
export function ponerMarca(): void {
  try {
    localStorage.setItem(CLAVE_DE_LA_MARCA, '1');
  } catch {
    // Sin almacenamiento: la firma sigue, sin la marca.
  }
}

export function borrarMarca(): void {
  try {
    localStorage.removeItem(CLAVE_DE_LA_MARCA);
  } catch {
    // Sin almacenamiento: no hay nada que borrar.
  }
}

export function hayMarca(): boolean {
  try {
    return localStorage.getItem(CLAVE_DE_LA_MARCA) !== null;
  } catch {
    return false;
  }
}
