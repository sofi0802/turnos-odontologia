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
`styles.css` pasa a ser una lista de `@import` en este orden: `base`, `componentes` (botones, campos, avisos, diálogos, tarjetas, selector, calendario, buscador), `layout` (encabezado, barra lateral, pie) y un módulo por pantalla o grupo (`portada`, `reserva`, `mi-cuenta`, `agenda`, `nuevo-turno`, `configuracion`). Vite resuelve los `@import` en el build y en el servidor de desarrollo. Los tests que hoy hacen `import estilos from '../src/styles.css?raw'` leen en su lugar `test/estilos.ts`, que junta los módulos con `import.meta.glob('../src/styles/*.css', { query: '?raw', eager: true })` en el orden de `styles.css`; así las reglas de CSS que ya se comprueban (por ejemplo, 44 px en los botones) siguen funcionando sin tocar su texto.

*Alternativa descartada*: dejar un solo archivo y ordenarlo. No resuelve que cada change agregue 200 líneas al final, y el archivo ya choca entre changes en paralelo.

### D4. La barra lateral reutiliza el `<nav id="menu-principal">`
Para no romper los tests ni la accesibilidad, el `<nav>` conserva su id, su `aria-label="Principal"`, su botón con `aria-controls="menu-principal"` y `aria-expanded`, y los `li[hidden]` por rol. Cambia dónde está y cómo se ve:
- Con sesión, `layout.ts` arma una estructura de dos columnas: la barra lateral (`<aside>` con el `<nav>`) a la izquierda y el contenido a la derecha; el encabezado queda arriba a todo el ancho con la marca, el botón "Menú" (solo en el celular) y el usuario con "Salir".
- Sin sesión no se dibuja la barra lateral: el encabezado ofrece "Reservar turno" y "Ingresar" ("Acceso personal" en la portada), como hoy.
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
Este change modifica `frontend-navegacion`, que también modifican `ingreso-unico-mi-cuenta` y `frontend-portada-y-acceso` (requisitos distintos, pero el mismo archivo de spec), y crea `frontend-sistema-visual`. Se archiva **al final de la etapa D**, después de los archives pendientes de esos dos changes. El delta valida hoy contra los specs vigentes porque solo modifica los requisitos "Menú adaptable a pantallas chicas" y "Menú según la sesión", que ninguno de los dos toca.

## UI Mockups

- Design system revision: 1 al proponer; 2 desde la etapa A (se registra al subirla).
- Decision: yes, 2026-11-23. El modo del proyecto es `system-and-mockups`.
- Mockups: se crean por etapa en `openspec/changes/frontend-uniformidad-visual/mockups/<etapa>/` (B: barra lateral, encabezado, pie, componentes y diálogos; C: pantallas del paciente; D: pantallas del personal). Fuente de referencia: `design-system/components.html` y los mockups de C-12, C-33, C-37 y C-38.
- Approval: pending (por etapa: A no lleva mockups; B, C y D pending).
- Preview: pending.
- Visual verify: pending (por etapa).

## Risks / Trade-offs

- **[Toca todas las pantallas]** Un error en un componente común se ve en todas. → Etapas chicas, un PR por vez, tests existentes siempre en verde y capturas de antes y después de cada pantalla.
- **[Conflictos con otros changes en `styles.css`]** Partir el archivo cambia todas sus líneas. → La etapa A se hace y se mergea sola y rápido, avisando a Enzo antes; mientras tanto nadie más toca `styles.css`.
- **[Tests que leen `styles.css`]** → D3: ayudante común; se informa cada test tocado.
- **[Tests de navegación atados al menú dentro del encabezado]** → D4 conserva ids, roles y atributos; se actualizan los pocos que dependen de la estructura y se informa cada uno.
- **[Contraste de los textos sobre el azul]** → D5 y un test que lo calcula.
- **[Cajón accesible con teclado y lector de pantalla]** → foco gestionado, `aria-expanded`, "Escape", y verificación manual con el teclado.
- **[Alcance abierto]** "Más bonito" no tiene fin. → Cada etapa tiene su lista cerrada de pantallas y mockups; lo que se agregue después va a otra etapa o a otro change.

## Open Questions

- ¿Los botones "Acceso personal" (portada) e "Ingresar" (demás páginas sin sesión) se unifican en uno solo, por ejemplo "Ingresar"? Se propone conservar lo de C-37; se decide en los mockups de la etapa B.
- ¿Qué lleva el pie además del nombre de la aplicación? Se propone solo el nombre (los datos del consultorio son `(TBD)`).
- ¿Hay más observaciones del equipo para sumar como etapa antes de la etapa C?
