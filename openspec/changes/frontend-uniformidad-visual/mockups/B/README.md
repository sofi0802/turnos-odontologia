# Mockups de C-39, etapa B: barra lateral, encabezado, pie y componentes comunes

- Design system revision: 3 (`design-system/README.md`; la 2 quitó los alias y la 3 cambia el texto del logo provisorio; ningún valor de `tokens.css` cambió)
- Approval: pending
- Preview: rendered (2026-11-23), capturas en `previews/` a 390 × 844 y 1440 × 900 (página completa), y a 320 px para la pantalla sin sesión, el cajón abierto y el diálogo. Ninguna pantalla tiene desplazamiento horizontal ni errores en la consola.

Los aprueba la líder antes del apply de la etapa B (`design-system/README.md`, modo `system-and-mockups`). Una vez aprobados, son el criterio de aceptación visual de las tareas B.3 a B.7 de `tasks.md`.

## Brief

- **Para quién**: pacientes y personal del consultorio, casi siempre desde el celular los primeros y desde la computadora los segundos.
- **Qué cambia**: el menú pasa de una lista dentro del encabezado a una **barra lateral por rol**; el encabezado y un pie nuevo toman el azul de la marca; los botones tienen hover, foco y presionado; los diálogos usan un solo molde; y las listas vacías y las cargas tienen estado propio. No cambia ninguna funcionalidad.
- **Decisiones de la líder (2026-11-23)**: un único botón "Ingresar" sin sesión (también en la portada) y el pie con el nombre genérico "Turnos odontología".
- **Tamaños**: mobile first a 390 × 844; en 1440 × 900 la barra lateral queda a la izquierda y el contenido a su derecha.
- **Sistema**: solo variables de `design-system/tokens.css`; la voz de `brand.md` (voseo); el logo provisorio de `design-system/assets/logo.svg` (con el nombre "Turnos odontología"); datos ficticios (`paciente.uno@example.com`, `recepcion`).
- **Fuera del sistema**: las clases de página (`.cabecera`, `.lateral`, `.pie`, `.dialogo`, `.vacio`, `.cargando`, `.turno`) arman estas pantallas con tokens existentes. No suman valores nuevos.

## Pantallas

| Archivo | Estado | Requisitos |
|---|---|---|
| `b-paciente.html` | Paciente con sesión en Reservar turno: barra lateral con "Reservar turno" y "Mis turnos", encabezado y pie azules | Menú según la sesión; Contraste del encabezado y del pie; El pie en todas las páginas |
| `b-paciente-cajon-abierto.html` | Celular con el cajón abierto sobre una capa que oscurece el contenido (desde 768 px la barra se ve siempre) | Abrir el menú en el celular; Cerrar el cajón con Escape o tocando fuera |
| `b-personal.html` | Personal con sesión en la Agenda: barra lateral con "Agenda" y "Configuración", la página abierta marcada | Menú del personal; Menú siempre visible en la computadora |
| `b-sin-sesion.html` | Sin sesión: sin barra lateral, "Reservar turno" e "Ingresar" en el encabezado, y el pie | Menú de un visitante |
| `b-botones.html` | Los cinco estilos de botón (principal, secundario, peligro, fantasma y horario) en normal, hover, presionado, foco y deshabilitado; los botones sobre el azul; y los diez íconos | Hover y presionado; Foco visible con el teclado; Botón deshabilitado; Transiciones con movimiento reducido |
| `b-estados.html` | Mis turnos vacío con ilustración, agenda de un día sin turnos y cargando | Lista vacía con ilustración; Carga con indicador |
| `b-dialogo.html` | Diálogo "Nuevo turno" con aviso, campo con error y botones | Orden de los botones en los diálogos; Lugar del aviso en los diálogos |

## Cómo verlos

- **Capturas**: en `previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png` (y `-320.png`).
- **En el navegador**: los HTML cargan `../../../../../design-system/`, así que hay que servirlos desde la raíz del repo. Desde ahí:

  ```powershell
  python -m http.server 8765 --bind 127.0.0.1
  ```

  Después se abre, por ejemplo, `http://127.0.0.1:8765/openspec/changes/frontend-uniformidad-visual/mockups/B/b-paciente.html`.

## Qué se midió

| Control | Resultado |
|---|---|
| Desplazamiento horizontal a 320, 390 y 1440 px | No hay en ninguna pantalla |
| Alto de los enlaces de la barra lateral, de los botones del encabezado y de los botones del diálogo | 44 px |
| Alto del encabezado | 60 px (108 px a 320 px, donde pasa a dos filas) |
| Contraste del texto blanco sobre `--ds-acento-500` (encabezado y pie) | 5,7:1 (AA, mínimo 4,5:1) |
| Botones del diálogo | 390 px: apilados, "Crear turno" arriba; 1440 px: "Cancelar" a la izquierda y "Crear turno" a la derecha |
| Título principal (`h1`) por pantalla | Uno solo |
| Errores de la consola | 0 |

## Decisiones para revisar

1. **Barra lateral fija de 15 rem desde 768 px**, con la página abierta marcada por fondo suave, texto de acento y una barra de 3 px a la izquierda. En el celular es un cajón de hasta 18 rem (85 % del ancho) que se desliza desde la izquierda sobre una capa (`--ds-capa-modal`); se cierra al elegir, con "Escape" o al tocar la capa.
2. **El encabezado azul lleva solo el logo provisorio, que ahora dice "Turnos odontología"** (pedido de la revisión del 2026-11-23): se quitó el nombre que aparecía al costado y el logo conserva su rótulo "placeholder · (TBD)". En pantallas de menos de 480 px el logo baja a 28 px de alto y el encabezado oculta el nombre del usuario y el avatar para que entren "Menú", el logo y "Salir".
3. **Botones sobre el azul**: contorno y texto blancos, hover con `--ds-acento-600`, presionado con `--ds-acento-700` y anillo de foco blanco. Sobre el azul no se usa ningún botón de relleno.
4. **El pie lleva solo el nombre** ("Turnos odontología"), siempre al final de la pantalla (si el contenido es corto, queda abajo; si es largo, al final del contenido). No es fijo.
5. **Sin sesión no hay barra lateral.** El encabezado ofrece "Reservar turno" e "Ingresar". A 320 px el encabezado pasa a dos filas (108 px).
6. **Diálogos**: el aviso va debajo del título; "Cancelar" (o "Cerrar") a la izquierda y la acción principal a la derecha en la computadora; apilados con la principal arriba en el celular. Hoy cada diálogo los ordena distinto.
7. **Estados vacíos**: una ilustración propia de un calendario, un texto breve y, si corresponde, la acción siguiente ("Reservar turno"). La versión chica se usa en las listas dentro de otras pantallas.
8. **Carga**: una rueda con "Cargando…" y tres barras grises que sugieren la lista que va a aparecer (se puede dejar solo la rueda). Con movimiento reducido la rueda no gira.
9. **Íconos**: diez íconos de línea, propios (Reservar, Mis turnos, Agenda, Configuración, Menú, Cerrar, Buscar, Usuario, Confirmado y Aviso), con el color del texto y ocultos para los lectores de pantalla. Los de la barra lateral van delante de cada opción.
10. **Los estados de los botones se muestran fijos** con clases de ayuda (`es-hover`, `es-foco`, `es-activo`); en la aplicación son `:hover`, `:focus-visible` y `:active`.
11. **Pendiente de la etapa A**: la aprobación de la Revision 2 del design system todavía figura como `pending` en `design-system/README.md`.
