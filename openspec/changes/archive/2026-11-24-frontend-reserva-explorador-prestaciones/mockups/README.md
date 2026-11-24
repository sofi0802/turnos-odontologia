# Mockups de C-38 `frontend-reserva-explorador-prestaciones`

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22)
- Approval: approved (2026-11-23, by leader)
- Preview: rendered (2026-11-23), capturas en `previews/` a 390 × 844 y 1440 × 900 (página completa), y a 320 px para el selector cerrado, abierto y sin coincidencias. Ninguna pantalla tiene desplazamiento horizontal ni errores en la consola.

Los aprueba la líder antes del apply (`design-system/README.md`, modo `system-and-mockups`). Una vez aprobados, son el criterio de aceptación visual de las tareas de `tasks.md`.

## Brief

- **Para quién**: pacientes que reservan un turno, casi siempre desde el celular y sin sesión.
- **Qué tiene que poder hacer**: encontrar su prestación escribiendo parte del nombre o recorriendo la lista, ver la duración de cada una y seguir con "Continuar".
- **Qué cambia**: en el paso 1 de `#/reserva`, la lista de botones de opción se reemplaza por un selector desplegable con buscador. Los pasos 2 y 3 no cambian.
- **Tamaños**: mobile first a 390 × 844; en 1440 × 900 el contenido queda en una columna de 36 rem, como en C-12.
- **Sistema**:
  - Solo variables de `design-system/tokens.css`.
  - Componentes de `design-system/components.html`: encabezado, pasos, campo, botones y avisos.
  - El logo provisorio de `design-system/assets/logo.svg` y el nombre de `brand.js`.
  - La voz de `brand.md`: voseo, frases cortas.
- **Restricciones**: datos 100 % ficticios (prestaciones de ejemplo); sin datos del consultorio ni de salud.
- **Fuera del sistema**: las clases del selector (`.selector`, `.selector-campo`, `.selector-lista`, `.selector-opcion`, `.selector-vacio`) arman el componente con tokens existentes. No suman valores nuevos.

## Pantallas

| Archivo | Estado | Requisitos |
|---|---|---|
| `paso1-cerrado.html` | Selector cerrado, sin elegir, con el texto de ayuda "Buscá una prestación" | Elección de la prestación |
| `paso1-abierto.html` | Abierto con las 12 prestaciones (ficticias); la primera es la opción activa; la lista se desplaza por dentro | Elección de la prestación; Selector accesible; Selector cómodo en el celular |
| `paso1-filtrado.html` | Se escribió "limp": queda "Limpieza dental · 45 minutos" | Buscar la prestación |
| `paso1-vacio.html` | Se escribió "xyz": "No hay prestaciones que coincidan con «xyz»." | Buscar la prestación |
| `paso1-elegido.html` | "Limpieza dental" elegida: el campo muestra el nombre y la lista se cerró | Elegir con el teclado y con el toque |
| `paso1-sin-elegir.html` | "Continuar" sin elegir: "Elegí una prestación." | Reserva en tres pasos |

## Cómo verlos

- **Capturas**: en `previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png` (y `-320.png`).
- **En el navegador**: los HTML cargan `../../../../design-system/`, así que hay que servirlos desde la raíz del repo. Desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  Después se abre `http://127.0.0.1:8765/openspec/changes/frontend-reserva-explorador-prestaciones/mockups/paso1-abierto.html`.

## Qué se midió

| Control | Resultado |
|---|---|
| Desplazamiento horizontal a 320, 390 y 1440 px | No hay en ninguna pantalla |
| Alto de las opciones de la lista | 48 px (56 px cuando el nombre ocupa dos líneas a 320 px) |
| Alto del campo y de la flecha | 44 px |
| Lista abierta | 16 rem de alto máximo, con desplazamiento propio; ancho igual al del campo |
| Título principal (`h1`) por pantalla | Uno solo |
| Errores de la consola | 0 |

## Decisiones para revisar

1. **La lista va en el flujo de la página** (empuja "Continuar" hacia abajo), no flotando encima. Evita recortes con el teclado virtual del celular (design D3).
2. **La lista se abre al enfocar el campo** y muestra todas las prestaciones; la flecha de la derecha abre y cierra para quien no escribe (design D4, pregunta abierta).
3. **La opción activa** (con el teclado) se marca con fondo suave y una barra de acento a la izquierda; la elegida, en negrita y con el color de acento, sin tilde (se quitó en la revisión del 2026-11-23 porque corría la duración hacia la izquierda). No hay resaltado de las letras coincidentes: no lo pide el spec.
4. **La duración está a la derecha** de cada opción ("30 minutos"); para un lector de pantalla se lee "Consulta · 30 minutos".
5. **No hay indicador de más opciones** en la lista que se desplaza, más allá de la barra de desplazamiento del navegador. Se puede agregar un degradé al borde si la líder lo prefiere.
6. **El texto de ayuda del paso** cambia a "Buscá la prestación o elegila de la lista. Después elegís el día y el horario." y el campo muestra "Buscá una prestación" mientras está vacío.
7. **El encabezado** es el del design system (con logo provisorio), como en los mockups de C-12 y C-37; el de la aplicación real lo rediseña C-39.
