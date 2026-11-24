# Design: frontend-sobreturno-y-extension (C-45)

## Context

- El backend de C-08 ya está mergeado y probado. `POST /api/v1/turnos/sobreturno` recibe `pacienteId`, `profesionalId`, `prestacionId`, `inicio` con zona y `motivo` (obligatorio, hasta 500), elige el sillón él mismo y responde `TurnoResponse` (con `sillonId` y `numeroReserva`). `POST /api/v1/turnos/{id}/extender` recibe `fin` absoluto con zona y `motivo`. Los rechazos son `409` con `codigo` (`SIN_SILLON_DISPONIBLE`, `SOLAPAMIENTO_SILLON`, `MARGEN_INSUFICIENTE`, `TURNO_NO_ACTIVO`, `TURNO_NO_EXTENSIBLE`) y `422 DATOS_INVALIDOS`.
- En el frontend, la agenda (`pages/agenda.ts`) arma la barra con "Nuevo turno" y un `ControlAgenda`. Los formularios viven en `agenda/formularios.ts` y usan el diálogo uniforme de C-39 (`shared/ui/dialogo.ts`), `manejarEnvio` para los errores y `shared/ui/mensajes.ts` para los textos por código. Las acciones por turno (`AccionesTurno`: cancelar, reprogramar, asistencia) se dibujan en `agenda/vista.ts` (día) y `agenda/semana.ts` (detalle de la semana, que cierra el detalle antes de abrir un formulario).
- `TurnoAgendaResponse` ya trae `pacienteId`, pero `TurnoAgenda` (`agenda/consulta.ts`) no lo lee. No trae `extendido`.
- `formularios.ts` tiene 480 líneas. Sumarle dos formularios más lo vuelve difícil de leer.

## Goals / Non-Goals

**Goals:**
- Cargar un sobreturno y extender un turno desde la agenda con los mismos componentes, mensajes y estilo que "Nuevo turno" y "Reprogramar".
- Resolver el choque de una extensión sin mover nada solo: el usuario elige reprogramar el turno que choca o cargar la atención como sobreturno (US-019 CA-2).

**Non-Goals:**
- Indicar en la agenda que un turno fue extendido (falta `extendido` en `TurnoAgendaResponse`; es un cambio de `agenda/api`).
- Calcular en el frontend qué sillón quedará libre para el sobreturno: lo decide el servidor.
- Cambiar "Nuevo turno", "Cancelar", "Reprogramar" o las acciones de asistencia.

## Decisions

### D1. Un módulo nuevo `agenda/sobreturno.ts`
Los dos formularios (`abrirSobreturno` y `abrirExtension`) y la búsqueda del turno que choca van en `agenda/sobreturno.ts`. Reusan de `formularios.ts` el diálogo, `selector`, `entrada`, `validarObligatorios`, `manejarEnvio` y `leerNumeroReserva`, que se exportan.
*Alternativa descartada*: sumarlos a `formularios.ts`. Pasaría de 700 líneas y mezclaría flujos distintos.

### D2. Hora libre en el sobreturno, no la lista de horarios libres
El sobreturno existe para pisar la agenda del profesional, así que no se usa el buscador de horarios de C-40. Son un campo `date` (con `usarSelectorFecha`, como en Reprogramar) y un campo `time` con paso de 5 minutos. La fecha propuesta es el día que muestra la agenda. Para la hora se propone la próxima múltiplo de 5 si la fecha es hoy; si no, queda vacía.
*Alternativa descartada*: el calendario y los horarios libres de C-40. Ocultarían justo los horarios ocupados, que es donde se necesita el sobreturno.

### D3. Profesional según el rol
Para `ADMIN` y `RECEPCIONISTA`, el selector de profesional se llena con los habilitados para la prestación elegida (`cargarProfesionales`, igual que en Nuevo turno) y queda deshabilitado hasta elegir prestación. Para `ODONTOLOGO`, `pages/agenda.ts` pasa su `profesionalId` (el mismo `profesionalFijo` de C-35): el campo muestra su nombre, deshabilitado. Si no atiende la prestación elegida, la API responde `409 PROFESIONAL_NO_HABILITADO` y se ve el mensaje que ya existe.

### D4. Motivo como campo de texto de una línea
`crearCampo` solo admite `input` y `select`, así que el motivo es un `input type="text"` con `maxlength="500"`, `autocomplete="off"` y la ayuda "Motivo administrativo. No escribas datos de salud." (regla dura 6). Se envía con `trim()`. El motivo no se registra en la consola ni en `localStorage`.
*Alternativa descartada*: un `textarea`, que obligaría a ampliar `crearCampo` en `shared/ui` para un solo uso.

### D5. "Extender" en `AccionesTurno`, visible por estado
`AccionesTurno` suma `extender(turno)`. `vista.ts` dibuja el botón después de "Reprogramar" solo si `estado` es `RESERVADO` o `CONFIRMADO` y `esSobreturno` es `false`. `semana.ts` lo envuelve en `cerrandoAntes`, como cancelar y reprogramar. No hace falta filtrar por rol: la agenda solo la ven `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`, y los tres pueden extender (matriz RBAC). Tampoco se filtra por hora: la API decide si el turno es extensible.

### D6. Nuevo fin como hora del mismo día
El formulario de extender muestra "Hoy de 10:00 a 10:30" como resumen y un campo `time` "Nuevo fin", con el fin actual más 15 minutos y paso de 5. El `fin` se arma con `aIsoConZona(fecha del turno, hora)`. Antes de enviar se valida que sea posterior al fin actual. Una extensión que cruza la medianoche no se ofrece: el consultorio no atiende de noche.

### D7. Choque al extender: buscar el turno en la agenda cargada
`ControlAgenda` suma `turnosCargados()`, que devuelve los turnos de la última consulta. Ante `409` con `SOLAPAMIENTO_SILLON`, `SOLAPAMIENTO_PROFESIONAL` o `MARGEN_INSUFICIENTE`, `turnoQueChoca(turno, nuevoFin, turnos)` busca el primer turno activo (`RESERVADO` o `CONFIRMADO`), distinto del extendido, del mismo sillón o del mismo profesional, que empieza en `[fin actual, nuevo fin + 10 min)` (margen mínimo de RN-AG-04). Si lo encuentra, ofrece "Reprogramar el turno de las HH:mm", que cierra el diálogo y llama a `abrirReprogramacion(control, siguiente)`. Siempre ofrece "Cargar como sobreturno", que cierra el diálogo y abre `abrirSobreturno` precargado con el paciente, la prestación, el profesional, la fecha, la hora del fin actual y el motivo escrito.
*Alternativa descartada*: pedirle al backend el turno que choca. Implicaría un contrato nuevo en `agenda/api` para un dato que la agenda ya tiene casi siempre. Si el turno que choca está fuera de los filtros (otro profesional en el mismo sillón con filtro de profesional), solo se ofrece el sobreturno, y el escenario "Choque sin turno identificable" lo cubre.

### D8. Paciente precargado en el sobreturno
`TurnoAgenda` suma `pacienteId`. Cuando el sobreturno viene de un choque, el campo de paciente no es el buscador sino un texto fijo con "Apellido, Nombre" y el id guardado. Así no hay que buscar de nuevo a alguien que ya está en el turno.

### D9. Mensajes
`MENSAJES_POR_CODIGO` suma `SIN_SILLON_DISPONIBLE` y `TURNO_NO_EXTENSIBLE`. El aviso del choque es una línea propia sobre el mensaje del código. Los `422` usan `manejarEnvio` con el mapa de campos `{ pacienteId, profesionalId, prestacionId, inicio, motivo }` para el sobreturno y `{ fin, motivo }` para la extensión.

### D10. Botón "Sobreturno" como botón secundario
En la barra de filtros, "Sobreturno" va antes de "Nuevo turno", con la clase `boton-secundario` del sistema visual (C-39), para que la acción principal siga siendo el turno normal. En 390 px los dos botones ocupan una fila, cada uno a la mitad del ancho.

## Risks / Trade-offs

- [El turno que choca puede no estar en la agenda cargada, por los filtros o porque empieza en otro día] → Solo se ofrece "Cargar como sobreturno", y el usuario puede quitar el filtro y reprogramar a mano.
- [El sobreturno precargado desde un choque dura lo que dura la prestación, no los minutos extra pedidos] → Es la regla de C-08 (el fin sale de la prestación). El formulario lo dice en la ayuda de la hora: "El sobreturno dura lo que dura la prestación."
- [La agenda no muestra que un turno fue extendido] → Se ve el nuevo fin. Sumar `extendido` a `TurnoAgendaResponse` queda anotado como posible ajuste de `agenda/api`.
- [Un error del motivo puede contener texto clínico escrito por el usuario] → No se loguea ni se guarda en el navegador. La ayuda del campo lo desalienta.

## Migration Plan

Solo frontend: se despliega con el build de Vite, sin migraciones. Para volver atrás, se revierte el PR.

## UI Mockups

- Design system revision: 4
- Decision: yes (2026-11-24, política `system-and-mockups`)
- Mockups: `openspec/changes/frontend-sobreturno-y-extension/mockups/` (ver su README)
- Approval: pending
- Preview: rendered (2026-11-24, 8 pantallas en `mockups/previews/` a 390x844 y 1440x900, sin desplazamiento horizontal)
- Visual verify: pending
