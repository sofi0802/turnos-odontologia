## ADDED Requirements

### Requirement: La seña en mis turnos
Cada turno de `GET /api/v1/paciente/turnos` SHALL incluir `sena`: `null` si el turno no tiene seña, o un objeto con `estado` (`PENDIENTE`, `PAGADA`, `VENCIDA`, `IMPUTADA`, `DEVUELTA` o `RETENIDA`), `importe`, `venceEn` y `linkPago`. `linkPago` MUST venir solo con la seña pendiente, y la respuesta MUST NOT incluir datos del pago en Mercado Pago.

#### Scenario: Turno con seña pendiente
- **GIVEN** "Paciente Dos" con un turno y su seña pendiente de `4500.00` que vence a las 10:30
- **WHEN** pide `GET /api/v1/paciente/turnos`
- **THEN** ese turno tiene `sena` con `estado` `PENDIENTE`, `importe` `4500.00`, `venceEn` a las 10:30 y un `linkPago`

#### Scenario: Turno con seña pagada
- **GIVEN** "Paciente Dos" con un turno y su seña pagada
- **WHEN** pide `GET /api/v1/paciente/turnos`
- **THEN** ese turno tiene `sena` con `estado` `PAGADA` y sin `linkPago`

#### Scenario: Turno sin seña
- **GIVEN** "Paciente Dos" con un turno sin seña
- **WHEN** pide `GET /api/v1/paciente/turnos`
- **THEN** ese turno tiene `sena` `null`

### Requirement: Nuevo link de pago de la seña
`POST /api/v1/paciente/reservas/{numeroReserva}/sena/link` SHALL pedir a la pasarela un link nuevo para la seña pendiente de un turno propio y devolver `200` con `linkPago`. Si la seña no está pendiente o el turno no tiene seña, la respuesta MUST ser `409` con `SENA_NO_PENDIENTE`. Un turno ajeno o inexistente MUST responder como en "Reservas ajenas o inexistentes". Si la pasarela no responde, la respuesta MUST ser `503` con `PASARELA_NO_DISPONIBLE`.

#### Scenario: Pedir un link nuevo
- **GIVEN** "Paciente Dos" con una seña pendiente sin link
- **WHEN** pide `POST /api/v1/paciente/reservas/{numeroReserva}/sena/link` con el número de reserva de ese turno
- **THEN** la respuesta es `200` con un `linkPago`

#### Scenario: Seña ya pagada
- **GIVEN** "Paciente Dos" con la seña pagada
- **WHEN** pide un link nuevo para ese turno
- **THEN** la respuesta es `409` con `SENA_NO_PENDIENTE`

#### Scenario: Pasarela caída al pedir el link
- **GIVEN** la pasarela simulada responde con error
- **WHEN** "Paciente Dos" pide un link nuevo para su seña pendiente
- **THEN** la respuesta es `503` con `PASARELA_NO_DISPONIBLE`
