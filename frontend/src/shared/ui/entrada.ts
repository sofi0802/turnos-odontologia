/**
 * Traduce las direcciones de los links de los avisos de C-11, que son de camino y no de
 * hash (design D9 de C-12), antes de crear el router. No suma entradas al historial.
 */
let tokenConfirmacion: string | null = null;
/** C-23: la aplicación se abrió al volver de Mercado Pago (`#/mis-turnos?pago=…`). */
let vueltaDePago = false;

const LINK_MIS_TURNOS = /^(.*)\/mis-turnos\/(cancelar|reprogramar)\/?$/;
const LINK_CONFIRMAR = /^(.*)\/confirmar\/([^/]+)\/?$/;
const VUELTA_DE_PAGO = /^#\/mis-turnos\?pago=/;

let escuchaVueltaDePago = false;

/**
 * Si la dirección es la vuelta de un pago, la guarda y deja `#/mis-turnos`. El resultado de la vuelta no se usa: la
 * seña se da por pagada solo con la notificación verificada (RN-SE-05).
 */
function tomarVueltaDeLaDireccion(ubicacion: Location): boolean {
  if (!VUELTA_DE_PAGO.test(ubicacion.hash)) {
    return false;
  }
  vueltaDePago = true;
  history.replaceState(history.state, '', `${ubicacion.pathname}#/mis-turnos`);
  return true;
}

export function traducirEntrada(ubicacion: Location = window.location): void {
  if (!escuchaVueltaDePago) {
    // La vuelta también puede llegar sin recargar la página (el link de la pasarela simulada solo cambia el hash).
    // Este listener se registra antes que el del router, así que la dirección ya está traducida cuando el router la lee.
    escuchaVueltaDePago = true;
    window.addEventListener('hashchange', () => {
      tomarVueltaDeLaDireccion(window.location);
    });
  }
  const camino = ubicacion.pathname;
  if (tomarVueltaDeLaDireccion(ubicacion)) {
    return;
  }
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

/** C-23: si se volvió de Mercado Pago; la primera lectura lo consume. */
export function tomarVueltaDePago(): boolean {
  const volvio = vueltaDePago;
  vueltaDePago = false;
  return volvio;
}

/** Para los tests: olvida el token guardado. */
export function olvidarTokenDeConfirmacion(): void {
  tokenConfirmacion = null;
}
