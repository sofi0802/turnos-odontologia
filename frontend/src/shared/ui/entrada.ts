/**
 * Traduce las direcciones de los links de los avisos de C-11, que son de camino y no de
 * hash (design D9 de C-12), antes de crear el router. No suma entradas al historial.
 */
let tokenConfirmacion: string | null = null;

const LINK_MIS_TURNOS = /^(.*)\/mis-turnos\/(cancelar|reprogramar)\/?$/;
const LINK_CONFIRMAR = /^(.*)\/confirmar\/([^/]+)\/?$/;

export function traducirEntrada(ubicacion: Location = window.location): void {
  const camino = ubicacion.pathname;
  const misTurnos = LINK_MIS_TURNOS.exec(camino);
  if (misTurnos !== null) {
    history.replaceState(history.state, '', `${misTurnos[1] ?? ''}/#/mis-turnos`);
    return;
  }
  const confirmar = LINK_CONFIRMAR.exec(camino);
  if (confirmar !== null) {
    // El token queda solo en memoria: ni en la dirección ni en el almacenamiento.
    tokenConfirmacion = decodeURIComponent(confirmar[2] ?? '');
    history.replaceState(history.state, '', `${confirmar[1] ?? ''}/#/confirmar`);
  }
}

/** Token del link de confirmación que abrió la aplicación, si lo hubo. */
export function tokenDeConfirmacion(): string | null {
  return tokenConfirmacion;
}

/** Para los tests: olvida el token guardado. */
export function olvidarTokenDeConfirmacion(): void {
  tokenConfirmacion = null;
}
