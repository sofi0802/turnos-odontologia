# Design

## Context

La ficha clínica del frontend es de C-44: `pages/pacientes.ts` arma la página y monta las secciones de `frontend/src/ficha/` (ficha básica, odontograma, historial, ausencias). Los pedidos están en `ficha/api.ts`, con lectura defensiva y sin escribir en la consola ni en el almacenamiento (D9 de C-44). La confirmación al cambiar de paciente usa `abrirDialogo` y hoy solo mira los pendientes del odontograma. Los estilos van en módulos numerados de `frontend/src/styles/` (último: `18-sobreturno.css`), con los tokens `--ds-*` del design system (Revision 4). La API es la de C-25 (`openspec/changes/periodontograma/`). Las decisiones de producto son de la líder (proposal).

## Goals / Non-Goals

**Goals:**
- Que el odontólogo cargue una visita en pocos minutos partiendo de la anterior, también desde una tablet.
- Que el estado se entienda sin depender del color y que todo se use con teclado.

**Non-Goals:**
- Comparar versiones, gráficos de evolución, índices de placa o de sangrado del paciente, impresión.
- Calcular el estado en el frontend: lo calcula el backend (RN-CL-06) y la pantalla muestra el que devuelve la API.
- Cambiar el resto de la ficha de C-44.

## Decisions

### D1. Módulos nuevos
- `ficha/periodontograma-api.ts`: pedidos `listarVersiones`, `obtenerVersion` y `registrarVersion`, con lectura defensiva como `ficha/api.ts` (descarta valores con otro tipo). Se separa de `api.ts` para no agrandarlo.
- `ficha/periodontograma-modelo.ts`: funciones puras. Orden de las piezas por cuadrante, piezas multirradiculares (misma regla que `PiezaDental.esMultirradicular()`), nombres de los sitios según el maxilar, validación del borrador y armado del pedido. Se prueba sin DOM.
- `ficha/periodontograma.ts`: la sección. Exporta `crearPeriodontograma(...)` y devuelve `{ contenedor, sinGuardar(): boolean }`.
- `pages/pacientes.ts` solo la monta debajo del odontograma y suma `sinGuardar()` a la confirmación de "Cambiar de paciente".

### D2. Carga independiente
La sección pide la lista y la versión más nueva al montarse, después de que la ficha y el odontograma cargaron, con su propio `AbortController` y el contador de dibujo de la página (un pedido viejo no dibuja nada). Un error se muestra solo en la sección, con "Reintentar" (spec "Falla la carga del periodontograma"). Así una falla del periodontograma no deja sin ficha al odontólogo, a diferencia de la regla "sin datos parciales" de C-44, que sigue valiendo para la ficha básica y el odontograma.

### D3. Mapa de piezas
- Una grilla HTML de botones: una fila de 16 por arcada, con el orden FDI de D4 de C-44 (lado derecho del paciente a la izquierda). En 390 px cada cuadrante ocupa dos filas de 4 botones (unos 75 px cada uno), para que el nombre del estado entre completo.
- Cada botón lleva un dibujo SVG propio y simplificado del diente según su tipo (incisivo, canino, premolar, premolar con dos raíces para 14 y 24, molar superior de tres raíces y molar inferior de dos), en un lienzo de 40 × 100 con el límite amelocementario fijo y 3,2 unidades por mm. Los inferiores son el mismo dibujo invertido, con la raíz hacia abajo.
- Sobre el diente, en la cara vestibular, se trazan el margen de la encía (rojo, `--ds-error`) en LAC − recesión y el fondo de la bolsa (azul, `--ds-info`) en LAC − (recesión + profundidad), uniendo los tres sitios. El contorno va con el color del estado; ausente se tacha y "Sin cargar" lleva contorno punteado. El número y el nombre del estado van siempre en texto, así que el estado no depende del color (spec "Mapa de piezas con su estado").
- El SVG es `aria-hidden`: la información accesible está en el `aria-label` del botón y en la tabla del detalle.
- *Alternativa*: botones con barras de nivel, sin dibujo. Se descartó a pedido de la líder: el odontólogo reconoce mejor la pieza y ve la pérdida de inserción como en el periodontograma en papel.
- El botón tiene `aria-label` "Pieza 21: Severo" y `aria-pressed` cuando está elegido. Las flechas no mueven el foco entre piezas: con 32 botones alcanza con Tab, como el odontograma.
- Debajo, una leyenda con los cuatro estados, ausente, sin cargar y los tres trazos (margen, fondo de la bolsa y límite amelocementario).

### D4. Detalle y formulario de la pieza
- Al elegir una pieza se abre un panel debajo del mapa. En consulta es una tabla de 6 filas (sitios) y 5 columnas (profundidad, recesión, pérdida de inserción, sangrado, placa), más movilidad, furcación y estado. La pérdida de inserción se muestra calculada (profundidad + recesión) porque es solo una suma para leer, no el estado.
- En la nueva versión, el panel tiene la misma tabla con campos: `input type="number"` con `inputmode="numeric"`, `min`, `max` y `step=1` para profundidad y recesión, y casillas para sangrado y placa. La movilidad y la furcación son `select` de 0 a 3 (la furcación con "Sin registrar" vacío y solo en piezas multirradiculares). Arriba: "Pieza ausente" (casilla) y "Quitar de esta versión".
- Los nombres de los sitios siguen el maxilar: palatinos en las piezas superiores y linguales en las inferiores, aunque el código de la API sea siempre `ML`, `L` y `DL`.
- Arriba de la tabla, la pieza en grande (96 × 240) por vestibular y por palatino o lingual, con grilla de 1 mm (más marcada cada 5 mm). En la nueva versión el dibujo se actualiza con lo cargado; una cara con un sitio vacío se dibuja sin líneas.
- La ayuda de la recesión va en un `<p>` enlazado con `aria-describedby` a cada campo de recesión.
- En 390 px la tabla pasa a una tarjeta por sitio (cuatro campos en dos columnas), para no tener desplazamiento horizontal.

### D5. Borrador de la nueva versión
- "Nueva versión" copia la versión más nueva a un borrador en memoria (aunque el selector muestre una anterior). Una pieza que se carga de cero arranca con los campos vacíos, no con valores por defecto, para no inventar mediciones.
- `sinGuardar()` compara el borrador con la copia inicial; "Cancelar" pregunta solo si hay diferencias (spec "Cancelar sin cambios").
- El borrador nunca se guarda en `localStorage` ni en la dirección (D9 de C-44): si se recarga la página, se pierde.

### D6. Validación y envío
- La validación es la de RN-CL-05, en `periodontograma-modelo.ts`: devuelve los errores por pieza y por campo. Si hay alguno, no se pide nada, se marca cada campo con `aria-invalid` y su mensaje, la pieza del mapa se marca con error y el foco va a la primera pieza con errores.
- Mensajes del frontend: "Entre 0 y 15 mm.", "Entre −5 y 15 mm.", "Completá los 6 sitios y la movilidad de la pieza N.", "Cargá al menos una pieza.".
- Con `201`, la respuesta es la versión completa (C-25 la devuelve), así que no hace falta otro `GET`: se agrega al principio del selector y se muestra.
- Mensajes nuevos en `MENSAJES_POR_CODIGO`: `SITIOS_INCOMPLETOS` y `FURCACION_NO_APLICA`. `PIEZA_INVALIDA` y `DATOS_INVALIDOS` ya tienen el suyo de C-44.

### D7. Tokens del estado periodontal (design system Revision 5)
- `--ds-perio-sano: var(--ds-ok)`, `--ds-perio-leve: var(--ds-alerta)`, `--ds-perio-moderado: #9a3412` (nuevo, 7.3:1 sobre blanco) y `--ds-perio-severo: var(--ds-error)`, cada uno con su `-fondo`. Ausente reusa `--ds-odonto-ausente`.
- Se suma una sección "Periodontograma" a `components.html`. La revisión sube a 5 y `Approval` vuelve a `pending` hasta que la líder apruebe junto con los mockups; `.active-orchestrator-state.json` se actualiza igual.
- *Alternativa*: reusar solo colores existentes (`--ds-info` para moderado). Se descarta: el azul se lee como "información", no como un grado intermedio entre leve y severo.

### D8. Estilos
Un módulo nuevo `frontend/src/styles/19-periodontograma.css`, importado al final de `styles.css`, con la grilla, los botones de pieza, los trazos del dibujo, el panel y las tarjetas de 390 px. Solo usa tokens.

## Risks / Trade-offs

- [Riesgo] Con la recesión positiva por costumbre, una encía sana sale "Leve". → La ayuda de D4 está siempre visible junto a los campos, no en un tooltip.
- [Riesgo] Cargar 32 piezas × 6 sitios es mucho para una sola pantalla. → La carga parte de la última versión y se edita pieza por pieza; el mapa muestra cuáles faltan.
- [Riesgo] Validar en el frontend duplica reglas del backend. → Son rangos fijos de RN-CL-05 con tests de los bordes; el `422` del backend se sigue mostrando.
- [Trade-off] El PR depende del de C-25. → Se mergea después; si el backend cambia el contrato, se actualiza la lectura defensiva.
- [Riesgo] `frontend/` es de Enzo y la ficha de C-44 es de Emanuel. → Se les avisa en el PR; el cambio en `pacientes.ts` se limita a montar la sección y a la confirmación.

## Migration Plan

Sin migraciones ni cambios de backend. Rollback: revertir el PR deja la ficha de C-44 como estaba; el design system vuelve a la Revision 4.

## UI Mockups

- Design system revision: 5 propuesta (D7; hoy `design-system/README.md` está en la 4, aprobada).
- Decision: yes (2026-11-24). Modo `system-and-mockups`.
- Mockups: `openspec/changes/frontend-periodontograma/mockups/` (ver su `README.md`).
- Approval: pending.
- Preview: rendered (2026-11-24). Capturas en `mockups/previews/`, a 390 × 844 y 1440 × 900, sin desplazamiento horizontal ni errores de consola.
- Visual verify: pendiente (se hace en el verify, después del apply).
