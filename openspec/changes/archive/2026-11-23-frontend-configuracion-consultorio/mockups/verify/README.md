# Verificación visual de C-34 (2026-11-23)

Prueba hecha con Playwright sobre el backend (`master` con C-35) y el frontend de desarrollo, a 1440 × 900 y a 390 × 844, como `admin`, `recepcion` y `ana.ejemplo`. Usuarios ficticios de la base local.

**Resultado:** differences. Solo hay diferencias minor: ningún blocker ni major.

## Qué se revisó

| Usuario | Pestañas | Resultado |
|---|---|---|
| `recepcion` | Profesionales, Sillones, Prestaciones, Margen, Horarios, Ausencias (sin Bloqueos) | Solo lectura en catálogos, margen y horarios; registra y borra ausencias. |
| `ana.ejemplo` | Las siete | Solo lectura salvo Bloqueos; horarios, ausencias y bloqueos de su profesional fijo ("Tus horarios · Ana Ejemplo"). |

- **Escritura real, en la base de desarrollo, con los datos borrados después:**
  - Recepción registró una licencia de Bruno Prueba el 2026-12-21 de 10:00 a 12:00 (`POST` 201, fechas en `-03:00`). Después usó "Ver turnos afectados" (0 turnos) y la borró (`DELETE` 204).
  - Ana registró un bloqueo propio el 2026-12-22 de 11:00 a 12:00 (`POST` 201, "Bloqueo registrado.") y lo borró (`DELETE` 204).
- **Recepción y Ana, sin errores:** ninguno en la consola, ninguna respuesta 4xx o 5xx y ninguna página con desplazamiento horizontal, en ningún tamaño.
- **`admin`:** todo contra el backend real, sin errores salvo los 409 y 422 provocados a propósito.

  | Prueba | Pedido | Resultado |
  |---|---|---|
  | Sillones: alta, edición y desactivación | `POST /sillones` 201, `PUT /sillones/3` 200, `POST /sillones/3/desactivar` 200 | La lista muestra "Sillón Prueba C-34 bis" como "Inactivo" con "Activar". |
  | Prestación de 32 minutos | `POST /prestaciones` 422 | "La duración tiene que ser mayor que 0 y múltiplo de 5." y el formulario sigue abierto. |
  | Prestación de 30 minutos, después desactivada | `POST` 201 y `POST /prestaciones/5/desactivar` 200 | Queda "Inactivo". |
  | Profesional con la matrícula `MP-0002` | `POST /profesionales` 409 | "Ya existe un profesional con esa matrícula."; se canceló y no se creó nada. |
  | Especialidades de Ana Ejemplo | `PUT /profesionales/1/prestaciones` 200, con `{"prestacionIds":[1,4,2,3]}` | Se agregó "Tratamiento de conducto" y después se restauró `[1,4,2]`. |
  | Sillón habitual de Ana Ejemplo | `PUT /profesionales/1/sillon-habitual` 200, con `{"sillonId":2}` | "Sillón habitual guardado: Sillón 2." y después se restauró el 1. |
  | Margen | `PUT` con 5 → 422; con 15 → 200 | "El margen tiene que ser de 10 minutos o más." con el vigente en 10; después "Margen guardado: 15 minutos.", y se restauró 10. |
  | Horarios de Ana Ejemplo | `PUT` con el sábado de 09:00 a 12:00 → 200; con el lunes de 12:00 a 15:00 → 422 | "Horario guardado."; después "Hay franjas del mismo día que se superponen.", con la franja sin guardar todavía en la lista. Se usó "Descartar cambios" y se restauraron las cinco franjas. |
  | Ausencia de Ana Ejemplo, 2026-11-24 de 08:00 a 09:00 | `POST` 201 | "Turnos afectados: 1" con el turno del martes 24 a las 08:30, "Consulta o control", sin datos del paciente. |
  | Reasignar ese turno | `POST .../reasignar` 409 | "Reasignar" ofrece solo a Bruno Prueba, que hace la prestación. El rechazo "El profesional no atiende en ese horario." se muestra en el diálogo y el turno no cambia. Después se borró la ausencia (`DELETE` 204). |

- **Estado de la base después de las pruebas:** igual que antes (margen de 10, Ana con Sillón 1, especialidades `[1,4,2]`, cinco franjas, sin ausencias y el turno 1 sin cambios). Solo quedan, desactivados, "Sillón Prueba C-34 bis" (id 3) y "Prestación Prueba C-34" (id 5), porque la API no permite borrarlos.
- **No verificado a mano:** una reasignación exitosa, porque cambiaría un turno existente de la base de desarrollo. La cubre el test "Reasignar un turno afectado".

## Diferencias con los mockups

| Prioridad | Pantalla | Tamaño | Esperado (mockup) | Observado |
|---|---|---|---|---|
| minor | Todas | Ambos | Encabezado con el logo provisorio y fondo gris de página | Encabezado de C-09 sin logo y fondo blanco. Es la misma diferencia que ya tenía C-12 y no es de este change. |
| minor | Diálogos | 1440 | Botones a la derecha | "Cancelar" y el botón principal quedan a la izquierda. |
| minor | Ausencias | Ambos | Recuadro de turnos afectados en tono de alerta cuando hay turnos | También se muestra en tono de alerta con "Turnos afectados: 0". |

## Capturas

Las capturas están en esta carpeta y se llaman `<usuario>-<pestaña>-<ancho>.png`, por ejemplo `recepcion-ausencias-1440.png` o `ana.ejemplo-horarios-390.png`. Las de `admin` empiezan con `admin-`. Además hay tres de escrituras de recepción y Ana: `recepcion-ausencia-registrada-1440.png`, `ana.ejemplo-bloqueo-formulario-1440.png` y `ana.ejemplo-bloqueo-registrado-1440.png`.

## Revisión 2 (segunda prueba, 2026-11-23)

Resultado: passed (2026-11-23). Se hizo en el navegador de la app con el backend y el frontend de esta rama (`bootRun` y `npm run dev`), como `admin`, `recepcion` y `ana.ejemplo`, a 1440 × 900 y 390 × 844. Se comprobó:
  - las tarjetas de sillón con "Ocupado · …" y el `409 SILLON_HABITUAL_OCUPADO` real;
  - el alta con prestaciones y el sillón "(de …)" deshabilitado;
  - las franjas para varios días y "Elegí al menos un día.";
  - los minutos de 5 en 5;
  - "Todos los profesionales" con la columna Profesional en ausencias y bloqueos;
  - los turnos afectados por un bloqueo con "Reasignar" para admin y recepción;
  - el odontólogo sin selector ni "Reasignar".

  No hay desplazamiento horizontal a 390 px ni errores en la consola, salvo el `409` provocado a propósito. Hay una diferencia minor: en Bloqueos de recepción, el texto de ayuda queda debajo del recuadro de turnos afectados y no encima. No se guardaron capturas en el repo, porque la base de desarrollo tiene un profesional cargado a mano con un nombre que podría ser real (regla dura 1).
