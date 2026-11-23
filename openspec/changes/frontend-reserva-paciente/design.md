# Design

## Context

- **Lo que hay en `frontend/`** (C-02 y C-09):
  - Un router por hash con coincidencia exacta de ruta.
  - Un layout con un menú fijo de tres enlaces.
  - Páginas con la marca `privada: true`, que `app.ts` solo dibuja con sesión del personal; con rol `PACIENTE` muestra un aviso.
  - El cliente de API con CSRF y errores tipados (`ErrorNegocio`, `ErrorValidacion`, `ErrorSesion`, `ErrorPermiso`, `ErrorNoEncontrado`).
  - Las utilidades de zona horaria (`zona.ts`) y los tests con `ServidorFalso` y jsdom.
- **El backend que se consume ya está en `master`**, sin cambios: catálogo público, disponibilidad, registro, login y rutas `/paciente/*` (specs `disponibilidad-online`, `reserva-online`, `turnos-paciente` y `cuentas-pacientes`).
- **Datos que el backend trae o no trae:**
  - `SIN_DISPONIBILIDAD` trae `alternativas`, pero `leerProblemDetail` hoy las descarta.
  - El registro no inicia la sesión.
- **C-09 está mergeado pero no archivado.** Su delta de `frontend-navegacion` es la base del de este change.
- **Design system Revision 1** (`design-system/`, aprobado el 2026-11-22, modo `system-and-mockups`, PR #42):
  - Define `tokens.css`, con alias que tienen los mismos nombres y valores que el `:root` de `frontend/src/styles.css`.
  - Define los componentes de referencia de `components.html`: encabezado, botones, campos, avisos, estados del turno, reserva móvil, comprobante y diálogo.
  - Fija la voz de `brand.md`: voseo, y errores que dicen qué pasó y qué hacer.
  - Todavía no está aplicado en `frontend/`.

## Goals / Non-Goals

**Goals:**
- Que el paciente pueda hacer todo el Flujo 2 y el Flujo 3 desde el celular, sin ver nada del personal.
- Reusar lo que ya existe: cliente, errores, sesión, zona, campos y `ServidorFalso`. No duplicar la lógica de sesión que C-09 dejó en `app.ts`.
- Que las pantallas del paciente sigan el design system Revision 1 y los mockups aprobados de `mockups/`, sin librerías.

**Non-Goals:**
- Rediseñar la agenda del personal ni su ingreso.
- Configuración, la ficha, recuperar la contraseña, el canal de recordatorios y el consentimiento.
- Modificar endpoints del backend.

## Decisions

### D1. Módulos nuevos
- `paciente/catalogo.ts`: `GET /public/prestaciones` y `/public/profesionales`, con validación de forma, como `agenda/catalogos.ts`.
- `paciente/disponibilidad.ts`: arma la query de `GET /public/disponibilidad` y lee la lista `{inicio, fin}`.
- `paciente/reserva.ts`: `POST /paciente/reservas` y la reserva pendiente (D4).
- `paciente/turnos.ts`:
  - `GET /paciente/turnos`.
  - Separar los próximos de los anteriores, según el estado y el inicio respecto del momento actual.
  - Cancelar y reprogramar.
- `paciente/horarios.ts`: la grilla de botones de horario, compartida por la reserva, las alternativas y el cambio de horario.
- `pages/mi-cuenta.ts`, `pages/reserva.ts` (deja de usar `paginaConTitulo`), `pages/mis-turnos.ts` y `pages/confirmar.ts`.
- `shared/ui/entrada.ts`: traduce las direcciones de los links de los avisos (D9).
- `shared/sesion/sesion.ts`: suma `registrarPaciente(api, datos)` y `esPaciente(rol)`.
- `shared/fecha/zona.ts`: suma `fechaLarga(instante)`, que devuelve "martes 1 de diciembre" con `Intl.DateTimeFormat('es-AR', {weekday:'long', day:'numeric', month:'long', timeZone: ZONA})`.

Alternativa descartada: meter todo en `pages/reserva.ts`. Separar la lógica pura deja testear cada escenario sin el DOM completo, como hizo C-09.

### D2. Acceso y menú por página
`Pagina` reemplaza `privada?: boolean` por dos propiedades:
- `acceso?: 'publica' | 'personal' | 'paciente'`, que por defecto es `'publica'`.
- `menu?: 'personal' | 'paciente'`. Sin `menu`, la página no figura en el menú.

`app.ts` generaliza la guarda que hizo C-09:
- **`personal`:** igual que hoy. Sin sesión va a `#/ingreso`; con sesión de paciente, muestra el aviso.
- **`paciente`:** sin sesión va a `#/mi-cuenta`; con sesión del personal, muestra "Esta sección es para pacientes."

Agenda queda con `acceso: 'personal'`, sin cambio de comportamiento. Reserva es `publica` y muestra sola el aviso para el personal, porque sin sesión tiene que verse.

Alternativa descartada: sumar `privadaPaciente: true` al lado de `privada`. Dos banderas booleanas admiten combinaciones sin sentido.

### D3. El layout filtra el menú con la sesión
- `crearLayout` recibe todas las páginas con `menu`.
- `mostrarSesion(sesion)` oculta con `hidden` los `<li>` del grupo que no corresponde y muestra "Ingresar" (enlace a `#/mi-cuenta`) sin sesión, o el usuario y "Salir" con sesión.
- Como `app.ts` ya llama a `mostrarSesion` en cada navegación y en cada login o logout, el menú queda al día sin recargar.
- `alSalir` lleva a `#/reserva` si la sesión era de paciente, y a `#/ingreso` si era del personal, como hoy.

### D4. Reserva pendiente en `sessionStorage`
- **Qué se guarda:** al confirmar sin sesión, `{prestacionId, profesionalId|null, fecha, inicio}` en `sessionStorage` con la clave `turnos.reservaPendiente`. No incluye datos del paciente ni de salud.
- **Al leerla:** se valida la forma, y cualquier error de lectura o de formato equivale a no tener reserva.
- **Al volver:** `mi-cuenta` vuelve a `#/reserva` si hay una reserva pendiente.
- **Al dibujarse la reserva:** restaura las elecciones y vuelve a pedir la disponibilidad. Si el horario ya no está, avisa "Ese horario se ocupó recién. Elegí otro." y lo descarta.
- **Se borra** al confirmar con `201` y al salir.

Alternativas descartadas:
- Guardar la elección en la dirección (`#/reserva?...`): el router compara rutas exactas, y la dirección queda en el historial.
- `localStorage`: sobrevive al cerrar el navegador, y una reserva vieja no tiene sentido.

### D5. Se envía el `inicio` que dio la API
La reserva y la reprogramación envían el `inicio` exacto que vino en la disponibilidad o en las alternativas, sin volver a calcularlo desde la hora mostrada. Así no hay conversión de zona en el frontend y el backend recibe un horario que él mismo ofreció.

### D6. Ingreso del paciente separado, sobre el mismo login
`#/mi-cuenta` usa el mismo `iniciarSesion` que `#/ingreso`, porque es el mismo `POST /public/login`. Muestra dos secciones, "Ingresar" y "Crear cuenta", con un enlace para pasar de una a otra.

Después de un login correcto:
- Con rol `PACIENTE`, va a la reserva pendiente o a `#/mis-turnos`.
- Con un rol del personal, va a `#/agenda`.

Después de un `201` del registro, llama a `iniciarSesion` con el email y la contraseña que todavía tiene el formulario y las borra al terminar.

Alternativa descartada: un solo ingreso para todos. Modificaría el spec de C-09, que todavía no está archivado, y es la página de Enzo. Lo eligió así el responsable del change (2026-11-23).

### D7. Las alternativas en `ErrorNegocio`
- `ErrorNegocio` suma `alternativas: readonly HorarioAlternativo[]`, con `inicio` y `fin` como `string`.
- `leerProblemDetail` las lee con la misma validación de forma que `errores`. El cliente las pasa al construir el error del `409`.
- Los demás usos de `ErrorNegocio` no cambian, porque la lista es vacía por defecto.

### D8. Mensajes nuevos en la tabla de C-09
`MENSAJES_POR_CODIGO` suma:
- `SIN_DISPONIBILIDAD`, `CANCELACION_FUERA_DE_PLAZO`, `RESERVA_INEXISTENTE` y `PROFESIONAL_NO_HABILITADO`.
- `DNI_YA_REGISTRADO` y `EMAIL_YA_REGISTRADO`, con los textos de los specs.

Los mensajes que dependen de la pantalla (por ejemplo, "Email o contraseña incorrectos.") viven en su página, como hizo C-09 con el ingreso del personal.

### D9. Las direcciones de los links se traducen antes del router
Los avisos de C-11 usan rutas de camino, no de hash: `Enlaces.java` arma `base + /mis-turnos/cancelar`, `/mis-turnos/reprogramar` y `/confirmar/{token}`.

Antes de crear el router, `main.ts` llama a `traducirEntrada(location)`:
- `/mis-turnos/cancelar` y `/mis-turnos/reprogramar` → `history.replaceState` a `/#/mis-turnos`.
- `/confirmar/{token}` → guarda el token en memoria (no en `sessionStorage` ni en la dirección) y reemplaza la dirección por `/#/confirmar`. La página `confirmar` lee el token de ahí. Si se abre `#/confirmar` sin token, muestra "El link no es válido. Revisá que esté completo." sin botón.

La página solo envía el `POST` cuando se toca el botón. Así un revisor de links que abra la dirección no gasta el token, que es lo que el backend busca al aceptar solo `POST`.

Alternativa descartada: cambiar las rutas de C-11 a `#/...`. Las rutas de cancelar y reprogramar son configurables, pero la de confirmar está fija en el backend de Juan Cruz, y este change no toca el backend.

### D10. Estilos con el design system
- **`styles.css` arranca con `@import '../../design-system/tokens.css';`** y borra su propio `:root` de colores, espacios y radio.
  - Los alias de `tokens.css` tienen los mismos nombres y valores, así que la agenda de C-09 no cambia de aspecto.
  - Vite resuelve el `@import` de CSS al compilar, tanto en `dev` como en `build`, así que no hace falta abrir `server.fs.allow` fuera de `frontend/`. Se verifica con `npm run build` y con `npm run dev` en la tarea 7.1.
- **Clases de `design-system/components.html`**, copiadas a `styles.css` usando solo variables `--ds-*`: `.btn`, `.btn-principal`, `.btn-fantasma`, `.btn-peligro`, `.campo`, `.control`, `.error-campo`, `.aviso-ok`, `.aviso-error`, `.aviso-alerta`, `.aviso-info`, `.estado` con sus `e-*`, `.pasos`, `.opciones`, `.opcion`, `.chips`, `.chip`, `.comprobante`, `.numero-reserva`, `.detalle-lista` y `.dialogo`.
  - No se inventan valores nuevos. Si hace falta uno, se propone en `tokens.css`, lo que sube la Revision y requiere aprobación.
- **Cómo se ve cada pantalla**:
  - **Reserva:** un paso por pantalla (`brand.md`), con la barra `.pasos`. Prestación y profesional se eligen como `.opcion` (radio) y los horarios como `.chip` con `aria-pressed`. El comprobante usa `.comprobante` y `.numero-reserva`.
  - **Mis turnos:** tarjetas con `.estado` y el diálogo de cancelar.
  - **Mi cuenta:** el formulario angosto de campos.
- **Los mockups aprobados de `mockups/` son el criterio de aceptación visual** (sección UI Mockups). Los tests de `responsivo.test.ts` siguen el patrón de C-09: comprueban la regla en el CSS, porque jsdom no calcula el diseño, y las capturas reales son la evidencia.

Alternativa descartada: importar `tokens.css` desde `main.ts`. Vite lo serviría como `/@fs/...` y, al estar fuera de la raíz `frontend/`, lo bloquea en desarrollo salvo que se abra `server.fs.allow`.

## Risks / Trade-offs

- **[C-09 se archiva después de C-12]** → el `MODIFIED` de "Páginas disponibles" no encontraría el texto que deja C-09.
  - Mitigación: pedir a Enzo o a Sofia el archive de C-09 antes del archive de C-12. Queda anotado en CHANGES.md y en el PR.
  - El apply no depende de eso, porque el código de C-09 ya está en `master`.
- **[Al cambiar de horario, la disponibilidad pública cuenta el turno propio como ocupado]** → el paciente no ve su mismo horario ni los que se superponen con él, aunque el backend aceptaría reprogramarlo ahí (spec `turnos-paciente`). Es aceptable para el MVP. Si hiciera falta, el backend tendría que excluir el turno en la disponibilidad, en otro change.
- **[Se toca código de C-09: `app.ts`, `layout.ts` y `pagina.ts`]** → avisar a Enzo. Los tests de C-09 (`navegacion.test.ts` y `sesion.test.ts`) se actualizan donde cambia el menú y se corren completos.
- **[Importar `tokens.css` cambia la agenda de C-09]** → los alias tienen los mismos nombres y valores que el `:root` actual (`--color-acento` `#0b6e8a`, `--espacio-*`, `--radio`), así que no debería cambiar nada visible. Se comprueba con los tests de C-09 y con una captura de la agenda en verify.
- **[El servidor de producción no devuelve `index.html` en `/mis-turnos/...` o `/confirmar/...`]** → los links de los avisos darían 404. Vite lo hace en desarrollo (`appType: 'spa'`). Queda anotado para C-14, que arma la entrega.
- **[La contraseña queda en memoria entre el registro y el login]** → no se guarda en ningún almacenamiento y el campo se vacía al terminar.
- **[Prueba manual]** → hace falta la base de dev con el seed de C-04 y C-07. Ana Ejemplo atiende de lunes a viernes de 8 a 13 y Bruno Prueba de 10 a 18. El paciente se crea desde la misma pantalla de registro.

## Migration Plan

Solo frontend, sin cambios de backend ni de base. Se despliega con el build de Vite, y para volver atrás alcanza con revertir el PR. `VITE_API_BASE_URL` sigue siendo `http://localhost:8080/api/v1` en desarrollo.

## UI Mockups

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22).
- Decision: yes, 2026-11-23. El modo es `system-and-mockups` en `.active-orchestrator-state.json` y en `design-system/README.md`.
- Mockups: `openspec/changes/frontend-reserva-paciente/mockups/` (ver su `README.md`). Fuente de referencia: `design-system/components.html`, secciones "Reserva online", "Formularios y avisos" y "Diálogo de confirmación".
- Approval: pending. La aprueba la líder antes del apply (`design-system/README.md`).
- Preview: rendered (2026-11-23), capturas en `mockups/previews/` a 390 × 844 y 1440 × 900, sin desplazamiento horizontal ni errores en la consola.
- Visual verify: pendiente. En verify se comparan `#/reserva`, `#/mi-cuenta`, `#/mis-turnos` y `#/confirmar` con los mockups aprobados, a 390 × 844 y 1440 × 900, con capturas en `mockups/verify/`.
