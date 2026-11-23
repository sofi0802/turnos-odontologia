# Design

## Context

Lo observado en `master` (después de C-37):

- **Paso 1** (`pages/reserva.ts`, `paso1`): crea un `fieldset` "Prestación" con un botón de opción (`radio`) por prestación, con el nombre y la duración ("Consulta · 30 minutos"). `Continuar` exige `eleccion.prestacionId`; si falta, muestra "Elegí una prestación." (`MENSAJE_ELEGI_PRESTACION`). Si cambia la prestación respecto de la que cargó los profesionales, borra el profesional y el horario elegidos.
- **Las prestaciones** llegan completas de `GET /public/prestaciones` (`paciente/catalogo.ts`, `PrestacionPublica`: `id`, `nombre`, `duracionMinutos`) y se guardan en memoria; no hay paginación ni categorías.
- **Ya existe un combobox** en el proyecto: `agenda/buscador-pacientes.ts` (C-36), con `role="combobox"`, `aria-controls`, `aria-activedescendant`, teclado y toque. Pero busca en el servidor, con espera y estados de carga, y está pensado para la agenda del personal; no sirve tal cual para una lista local.
- **Estilos**: `.opciones`, `.opcion` y `.opcion-nombre` (`styles.css`) son de la lista de botones de opción. `.opcion` también la usa el paso 2 para el profesional.
- **Tests**: `reserva-paciente.test.ts` comprueba que la prestación es un `radio` ("Consulta · 30 minutos") y elige con ese control; otros tests (reserva, cuenta, responsivo) la eligen del mismo modo.

Motivación y alcance: ver `proposal.md`.

## Goals / Non-Goals

**Goals:**
- Encontrar una prestación escribiendo parte de su nombre, sin recorrer una lista larga.
- Que funcione con teclado, con toque y con lector de pantalla.
- Cambiar solo el paso 1: lo que sigue (profesional, horarios, resumen, confirmación) y lo que se envía no se tocan.

**Non-Goals:**
- Categorías de prestaciones, búsqueda en el servidor o paginación.
- Un componente genérico para todo el proyecto: se crea uno chico, local y sin dependencias, y no se reemplaza el buscador de pacientes.
- Rediseñar el paso 1 o el resto de la reserva (C-39).

## Decisions

### D1. Un componente propio, `shared/ui/selector-buscable.ts`
Una función `crearSelectorBuscable(opciones)` que recibe las opciones (`id`, `texto`, `detalle`), la etiqueta y un `alElegir`, y devuelve el campo, la lista y un método para fijar la elección (volver del paso 2) y para leer la elegida. Vive en `shared/ui/` junto a `campos.ts`, para que otras pantallas lo puedan usar después, pero hoy lo usa solo la reserva.

*Alternativa descartada*: reutilizar `buscador-pacientes.ts`. Está acoplado a la API, a la espera de 300 ms y a los estados de carga y de error del servidor; adaptarlo agregaría más código que escribir un filtro local.

### D2. Filtrado local, sin tildes ni mayúsculas
El texto y los nombres se normalizan con `normalize('NFD')`, sin marcas diacríticas y en minúsculas, y la coincidencia es por "contiene". No hay espera ni pedidos: las prestaciones ya están en memoria. Con el campo vacío se ofrecen todas, en el orden de la API.

### D3. La lista se muestra en el flujo de la página, no flotando
La lista es un bloque debajo del campo (no una capa superpuesta), con alto máximo (`max-height`, unos 16 rem) y desplazamiento por dentro. Evita recortes con el teclado virtual del celular y no tapa "Continuar" de forma impredecible; el costo es que, con la lista abierta, "Continuar" baja. Se cierra al elegir, con "Escape" o al salir del campo.

### D4. Abrir con el foco y mostrar todas
Al enfocar o tocar el campo se abre la lista con todas las prestaciones (es un "selector desplegable"), y al escribir se filtra. Una flecha cierra o abre la lista desde un botón, para quien no escribe.

### D5. Elegir fija el nombre y cambiar el texto quita la elección
Al elegir, el campo pasa a mostrar el nombre y `eleccion.prestacionId` se fija. Cualquier cambio posterior del texto pone `prestacionId` en `null` (y "Continuar" muestra "Elegí una prestación."), de modo que lo que se ve y lo que se envía nunca difieren. Al volver del paso 2, el campo se rearma con la prestación ya elegida.

### D6. Accesibilidad
Patrón combobox con lista (ARIA 1.2): campo `role="combobox"` con `aria-autocomplete="list"`, `aria-expanded`, `aria-controls` y `aria-activedescendant`; lista `role="listbox"`; opciones `role="option"` con `aria-selected`. Teclado: flecha abajo/arriba (abre y mueve), Enter (elige la activa), Escape (cierra), Tab (cierra y sigue). La cantidad de coincidencias y "No hay prestaciones…" se anuncian en una región `aria-live="polite"`. La etiqueta "Prestación" es un `<label>` real del campo.

### D7. Sin cambios en el flujo
`paso1` sigue creando "Continuar", el aviso `role="alert"` y los mismos mensajes; solo cambia el control de elección. `MENSAJE_ELEGI_PRESTACION` y `MENSAJE_SIN_PRESTACIONES` no cambian. El paso 2 no se toca. Los estilos de `.opcion` quedan porque el paso 2 los usa.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, aprobada el 2026-11-22).
- Decision: yes, 2026-11-23. El modo del proyecto es `system-and-mockups`.
- Mockups: `openspec/changes/frontend-reserva-explorador-prestaciones/mockups/` (ver su `README.md`): el paso 1 con el selector cerrado, abierto con 12 prestaciones (lista larga con desplazamiento propio), filtrado, sin coincidencias, con una prestación elegida y con el error de "Continuar" sin elegir. Fuente de referencia: `design-system/components.html` (secciones "Reserva online" y "Formularios y avisos") y los mockups de C-12 y C-37.
- Approval: pending.
- Preview: rendered (2026-11-23), capturas en `mockups/previews/` a 390 × 844 y 1440 × 900 (y a 320 px), sin desplazamiento horizontal ni errores en la consola, opciones de 48 px y campo de 44 px.
- Visual verify: pending.

## Risks / Trade-offs

- **[Un componente nuevo con teclado y ARIA]** Es la parte con más riesgo de errores sutiles. → Un test por comportamiento (flechas, Enter, Escape, Tab, foco) y verificación manual con el teclado en el navegador.
- **[Tests que eligen la prestación como `radio`]** Se actualizan al selector; se informa cada uno y los pasos 2 y 3 no cambian.
- **[Lista inline empuja "Continuar"]** → D3: aceptable y más predecible en el celular; se revisa en los mockups.
- **[Conflicto en `styles.css` con C-40]** Enzo trabaja en C-40 sobre el mismo archivo. → El apply espera a que C-40 esté mergeado y se agrega el bloque de estilos propio con un comentario de C-38.
- **[Muchas prestaciones]** Con cientos de opciones el filtrado sigue siendo inmediato; si algún día hubiera miles, habría que paginar (fuera de alcance).

## Open Questions

- ¿La lista se abre al enfocar (D4) o solo al escribir o al tocar una flecha? Se propone abrir al enfocar; se ajusta en los mockups.
