# Design

## Context

Lo observado en `master` (después de C-38 y C-40, 447 tests y build en verde):

- **Estilos**: `frontend/src/styles.css` tiene 2.074 líneas en un solo archivo, con secciones de cada change (agenda, diálogos, C-33, componentes del design system, selector C-38, páginas del paciente, buscador C-36, portada C-37, nuevo turno C-40, configuración C-34). Usa 487 veces `var(--ds-*)` y todavía unas 92 veces los alias viejos: `--color-*` (46), `--espacio-*` (38) y `--radio` (8), que `design-system/tokens.css` define "con los nombres que ya usaba `frontend/`".
- **Los tests leen `styles.css` como texto** (`import estilos from '../src/styles.css?raw'`, en `responsivo.test.ts` y `portada.test.ts`). Partir el archivo en módulos con `@import` hace que `?raw` devuelva solo las importaciones.
- **Encabezado** (`shared/ui/layout.ts`): `<header class="encabezado">` con la marca (enlace a `#/inicio`), el botón "Menú", el `<nav id="menu-principal">` con los enlaces de las páginas con `menu` (filtrados por rol: `item.hidden`) y el bloque de usuario ("Salir", o "Ingresar" / "Acceso personal"). Fondo claro (`--color-superficie`). No hay pie.
- **Menú**: dentro del encabezado, plegable en pantallas de menos de 768 px y siempre visible desde 768 px. Los tests de navegación dependen de `nav a`, `#menu-principal`, `button[aria-controls="menu-principal"]`, `.abierto`, `li[hidden]` y de `.usuario-nombre`.
- **Diálogos**: hay siete lugares con `<dialog>` (`agenda/formularios.ts`, `configuracion/{ausencias,bloqueos,catalogo,reasignacion,ui}.ts` y `pages/mis-turnos.ts`). El orden de los botones no es uniforme: `formularios.ts` pone "Crear turno" y después "Cerrar"; `configuracion/ui.ts` pone "Cancelar" y después el envío; `mis-turnos.ts` pone "Volver" y después "Confirmar".
- **Hover y foco**: `.btn`, `.chip` y `.opcion` tienen algunos estados; los demás botones (`.boton-secundario`, `.menu a`, filas de Configuración) no tienen hover ni foco propios más allá del foco por defecto.
- **Design system**: Revision 1, aprobada el 2026-11-22. `tokens.css` es la única fuente de valores y su regla es que un cambio de un token compartido sube la revisión y obliga a renovar la aprobación. El acento es `#0b6e8a` (contraste 5,7:1 sobre blanco, y también con texto blanco encima).

Motivación y alcance: ver `proposal.md`.

## Goals / Non-Goals

**Goals:**
- Que la aplicación se vea más bonita y uniforme sin cambiar lo que hace.
- Una barra lateral por rol, con el comportamiento de cajón en el celular.
- Estilos que se puedan mantener: un solo juego de variables y módulos chicos.
- Hacerlo en etapas que se puedan revisar, aprobar y mergear una por una.

**Non-Goals:**
- Funcionalidades nuevas, reglas de negocio o backend.
- Logo y nombre definitivos, modo oscuro, librerías de UI o de íconos, fuentes remotas.
- Rehacer el HTML de pantallas que ya funcionan, salvo lo que pida la barra lateral, el pie y los componentes comunes.

## Decisions

### D1. Cuatro etapas, un PR por vez, cada una con su compuerta
Se cumple el modo `system-and-mockups`: cada etapa con cambio visual (B, C y D) tiene sus mockups, su aprobación de la líder antes del apply y capturas de antes y después a 390 y 1440 px (en `mockups/<etapa>/`). La etapa A no cambia nada visible: se verifica con que todos los tests y capturas de antes y después son iguales.

| Etapa | Qué incluye | Mockups |
|---|---|---|
| A | `tokens.css` Revision 2 sin alias, `styles.css` en módulos, ayudante de tests para leer los estilos | No (sin cambio visual; capturas idénticas) |
| B | Barra lateral, encabezado y pie, botones con hover y foco, íconos, diálogos, estados vacíos y de carga | Sí |
| C | Portada, Reservar turno, Mis turnos, Mi cuenta y confirmación con los componentes nuevos | Sí |
| D | Agenda, Nuevo turno, Configuración y sus diálogos | Sí; su PR espera al merge de C-40 (ya mergeado) |

### D2. Design system Revision 2, solo para quitar los alias
Quitar los alias de `tokens.css` es un cambio de un archivo compartido, así que sube la revisión a 2 y pide renovar la aprobación del sistema (`design-system/README.md`). Como los alias valen lo mismo que los `--ds-*`, no cambia ningún valor ni el aspecto de ninguna pantalla; el encabezado y el pie azules (etapa B) sí cambian el aspecto, y se aprueban con los mockups de esa etapa. `components.html` se actualiza en la etapa B con la barra lateral, el pie y los estados nuevos.

### D3. Estilos en módulos con `@import`, y un ayudante para los tests
`styles.css` pasa a ser la importación de los tokens y una lista de `@import` de módulos en `src/styles/`, cortados **de forma contigua y en el mismo orden** que tenía el archivo único, para no alterar la cascada: `01-base`, `02-formularios`, `03-agenda`, `04-dialogos`, `05-reprogramar`, `06-componentes`, `07-selector-prestaciones`, `08-paciente`, `09-buscador-pacientes`, `10-portada`, `11-nuevo-turno` y `12-configuracion`. Los módulos nuevos de la etapa B (barra lateral, encabezado y pie) se suman con su número. Reordenar por tema queda para después, con capturas que lo respalden. Vite resuelve los `@import` en el build y en el servidor de desarrollo. Los tests que hoy hacen `import estilos from '../src/styles.css?raw'` leen en su lugar `test/estilos.ts`, que importa cada módulo con `?raw` y los junta en el orden de `styles.css` (Vitest deja vacíos los `.css` que no están en `css.include`, así que `vite.config.ts` suma `src/styles/` y `design-system/tokens.css`, y `server.fs.allow` suma `../design-system`; un test avisa si un módulo del disco no está en el ayudante); así las reglas de CSS que ya se comprueban (por ejemplo, 44 px en los botones) siguen funcionando sin tocar su texto.

*Alternativa descartada*: dejar un solo archivo y ordenarlo. No resuelve que cada change agregue 200 líneas al final, y el archivo ya choca entre changes en paralelo.

### D4. La barra lateral reutiliza el `<nav id="menu-principal">`
Para no romper los tests ni la accesibilidad, el `<nav>` conserva su id, su `aria-label="Principal"`, su botón con `aria-controls="menu-principal"` y `aria-expanded`, y los `li[hidden]` por rol. Cambia dónde está y cómo se ve:
- Con sesión, `layout.ts` arma una estructura de dos columnas: la barra lateral (`<aside>` con el `<nav>`) a la izquierda y el contenido a la derecha; el encabezado queda arriba a todo el ancho con la marca, el botón "Menú" (solo en el celular) y el usuario con "Salir".
- Sin sesión no se dibuja la barra lateral: el encabezado ofrece "Reservar turno" y un único botón "Ingresar" a `#/mi-cuenta` arriba a la derecha, también en la portada (decisión de la líder, ver D10).
- En el celular (menos de 768 px) la barra es un cajón que se desliza desde la izquierda sobre una capa que oscurece el contenido; se cierra al elegir una página, con "Escape" o al tocar la capa. El foco pasa al cajón al abrirlo y vuelve al botón al cerrarlo. Sin animación con `prefers-reduced-motion`.
- La barra lateral y el encabezado se muestran o se ocultan cuando llega `mostrarSesion`, sin recargar.

### D5. Color de marca y contraste
El encabezado y el pie usan `--ds-acento-500` de fondo y `--ds-acento-texto` de texto. El contraste es 5,7:1 (AA). Los enlaces y botones dentro del encabezado azul usan variantes sobre azul (fondo `rgb` translúcido o `--ds-acento-600` en hover, `--ds-acento-700` presionado, anillo de foco blanco o `--ds-acento-100` para que se vea sobre el azul). Un test calcula el contraste a partir de `tokens.css` para que un cambio de token no lo rompa sin aviso. El pie lleva solo el nombre de la aplicación; los datos del consultorio quedan `(TBD)`.

### D6. Hover, foco y movimiento con un solo juego de reglas
Los estados se definen una vez por estilo de botón en el módulo de componentes, con las variables `--ds-acento-600/700`, `--ds-foco-*` y `--ds-duracion-rapida`. Todas las transiciones usan esos tokens y un bloque `@media (prefers-reduced-motion: reduce)` las anula. Los tests leen las reglas (jsdom no aplica `:hover` ni `:focus-visible`).

### D7. Íconos y estados vacíos propios
Un módulo `shared/ui/iconos.ts` crea íconos SVG con el DOM (como `pages/inicio-ilustraciones.ts` y el selector de C-38), con `currentColor`, `aria-hidden="true"` y sin archivos ni fuentes remotas. Un componente `estado-vacio.ts` arma la ilustración, el texto y la acción siguiente; y un indicador de carga (`.cargando`) reemplaza los textos "Cargando…". Se usa en Mis turnos, la agenda sin turnos, las listas de Configuración y la búsqueda de pacientes.

### D8. Diálogos con un solo molde
Un ayudante `shared/ui/dialogo.ts` arma el diálogo: título, lugar del aviso debajo del título, cuerpo y fila de botones (descarta a la izquierda y principal a la derecha en la computadora; apilados con el principal arriba en el celular). Los siete diálogos lo usan. El texto de los botones y los mensajes no cambian, y se informa cada test que depende del orden de los botones.

### D9. Archive
Este change modifica `frontend-navegacion`, que también modifican `ingreso-unico-mi-cuenta` y `frontend-portada-y-acceso` (requisitos distintos, pero el mismo archivo de spec), y crea `frontend-sistema-visual`. Se archiva **al final de la etapa D**. El equipo acordó (2026-11-23) que el apply de las etapas **no espera** el archive de `ingreso-unico-mi-cuenta`; el archive de C-39 sí va después de los de esos dos changes, para que los specs queden en orden. El delta valida hoy contra los specs vigentes porque solo modifica los requisitos "Menú adaptable a pantallas chicas" y "Menú según la sesión", que ninguno de los dos toca.

### D10. Decisiones de la líder (2026-11-23)
- **Un solo botón de ingreso**: "Acceso personal" (portada) e "Ingresar" (demás páginas) se unifican en "Ingresar", arriba a la derecha, sin sesión y en todas las páginas. Cambia lo que dejó C-37 (requisito "Acceso del personal" de `frontend-portada`, todavía sin archivar): en la etapa B, cuando `frontend-portada-y-acceso` esté archivado, se agrega al change el delta que modifica ese requisito y sus dos escenarios ("El personal entra desde la portada" y "El botón del personal en el celular"). Mi cuenta mantiene la indicación para el personal, que sigue explicando que el personal ingresa en el mismo formulario.
- **El pie**: por ahora solo el nombre genérico de la aplicación, "Turnos odontología" (los datos del consultorio siguen `(TBD)`).
- **Más observaciones**: no hay por ahora; si llegan, se suman como una etapa.

## UI Mockups

- Design system revision: 1 al proponer; **2 desde la etapa A** (2026-11-23, solo quita los alias y suma `--ds-capa-modal`; sin cambio visual; aprobación pendiente de renovar por la líder).
- Decision: yes, 2026-11-23. El modo del proyecto es `system-and-mockups`.
- Mockups: se crean por etapa en `openspec/changes/frontend-uniformidad-visual/mockups/<etapa>/` (B: barra lateral, encabezado, pie, componentes y diálogos; C: pantallas del paciente; D: pantallas del personal). Fuente de referencia: `design-system/components.html` y los mockups de C-12, C-33, C-37 y C-38.
- Approval: pending (por etapa: A no lleva mockups; B y C aprobadas; D pending: mockups creados el 2026-11-24, falta la aprobación de la líder). Etapa C: mockups aprobados por la líder (2026-11-24). Etapa B: mockups aprobados por la líder (2026-11-23), con dos ajustes pedidos en la revisión: el foco de los botones de peligro es rojo y el logo ya no lleva el rótulo "placeholder".
- Preview: etapa C rendered y aplicada (2026-11-24), capturas en `mockups/C/previews/` a 390 × 844 y 1440 × 900 (y a 320 px), sin desplazamiento horizontal ni errores en la consola. Etapa B rendered (2026-11-23), capturas en `mockups/B/previews/` a 390 × 844 y 1440 × 900 (y a 320 px), sin desplazamiento horizontal ni errores en la consola. Etapa D rendered (2026-11-24), capturas en `mockups/D/previews/` a 390 × 844 y 1440 × 900 (página completa, con la base "antes" en `mockups/D/antes/`), sin desplazamiento horizontal ni errores en la consola.
- Visual verify: etapa B differences (2026-11-23): sin blockers ni majors; 3 diferencias menores (fondo de la página, aviso del diálogo sin error y estado vacío no provocado), en `mockups/B/verify/README.md`; etapa C differences (2026-11-24): sin blockers ni majors; 2 diferencias menores en `mockups/C/verify/README.md`; D pending.

## Risks / Trade-offs

- **[Toca todas las pantallas]** Un error en un componente común se ve en todas. → Etapas chicas, un PR por vez, tests existentes siempre en verde y capturas de antes y después de cada pantalla.
- **[Conflictos con otros changes en `styles.css`]** Partir el archivo cambia todas sus líneas. → La etapa A se hace y se mergea sola y rápido, avisando a Enzo antes; mientras tanto nadie más toca `styles.css`.
- **[Tests que leen `styles.css`]** → D3: ayudante común; se informa cada test tocado.
- **[Tests de navegación atados al menú dentro del encabezado]** → D4 conserva ids, roles y atributos; se actualizan los pocos que dependen de la estructura y se informa cada uno.
- **[Contraste de los textos sobre el azul]** → D5 y un test que lo calcula.
- **[Cajón accesible con teclado y lector de pantalla]** → foco gestionado, `aria-expanded`, "Escape", y verificación manual con el teclado.
- **[Alcance abierto]** "Más bonito" no tiene fin. → Cada etapa tiene su lista cerrada de pantallas y mockups; lo que se agregue después va a otra etapa o a otro change.

## Open Questions

Sin preguntas abiertas: la líder las respondió el 2026-11-23 (ver D10).
