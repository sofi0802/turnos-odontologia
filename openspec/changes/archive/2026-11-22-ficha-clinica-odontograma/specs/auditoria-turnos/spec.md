# Spec Delta

## ADDED Requirements

### Requirement: Registro de la ausencia del paciente
Al marcar un turno como ausente, el sistema SHALL registrar la acción `AUSENTE` con el id del turno, el usuario de la sesión y el momento, en la misma operación. Si la ausencia se rechaza, MUST NOT quedar ningún registro. El registro no incluye datos de la ficha clínica.

#### Scenario: Se registra la ausencia
- **GIVEN** el usuario `ana.ejemplo`, odontólogo de la Dra. A, con sesión iniciada
- **AND** un turno activo de "Paciente Uno" a las 10:00 con la Dra. A, cuya hora de inicio ya pasó
- **WHEN** lo marca como ausente
- **THEN** existe un registro de auditoría con la acción `AUSENTE`, el id de ese turno y el actor `ana.ejemplo`

#### Scenario: Ausencia rechazada sin registro
- **GIVEN** un turno activo de "Paciente Uno" a las 10:00 con la Dra. A, cuya hora de inicio todavía no llegó
- **WHEN** `ana.ejemplo` lo marca como ausente
- **THEN** la operación se rechaza con `TURNO_NO_INICIADO`
- **AND** no existe ningún registro de auditoría `AUSENTE` para ese turno
