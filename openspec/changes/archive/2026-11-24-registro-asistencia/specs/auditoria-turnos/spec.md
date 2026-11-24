## MODIFIED Requirements

### Requirement: Registro de la ausencia del paciente
Al marcar un turno como ausente, el sistema SHALL registrar la acción `AUSENTE` con el id del turno, el usuario de la sesión y el momento, en la misma operación. Si la ausencia se rechaza, MUST NOT quedar ningún registro. El registro no incluye datos de la ficha clínica.

#### Scenario: Se registra la ausencia
- **GIVEN** el usuario `ana.ejemplo`, odontólogo de la Dra. A, con sesión iniciada
- **AND** un turno activo de "Paciente Uno" a las 10:00 con la Dra. A, cuya tolerancia de 15 minutos ya pasó
- **WHEN** lo marca como ausente
- **THEN** existe un registro de auditoría con la acción `AUSENTE`, el id de ese turno y el actor `ana.ejemplo`

#### Scenario: Ausencia rechazada sin registro
- **GIVEN** un turno activo de "Paciente Uno" a las 10:00 con la Dra. A, cuya hora de inicio todavía no llegó
- **WHEN** `ana.ejemplo` lo marca como ausente
- **THEN** la operación se rechaza con `AUSENCIA_ANTES_DE_TOLERANCIA`
- **AND** no existe ningún registro de auditoría `AUSENTE` para ese turno

## ADDED Requirements

### Requirement: Registro de la asistencia
El sistema SHALL registrar en la auditoría, en la misma operación, la acción `LLEGADA` al registrar la llegada, `DESHACER_AUSENCIA` al deshacer una ausencia y `ATENDIDO` cuando el turno pasa a atendido, con el id del turno, el actor y el momento. El actor es el usuario de la sesión, salvo el paso automático a `ATENDIDO`, cuyo actor es `sistema`. Si la operación se rechaza, MUST NOT quedar ningún registro. La marca de ausencia desde la agenda registra `AUSENTE` igual que desde la ficha.

#### Scenario: Se registran la llegada y el paso a atendido
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** `recepcion` registra la llegada a las 10:05 y después corre el proceso programado a las 10:41
- **THEN** existe un registro `LLEGADA` con el actor `recepcion`
- **AND** existe un registro `ATENDIDO` con el actor `sistema`

#### Scenario: Se registra el deshacer de la ausencia
- **GIVEN** un turno `AUSENTE` de "Paciente Uno" de 10:00 a 10:30, con el horario libre, a las 10:22
- **WHEN** `recepcion` deshace la ausencia
- **THEN** existe un registro `DESHACER_AUSENCIA` con el id del turno y el actor `recepcion`

#### Scenario: Deshacer rechazado sin registro
- **GIVEN** un turno `AUSENTE` de "Paciente Uno" de 10:00 a 10:30 cuyo horario ya se ocupó
- **WHEN** `recepcion` deshace la ausencia y se rechaza por superposición
- **THEN** no existe ningún registro `DESHACER_AUSENCIA` para ese turno
