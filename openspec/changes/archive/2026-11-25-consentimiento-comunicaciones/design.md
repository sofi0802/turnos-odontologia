# Design

## Context

- V7 (C-11) agregó a `paciente` las columnas `consentimiento_comunicaciones` (`false` por defecto) y `consentimiento_fecha`. Nada las cambia todavía. `notificaciones/` ya las lee en `TurnosARecordarJdbc`, y `ContactoPaciente.canalDeEnvio()` no devuelve canal sin consentimiento. Los reintentos (`reintentosVencidos`) vuelven a leer el contacto vigente del paciente.
- C-15 (lista de espera, sin archivar) rechaza la inscripción con `409 SIN_CONSENTIMIENTO`, leyendo la misma columna.
- `usuarios/` (dueño Juan Sebastián) tiene el registro público (`RegistrarPaciente`, `@Transactional`) y el perfil del paciente (`GET /api/v1/paciente/perfil`: nombre, apellido, DNI, email y teléfono).
- La búsqueda de C-36 MUST NOT devolver el consentimiento ("Datos mínimos en la respuesta de la búsqueda"). Por eso la recepción lo consulta aparte, después de elegir al paciente.
- `#/mi-cuenta` es el ingreso único (pacientes y personal). Desde C-49, con sesión de paciente muestra la página "Mi cuenta" con "Mi cobertura" (`dibujarCuentaConSesion`), y la barra lateral del paciente ya tiene "Mi cuenta". C-50 va a sumar "Mis presupuestos" en la misma página.
- El proyecto está en modo `system-and-mockups` (design system Revision 5, aprobado).

## Goals / Non-Goals

**Goals:**
- Un único lugar que cambia el consentimiento y escribe su historial, usado por el paciente, el registro y la recepción.
- Que C-15 y C-11 no cambien: siguen leyendo `paciente.consentimiento_comunicaciones`.

**Non-Goals:**
- Cambiar la búsqueda de pacientes, editar datos personales, marcar `tiene_whatsapp` o separar el consentimiento por canal.
- Mostrar notificaciones en la aplicación o enviar mensajes reales.

## Decisions

### D1. El estado vigente sigue en `paciente` y el historial va en una tabla nueva (V11)
`consentimiento_comunicacion_evento` (`id`, `paciente_id`, `otorgado`, `registrado_en timestamptz`, `origen` con CHECK `PACIENTE`/`REGISTRO`/`RECEPCION`, `medio` con CHECK `TELEFONO`/`PRESENCIAL` y nulo, `usuario_id` nulo, CHECK que exige `medio` y `usuario_id` solo y siempre con `RECEPCION`). Cada cambio inserta un evento y actualiza `consentimiento_comunicaciones` y `consentimiento_fecha` en la misma transacción, bloqueando la fila del paciente (`SELECT ... FOR UPDATE`) para que dos cambios simultáneos no desordenen el historial. Un trigger rechaza `UPDATE` y `DELETE` sobre la tabla de eventos.
- *Alternativa descartada*: calcular el estado solo desde el historial. Obligaría a cambiar las consultas de C-11 y C-15.
- Los pacientes existentes no reciben eventos: todos están en `false`, que es el estado "sin historial".

### D2. El consentimiento vive en `notificaciones/consentimiento`
Caso de uso `CambiarConsentimiento` (idempotente: si el valor no cambia, no inserta) y `ConsultarConsentimiento`, con su repositorio JDBC y dos controllers: `ConsentimientoPacienteController` (`/api/v1/paciente/consentimiento-comunicaciones`, el id sale de `UsuarioAutenticado.pacienteId()`) y `ConsentimientoPersonalController` (`/api/v1/pacientes/{id}/consentimiento-comunicaciones`, `@PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")`, como la lista de espera). `usuario_id` sale de la sesión y en la respuesta viaja solo el `username`.
- Errores: `422 DATOS_INVALIDOS` sin `otorgado` o sin `medio` en el recurso del personal; `404 RECURSO_NO_ENCONTRADO` si el paciente no existe. Las dos son respuestas `ProblemDetail`.

### D3. El registro usa un puerto, no el módulo de notificaciones
`usuarios/application` define `ConsentimientoInicial { void otorgadoAlRegistrar(Long pacienteId); }`, y `notificaciones/infrastructure` lo implementa llamando a `CambiarConsentimiento` con origen `REGISTRO`. `RegistrarPaciente` lo llama dentro de su transacción, después de crear al paciente, solo si `aceptaMensajes` es `true`. Un rechazo del registro revierte todo. Es el mismo patrón que `AvisoReservaOnline` de C-10. Se avisa a Juan Sebastián, porque toca `RegistroPacienteRequest` y `RegistrarPaciente`.

### D4. Dos secciones nuevas en "Mi cuenta", sin otra entrada en la barra lateral
`dibujarCuentaConSesion` de `pages/mi-cuenta.ts` arma, en este orden, "Tus datos" (`paciente/tus-datos.ts`), "Mensajes del consultorio" (`paciente/mensajes.ts`) y "Mi cobertura" (C-49). Cada sección pide sus datos por separado (perfil, consentimiento y cobertura), así un error en una no oculta las demás. El usuario eligió esta opción en lugar de una página `#/cuenta` aparte, para no tener dos entradas de cuenta en la barra lateral. Como la barra lateral no cambia, "Menú según la sesión" no se modifica.
- *Coordinación*: `mi-cuenta.ts` lo tocan C-49 (Juan Sebastián) y C-50. Cada change suma su sección con una sola línea en `dibujarCuentaConSesion`, y el segundo PR resuelve el orden.

### D5. Un solo componente de consentimiento para la recepción
`shared/ui/consentimiento-paciente.ts`: recibe el id del paciente elegido y el rol de la sesión. Con `ADMIN` o `RECEPCIONISTA` pide el estado y dibuja la línea "Acepta mensajes" o "No acepta mensajes" con su acción. El diálogo del medio (`role="dialog"`, con el orden de botones de C-39) se abre encima del formulario sin cerrarlo. Se engancha en el `alElegir` del buscador en `pages/lista-espera.ts` y en `agenda/formularios.ts` ("Nuevo turno"). El buscador de C-36 no cambia.
- Ante `SIN_CONSENTIMIENTO`, "Anotar paciente" muestra el mismo componente con el foco en "Registrar consentimiento". Después de registrar, "Guardar" vuelve a enviar el mismo pedido.

### D6. El aviso de C-15 al paciente
`lista-espera/paciente.ts` (de C-15, dueña Sofia) suma al mensaje `SIN_CONSENTIMIENTO` el enlace "Aceptar mensajes en Mi cuenta" a `#/mi-cuenta`. Es un cambio de una línea, coordinado con Sofia y avisado en el PR.

### D7. Textos para el paciente
La sección explica qué viaja en los mensajes (fecha, hora, consultorio y links, sin datos de salud), por qué canal (WhatsApp si lo tiene, y si no, email) y que se puede revocar en cualquier momento. Revocar pide confirmación, porque deja sin recordatorios ni ofertas de la lista de espera.

## Risks / Trade-offs

- **[C-15 sin archivar]** El enlace del aviso y el rechazo `SIN_CONSENTIMIENTO` dependen de código de C-15 ya mergeado, pero su spec (`frontend-lista-espera`) todavía no está en `openspec/specs/`. → Los requirements van en `frontend-consentimiento-comunicaciones`, que no depende de ese spec.
- **[Tres changes sobre "Mi cuenta"]** C-49, C-50 y C-17 suman secciones a la misma página. → Cada uno en su propio archivo, con una línea en `dibujarCuentaConSesion` (D4). Los requirements de C-17 son ADDED y no modifican el de C-49.
- **[El personal registra por el paciente]** Un registro falso de la recepción no se puede detectar. → El historial guarda quién, cuándo y por qué medio, y el diálogo exige confirmar que el paciente lo pidió.
- **[Pacientes existentes]** Siguen sin consentimiento. → Es lo correcto según RN-NO-03: nadie recibe mensajes sin haberlo aceptado.

## Migration Plan

V11 crea la tabla, sus CHECK, el índice por `paciente_id` y el trigger. No modifica datos existentes. Para volver atrás alcanza con revertir el código: la tabla queda sin uso y las columnas de V7 siguen como estaban.

## UI Mockups

- Design system revision: 5
- Decision: yes, 2026-11-25. Modo del proyecto `system-and-mockups`.
- Mockups: `openspec/changes/consentimiento-comunicaciones/mockups/` ("Mi cuenta" con y sin consentimiento, confirmación de revocar, casilla del registro, línea y diálogo de la recepción, rechazo `SIN_CONSENTIMIENTO` y aviso de la lista de espera).
- Approval: approved (2026-11-25, by Sofia Indovino): mergeó la propuesta y los mockups en el PR #142 sin pedir cambios
- Preview: rendered (2026-11-25), 12 capturas en `mockups/previews/` a 390 × 844 y 1440 × 900 (página completa), sin desplazamiento horizontal ni errores en la consola
- Visual verify: passed (2026-11-25): sin blockers ni majors; dos diferencias menores corregidas durante la verificación; 16 capturas en `mockups/verify/` con su README (y lo que no se revisó en el navegador)
