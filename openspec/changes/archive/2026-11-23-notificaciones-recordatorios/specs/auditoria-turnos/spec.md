# Spec Delta

## ADDED Requirements

### Requirement: Registro de la confirmación de un turno
El sistema SHALL registrar la confirmación de un turno con la acción `CONFIRMAR`, el id del turno, el actor `paciente-token` y el momento en que ocurrió. MUST NOT guardar el token en la auditoría.

#### Scenario: Se registra la confirmación
- **GIVEN** un turno `RESERVADO` de "Paciente Uno" con un token sin usar
- **WHEN** el paciente confirma con ese token
- **THEN** existe un registro de auditoría con la acción `CONFIRMAR`, el id de ese turno y el actor `paciente-token`
- **AND** ningún campo del registro contiene el token

#### Scenario: Confirmación rechazada sin registro
- **GIVEN** un turno cancelado de "Paciente Uno" con un token sin usar
- **WHEN** se intenta confirmar con ese token y la operación se rechaza
- **THEN** no existe ningún registro `CONFIRMAR` para ese turno
