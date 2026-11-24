# Proposal

## Why

La aplicación funciona, pero cada pantalla se fue armando en un change distinto (C-09, C-12, C-33 a C-38 y C-40) y se nota: el menú es una lista dentro del encabezado y no hay barra lateral; el encabezado y el pie no tienen el color de la marca; los botones no reaccionan igual al pasar el mouse o al enfocarlos con el teclado; los diálogos ponen "Cancelar" y "Confirmar" en distinto orden según la pantalla; y las listas vacías o en carga son solo texto. Además `styles.css` tiene más de 2.000 líneas en un único archivo y todavía mezcla los nombres viejos de variables (`--color-*`, `--espacio-*`, `--radio`: unos 90 usos) con los del design system (`--ds-*`). En la revisión del 2026-11-23 se pidió que la aplicación se vea **más bonita y uniforme**. Este change lo hace por etapas, sin cambiar el comportamiento.

## What Changes

- **Barra lateral desplegable por rol.** Con sesión, el menú pasa a una barra lateral con las opciones del rol que ingresó: "Reservar turno" y "Mis turnos" para el paciente, "Agenda" y "Configuración" para el personal. En el celular es un cajón que se abre con el botón "Menú" (se cierra al elegir, con "Escape" o al tocar fuera); desde 768 px se ve siempre. Sin sesión se mantiene un encabezado simple, sin barra lateral, con un único botón "Ingresar" arriba a la derecha en todas las páginas, la portada incluida (decidido por la líder el 2026-11-23: "Acceso personal" y "Ingresar" se unifican; la portada deja de usar "Acceso personal").
- **Color de marca en el encabezado y el pie.** El margen superior y un pie nuevo (con el nombre de la aplicación) pasan al azul de los botones (`--ds-acento-500`), con texto blanco y contraste de al menos 4,5:1 (AA).
- **Hover y foco en todos los botones**, según su estilo (principal, secundario, peligro, fantasma, botones de horario `.chip`, opciones y filas elegibles), con transiciones suaves que respetan `prefers-reduced-motion`.
- **Más bonito y uniforme:** más aire entre elementos, tarjetas con sombra suave y radios consistentes, jerarquía tipográfica clara, íconos SVG propios, estados vacíos y de carga con ilustración, y los mismos botones, campos, avisos y diálogos en todas las pantallas (por ejemplo, el mismo orden de botones y el mismo lugar del aviso en todos los diálogos).
- **Base técnica, sin cambiar el comportamiento:** dejar solo variables `--ds-*` (se eliminan los alias `--color-*`, `--espacio-*` y `--radio` de `tokens.css` y de los estilos) y partir `styles.css` en módulos.
- **Por etapas, de a un PR por vez**, cada una con mockups aprobados y capturas de antes y después a 390 y 1440 px:
  - **A.** Tokens (design system **Revision 2**) y CSS modular, sin cambio visual.
  - **B.** Barra lateral, encabezado, pie y componentes comunes (botones, campos, avisos, diálogos, íconos, estados vacíos y de carga).
  - **C.** Pantallas del paciente: portada, reserva, Mis turnos, Mi cuenta y confirmación.
  - **D.** Pantallas del personal: agenda, Nuevo turno, Configuración y sus diálogos.
- **Observaciones de otras pantallas** que lleguen del equipo se suman como una etapa más.

Fuera de alcance:
- Funcionalidades nuevas, cambios de reglas de negocio y cualquier cambio en `backend/`.
- El logo y el nombre definitivos (siguen `(TBD)` en el design system), el modo oscuro y las librerías de UI o de íconos.
- Datos reales del consultorio (dirección, teléfono, horarios) en el pie.

## Capabilities

### New Capabilities

- `frontend-sistema-visual`: las reglas visuales comunes que se pueden verificar: color y contraste del encabezado y el pie, hover y foco de los botones, movimiento reducido, solo variables del design system, estilos en módulos, y botones y avisos uniformes en los diálogos.

### Modified Capabilities

- `frontend-navegacion`: el menú pasa a una barra lateral por rol (cajón en el celular, siempre visible desde 768 px) y el encabezado sin sesión se mantiene simple.

## Impact

- **`frontend/src/`** (módulo de Enzo, que se avisa en cada PR): `shared/ui/layout.ts`, `styles.css` (se parte en módulos), componentes de `shared/ui/`, y los estilos y la estructura de todas las pantallas (`pages/`, `agenda/`, `paciente/`, `configuracion/`).
- **`design-system/`**: la etapa A sube `tokens.css` a **Revision 2** (se quitan los alias) y pide renovar la aprobación del sistema; `components.html` se actualiza con el encabezado, la barra lateral, el pie y los estados nuevos.
- **Tests**: los que leen `styles.css` como texto pasan a leer sus módulos por un ayudante común; los demás siguen pasando, y los que dependen del menú dentro del encabezado se actualizan y se informa cada uno.
- **Dependencias**: C-33 a C-38 y C-40 (todos mergeados). El equipo acordó que el apply no espera el archive de `ingreso-unico-mi-cuenta`; el archive de C-39 va al final, después de los de `ingreso-unico-mi-cuenta` y `frontend-portada-y-acceso`.
