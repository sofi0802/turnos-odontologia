# Design

## Context

Lo observado en `origin/master` (código y verificación en `verify/ingreso.md`):

- **El router** (`shared/ui/router.ts`) recibe una página inicial; hoy `app.ts` le pasa `paginaAgenda`. Una dirección vacía o desconocida muestra esa página y reemplaza el hash. `REDIRECCIONES` ya lleva `ingreso` a `mi-cuenta` y **funciona**.
- **Las páginas privadas** (`acceso: 'personal'` o `'paciente'`) sin sesión muestran Mi cuenta (`app.ts`, D3 de `ingreso-unico-mi-cuenta`). Por eso un visitante que abre la aplicación sin dirección termina en el login.
- **Mi cuenta** (`pages/mi-cuenta.ts`): después de un login lleva al paciente a Mis turnos (o a la reserva si había una pendiente) y al personal a la Agenda (`seguirConSesion`). Con una sesión ya iniciada, solo el paciente es redirigido; **el personal se queda** en Mi cuenta.
- **El encabezado** (`shared/ui/layout.ts`): la marca es un `<span>`, no un enlace. Sin sesión muestra "Ingresar"; con sesión, el usuario y "Salir". El menú ya cambia según el rol.
- **"Salir"** (`alSalir` en `app.ts`): el paciente va a `#/reserva` y borra la reserva pendiente; el personal va a `#/mi-cuenta`.
- Una página sin `menu` no figura en el menú (así está Mi cuenta).
- **`ingreso-unico-mi-cuenta`** (Emanuel) está en `master` pero **sin archivar**: sus deltas todavía no están en `openspec/specs/`.

Motivación y alcance: ver `proposal.md`.

## Goals / Non-Goals

**Goals:**
- Una portada que explique la aplicación y deje reservar o ingresar con un toque, sin pedir datos.
- Que cada rol llegue a su espacio sin pasos de más, con y sin sesión.
- Cambiar solo lo necesario del router, el encabezado y Mi cuenta.

**Non-Goals:**
- Rediseñar el encabezado, agregar la barra lateral o cambiar colores (C-39). El botón "Acceso personal" y el logo como enlace se resuelven con el encabezado actual.
- Cambiar el login, la seguridad o el backend.
- Mostrar datos reales del consultorio (dirección, teléfono, horarios).

## Decisions

### D1. La portada vive en `#/inicio` y es la página inicial
Decidido por la líder el 2026-11-23. Se agrega la página `inicio` (`acceso: 'publica'`, sin `menu`) y `app.ts` se la pasa a `crearRouter` como página inicial. La dirección vacía y las desconocidas la muestran, con o sin sesión. `#/ingreso` sigue en `REDIRECCIONES` hacia `mi-cuenta`: no se toca lo que dejó el PR #59.

*Alternativa descartada*: la portada en `#/ingreso`. Obligaría a revertir la redirección de Emanuel y a mezclar "ingreso" con "inicio".

### D2. La portada con sesión no redirige; ofrece ir al espacio del rol
Con una sesión, la portada cambia sus botones: paciente, "Reservar turno" y "Mis turnos"; personal, "Ir a la agenda". No muestra "Ingresar a mi cuenta" ni "Acceso personal". Se descartó redirigir sola porque el personal que abre la dirección base vería la Agenda sin pasar nunca por la portada, y quien quiere ver el sitio no podría; el costo es un toque más para el personal.

La página es pública, así que se dibuja enseguida sin sesión y se ajusta cuando llega `sesionLista`, igual que Reservar turno (`app.ts`, `mostrar`).

### D3. "Acceso personal" y el logo, dentro del encabezado actual
- El botón "Acceso personal" ocupa en la portada el lugar de "Ingresar" del encabezado (arriba a la derecha), porque ahí ya están "Ingresar a mi cuenta" y "Reservar turno" como botones principales. En las demás páginas públicas el encabezado sigue con "Ingresar". Se logra con una propiedad opcional de la página que le dice al encabezado qué texto usar; el diseño final del encabezado es de C-39.
- La marca "Turnos odontología" se convierte en un enlace a `#/inicio` (hoy es un `<span>`).

### D4. "Salir" lleva a la portada, para todos los roles
Paciente y personal terminan en `#/inicio`. El paciente sigue borrando la reserva pendiente. Es un cambio de una línea en `alSalir` y quita la diferencia entre roles que hoy no tiene razón de ser.

### D5. Mi cuenta con sesión del personal va a la Agenda
Se extiende el bloque final de `dibujar` en `mi-cuenta.ts`: con una sesión de paciente va a Mis turnos (como hoy) y con una del personal a la Agenda. Cierra la laguna de `verify/ingreso.md`.

### D6. La indicación para el personal no cambia la etiqueta
Un párrafo breve debajo del formulario de ingreso: "¿Sos del personal del consultorio? Ingresá con tu usuario en este mismo formulario." Se mantiene la etiqueta "Email" y el mensaje "Completá el email.", porque el spec vigente y sus tests dependen de ellos y cambiarlos amplía el alcance sin que lo pidiera la revisión.

### D7. Ilustraciones propias en SVG en línea
Un módulo (`pages/inicio-ilustraciones.ts`) devuelve elementos SVG creados con el DOM, con los colores de `design-system/tokens.css` y `aria-hidden="true"`. Sin archivos de imagen, sin fuentes ni scripts remotos y sin fotos de personas. Los textos de la portada son los de la voz de `design-system/brand.md` (voseo, frases cortas) y no incluyen datos del consultorio ni de salud.

### D8. Orden de archive con `ingreso-unico-mi-cuenta`
Este change modifica requisitos que `ingreso-unico-mi-cuenta` también modifica (`Páginas disponibles`, `Página inicial y direcciones desconocidas`, `Cierre de sesión`). Los textos de este delta ya incorporan los cambios de Emanuel (por ejemplo, `#/ingreso` redirige a `#/mi-cuenta`), de modo que **se archiva después** de ese change; `openspec validate` pasa hoy contra los specs vigentes. Si Emanuel cambia su delta antes de archivarlo, se vuelve a revisar este.

### D9. Conflicto de número con la ficha de Enzo
Enzo publicó una rama (`docs/C-37-nuevo-turno-guiado`) que le da el número C-37 a `frontend-nuevo-turno-guiado`. Los números de este change ya están en `master` (PR #61), así que el de Enzo tiene que renumerarse (por ejemplo, C-40). No hay conflicto técnico: los nombres y los archivos son distintos.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, aprobada el 2026-11-22).
- Decision: yes, 2026-11-23. El modo del proyecto es `system-and-mockups`.
- Mockups: `openspec/changes/frontend-portada-y-acceso/mockups/` (ver su `README.md`): la portada sin sesión, con sesión de paciente y con sesión del personal, y Mi cuenta con la indicación para el personal. Fuente de referencia: `design-system/components.html` (encabezado, botones, campos, pestañas) y los mockups de C-12 y C-33.
- Approval: approved (2026-11-23, by leader: PR #64 mergeado por Sofía con los mockups; el usuario pidió seguir con el apply).
- Preview: rendered (2026-11-23), capturas en `mockups/previews/` a 390 × 844 y 1440 × 900 (y a 320 px), sin desplazamiento horizontal ni errores en la consola, controles de 44 px.
- Visual verify: differences (2026-11-23): sin blockers ni majors; 3 diferencias menores (encabezado sin logo, fondo de página blanco, "Ingresar" del encabezado de Mi cuenta en una segunda fila en el celular), todas del encabezado y el fondo que son de C-39. Capturas en `mockups/verify/` y detalle en `mockups/verify/README.md`.

## Risks / Trade-offs

- **[El personal pasa por la portada]** Quien abre la dirección base ya no cae en la Agenda. → D2: el botón "Ir a la agenda" es el principal con sesión; la portada es liviana.
- **[Encabezado provisorio]** "Acceso personal" solo en la portada y "Ingresar" en las demás páginas queda desparejo hasta C-39. → Se acepta y se documenta; C-39 unifica el encabezado.
- **[Tests que asumen la Agenda como página inicial]** `navegacion.test.ts`, `sesion.test.ts` y otros dan por hecho que la dirección vacía muestra la Agenda y que "Salir" lleva a Mi cuenta o a la reserva. → Se actualizan con el cambio de requisitos y se informa cada uno.
- **[Doble modificación de requisitos]** → D8.
- **[Solo se vio con la API simulada]** La verificación de `#/ingreso` usó una API simulada de la sesión. → El apply repite las redirecciones también contra el backend real con las cuentas de prueba.

## Open Questions

- ¿Los textos de la portada ("cómo funciona en tres pasos") los redacta el equipo o se usan los del borrador de los mockups? No cambia las tareas: se ajustan en los mockups.
