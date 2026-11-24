import logoUrl from '../../../../design-system/assets/logo.svg';

/** Nombre de la aplicación: título de la portada, texto del pie y nombre accesible del logo. */
export const NOMBRE_APLICACION = 'Turnos odontología';

/**
 * Logo del encabezado: el archivo `design-system/assets/logo.svg`, que ya lleva el nombre. Reemplazar el
 * archivo cambia el logo en toda la aplicación (ver "Cómo reemplazar lo provisorio" en el design system).
 */
export function crearLogo(): HTMLImageElement {
  const logo = document.createElement('img');
  logo.src = logoUrl;
  logo.alt = NOMBRE_APLICACION;
  logo.className = 'logo';
  return logo;
}
