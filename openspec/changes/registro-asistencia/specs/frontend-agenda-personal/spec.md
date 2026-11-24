## ADDED Requirements

### Requirement: Etiquetas de asistencia
La agenda SHALL mostrar en cada turno su asistencia con una etiqueta de texto, para todos los roles del personal: "Atendido" para `ATENDIDO`, "Ausente" para `AUSENTE`, "Llegó HH:mm" para un turno activo con `llegadaEn`, y "Sin registrar" para un turno activo sin llegada cuyo fin ya pasó. La diferencia MUST NOT depender solo del color. Los turnos `ATENDIDO` y `AUSENTE` MUST NOT ofrecer "Cancelar turno" ni "Reprogramar".

#### Scenario: Turnos con asistencia registrada
- **GIVEN** que el momento actual es el 2026-12-01 a las 11:00
- **AND** la API devuelve un turno `ATENDIDO` de 09:00 a 09:30, uno `AUSENTE` de 09:30 a 10:00 y uno `RESERVADO` de 10:40 a 11:10 con `llegadaEn` a las 10:35
- **WHEN** se abre la agenda del día
- **THEN** los turnos muestran "Atendido", "Ausente" y "Llegó 10:35"
- **AND** los turnos "Atendido" y "Ausente" no ofrecen "Cancelar turno" ni "Reprogramar"

#### Scenario: Turno sin registrar en la agenda
- **GIVEN** que el momento actual es el 2026-12-01 a las 11:00
- **AND** la API devuelve un turno `RESERVADO` de 10:00 a 10:30 sin `llegadaEn`
- **WHEN** se abre la agenda del día
- **THEN** el turno muestra "Sin registrar"

### Requirement: Acciones de asistencia de la recepción
Con sesión `RECEPCIONISTA`, la agenda SHALL ofrecer "Llegó" en un turno activo sin llegada desde el día del turno, "Ausente" en un turno activo sin llegada (habilitado 15 minutos después del inicio; antes, deshabilitado con "Desde las HH:mm") y "Deshacer ausencia" en un turno `AUSENTE` que no terminó. Cada botón envía su `POST` y con `200` recarga la agenda. Con sesión `ADMIN` u `ODONTOLOGO` MUST NOT ofrecerlos.

#### Scenario: Registrar la llegada
- **GIVEN** la sesión de `recepcion` y el momento actual el 2026-12-01 a las 10:05
- **AND** el turno 7 `RESERVADO` de 10:00 a 10:30 sin `llegadaEn`
- **WHEN** se elige "Llegó" en ese turno
- **THEN** se pide `POST /api/v1/turnos/7/llegada`
- **AND** al responder `200` se vuelve a pedir la agenda

#### Scenario: Ausente antes de la tolerancia
- **GIVEN** la sesión de `recepcion` y el momento actual el 2026-12-01 a las 10:10
- **AND** el turno 7 `RESERVADO` de 10:00 a 10:30 sin `llegadaEn`
- **WHEN** se abre la agenda del día
- **THEN** el botón "Ausente" del turno 7 está deshabilitado y dice "Desde las 10:15"

#### Scenario: Marcar ausente
- **GIVEN** la sesión de `recepcion` y el momento actual el 2026-12-01 a las 10:20
- **AND** el turno 7 `RESERVADO` de 10:00 a 10:30 sin `llegadaEn`
- **WHEN** se elige "Ausente" en ese turno
- **THEN** se pide `POST /api/v1/turnos/7/ausencia`

#### Scenario: Deshacer la ausencia
- **GIVEN** la sesión de `recepcion` y el momento actual el 2026-12-01 a las 10:22
- **AND** el turno 7 `AUSENTE` de 10:00 a 10:30
- **WHEN** se elige "Deshacer ausencia" en ese turno
- **THEN** se pide `POST /api/v1/turnos/7/deshacer-ausencia`

#### Scenario: El odontólogo no ve las acciones de asistencia
- **GIVEN** la sesión de `ana.ejemplo` y el momento actual el 2026-12-01 a las 10:20
- **AND** el turno 7 `RESERVADO` de 10:00 a 10:30 con la Dra. Ana Ejemplo
- **WHEN** se abre la agenda del día
- **THEN** el turno 7 no ofrece "Llegó", "Ausente" ni "Deshacer ausencia"

### Requirement: Mensajes de los rechazos de asistencia
Ante un rechazo de "Llegó", "Ausente" o "Deshacer ausencia", la agenda MUST mostrar el mensaje en español de su código y volver a pedir la agenda. Al deshacer, cualquier rechazo por superposición o margen muestra el mismo mensaje.

#### Scenario: Deshacer con el horario ocupado
- **GIVEN** la sesión de `recepcion` y el turno 7 `AUSENTE` de 10:00 a 10:30
- **WHEN** se elige "Deshacer ausencia" y la API responde `409` con el código `SOLAPAMIENTO_SILLON`
- **THEN** se ve "El horario ya se ocupó. Dale otro turno al paciente."
- **AND** se vuelve a pedir la agenda

#### Scenario: Llegada ya registrada en otra pantalla
- **GIVEN** la sesión de `recepcion` y el turno 7 `RESERVADO` sin `llegadaEn` en la agenda
- **WHEN** se elige "Llegó" y la API responde `409` con el código `LLEGADA_REGISTRADA`
- **THEN** se ve "El paciente ya tiene la llegada registrada."

#### Scenario: Rechazo AUSENCIA_ANTES_DE_TOLERANCIA
- **GIVEN** la sesión de `recepcion` y el turno 7 `RESERVADO` en la agenda
- **WHEN** se elige "Ausente" y la API responde `409` con el código `AUSENCIA_ANTES_DE_TOLERANCIA`
- **THEN** se ve "Todavía no pasaron 15 minutos desde el inicio del turno."

#### Scenario: Rechazo LLEGADA_ANTICIPADA
- **GIVEN** la sesión de `recepcion` y el turno 7 `RESERVADO` en la agenda
- **WHEN** se elige "Llegó" y la API responde `409` con el código `LLEGADA_ANTICIPADA`
- **THEN** se ve "La llegada se registra el día del turno."

#### Scenario: Rechazo TURNO_NO_AUSENTE
- **GIVEN** la sesión de `recepcion` y el turno 7 `AUSENTE` en la agenda
- **WHEN** se elige "Deshacer ausencia" y la API responde `409` con el código `TURNO_NO_AUSENTE`
- **THEN** se ve "El turno no está marcado como ausente."

#### Scenario: Rechazo TURNO_TERMINADO
- **GIVEN** la sesión de `recepcion` y el turno 7 `AUSENTE` en la agenda
- **WHEN** se elige "Deshacer ausencia" y la API responde `409` con el código `TURNO_TERMINADO`
- **THEN** se ve "El turno ya terminó."
