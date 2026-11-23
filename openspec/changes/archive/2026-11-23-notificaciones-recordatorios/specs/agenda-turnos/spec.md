# Spec Delta

## ADDED Requirements

### Requirement: Confirmación de un turno reservado
El sistema SHALL permitir que un turno `RESERVADO` pase a `CONFIRMADO` sin cambiar su horario, su sillón, su profesional ni su número de reserva. Un turno en cualquier otro estado MUST NOT poder confirmarse.

#### Scenario: Confirmar un turno reservado
- **GIVEN** un turno `RESERVADO` de "Paciente Uno" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se lo confirma
- **THEN** el turno queda `CONFIRMADO` con el mismo horario, sillón, profesional y número de reserva
- **AND** sigue ocupando la agenda con el mismo margen

#### Scenario: Confirmar un turno que no está reservado
- **GIVEN** un turno `CANCELADO`, `ATENDIDO` o `AUSENTE`
- **WHEN** se intenta confirmarlo
- **THEN** la operación se rechaza y el turno queda sin cambios
