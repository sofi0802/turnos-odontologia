## MODIFIED Requirements

### Requirement: Consultar la agenda
`GET /api/v1/turnos?desde&hasta` SHALL responder `200` con los turnos activos, `ATENDIDO` y `AUSENTE` (no los `CANCELADO`) cuyo intervalo se cruza con `[desde, hasta)`, con su `estado` y la hora de llegada si se registró (`llegadaEn`), ordenados por inicio, con el nombre del paciente. Acepta los filtros opcionales `profesionalId` y `sillonId`. `desde` y `hasta` son obligatorios. La respuesta MUST NOT incluir el número de reserva ni datos de salud. El alcance depende del rol de la sesión (RN-AG-18): con sesión `ODONTOLOGO`, la respuesta MUST incluir solo los turnos del profesional vinculado a ese usuario, aunque el pedido traiga otro `profesionalId`, que se reemplaza sin error; `sillonId` se aplica sobre esos turnos. Con sesión `ADMIN` o `RECEPCIONISTA`, la respuesta SHALL incluir los turnos de todos los profesionales que cumplan los filtros, aunque el usuario tenga un profesional vinculado.

#### Scenario: La agenda muestra solo los turnos activos
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada
- **AND** un turno activo a las 10:00 y un turno `CANCELADO` a las 11:00, los dos con la Dra. A
- **WHEN** pide `GET /api/v1/turnos` desde las 00:00 hasta las 23:59 del 2026-12-01
- **THEN** la respuesta es `200` con un solo turno, el de las 10:00
- **AND** el turno no incluye el número de reserva

#### Scenario: Filtro por profesional
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** un turno activo a las 10:00 con la Dra. A y otro a las 10:00 con el Dr. B
- **WHEN** pide `GET /api/v1/turnos` del 2026-12-01 con `profesionalId` de la Dra. A
- **THEN** la respuesta es `200` con un solo turno, el de la Dra. A

#### Scenario: Falta el período
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/turnos` sin `desde`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

#### Scenario: El odontólogo sin filtro recibe solo sus turnos
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada, vinculado a la Dra. A
- **AND** un turno activo a las 10:00 con la Dra. A en el Sillón 1 y otro a las 10:00 con el Dr. B en el Sillón 2
- **WHEN** pide `GET /api/v1/turnos` del 2026-12-01 sin `profesionalId` ni `sillonId`
- **THEN** la respuesta es `200` con un solo turno, el de la Dra. A

#### Scenario: El odontólogo pide la agenda de otro profesional
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada, vinculado a la Dra. A
- **AND** un turno activo a las 10:00 con la Dra. A y otro a las 11:00 con el Dr. B
- **WHEN** pide `GET /api/v1/turnos` del 2026-12-01 con `profesionalId` del Dr. B
- **THEN** la respuesta es `200` con un solo turno, el de la Dra. A
- **AND** la respuesta no incluye ningún turno del Dr. B

#### Scenario: El odontólogo filtra por un sillón compartido
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada, vinculado a la Dra. A
- **AND** un turno activo a las 09:00 con la Dra. A en el Sillón 1 y otro a las 11:00 con el Dr. B en el Sillón 1
- **WHEN** pide `GET /api/v1/turnos` del 2026-12-01 con `sillonId` del Sillón 1
- **THEN** la respuesta es `200` con un solo turno, el de las 09:00 con la Dra. A

#### Scenario: Recepción y administración ven a todos los profesionales
- **GIVEN** un turno activo a las 10:00 con la Dra. A y otro a las 10:00 con el Dr. B
- **AND** el usuario `admin` vinculado al Dr. B
- **WHEN** `recepcion` y luego `admin` piden `GET /api/v1/turnos` del 2026-12-01 sin filtros
- **THEN** cada respuesta es `200` con los dos turnos

#### Scenario: La agenda incluye los atendidos y los ausentes
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **AND** el 2026-12-01 con la Dra. A: un turno `ATENDIDO` a las 09:00 con la llegada a las 08:55, uno `AUSENTE` a las 10:00 y uno `CANCELADO` a las 11:00
- **WHEN** pide `GET /api/v1/turnos` del 2026-12-01
- **THEN** la respuesta es `200` con los turnos de las 09:00 (`ATENDIDO`, `llegadaEn` 08:55) y de las 10:00 (`AUSENTE`)
- **AND** no incluye el de las 11:00

## ADDED Requirements

### Requirement: Registrar la asistencia por la API
La recepción SHALL registrar la asistencia con `POST /api/v1/turnos/{id}/llegada`, `POST /api/v1/turnos/{id}/ausencia` y `POST /api/v1/turnos/{id}/deshacer-ausencia`, sin cuerpo. Cada uno responde `200` con el turno actualizado. Los rechazos de negocio responden `409` con su código estable, un turno inexistente `404` con `TURNO_INEXISTENTE`, y una sesión que no es `RECEPCIONISTA`, `403` con `ACCESO_DENEGADO`.

#### Scenario: La recepción registra la llegada
- **GIVEN** el usuario `recepcion` con sesión iniciada y el momento actual el 2026-12-01 a las 10:05
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A
- **WHEN** pide `POST /api/v1/turnos/{id}/llegada`
- **THEN** la respuesta es `200` con el `estado` `RESERVADO` y `llegadaEn` a las 10:05

#### Scenario: Ausencia antes de la tolerancia por la API
- **GIVEN** el usuario `recepcion` con sesión iniciada y el momento actual el 2026-12-01 a las 10:10
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A
- **WHEN** pide `POST /api/v1/turnos/{id}/ausencia`
- **THEN** la respuesta es `409` con el código `AUSENCIA_ANTES_DE_TOLERANCIA`

#### Scenario: El odontólogo usa la ausencia de la agenda
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada y el momento actual el 2026-12-01 a las 10:20
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A
- **WHEN** pide `POST /api/v1/turnos/{id}/ausencia`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el turno sigue activo

#### Scenario: Deshacer la ausencia de un turno inexistente
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `POST /api/v1/turnos/999999/deshacer-ausencia`
- **THEN** la respuesta es `404` con el código `TURNO_INEXISTENTE`
