## MODIFIED Requirements

### Requirement: Consultar la agenda
`GET /api/v1/turnos?desde&hasta` SHALL responder `200` con los turnos activos cuyo intervalo se cruza con `[desde, hasta)`, ordenados por inicio, con el nombre del paciente. Acepta los filtros opcionales `profesionalId` y `sillonId`. `desde` y `hasta` son obligatorios. La respuesta MUST NOT incluir el número de reserva ni datos de salud. El alcance depende del rol de la sesión (RN-AG-18): con sesión `ODONTOLOGO`, la respuesta MUST incluir solo los turnos del profesional vinculado a ese usuario, aunque el pedido traiga otro `profesionalId`, que se reemplaza sin error; `sillonId` se aplica sobre esos turnos. Con sesión `ADMIN` o `RECEPCIONISTA`, la respuesta SHALL incluir los turnos de todos los profesionales que cumplan los filtros, aunque el usuario tenga un profesional vinculado.

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
