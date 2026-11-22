# turnos-paciente Specification

## Purpose

Permitir que el paciente con sesión vea solo sus propios turnos y los cancele o reprograme online con su número de reserva hasta 24 h antes del inicio, sin poder operar ni descubrir reservas de otros pacientes (US-022, US-024, RN-AG-07, RN-AG-08, RN-RO-08). En los escenarios, con datos ficticios y salvo que digan otra cosa: "Paciente Uno" (DNI `99000001`) y "Paciente Dos" (DNI `99000002`) tienen cuenta de paciente; la Dra. A (sillón habitual: Sillón 1, franjas de lunes a viernes de 08:00 a 13:00) y el Dr. B (sillón habitual: Sillón 2, franjas de lunes a viernes de 10:00 a 18:00) están activos y habilitados para "Consulta" (30 minutos); el Sillón 1 y el Sillón 2 están activos; el margen es de 10 minutos; el momento actual es el martes 2026-12-01 a las 08:00, en `America/Argentina/Buenos_Aires`. Todo `POST` lleva un token CSRF válido.

## Requirements

### Requirement: Consultar mis turnos
`GET /api/v1/paciente/turnos` SHALL responder `200` con los turnos del paciente de la sesión, en cualquier estado y ordenados por inicio, cada uno con `numeroReserva`, `inicio`, `fin`, `estado`, la prestación y el profesional (`id` y `nombre`). MUST NOT incluir turnos de otros pacientes, el sillón ni datos de salud, y MUST ignorar cualquier identificador de paciente del pedido.

#### Scenario: Solo los turnos propios
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** "Paciente Uno" tiene turnos el 2026-12-02 a las 10:00 y el 2026-12-03 a las 10:00, y "Paciente Dos" tiene uno el 2026-12-02 a las 11:00
- **WHEN** pide `GET /api/v1/paciente/turnos`
- **THEN** la respuesta es `200` con dos turnos, los de "Paciente Uno", ordenados por inicio
- **AND** la respuesta no contiene datos ni el número de reserva del turno de "Paciente Dos"

#### Scenario: Intento de ver los turnos de otro paciente
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** "Paciente Dos" tiene un turno el 2026-12-02 a las 11:00
- **WHEN** pide `GET /api/v1/paciente/turnos?pacienteId={id}` con el id de "Paciente Dos"
- **THEN** la respuesta es `200` y no contiene el turno de "Paciente Dos"

### Requirement: Indicador de modificación online
Cada turno de la lista SHALL incluir `modificableOnline`, verdadero solo si el turno está activo y faltan 24 h o más para su inicio, para que el paciente sepa si puede cancelarlo o reprogramarlo sin llamar al consultorio.

#### Scenario: Turnos dentro y fuera del plazo
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** "Paciente Uno" tiene un turno activo el 2026-12-02 a las 09:00 y otro el 2026-12-01 a las 10:00
- **WHEN** pide `GET /api/v1/paciente/turnos`
- **THEN** el turno del 2026-12-02 tiene `modificableOnline` verdadero
- **AND** el turno del 2026-12-01 tiene `modificableOnline` falso

### Requirement: Cancelar online con el número de reserva
`POST /api/v1/paciente/reservas/{numeroReserva}/cancelar` SHALL cancelar el turno activo del paciente de la sesión que tiene ese número de reserva, y responder `200` con el turno en estado `CANCELADO`. El horario MUST quedar libre de inmediato (RN-AG-08).

#### Scenario: Cancelación con anticipación
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** un turno activo de "Paciente Uno" el 2026-12-02 de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** pide cancelar ese turno con su número de reserva
- **THEN** la respuesta es `200` con `estado` `CANCELADO`
- **AND** la disponibilidad de "Consulta" del 2026-12-02 con la Dra. A vuelve a incluir el horario de las 10:00

### Requirement: Plazo de 24 horas para el paciente
El paciente MUST poder cancelar o reprogramar online solo si faltan 24 h o más para el inicio del turno. Con menos plazo, el sistema MUST responder `409` con el código `CANCELACION_FUERA_DE_PLAZO` y un detalle que indique comunicarse con el consultorio, sin modificar el turno.

#### Scenario: Cancelación exactamente 24 h antes
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:00
- **AND** "Paciente Uno" con sesión iniciada y un turno activo suyo el 2026-12-02 a las 10:00
- **WHEN** pide cancelar ese turno con su número de reserva
- **THEN** la respuesta es `200` con `estado` `CANCELADO`

#### Scenario: Cancelación a 23 h rechazada
- **GIVEN** que el momento actual es el 2026-12-01 a las 11:00
- **AND** "Paciente Uno" con sesión iniciada y un turno activo suyo el 2026-12-02 a las 10:00
- **WHEN** pide cancelar ese turno con su número de reserva
- **THEN** la respuesta es `409` con el código `CANCELACION_FUERA_DE_PLAZO`
- **AND** el turno sigue en estado `RESERVADO`
- **AND** no existe un registro de auditoría `CANCELAR` para ese turno

#### Scenario: Reprogramación a 23 h rechazada
- **GIVEN** que el momento actual es el 2026-12-01 a las 11:00
- **AND** "Paciente Uno" con sesión iniciada y un turno activo suyo el 2026-12-02 a las 10:00 con la Dra. A
- **WHEN** pide reprogramar ese turno al 2026-12-03 a las 10:00 con la Dra. A
- **THEN** la respuesta es `409` con el código `CANCELACION_FUERA_DE_PLAZO`
- **AND** el turno original sigue en estado `RESERVADO` y no se crea ningún turno

### Requirement: Reservas ajenas o inexistentes
Cuando el número de reserva no existe o es de un turno de otro paciente, cancelar y reprogramar MUST responder lo mismo: `404` con el código `RESERVA_INEXISTENTE`, sin datos del turno y sin modificarlo. Así no se puede saber si un número de reserva ajeno existe.

#### Scenario: Número de reserva de otro paciente
- **GIVEN** "Paciente Uno" con sesión iniciada
- **AND** un turno activo de "Paciente Dos" el 2026-12-02 a las 11:00
- **WHEN** "Paciente Uno" pide cancelar con el número de reserva del turno de "Paciente Dos"
- **THEN** la respuesta es `404` con el código `RESERVA_INEXISTENTE`
- **AND** el turno de "Paciente Dos" sigue en estado `RESERVADO`
- **AND** la respuesta no contiene datos de "Paciente Dos" ni del turno

#### Scenario: Número de reserva inexistente
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide reprogramar con el número de reserva `ZZZZ-ZZZZ`, que no corresponde a ningún turno, al 2026-12-03 a las 10:00
- **THEN** la respuesta es `404` con el código `RESERVA_INEXISTENTE`

### Requirement: Turno propio que ya no está activo
Cancelar o reprogramar online un turno propio que no está activo MUST responderse `409` con el código `TURNO_NO_ACTIVO`.

#### Scenario: Cancelar un turno ya cancelado
- **GIVEN** "Paciente Uno" con sesión iniciada y un turno `CANCELADO` suyo el 2026-12-02 a las 10:00
- **WHEN** pide cancelar ese turno con su número de reserva
- **THEN** la respuesta es `409` con el código `TURNO_NO_ACTIVO`

### Requirement: Reprogramar online
`POST /api/v1/paciente/reservas/{numeroReserva}/reprogramar` con `inicio` y, opcionalmente, `profesionalId` SHALL cancelar el turno original y crear uno nuevo de la misma prestación con las reglas y la asignación de la reserva online (sin `profesionalId`, "sin preferencia"), y responder `200` con el turno nuevo y un número de reserva nuevo. El original no cuenta como conflicto, y si el nuevo se rechaza el original no cambia.

#### Scenario: Reprogramación a otro horario
- **GIVEN** "Paciente Uno" con sesión iniciada y un turno activo suyo el 2026-12-02 de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** pide reprogramarlo al 2026-12-02 a las 11:00 con la Dra. A
- **THEN** la respuesta es `200` con un turno de 11:00 a 11:30 en estado `RESERVADO` y un número de reserva distinto del original
- **AND** el turno original queda en estado `CANCELADO`

#### Scenario: Reprogramación sobre el propio horario
- **GIVEN** "Paciente Uno" con sesión iniciada y un turno activo suyo el 2026-12-02 de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** pide reprogramarlo al 2026-12-02 a las 10:15 con la Dra. A
- **THEN** la respuesta es `200` con un turno de 10:15 a 10:45

#### Scenario: Reprogramación sin preferencia
- **GIVEN** "Paciente Uno" con sesión iniciada y un turno activo suyo el 2026-12-02 a las 10:00 con la Dra. A
- **AND** la Dra. A tiene otro turno activo el 2026-12-02 a las 08:00 y el Dr. B no tiene turnos ese día
- **WHEN** pide reprogramarlo al 2026-12-02 a las 11:00 sin profesional
- **THEN** la respuesta es `200` con el profesional Dr. B

#### Scenario: Reprogramación a un horario no disponible
- **GIVEN** "Paciente Uno" con sesión iniciada y un turno activo suyo el 2026-12-02 a las 10:00 con la Dra. A
- **AND** un turno activo de "Paciente Dos" el 2026-12-02 de 11:00 a 11:30 con la Dra. A en el Sillón 1
- **WHEN** pide reprogramarlo al 2026-12-02 a las 11:00 con la Dra. A
- **THEN** la respuesta es `409` con el código `SIN_DISPONIBILIDAD`
- **AND** el turno original sigue en estado `RESERVADO` a las 10:00
- **AND** no se crea ningún turno nuevo

### Requirement: Validación de la reprogramación online
Reprogramar online MUST responder `422` con el código `DATOS_INVALIDOS` y la lista de campos con error, sin modificar el turno, cuando falta `inicio`, cuando no está en ISO-8601 con zona horaria, o cuando `profesionalId` no corresponde a un profesional existente.

#### Scenario: Reprogramación sin inicio
- **GIVEN** "Paciente Uno" con sesión iniciada y un turno activo suyo el 2026-12-02 a las 10:00
- **WHEN** pide reprogramarlo sin `inicio`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `inicio`

### Requirement: Rutas de turnos solo para el paciente
Consultar, cancelar y reprogramar online MUST exigir una sesión con rol `PACIENTE`: sin sesión responden `401 NO_AUTENTICADO` y con una sesión del personal `403 ACCESO_DENEGADO`, sin modificar ningún turno.

#### Scenario: Mis turnos sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** pide `GET /api/v1/paciente/turnos`
- **THEN** la respuesta es `401` con el código `NO_AUTENTICADO`

#### Scenario: El personal intenta cancelar por la ruta del paciente
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo de "Paciente Uno" el 2026-12-02 a las 10:00
- **WHEN** pide cancelar ese turno por `POST /api/v1/paciente/reservas/{numeroReserva}/cancelar`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el turno sigue en estado `RESERVADO`
