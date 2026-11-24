# Verificación visual de C-38 (2026-11-23)

- Design system revision: 1. Mockups aprobados por la líder.
- Entorno: backend real (`bootRun` con `.env`, PostgreSQL en Docker) y frontend con Vite en `http://localhost:5173`, sin sesión. La lista larga (12 prestaciones) se simuló solo para `GET /public/prestaciones`; el resto va al backend real.
- Capturas: `<pantalla>-<ancho>.png` en esta carpeta (390 × 844, 1440 × 900 y 320 × 844 donde aplica).

## Pantallas revisadas

| Pantalla | 390 | 1440 | 320 | Resultado |
|---|---|---|---|---|
| `paso1-cerrado` | sí | sí | sí | coincide |
| `paso1-abierto` (4 prestaciones reales) | sí | sí | sí | coincide |
| `paso1-filtrado` | sí | sí | no | coincide |
| `paso1-vacio` | sí | sí | no | coincide |
| `paso1-elegido` | sí | sí | no | coincide |
| `paso1-sin-elegir` | sí | sí | no | coincide |
| `paso1-lista-larga` (12 simuladas) | sí | sí | sí | coincide: se desplaza por dentro |

No se revisó: los estados filtrado, sin coincidencias, elegido y sin elegir a 320 px.

## Comprobaciones en el navegador

- Sin desplazamiento horizontal en ninguna captura; consola sin errores; ningún pedido fuera de `localhost`.
- Por carga de la página solo hay `GET /public/sesion` y `GET /public/prestaciones`: escribir en el selector no envía pedidos.
- Búsqueda: "limp" → "Limpieza (profilaxis)"; "EXTRACCION" → "Extracción simple"; "xyz" → "No hay prestaciones que coincidan con «xyz»."; borrar el texto vuelve a las cuatro.
- Teclado: flecha abajo ×2 + Enter elige la segunda; Escape cierra sin elegir; Tab cierra; la elección sobrevive a "Volver" desde el paso 2.
- Toque: elegir una opción y "Continuar" pasa al paso 2 y pide `GET /public/profesionales?prestacionId=4` (la elegida).
- Cambiar el texto después de elegir y tocar "Continuar" muestra "Elegí una prestación.".
- Lista larga: 12 opciones de 48 px, lista de 16 rem con desplazamiento propio y ancho igual al del campo (324 px a 390, 254 px a 320, 510 px a 1440).

## Problema encontrado y corregido durante la verificación

Con la lista abierta, tocar "Continuar" cerraba la lista en el `mousedown` y el botón se corría antes del `click`: el toque se perdía. Ahora la lista espera a que se suelte el puntero para cerrarse, con dos tests y la prueba en el navegador.

## Diferencias con el mockup

| Prioridad | Pantalla | Esperado (mockup) | Observado | Reproducción |
|---|---|---|---|---|
| minor | Todas | Encabezado con logo provisorio y avatar | Encabezado actual, sin logo ni avatar | Abrir `#/reserva` |
| minor | Todas | Fondo de página gris azulado | Fondo blanco | Abrir `#/reserva` |
| minor | Selector enfocado | Un solo borde de acento | Anillo de foco del navegador sumado al borde de acento (más grueso) | Enfocar el campo |

Las dos primeras son del encabezado y el fondo, que rediseña C-39. Blockers: ninguno. Majors: ninguno.
