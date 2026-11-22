# Spec Delta

## Purpose

Definir la agenda del personal en la aplicación web: cómo se ven los turnos del día y de la semana, cómo se filtran por profesional o sillón, y cómo se crean, cancelan y reprograman turnos con mensajes claros para cada rechazo de la API. En los escenarios, con datos ficticios, hay una sesión de `recepcion`, la Dra. Ana Ejemplo (id 1), el Dr. Bruno Prueba (id 2), el Sillón 1 (id 1), el Sillón 2 (id 2), la prestación "Consulta" (id 1, 30 minutos) y "Paciente Uno" (id 1, DNI `99000001`). La hora es la de `America/Argentina/Buenos_Aires` (`-03:00`) y hoy es el martes 2026-12-01.

## ADDED Requirements

### Requirement: Agenda del día
Al abrirse, la agenda SHALL mostrar el día de hoy pidiendo `GET /api/v1/turnos` con `desde` a las 00:00 de ese día y `hasta` a las 00:00 del día siguiente, en ISO-8601 con zona horaria. Cada turno SHALL mostrar la hora de inicio y de fin, el nombre y apellido del paciente, el profesional, el sillón y la prestación, ordenados por inicio.

#### Scenario: Ver los turnos de hoy
- **GIVEN** que la API tiene un turno de "Paciente Uno" de 10:00 a 10:30 con la Dra. Ana Ejemplo en el Sillón 1 para "Consulta"
- **WHEN** se abre la agenda
- **THEN** se pide `GET /api/v1/turnos?desde=2026-12-01T00:00:00-03:00&hasta=2026-12-02T00:00:00-03:00`
- **AND** se ve un turno "10:00 – 10:30" de "Paciente Uno" con "Ana Ejemplo", "Sillón 1" y "Consulta"

#### Scenario: Día sin turnos
- **GIVEN** que la API responde `200` con una lista vacía
- **WHEN** se abre la agenda
- **THEN** se ve el mensaje "No hay turnos para este día."

#### Scenario: Hora mostrada en la zona del consultorio
- **GIVEN** que la API devuelve un turno con `inicio` `2026-12-01T13:00:00Z`
- **WHEN** se abre la agenda
- **THEN** el turno se muestra a las "10:00"

### Requirement: Agenda de la semana
La agenda SHALL permitir cambiar entre la vista del día y la de la semana. La semana va de lunes 00:00 al lunes siguiente 00:00 y muestra los turnos agrupados por día. Botones "Anterior", "Hoy" y "Siguiente" SHALL mover el período mostrado un día o una semana según la vista.

#### Scenario: Cambiar a la vista semanal
- **GIVEN** la agenda del martes 2026-12-01 abierta
- **WHEN** se elige la vista "Semana"
- **THEN** se pide `GET /api/v1/turnos?desde=2026-11-30T00:00:00-03:00&hasta=2026-12-07T00:00:00-03:00`
- **AND** los turnos se ven agrupados bajo cada día de lunes a domingo

#### Scenario: Ir al día siguiente
- **GIVEN** la agenda del día 2026-12-01 abierta
- **WHEN** se usa "Siguiente"
- **THEN** se pide la agenda con `desde=2026-12-02T00:00:00-03:00` y `hasta=2026-12-03T00:00:00-03:00`

#### Scenario: Volver a hoy
- **GIVEN** la agenda de la semana del 2026-12-14 abierta
- **WHEN** se usa "Hoy"
- **THEN** se muestra la semana que contiene el 2026-12-01

### Requirement: Filtros por profesional y por sillón
La agenda SHALL ofrecer los filtros "Profesional" y "Sillón" con las opciones de `GET /api/v1/profesionales` y `GET /api/v1/sillones`, más la opción "Todos". Al elegir una opción SHALL volver a pedir la agenda con `profesionalId` o `sillonId`. Si esas listas no se pueden obtener, los filtros SHALL quedar solo con "Todos" y un aviso.

#### Scenario: Filtrar por profesional
- **GIVEN** la agenda del día abierta
- **WHEN** se elige "Ana Ejemplo" en el filtro "Profesional"
- **THEN** se pide `GET /api/v1/turnos` del mismo período con `profesionalId=1`

#### Scenario: Filtrar por sillón y profesional a la vez
- **GIVEN** la agenda filtrada por "Ana Ejemplo"
- **WHEN** se elige "Sillón 2" en el filtro "Sillón"
- **THEN** se pide `GET /api/v1/turnos` del mismo período con `profesionalId=1` y `sillonId=2`

#### Scenario: Catálogos no disponibles
- **GIVEN** que `GET /api/v1/profesionales` responde `404`
- **WHEN** se abre la agenda
- **THEN** el filtro "Profesional" solo tiene la opción "Todos"
- **AND** se ve el aviso "No pudimos cargar la lista de profesionales."
- **AND** los turnos del día se muestran igual

### Requirement: Indicadores de confirmado y sobreturno
Un turno con `estado` `CONFIRMADO` SHALL mostrar la etiqueta "Confirmado" y un turno con `esSobreturno` `true` SHALL mostrar la etiqueta "Sobreturno". La diferencia MUST NOT depender solo del color. Un turno `RESERVADO` con `esSobreturno` `false` no lleva etiquetas.

#### Scenario: Turno confirmado
- **GIVEN** que la API devuelve un turno con `estado` `CONFIRMADO`
- **WHEN** se abre la agenda
- **THEN** el turno muestra la etiqueta de texto "Confirmado"

#### Scenario: Sobreturno
- **GIVEN** que la API devuelve un turno con `esSobreturno` `true`
- **WHEN** se abre la agenda
- **THEN** el turno muestra la etiqueta de texto "Sobreturno"

#### Scenario: Turno común sin etiquetas
- **GIVEN** que la API devuelve un turno `RESERVADO` con `esSobreturno` `false`
- **WHEN** se abre la agenda
- **THEN** el turno no muestra las etiquetas "Sobreturno" ni "Confirmado"

### Requirement: Crear un turno
El botón "Nuevo turno" SHALL abrir un formulario con paciente (id), profesional, sillón, prestación, fecha y hora. Al enviarlo SHALL pedir `POST /api/v1/turnos` con los ids y `inicio` en ISO-8601 con zona horaria. Con `201`, MUST cerrar el formulario, mostrar "Turno creado. Número de reserva: <numeroReserva>." y recargar la agenda.

#### Scenario: Recepción crea un turno
- **GIVEN** la agenda del día abierta
- **WHEN** se completa el formulario con el paciente 1, "Ana Ejemplo", "Sillón 1", "Consulta", 2026-12-01 a las 10:00 y se envía
- **THEN** se pide `POST /api/v1/turnos` con `pacienteId` 1, `profesionalId` 1, `sillonId` 1, `prestacionId` 1 e `inicio` `2026-12-01T10:00:00-03:00`
- **AND** al responder `201` con `numeroReserva` `R-0001` se ve "Turno creado. Número de reserva: R-0001."
- **AND** se vuelve a pedir la agenda del día

#### Scenario: Faltan datos en el formulario
- **GIVEN** el formulario de nuevo turno abierto
- **WHEN** se envía sin elegir el sillón
- **THEN** no se envía ningún pedido
- **AND** el campo "Sillón" muestra "Elegí un sillón."

### Requirement: Mensajes por código de rechazo
Ante un `409`, el formulario MUST quedar abierto con lo cargado y mostrar un mensaje en español propio de cada código conocido (`SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON`, `MARGEN_INSUFICIENTE`, `INICIO_EN_EL_PASADO`, `RECURSO_INACTIVO`, `TURNO_NO_ACTIVO`, `FUERA_DE_HORARIO`, `PROFESIONAL_AUSENTE`, `AGENDA_BLOQUEADA`). Con otro código, SHALL mostrar el `detail` de la API.

#### Scenario: Solapamiento con el profesional
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `SOLAPAMIENTO_PROFESIONAL`
- **THEN** se ve "El profesional ya tiene un turno en ese horario."
- **AND** el formulario sigue abierto con los datos cargados

#### Scenario: Margen insuficiente
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `MARGEN_INSUFICIENTE`
- **THEN** se ve "No queda el margen mínimo entre turnos."

#### Scenario: Sillón ocupado
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `SOLAPAMIENTO_SILLON`
- **THEN** se ve "El sillón ya está ocupado en ese horario."

#### Scenario: Inicio en el pasado
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `INICIO_EN_EL_PASADO`
- **THEN** se ve "El horario elegido ya pasó."

#### Scenario: Recurso inactivo
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `RECURSO_INACTIVO`
- **THEN** se ve "El profesional, el sillón o la prestación está inactivo."

#### Scenario: Fuera del horario del profesional
- **GIVEN** el formulario de nuevo turno con la Dra. Ana Ejemplo el 2026-12-01 a las 15:00
- **WHEN** se envía y la API responde `409` con el código `FUERA_DE_HORARIO`
- **THEN** se ve "El profesional no atiende en ese horario."
- **AND** el formulario sigue abierto con los datos cargados

#### Scenario: Profesional ausente
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `PROFESIONAL_AUSENTE`
- **THEN** se ve "El profesional tiene registrada una ausencia en ese horario."

#### Scenario: Agenda bloqueada
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `AGENDA_BLOQUEADA`
- **THEN** se ve "La agenda del profesional está bloqueada en ese horario."

#### Scenario: Código sin mensaje propio
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `409` con el código `OTRO_CODIGO` y el `detail` "Regla ficticia"
- **THEN** se ve "Regla ficticia"

### Requirement: Errores de campo ante un 422
Ante un `422` con `DATOS_INVALIDOS`, el formulario MUST mostrar cada mensaje de la lista `errores` junto al campo que nombra, sin cerrarse. Un error de un campo que el formulario no muestra SHALL aparecer como mensaje general.

#### Scenario: Profesional inexistente
- **GIVEN** el formulario de nuevo turno completo
- **WHEN** se envía y la API responde `422` con el código `DATOS_INVALIDOS` y un error del campo `profesionalId` con el mensaje "no existe"
- **THEN** el campo "Profesional" muestra "no existe"
- **AND** el formulario sigue abierto

### Requirement: Cancelar un turno
Al elegir un turno de la agenda SHALL ofrecerse "Cancelar turno", que pide el DNI del paciente y el número de reserva y envía `POST /api/v1/turnos/{id}/cancelar` con `dni` y `numeroReserva`. Con `200`, MUST mostrar "Turno cancelado." y recargar la agenda; ante un rechazo, mostrar su mensaje sin cerrar el formulario.

#### Scenario: Recepción cancela un turno
- **GIVEN** la agenda con el turno 7 de "Paciente Uno" a las 10:00
- **WHEN** se elige ese turno, "Cancelar turno", se ingresa el DNI `99000001` y el número de reserva `R-0001` y se confirma
- **THEN** se pide `POST /api/v1/turnos/7/cancelar` con `dni` `99000001` y `numeroReserva` `R-0001`
- **AND** al responder `200` se ve "Turno cancelado." y se vuelve a pedir la agenda

#### Scenario: Turno ya cancelado
- **GIVEN** el formulario de cancelación del turno 7 completo
- **WHEN** se confirma y la API responde `409` con el código `TURNO_NO_ACTIVO`
- **THEN** se ve "El turno ya no está activo."

#### Scenario: Turno que ya no existe
- **GIVEN** el formulario de cancelación del turno 7 completo
- **WHEN** se confirma y la API responde `404` con el código `TURNO_INEXISTENTE`
- **THEN** se ve "El turno ya no existe." y se vuelve a pedir la agenda

### Requirement: Reprogramar un turno
Al elegir un turno SHALL ofrecerse "Reprogramar", que pide DNI, número de reserva, nueva fecha y hora y, opcionalmente, otro profesional u otro sillón, y envía `POST /api/v1/turnos/{id}/reprogramar`. Con `200`, MUST mostrar "Turno reprogramado. Nuevo número de reserva: <numeroReserva>." y recargar la agenda.

#### Scenario: Recepción reprograma un turno
- **GIVEN** la agenda con el turno 7 de "Paciente Uno" a las 10:00 con la Dra. Ana Ejemplo
- **WHEN** se elige "Reprogramar", se ingresa el DNI `99000001`, el número de reserva `R-0001` y las 11:00 del mismo día sin cambiar profesional ni sillón, y se confirma
- **THEN** se pide `POST /api/v1/turnos/7/reprogramar` con `dni`, `numeroReserva` e `inicio` `2026-12-01T11:00:00-03:00`, sin `profesionalId` ni `sillonId`
- **AND** al responder `200` con `numeroReserva` `R-0002` se ve "Turno reprogramado. Nuevo número de reserva: R-0002."

#### Scenario: Reprogramación rechazada por el sillón
- **GIVEN** el formulario de reprogramación del turno 7 completo
- **WHEN** se confirma y la API responde `409` con el código `SOLAPAMIENTO_SILLON`
- **THEN** se ve "El sillón ya está ocupado en ese horario."
- **AND** el formulario sigue abierto

### Requirement: Agenda usable en el celular
En pantallas de menos de 768 px la agenda SHALL mostrar los turnos en una sola columna, con filtros y formularios a todo el ancho y controles de al menos 44 × 44 px. En ningún ancho desde 320 px MUST NOT haber desplazamiento horizontal.

#### Scenario: Agenda en el celular
- **WHEN** se abre la agenda de la semana en una pantalla de 390 × 844 px
- **THEN** los turnos se ven en una sola columna
- **AND** la página no tiene desplazamiento horizontal

#### Scenario: Agenda en la computadora
- **WHEN** se abre la agenda de la semana en una pantalla de 1440 × 900 px
- **THEN** los filtros y los turnos se ven sin desplazamiento horizontal
