## ADDED Requirements

### Requirement: Causa de la cancelación de un turno con seña
`POST /api/v1/turnos/{id}/cancelar` SHALL aceptar el campo opcional `causa`, con los valores `PACIENTE` o `CONSULTORIO`, y guardarlo en la auditoría de la cancelación. Si el turno tiene una seña pagada (spec `senas`), la `causa` MUST ser obligatoria: sin ella, la respuesta es `422` con `DATOS_INVALIDOS` y el campo `causa`, y el turno no se cancela. Un turno sin seña pagada MUST poder cancelarse sin `causa`, como antes.

#### Scenario: Cancelar un turno con seña indicando la causa
- **GIVEN** un turno de "Paciente Dos" con la seña pagada
- **WHEN** la `RECEPCIONISTA` lo cancela con su DNI, su número de reserva y `causa` `CONSULTORIO`
- **THEN** la respuesta es `200`, el turno está `CANCELADO` y la auditoría de la cancelación tiene la causa `CONSULTORIO`

#### Scenario: Falta la causa de un turno con seña
- **GIVEN** un turno de "Paciente Dos" con la seña pagada
- **WHEN** la `RECEPCIONISTA` lo cancela sin `causa`
- **THEN** la respuesta es `422` con `DATOS_INVALIDOS` y el campo `causa`, y el turno sigue `RESERVADO`

#### Scenario: Turno sin seña sin causa
- **GIVEN** un turno de "Paciente Dos" sin seña
- **WHEN** la `RECEPCIONISTA` lo cancela sin `causa`
- **THEN** la respuesta es `200` y el turno está `CANCELADO`

#### Scenario: Causa inválida
- **WHEN** la `RECEPCIONISTA` cancela un turno con `causa` `OTRA`
- **THEN** la respuesta es `422` con `DATOS_INVALIDOS` y el campo `causa`
