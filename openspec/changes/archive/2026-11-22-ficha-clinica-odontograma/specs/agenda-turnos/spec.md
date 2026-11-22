# Spec Delta

## ADDED Requirements

### Requirement: Marcar la ausencia del paciente
El odontólogo del turno SHALL poder marcar que el paciente no se presentó a un turno activo cuya hora de inicio ya pasó. El turno pasa a `AUSENTE`, deja de ocupar la agenda y el horario que resta se puede volver a dar desde el momento actual (RN-AG-17).

#### Scenario: La ausencia libera el resto del horario
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:10
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** el odontólogo de la Dra. A lo marca como ausente
- **THEN** el turno queda en estado `AUSENTE`
- **AND** se puede crear un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:15

#### Scenario: Ausencia en un turno que ya terminó
- **GIVEN** que el momento actual es el 2026-12-01 a las 11:00
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A lo marca como ausente
- **THEN** el turno queda en estado `AUSENTE`

### Requirement: La ausencia solo después del inicio
El sistema MUST rechazar marcar como ausente un turno cuya hora de inicio todavía no llegó, con el código `TURNO_NO_INICIADO`. El turno MUST quedar sin cambios.

#### Scenario: Ausencia antes del inicio
- **GIVEN** que el momento actual es el 2026-12-01 a las 09:50
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A lo marca como ausente
- **THEN** la operación se rechaza con `TURNO_NO_INICIADO`
- **AND** el turno sigue en estado `RESERVADO`

### Requirement: La ausencia solo sobre turnos activos
El sistema MUST rechazar marcar como ausente un turno que no está activo con el código `TURNO_NO_ACTIVO`.

#### Scenario: Ausencia en un turno cancelado
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:10
- **AND** un turno `CANCELADO` de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A lo marca como ausente
- **THEN** la operación se rechaza con `TURNO_NO_ACTIVO`

### Requirement: Solo el odontólogo del turno marca la ausencia
El sistema MUST rechazar con `403` y el código `ACCESO_DENEGADO` que un usuario marque la ausencia si no es un `ODONTOLOGO` vinculado al profesional del turno. Esto incluye a otro odontólogo, al administrador y a la recepción.

#### Scenario: Otro odontólogo marca la ausencia
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:10
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo del Dr. B lo marca como ausente
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el turno sigue en estado `RESERVADO`

#### Scenario: La recepción marca la ausencia
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** `recepcion` lo marca como ausente
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: La ausencia corresponde al paciente del turno
El sistema MUST responder `404` con el código `TURNO_INEXISTENTE` cuando el turno no existe o no es de ese paciente, sin revelar de quién es.

#### Scenario: Turno de otro paciente
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:10
- **AND** un turno activo de "Paciente Dos" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A marca ese turno como ausencia de "Paciente Uno"
- **THEN** la respuesta es `404` con el código `TURNO_INEXISTENTE`
- **AND** el turno sigue en estado `RESERVADO`
