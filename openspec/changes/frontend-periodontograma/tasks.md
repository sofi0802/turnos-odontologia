# Tasks

> Rama `change/C-46-frontend-periodontograma`, que sale de `change/C-25-periodontograma`. El PR se mergea después del de C-25. Tests primero en cada grupo (Vitest con jsdom). Cada escenario de `specs/frontend-ficha-clinica/spec.md` tiene su test con el mismo nombre (regla dura 7). Criterio de aceptación visual: los mockups aprobados de `mockups/`.

## 1. Preparación y design system

- [ ] 1.1 Avisar a Enzo (dueño de `frontend/`) y a Emanuel (ficha de C-44) y verificar que el aviso quede en la descripción del PR
- [ ] 1.2 Sumar los tokens `--ds-perio-*` (design D7) a `design-system/tokens.css` y la sección "Periodontograma" a `components.html`; subir `README.md` a la Revision 5 con su fila en el historial; verificar el contraste AA del color nuevo y regenerar `design-system/previews/` a 390 y 1440 px
- [ ] 1.3 Actualizar `visual_design.revision` en `.active-orchestrator-state.json` y verificar que el JSON siga siendo válido

## 2. Modelo y pedidos

- [ ] 2.1 Escribir los tests de `ficha/periodontograma-modelo.ts` (orden de las piezas, multirradiculares 14, 24 y molares, nombres de los sitios por maxilar, validación con los bordes 0/15 y −5/15, piezas incompletas, versión sin piezas, armado del pedido con recesión negativa) y después el módulo; verificar con `npm test`
- [ ] 2.2 Escribir los tests de `ficha/periodontograma-api.ts` (lectura defensiva de la lista y de la versión, pedidos con las rutas de C-25) y después el módulo; verificar con `npm test`
- [ ] 2.3 Sumar `SITIOS_INCOMPLETOS` y `FURCACION_NO_APLICA` a `MENSAJES_POR_CODIGO` con sus tests en `mensajes.test.ts`; verificar con `npm test`

## 3. Consulta

- [ ] 3.1 Escribir los tests de los escenarios de "Periodontograma en la ficha", "Mapa de piezas con su estado", "Detalle de una pieza", "Versiones anteriores" y "Periodontograma accesible" (nombre de la pieza), y después la sección en modo consulta montada en `pages/pacientes.ts`; verificar con `npm test`
- [ ] 3.2 Crear `styles/19-periodontograma.css` (design D8) según los mockups aprobados e importarlo en `styles.css`; verificar con el test de módulos de estilos (`estilos-modulos.test.ts`) y `npm run build`

## 4. Nueva versión

- [ ] 4.1 Escribir los tests de "Nueva versión precargada", "Cargar, marcar ausente y quitar piezas", "Convención de la recesión" y "Periodontograma accesible" (nombre de un campo), y después el formulario; verificar con `npm test`
- [ ] 4.2 Escribir los tests de "Validación antes de guardar" y "Guardar la versión" (incluidos los rechazos `FURCACION_NO_APLICA` y `SITIOS_INCOMPLETOS`), y después la validación y el envío; verificar con `npm test`
- [ ] 4.3 Escribir los tests de "Descartar una carga sin guardar" (cancelar y cambiar de paciente) y sumar `sinGuardar()` a la confirmación de `pages/pacientes.ts`; verificar que los tests de C-44 sigan pasando

## 5. Permisos y privacidad

- [ ] 5.1 Escribir los tests de "Solo lectura para el administrador" (sin "Nueva versión") y de "Datos de salud solo en la pantalla" (una carga no queda en la dirección, `localStorage`, `sessionStorage` ni la consola); verificar con `npm test`

## 6. Verificación

- [ ] 6.1 Correr `npm test` y `npm run build` y verificar que todo pase, incluidos los tests de C-44
- [ ] 6.2 Verificación visual contra el backend de desarrollo con datos ficticios, como `ana.ejemplo` y como `admin`, a 390 × 844 y 1440 × 900: comparar cada pantalla con su mockup, revisar la consola y los pedidos fallidos, guardar las capturas en `mockups/verify/` y anotar las diferencias en el design
- [ ] 6.3 Marcar C-46 en `CHANGES.md` y abrir el PR a `master` (después del de C-25), con Enzo y Emanuel como revisores

## Workflow follow-up

- Archivar el change después del merge, junto con el archive de C-25 o después de él.
