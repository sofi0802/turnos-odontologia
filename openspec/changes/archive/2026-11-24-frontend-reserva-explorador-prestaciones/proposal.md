# Proposal

## Why

En el paso 1 de `#/reserva` ("¿Qué necesitás?") el paciente ve todas las prestaciones del consultorio como una lista de botones de opción. Con el catálogo chico del seed alcanza, pero un consultorio real puede tener decenas de prestaciones, y una lista larga obliga a desplazarse en el celular y a leerlas todas para encontrar la que busca. En la revisión del 2026-11-23 se pidió un "explorador" de prestaciones, y la líder decidió el formato: un selector desplegable con buscador que muestra solo las coincidencias.

## What Changes

- **El paso 1 pasa de una lista de opciones a un selector desplegable con buscador** (un combobox accesible, con teclado y con toque): se escribe parte del nombre y la lista muestra solo las coincidencias, cada una con su nombre y su duración ("Consulta · 30 minutos"). Sin texto, muestra todas.
- **La búsqueda ignora mayúsculas y tildes** ("limp" encuentra "Limpieza dental", "extraccion" encuentra "Extracción").
- **Sin coincidencias**, la lista dice "No hay prestaciones que coincidan con «xyz»." en lugar de quedar vacía.
- **Al elegir una prestación se sigue con el flujo actual**: "Continuar" lleva al paso 2 con la misma prestación, sin otro cambio. Si se vuelve del paso 2, la prestación sigue elegida y visible en el selector.
- **Cambiar el texto del buscador después de elegir quita la elección**, para que "Continuar" nunca avance con una prestación distinta de la que se ve.
- **Se mantiene** el mensaje "Elegí una prestación." al continuar sin elegir, y "No pudimos cargar las prestaciones…" (sin "Continuar") si no hay prestaciones o el pedido falla.
- **Design system y mockups.** Es una pantalla con un componente nuevo: se aplica el modo `system-and-mockups` (revisión 1). Los mockups se crean y se aprueban antes del apply.

Fuera de alcance:
- Los pasos 2 y 3, el resumen y lo que se envía a la API (no cambian).
- Categorías de prestaciones: necesitarían un cambio en el backend (`GET /public/prestaciones` no las devuelve).
- La búsqueda en el servidor: las prestaciones ya llegan completas en un solo pedido y se filtran en el navegador.
- El rediseño general, el encabezado y los colores (C-39).
- Cualquier cambio en `backend/`.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `frontend-reserva-paciente`: la elección de la prestación pasa a un selector con buscador (búsqueda, teclado, toque, accesibilidad y celular).

## Impact

- **`frontend/src/`** (módulo de Enzo, que se avisa en el PR): `pages/reserva.ts` (paso 1), un componente nuevo en `shared/ui/` (selector con buscador, sin dependencias) y `styles.css`.
- **Tests**: `reserva-paciente.test.ts` y los que eligen la prestación como botón de opción se actualizan al selector, y se informa cada uno. Los pasos 2 y 3 y sus tests no cambian.
- **Dependencias**: C-12 (la reserva) y C-37 (portada y encabezado, ya mergeado). El apply espera al PR de C-40 (Enzo), que toca otras pantallas pero también `styles.css`.
