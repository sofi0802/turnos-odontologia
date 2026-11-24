## ADDED Requirements

### Requirement: Botón de sobreturno
Con sesión `ADMIN`, `RECEPCIONISTA` u `ODONTOLOGO`, la agenda SHALL mostrar el botón "Sobreturno" junto a "Nuevo turno", que abre el formulario de sobreturno. Una sesión `PACIENTE` MUST NOT verlo.

#### Scenario: El paciente no ve el sobreturno
- **GIVEN** una sesión `PACIENTE`
- **WHEN** navega a la agenda del personal
- **THEN** no ve el botón "Sobreturno" ni la acción "Extender"

### Requirement: Formulario de sobreturno
El formulario SHALL pedir paciente (buscador de `frontend-buscador-pacientes`), prestación, profesional habilitado para esa prestación, fecha, hora y motivo, y MUST NOT pedir sillón. Con sesión `ODONTOLOGO`, el profesional MUST quedar fijo en el de la sesión. El motivo SHALL aceptar hasta 500 caracteres y avisar que no lleva datos de salud.

#### Scenario: El sobreturno no pide sillón
- **WHEN** se abre el formulario de sobreturno
- **THEN** no hay ningún campo "Sillón"
- **AND** el formulario explica que el sillón lo asigna el sistema

#### Scenario: El odontólogo carga un sobreturno con su profesional
- **GIVEN** la sesión de `ana.ejemplo`, vinculada a la Dra. Ana Ejemplo
- **WHEN** abre el formulario de sobreturno
- **THEN** el profesional es "Ana Ejemplo" y no se puede cambiar
- **AND** al enviarlo el pedido lleva `profesionalId` 1

#### Scenario: El motivo pide no escribir datos de salud
- **WHEN** se abre el formulario de sobreturno
- **THEN** el campo "Motivo" tiene la ayuda "Motivo administrativo. No escribas datos de salud."
- **AND** acepta hasta 500 caracteres

### Requirement: Enviar un sobreturno
Con los datos completos, el formulario SHALL pedir `POST /api/v1/turnos/sobreturno` con `pacienteId`, `profesionalId`, `prestacionId`, `inicio` en la zona horaria del consultorio y `motivo` sin espacios al principio ni al final. Con datos faltantes MUST NOT enviar nada. Con `201` MUST cerrar el formulario, mostrar "Sobreturno cargado en <sillón>. Número de reserva: <numeroReserva>." y recargar la agenda.

#### Scenario: Recepción carga un sobreturno
- **GIVEN** la sesión de `recepcion` y la agenda del día abierta el 2026-12-01
- **WHEN** elige "Sobreturno", elige "Uno, Paciente · DNI 99000001", "Consulta", "Ana Ejemplo", la fecha 2026-12-01, la hora 10:15 y escribe el motivo "Urgencia por dolor, pedido de recepción", y envía
- **THEN** se pide `POST /api/v1/turnos/sobreturno` con `pacienteId` 1, `prestacionId` 1, `profesionalId` 1, `inicio` `2026-12-01T10:15:00-03:00` y ese motivo
- **AND** al responder `201` con `sillonId` 2 y `numeroReserva` `R-0042` se ve "Sobreturno cargado en Sillón 2. Número de reserva: R-0042."
- **AND** se vuelve a pedir la agenda

#### Scenario: Motivo obligatorio
- **GIVEN** el formulario de sobreturno completo con el motivo vacío o con solo espacios
- **WHEN** se envía
- **THEN** no se envía ningún pedido a `/api/v1/turnos/sobreturno`
- **AND** el campo "Motivo" muestra "Escribí el motivo del sobreturno."

#### Scenario: Faltan datos del sobreturno
- **GIVEN** el formulario de sobreturno sin paciente elegido o sin hora
- **WHEN** se envía
- **THEN** no se envía ningún pedido a `/api/v1/turnos/sobreturno`
- **AND** cada campo vacío muestra su mensaje

### Requirement: Acción Extender
Con sesión `ADMIN`, `RECEPCIONISTA` u `ODONTOLOGO`, cada turno `RESERVADO` o `CONFIRMADO` que no es sobreturno SHALL ofrecer "Extender", en la vista del día y en el detalle de la semana. Un sobreturno o un turno que no está activo MUST NOT ofrecerlo.

#### Scenario: Un sobreturno no ofrece extender
- **GIVEN** el turno 8, un sobreturno `RESERVADO` de 11:00 a 11:30
- **WHEN** se abre la agenda del día
- **THEN** el turno 8 no ofrece "Extender"

#### Scenario: Un turno cancelado no ofrece extender
- **GIVEN** el turno 9 `CANCELADO`
- **WHEN** se abre la agenda del día
- **THEN** el turno 9 no ofrece "Extender"

#### Scenario: Extender desde la semana
- **GIVEN** la vista de la semana con el turno 7 `RESERVADO`
- **WHEN** se abre su detalle
- **THEN** el detalle ofrece "Extender"
- **AND** al elegirlo se cierra el detalle y se abre el formulario de extender

### Requirement: Extender un turno
"Extender" SHALL abrir un formulario con el horario actual, el nuevo fin (por defecto, 15 minutos después del fin actual) y el motivo. El nuevo fin MUST ser posterior al fin actual y del mismo día. Al enviarlo SHALL pedir `POST /api/v1/turnos/{id}/extender` con `fin` en la zona del consultorio y `motivo`. Con `200` MUST cerrar el formulario, mostrar "Turno extendido hasta las <HH:mm>." y recargar la agenda.

#### Scenario: Recepción extiende un turno
- **GIVEN** la sesión de `recepcion` y el turno 7 `RESERVADO` de 10:00 a 10:30 el 2026-12-01
- **WHEN** elige "Extender" en ese turno, deja el nuevo fin en 10:45, escribe el motivo "Se demoró la atención" y envía
- **THEN** se pide `POST /api/v1/turnos/7/extender` con `fin` `2026-12-01T10:45:00-03:00` y ese motivo
- **AND** al responder `200` se ve "Turno extendido hasta las 10:45."
- **AND** se vuelve a pedir la agenda

#### Scenario: El nuevo fin no es posterior al actual
- **GIVEN** el formulario de extender el turno 7, de 10:00 a 10:30
- **WHEN** se elige 10:30 o una hora anterior como nuevo fin y se envía
- **THEN** no se envía ningún pedido
- **AND** el campo "Nuevo fin" muestra "Elegí una hora posterior a las 10:30."

#### Scenario: Motivo obligatorio al extender
- **GIVEN** el formulario de extender con el motivo vacío
- **WHEN** se envía
- **THEN** no se envía ningún pedido
- **AND** el campo "Motivo" muestra "Escribí el motivo de la extensión."

### Requirement: Choque al extender un turno
Si la API rechaza una extensión con `409` y el código `SOLAPAMIENTO_SILLON`, `SOLAPAMIENTO_PROFESIONAL` o `MARGEN_INSUFICIENTE`, el formulario MUST quedar abierto, mostrar "La extensión choca con otro turno. No se movió nada." y ofrecer "Cargar como sobreturno", que abre el sobreturno con el paciente, la prestación, el profesional, la fecha, la hora del fin actual y el motivo del turno. El sistema MUST NOT reprogramar nada sin confirmación.

#### Scenario: La extensión choca con el turno siguiente
- **GIVEN** el turno 7 de 10:00 a 10:30 y el turno 10 de 10:40 a 11:10 en el mismo sillón, los dos en la agenda cargada
- **WHEN** se pide extender el turno 7 hasta las 11:00 y la API responde `409` con el código `SOLAPAMIENTO_SILLON`
- **THEN** se ve "La extensión choca con otro turno. No se movió nada."
- **AND** el formulario ofrece "Reprogramar el turno de las 10:40" y "Cargar como sobreturno"

#### Scenario: Cargar la extensión como sobreturno
- **GIVEN** el choque al extender el turno 7 de "Uno, Paciente", "Consulta", con la Dra. Ana Ejemplo, de 10:00 a 10:30 el 2026-12-01, con el motivo "Se demoró la atención"
- **WHEN** se elige "Cargar como sobreturno"
- **THEN** se abre el formulario de sobreturno con ese paciente, "Consulta", "Ana Ejemplo", la fecha 2026-12-01, la hora 10:30 y el motivo "Se demoró la atención"

### Requirement: Reprogramar el turno que choca
Ante ese choque, si la agenda cargada tiene un turno activo del mismo sillón o del mismo profesional que empieza entre el fin actual y el nuevo fin, el formulario SHALL ofrecer "Reprogramar el turno de las <HH:mm>", que abre la reprogramación de ese turno. Si no lo encuentra, MUST NOT ofrecerlo.

#### Scenario: Reprogramar el turno que choca
- **GIVEN** el turno 7 de 10:00 a 10:30 y el turno 10 de 10:40 a 11:10 en el mismo sillón, y la API rechazó extender el turno 7 hasta las 11:00 con `SOLAPAMIENTO_SILLON`
- **WHEN** se elige "Reprogramar el turno de las 10:40"
- **THEN** se cierra el formulario de extender y se abre la reprogramación del turno 10

#### Scenario: Choque sin turno identificable
- **GIVEN** el turno 7 de 10:00 a 10:30 y ningún otro turno cargado entre 10:30 y 11:00
- **WHEN** se pide extenderlo hasta las 11:00 y la API responde `409` con el código `MARGEN_INSUFICIENTE`
- **THEN** se ve "La extensión choca con otro turno. No se movió nada."
- **AND** el formulario ofrece "Cargar como sobreturno" y no ofrece reprogramar

### Requirement: Mensajes de los rechazos de sobreturno y extensión
Ante un `409` del sobreturno o de la extensión, el formulario MUST quedar abierto con lo cargado y mostrar el mensaje del código: `SIN_SILLON_DISPONIBLE` → "No hay ningún sillón libre en ese horario. Probá con otra hora."; `TURNO_NO_EXTENSIBLE` → "Un sobreturno no se puede extender."; los demás códigos, con los mensajes de "Mensajes por código de rechazo". Un `422` SHALL marcar los campos que informa la API (`inicio`, `fin` o `motivo`).

#### Scenario: Sin sillón libre para el sobreturno
- **GIVEN** el formulario de sobreturno completo
- **WHEN** se envía y la API responde `409` con el código `SIN_SILLON_DISPONIBLE`
- **THEN** se ve "No hay ningún sillón libre en ese horario. Probá con otra hora."
- **AND** el formulario sigue abierto con los datos cargados

#### Scenario: Turno que no se puede extender
- **GIVEN** el formulario de extender un turno
- **WHEN** se envía y la API responde `409` con el código `TURNO_NO_EXTENSIBLE`
- **THEN** se ve "Un sobreturno no se puede extender."

#### Scenario: Turno que ya no está activo
- **GIVEN** el formulario de extender un turno que otra persona canceló
- **WHEN** se envía y la API responde `409` con el código `TURNO_NO_ACTIVO`
- **THEN** se ve "El turno ya no está activo."

#### Scenario: Motivo rechazado por la API
- **GIVEN** el formulario de sobreturno completo
- **WHEN** se envía y la API responde `422` con un error en el campo `motivo`
- **THEN** el campo "Motivo" muestra el mensaje de la API
