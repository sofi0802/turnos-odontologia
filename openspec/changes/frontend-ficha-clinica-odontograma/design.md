# Design

## Context

- El motivo está en `proposal.md` (Why). Los requisitos están en `specs/frontend-ficha-clinica/`, `specs/frontend-navegacion/` y `specs/frontend-agenda-personal/`.
- **El backend ya existe (C-13 y C-43) y no cambia:**
  - `FichaClinicaController` deja leer a `ADMIN` y a `ODONTOLOGO`, y escribir solo al `ODONTOLOGO`. La recepción recibe `403` antes de que se lea ningún dato.
  - `GET /ficha` devuelve `FichaRespuesta`: los tres textos (vacíos si no hay ficha), `actualizadoPor` y `actualizadoEn` (nulos sin ficha) y `ausencias[]`. Cada ausencia trae `turnoId`, `inicio`, `profesionalId`, `profesional`, `deshechaEn` y `deshechaPor`.
  - `GET /odontograma` y `GET /odontograma/historial` devuelven `RegistroVista[]`: `id`, `pieza`, `cara` (`O`, `M`, `D`, `V`, `L` o `P`, o `null` para la pieza completa), `estado`, `profesionalId`, `profesional` y `registradoEn`.
  - `PUT /odontograma` recibe `{ cambios: [{ pieza, cara, estado }] }`, con 1 a 32 cambios, y usa el profesional de la sesión. Rechaza con `422` `PIEZA_INVALIDA`, `CARA_INVALIDA` o `DATOS_INVALIDOS`. La cara `L` solo vale en las piezas inferiores y la `P`, en las superiores.
  - `POST /ausencias` recibe `{ turnoId }`. Exige que el turno sea del paciente y del profesional de la sesión (si no, `403 ACCESO_DENEGADO`). Rechaza con `409` `AUSENCIA_ANTES_DE_TOLERANCIA`, `LLEGADA_REGISTRADA` o `TURNO_NO_ACTIVO`.
- **El frontend:**
  - El router (`shared/ui/router.ts`) compara la ruta exacta, sin parámetros.
  - `Pagina` filtra el menú por grupo (`personal` o `paciente`), no por rol.
  - `TurnoAgenda` no lee `pacienteId`, aunque `GET /turnos` lo envía.
  - El buscador de pacientes de C-36 (`agenda/buscador-pacientes.ts`) ya funciona para los tres roles del personal.
- **El design system está en la revisión 3**, con la aprobación pendiente de la líder. No hay tokens para los estados del odontograma.

## Goals / Non-Goals

**Goals:**
- Una página "Pacientes" que reúna la búsqueda y la ficha, para no repetir la ficha en dos lugares.
- Un odontograma propio en SVG, usable con teclado y en un celular de 390 px.
- Que ningún dato de salud salga de la memoria de la página.

**Non-Goals:**
- Cambiar el router para que acepte parámetros en la ruta.
- Guardar la ficha a medio escribir (borradores).
- Ver la ficha sin conexión.
- Comparar odontogramas de dos fechas.

## Decisions

### D1. Página "Pacientes" con el paciente en memoria, no en la dirección
- La página vive en `#/pacientes`. El paciente elegido se guarda en un módulo en memoria (`ficha/seleccion.ts`), no en la dirección ni en el almacenamiento del navegador.
- "Ver ficha" en la agenda deja en ese módulo el paciente (id, nombre y apellido) y el turno (id, profesional, inicio, fin, estado y llegada). Después navega a `#/pacientes`, que lo toma y lo borra.
- Al recargar, el módulo está vacío y la página muestra el buscador (escenario "Recargar la página").
- **Alternativa descartada:** `#/pacientes/1/ficha`. Obliga a cambiar el router, que comparten todas las páginas. Además, el id del paciente en la dirección queda en el historial del navegador junto a la palabra "ficha". El id no es un dato de salud, pero no hace falta exponerlo.
- **Alternativa descartada:** poner la ficha dentro de un diálogo de la agenda. Es una pantalla grande: no entra bien en un diálogo en el celular, y el administrador no tendría cómo buscar a un paciente sin un turno.

### D2. Páginas por rol: campo `roles` en `Pagina`
- `Pagina` suma `roles?: readonly Rol[]`. El layout muestra la opción del menú solo si el rol de la sesión está en la lista.
- `app.ts` comprueba la lista antes de dibujar. Si el rol no está, muestra el aviso "Esta sección es solo para odontólogos y administradores." sin llamar a `render`, de modo que no se pide ningún dato.
- `pacientes` usa `acceso: 'personal'`, `menu: 'personal'` y `roles: ['ADMIN', 'ODONTOLOGO']`, con un ícono nuevo en `iconos.ts`. Va en `PAGINAS` entre `paginaAgenda` y `paginaConfiguracion`, así queda en ese lugar de la barra lateral.
- La recepción no accede (RN-CL-01). Emanuel lo evaluó el 2026-11-24: darle lectura cambiaría la regla, el permiso del backend y el spec `ficha-clinica`, y lo descartó.
- Las páginas existentes no definen `roles` y siguen igual.
- **Alternativa descartada:** ocultar la opción solo en el layout. La recepción podría abrir la dirección y la página haría el pedido. El backend respondería `403`, pero el spec pide no hacer el pedido.

### D3. Estructura de la ficha
- Una sola columna en 390 px. En 1440 px, el odontograma ocupa el ancho y debajo van dos columnas: ficha básica y ausencias a la izquierda, historial a la derecha.
- Encabezado con el paciente (nombre, apellido y DNI), "Cambiar de paciente" (vuelve al buscador) y, si se abrió desde un turno, "Turno del dd/mm a las HH:mm" con "Marcar ausente".
- Carga: `GET /ficha` y `GET /odontograma` en paralelo (`Promise.all`). Si alguno falla, se muestra el error y "Reintentar", sin datos parciales.
- El historial se pide aparte, al abrir su sección (un `<details>` cerrado al principio), para no cargarlo en cada visita.

### D4. Odontograma en SVG
- **Dibujo:** cada pieza es un `<g>` con cinco polígonos, el esquema clásico de cuatro trapecios alrededor de un cuadrado central:
  - el central es la cara oclusal;
  - arriba y abajo van vestibular y lingual o palatina, según el maxilar;
  - a los costados van mesial y distal, invertidas según el cuadrante, para que la mesial quede del lado de la línea media.
- **Orden de las piezas:** dos filas por arcada en el orden clínico. Arriba van 18 a 11 y 21 a 28. Abajo van 48 a 41 y 31 a 38. Las temporales siguen el mismo esquema con 55 a 51, 61 a 65, 85 a 81 y 71 a 75.
- **Estado vigente:** se arma un mapa `pieza → { completa?: estado, caras: Map<cara, estado> }` con la respuesta de `GET /odontograma`, que ya trae solo el último registro de cada pieza y cara.
  - Para decidir qué tapa a qué, se compara `registradoEn`: si la pieza completa es más nueva que una cara, la pieza completa manda.
  - `AUSENTE` se dibuja con una cruz sobre la pieza; `CORONA`, con un contorno grueso; `ENDODONCIA`, con una línea vertical. Los demás estados pintan la cara.
  - Ajuste del apply (pedido por Emanuel al probar, 2026-11-24): las caras de esas tres piezas llevan además un tinte suave del color del estado (`color-mix` al 22 % sobre `--ds-superficie`, sin tokens nuevos), porque solo con la marca no se distinguían de un vistazo.
- **Estados:** la etiqueta de cada uno es "Sano", "Caries", "Obturado", "A tratar", "Ausente", "Corona" y "Endodoncia". Además del color, la cara elegida y la leyenda muestran la letra del estado, así el estado no depende solo del color.
- **Teclado:**
  - cada pieza es un grupo con `role="group"` y `aria-label="Pieza 16"`;
  - cada cara es un elemento enfocable (`tabindex` en roving por pieza), con `role="button"` y el nombre de su estado;
  - las flechas recorren las caras y las piezas; Enter o Espacio la elige.
- **Ancho:**
  - Siempre se dibujan dos SVG, "Lado derecho del paciente" (18 a 11 y 48 a 41) y "Lado izquierdo del paciente" (21 a 28 y 31 a 38), que escalan con `viewBox`. En pantallas angostas van uno debajo del otro: en 390 px cada cara mide unos 35 px en lugar de 17 (lo comprobaron los mockups). Desde 48 rem, `17-ficha.css` los pone lado a lado y se ve la boca entera. Un ancho máximo por SVG evita que las temporales se estiren.
  - Ajuste del apply: los mockups usaban un solo SVG en escritorio. Dibujar siempre las dos mitades da el mismo resultado sin depender de `matchMedia` (que jsdom no tiene) ni redibujar al cambiar el ancho.
  - El área táctil de 44 px la da el panel de edición (D5).
- **Texto además del dibujo:** un desplegable "Estado vigente en texto" lista cada pieza y cara con su estado, para quien no distingue los colores.
- **Alternativa descartada:** una tabla de piezas con un selector por cara. Es más simple, pero no se parece a un odontograma y el odontólogo pierde la vista de conjunto.
- **Alternativa descartada:** una librería de odontogramas. La regla dura 10 no permite frameworks, y ninguna se ajusta al catálogo de siete estados.

### D5. Edición por panel y lista de pendientes
- Al elegir una cara (o una pieza), un panel debajo del odontograma muestra la pieza y un selector de cara. Ofrece las caras de su maxilar más "Pieza completa".
- Los estados son botones de opción, cada uno con su color y su nombre, más "Sumar cambio". El panel tiene controles de 44 px, así sirve en el celular aunque las caras sean chicas.
- La lista de pendientes muestra cada cambio, con "Quitar", y "Guardar cambios (n)". Los pendientes se ven en el dibujo con un borde punteado hasta guardarlos.
- Si se suma otro cambio para la misma pieza y cara, reemplaza al pendiente anterior en lugar de duplicarlo.
- Ajuste del apply: `AUSENTE`, `CORONA` y `ENDODONCIA` son de toda la pieza. Al elegir uno de ellos, la cara pasa sola a "Pieza completa" y el cambio se envía sin cara; si después se elige una cara, ese estado se desmarca. Antes, elegirlos para una sola cara la dejaba gris y parecía que no se había pintado.
- La validación de caras por maxilar se hace en el frontend con la misma regla del backend (el primer dígito decide el maxilar), así no se envía un pedido que va a fallar. El `422` del backend igual se muestra (escenario "El servidor rechaza un cambio").
- Al guardar, se reemplaza el estado vigente con la respuesta del `PUT`, que devuelve el estado vigente completo (no hace falta otro `GET`), y se vacía la lista. Si el historial está abierto, se vuelve a pedir.
- **Salir con pendientes:** si se cambia de paciente con cambios sin guardar, se confirma con el diálogo común: "Hay cambios sin guardar en el odontograma. ¿Descartarlos?".

### D6. Ficha básica
- Para el `ODONTOLOGO`: tres `<textarea>` con `maxlength` de 4000 y un contador. "Guardar ficha" valida el largo antes de enviar.
- Ajuste del apply (2026-11-24): Emanuel pidió otra forma para los campos y eligió la opción B de `mockups/ficha-basica-opciones.html` ("campos suaves") entre cuatro. Cada campo tiene fondo gris claro sin borde duro y esquinas redondeadas, el contador a la derecha del rótulo y un texto de ejemplo ("Ej.: penicilina, látex, anestesia local"). Al enfocarlo, el fondo pasa a blanco con el borde de acento. El texto de ejemplo no es un dato: es igual para todos los pacientes.
- Para el `ADMIN`: los mismos datos como texto (`<p>`, que conserva los saltos de línea).
- La firma se muestra en la hora de Buenos Aires, como el resto de la aplicación (`shared/fecha/zona.ts`).

### D7. "Marcar ausente" solo desde un turno
- El botón aparece solo si se cumplen las cuatro condiciones:
  - hay un turno en `seleccion.ts`;
  - la sesión es `ODONTOLOGO`;
  - `turno.profesionalId === sesion.profesionalId`;
  - el turno está activo (`RESERVADO` o `CONFIRMADO`) y sin `llegadaEn`.
- El cálculo de "Desde las HH:mm" reusa el de `agenda/asistencia.ts` (C-43): la tolerancia de 15 minutos sale de una constante común, no de un número repetido.
- Después de marcar, el turno guardado pasa a `AUSENTE` en memoria y el botón desaparece.
- **Alternativa descartada:** listar los turnos del día del paciente para marcar la ausencia sin pasar por la agenda. Necesita un endpoint que filtre por paciente, que hoy no existe, y RN-AG-18 ya limita al odontólogo a su agenda.

### D8. Mensajes
- Se suman a `MENSAJES_POR_CODIGO` (`shared/ui/mensajes.ts`) los que faltan:
  - `PIEZA_INVALIDA`: "La pieza no existe en la notación FDI.";
  - `CARA_INVALIDA`: "Hay una cara que no corresponde a esa pieza.";
  - `RECURSO_NO_ENCONTRADO`: "No encontramos a ese paciente.";
  - el `403` de la ficha: "No tenés permiso para ver la ficha clínica.".
- `DATOS_INVALIDOS` en la ficha muestra "Revisá los datos de la ficha.".
- Se reusan los de C-43 (`LLEGADA_REGISTRADA`, `AUSENCIA_ANTES_DE_TOLERANCIA` y `TURNO_NO_ACTIVO`).

### D9. Privacidad
- **Errores:** los manejadores no hacen `console.log` del cuerpo, y los errores se muestran por su código, sin repetir el contenido del pedido.
- **Almacenamiento:** ningún `localStorage` ni `sessionStorage` en `ficha/`. Un test lo comprueba espiando `Storage.prototype.setItem` y `console`.
- **Pedidos:** se cancelan con `AbortController` al salir de la página. Si un pedido vuelve después, no dibuja nada.
- **Salir:** al salir, el `render` de otra página reemplaza el contenido y `seleccion.ts` se vacía.

### D10. Tokens del odontograma (design system revisión 4)
- Se suman siete tokens `--ds-odonto-<estado>`, más un fondo para "sin estado", en `design-system/tokens.css`.
- Reusan tonos existentes donde se puede:
  - caries con `--ds-error`;
  - obturado con `--ds-info`;
  - a tratar con `--ds-alerta`;
  - sano con `--ds-ok`;
  - ausente con `--ds-turno-cancelado`;
  - corona y endodoncia con tonos nuevos, con contraste AA sobre blanco.
- Suben la revisión del design system a 4 y se muestran en `components.html`, en una sección "Odontograma". La líder aprueba la revisión junto con los mockups.

## Risks / Trade-offs

- **[El odontograma no entra en 390 px]**
  - **Riesgo:** 16 piezas por fila dejan caras de unos 17 px.
  - **Mitigación:** en pantallas angostas el odontograma se parte en dos mitades, con caras de unos 35 px (D4). El panel de edición (D5) tiene controles de 44 px.
- **[El paciente se pierde al recargar]**
  - **Riesgo:** quien recarga tiene que volver a buscarlo.
  - **Mitigación:** es aceptado (D1); buscar por DNI es rápido.
- **[Dos odontólogos editan a la vez]**
  - **Riesgo:** cada uno ve el estado que cargó al abrir.
  - **Mitigación:** el historial no se pierde, porque el backend nunca sobrescribe. Al guardar, el estado vigente se reemplaza con la respuesta del servidor.
- **[La recepción ve la página en una versión vieja del frontend]**
  - **Riesgo:** un frontend viejo no oculta la página.
  - **Mitigación:** el backend igual responde `403` sin datos (C-13), así que no hay fuga.
- **[Conflicto con C-39 en `styles/` y `layout.ts`]**
  - **Riesgo:** C-39 tiene pendientes las tareas del archive.
  - **Mitigación:** se avisa a Juan Cruz. C-44 suma un módulo `17-ficha.css` y no toca los de C-39.
- **[Design system pendiente de aprobación]**
  - **Riesgo:** la revisión 3 espera que la líder renueve la aprobación, y el apply visual no arranca sin un sistema aprobado.
  - **Mitigación:** se pide en el mismo PR de los mockups.

## Migration Plan

- Sin migración ni cambios en el backend.
- La página nueva aparece al desplegar el frontend. Para deshacerla alcanza con revertir el PR.

## UI Mockups

- Design system revision: 4 (`design-system/README.md`). C-44 suma los tokens `--ds-odonto-*` (D10): corona `#4b3f8a` y endodoncia `#8a2f6b`; los demás reusan colores existentes. La líder aprobó las revisiones 2 a 4 al mergear el PR #94 (2026-11-24).
- Decision: yes (2026-11-24). Modo `system-and-mockups`.
- Mockups: `openspec/changes/frontend-ficha-clinica-odontograma/mockups/`. El brief, las decisiones y la tabla de pantallas con sus requisitos están en `mockups/README.md`.
  - `pacientes-buscar.html`
  - `ficha-odontologo.html`
  - `ficha-admin.html`
  - `ficha-estados.html`
  - `agenda-ver-ficha.html`
- Approval: approved (2026-11-24, by Emanuel) para los mockups de esta revisión. La revisión 4 del design system la aprobó Sofia al mergear el PR #94 (2026-11-24).
- Preview: rendered (2026-11-24). Capturas en `mockups/previews/`, a 390 y 1440 px, sin desplazamiento horizontal ni errores de consola.
- Visual verify: passed (2026-11-24). Contra el backend de desarrollo con datos ficticios, como `ana.ejemplo` y como `admin`, a 1440 × 900 y 390 × 844. Capturas en `mockups/verify/`:
  - `agenda-ver-ficha-1440.png`: "Ver ficha" en los turnos de la agenda del odontólogo (también aparece para `admin`).
  - `ficha-odontologo-1440.png` y `ficha-odontologo-390.png`: ficha abierta desde un turno, con "Marcar ausente".
  - `ficha-pendientes-1440.png` y `ficha-guardada-1440.png`: la ficha básica guardada, dos cambios pendientes (pieza 16 oclusal con caries, pieza 36 ausente) y, después de guardarlos, el odontograma y el historial con los dos registros.
  - `ficha-admin-1440.png` y `ficha-admin-390.png`: el administrador ve la ficha como texto, sin campos ni "Guardar", con el aviso de solo lectura.
  - `ficha-basica-campos-1440.png` y `ficha-basica-campos-390.png`: la ficha básica con los campos de la opción B, con "Alergias" enfocado.
  - `odontograma-estados-pieza-1440.png`: piezas 32 (corona), 41 (endodoncia) y 36 (ausente) con su marca y el tinte del estado.
  - Se comprobó: sin errores ni advertencias en la consola, `localStorage` y `sessionStorage` vacíos, la dirección queda en `#/pacientes`, "Salir" quita la ficha de la página y no hay desplazamiento horizontal a 390 px. Diferencias con los mockups: solo las dos de abajo (mitades del odontograma y anillo de foco), ninguna bloqueante.
- Ajuste del apply en los estilos: en `17-ficha.css` la cara enfocada lleva el anillo de foco del sistema (`--ds-foco-ancho` y `--ds-foco-color`) en vez de `outline: none`, porque el test del sistema visual no permite quitar el anillo.
