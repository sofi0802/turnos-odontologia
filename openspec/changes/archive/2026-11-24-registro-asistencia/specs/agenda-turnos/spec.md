## RENAMED Requirements

- FROM: `### Requirement: La ausencia solo después del inicio`
- TO: `### Requirement: La ausencia solo después de la tolerancia`

- FROM: `### Requirement: Solo el odontólogo del turno marca la ausencia`
- TO: `### Requirement: Quién marca la ausencia`

## MODIFIED Requirements

### Requirement: Marcar la ausencia del paciente
La recepción, desde la agenda, y el odontólogo del turno, desde la ficha clínica, SHALL poder marcar que el paciente no se presentó a un turno activo, a partir de los 15 minutos de su inicio. El turno pasa a `AUSENTE`, deja de ocupar la agenda y el horario que resta se puede volver a dar desde el momento actual (RN-AG-17). El sistema MUST NOT pasar un turno a `AUSENTE` por su cuenta.

#### Scenario: La ausencia libera el resto del horario
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:20
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** la recepción lo marca como ausente
- **THEN** el turno queda en estado `AUSENTE`
- **AND** se puede crear un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:25

#### Scenario: Ausencia en un turno que ya terminó
- **GIVEN** que el momento actual es el 2026-12-01 a las 11:00
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A lo marca como ausente desde la ficha
- **THEN** el turno queda en estado `AUSENTE`

#### Scenario: Un turno sin registro no pasa solo a ausente
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A, sin la llegada registrada
- **WHEN** el momento actual pasa a ser el 2026-12-01 a las 12:00 y corre el proceso programado
- **THEN** el turno sigue en estado `RESERVADO`

### Requirement: La ausencia solo después de la tolerancia
El sistema MUST rechazar marcar como ausente un turno cuando todavía no pasaron 15 minutos desde su inicio (tolerancia fija), con el código `AUSENCIA_ANTES_DE_TOLERANCIA`. La regla vale para la recepción y para el odontólogo. El turno MUST quedar sin cambios.

#### Scenario: Ausencia antes del inicio
- **GIVEN** que el momento actual es el 2026-12-01 a las 09:50
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A lo marca como ausente
- **THEN** la operación se rechaza con `AUSENCIA_ANTES_DE_TOLERANCIA`
- **AND** el turno sigue en estado `RESERVADO`

#### Scenario: Ausencia dentro de la tolerancia
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:14
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** la recepción lo marca como ausente
- **THEN** la operación se rechaza con `AUSENCIA_ANTES_DE_TOLERANCIA`
- **AND** el turno sigue en estado `RESERVADO`

#### Scenario: Ausencia al cumplirse la tolerancia
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:15
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** la recepción lo marca como ausente
- **THEN** el turno queda en estado `AUSENTE`

### Requirement: Quién marca la ausencia
Solo pueden marcar la ausencia un usuario `RECEPCIONISTA`, desde la agenda, y un `ODONTOLOGO` vinculado al profesional del turno, desde la ficha. El sistema MUST rechazar con `403` y el código `ACCESO_DENEGADO` a otro odontólogo y al administrador, y a la recepción en la ficha clínica.

#### Scenario: Otro odontólogo marca la ausencia
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:20
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo del Dr. B lo marca como ausente desde la ficha
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el turno sigue en estado `RESERVADO`

#### Scenario: La recepción marca la ausencia
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** `recepcion` lo marca como ausente desde la ficha clínica
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: El administrador marca la ausencia
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:20
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** `admin` lo marca como ausente desde la agenda
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el turno sigue en estado `RESERVADO`

## ADDED Requirements

### Requirement: La ausencia no se marca con la llegada registrada
El sistema MUST rechazar marcar como ausente un turno que tiene la llegada registrada, con el código `LLEGADA_REGISTRADA`.

#### Scenario: Ausencia de un paciente que llegó
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:20
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la llegada registrada a las 10:05
- **WHEN** la recepción lo marca como ausente
- **THEN** la operación se rechaza con `LLEGADA_REGISTRADA`
- **AND** el turno sigue activo

### Requirement: Llegada del paciente
Un usuario `RECEPCIONISTA` SHALL poder registrar la llegada del paciente a un turno activo, aunque llegue después del inicio. El sistema guarda el momento de la llegada y el turno MUST seguir activo y ocupando el profesional y el sillón hasta que pase a `ATENDIDO` (RN-AG-20). Otro rol MUST recibir `403` con el código `ACCESO_DENEGADO`.

#### Scenario: Llegada tarde
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:12
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** la recepción registra la llegada
- **THEN** el turno sigue en estado `RESERVADO` con la llegada a las 10:12
- **AND** crear un turno con la Dra. A en el Sillón 1 a las 10:20 se rechaza con `SOLAPAMIENTO_PROFESIONAL`

#### Scenario: El administrador registra una llegada
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:05
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30
- **WHEN** `admin` registra la llegada
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Rechazos de la llegada
La llegada se puede registrar desde las 00:00 del día del turno, en la zona del consultorio. El sistema MUST rechazar con `LLEGADA_ANTICIPADA` una llegada antes de ese momento, con `TURNO_NO_ACTIVO` la llegada a un turno que no está activo y con `LLEGADA_REGISTRADA` una segunda llegada. El turno MUST quedar sin cambios.

#### Scenario: Llegada antes del día del turno
- **GIVEN** que el momento actual es el 2026-11-30 a las 18:00
- **AND** un turno activo de "Paciente Uno" el 2026-12-01 de 10:00 a 10:30
- **WHEN** la recepción registra la llegada
- **THEN** la operación se rechaza con `LLEGADA_ANTICIPADA`

#### Scenario: Llegada a un turno ausente
- **GIVEN** un turno `AUSENTE` de "Paciente Uno" de 10:00 a 10:30
- **WHEN** la recepción registra la llegada
- **THEN** la operación se rechaza con `TURNO_NO_ACTIVO`

#### Scenario: Segunda llegada
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la llegada registrada a las 10:05
- **WHEN** la recepción registra la llegada otra vez
- **THEN** la operación se rechaza con `LLEGADA_REGISTRADA`

### Requirement: Paso automático a atendido
Un proceso programado SHALL pasar a `ATENDIDO` cada turno activo con la llegada registrada cuyo fin con margen (`fin` más el margen entre turnos) ya pasó. Hasta ese momento el turno MUST seguir activo, para que el margen de limpieza del sillón siga reservado. Si la llegada se registra cuando el fin con margen ya pasó (corrección de un turno sin registro), el turno SHALL pasar a `ATENDIDO` en la misma operación.

#### Scenario: El turno con llegada pasa a atendido
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 con la llegada a las 10:05 y un margen de 10 minutos
- **WHEN** el momento actual es el 2026-12-01 a las 10:41 y corre el proceso programado
- **THEN** el turno queda en estado `ATENDIDO`

#### Scenario: El margen sigue reservado
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30 en el Sillón 1, con la llegada a las 10:05 y un margen de 10 minutos
- **WHEN** el momento actual es el 2026-12-01 a las 10:35 y corre el proceso programado
- **THEN** el turno sigue activo
- **AND** crear un turno en el Sillón 1 a las 10:36 se rechaza con `MARGEN_INSUFICIENTE`

#### Scenario: Corrección de un turno sin registro
- **GIVEN** que el momento actual es el 2026-12-01 a las 12:00
- **AND** un turno activo de "Paciente Uno" de 10:00 a 10:30 sin la llegada registrada
- **WHEN** la recepción registra la llegada
- **THEN** el turno queda en estado `ATENDIDO`

### Requirement: Deshacer la ausencia
Un usuario `RECEPCIONISTA` SHALL poder deshacer la ausencia de un turno `AUSENTE` antes de su fin. El turno vuelve al estado que tenía antes de la ausencia (`CONFIRMADO` si se había confirmado, si no `RESERVADO`) y vuelve a ocupar la agenda. Otro rol MUST recibir `403` con el código `ACCESO_DENEGADO`.

#### Scenario: Deshacer con el horario libre
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:22
- **AND** un turno `AUSENTE` de "Paciente Uno" de 10:00 a 10:30 con la Dra. A, que estaba `CONFIRMADO`
- **WHEN** la recepción deshace la ausencia
- **THEN** el turno queda en estado `CONFIRMADO`

#### Scenario: El odontólogo intenta deshacer la ausencia
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:22
- **AND** un turno `AUSENTE` de "Paciente Uno" de 10:00 a 10:30 con la Dra. A
- **WHEN** el odontólogo de la Dra. A deshace la ausencia
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el turno sigue `AUSENTE`

### Requirement: Deshacer la ausencia solo con el horario libre
Al deshacer una ausencia, el sistema MUST validar otra vez la superposición y el margen con los turnos activos (RN-AG-02 a RN-AG-05), sin aplicar las reglas de turno en el pasado ni de horario de trabajo, y rechazar con su código si el horario ya se ocupó. Las restricciones de la base MUST seguir garantizándolo ante operaciones simultáneas. Con un turno que no está ausente el rechazo es `TURNO_NO_AUSENTE`, y después del fin, `TURNO_TERMINADO`.

#### Scenario: Deshacer con el horario ocupado
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:22
- **AND** un turno `AUSENTE` de "Paciente Uno" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **AND** un sobreturno activo de "Paciente Dos" de 10:20 a 10:40 en el Sillón 1
- **WHEN** la recepción deshace la ausencia
- **THEN** la operación se rechaza con `SOLAPAMIENTO_SILLON`
- **AND** el turno sigue `AUSENTE`

#### Scenario: Deshacer después del fin
- **GIVEN** que el momento actual es el 2026-12-01 a las 10:31
- **AND** un turno `AUSENTE` de "Paciente Uno" de 10:00 a 10:30
- **WHEN** la recepción deshace la ausencia
- **THEN** la operación se rechaza con `TURNO_TERMINADO`

#### Scenario: Deshacer un turno que no está ausente
- **GIVEN** un turno activo de "Paciente Uno" de 10:00 a 10:30
- **WHEN** la recepción deshace la ausencia
- **THEN** la operación se rechaza con `TURNO_NO_AUSENTE`
