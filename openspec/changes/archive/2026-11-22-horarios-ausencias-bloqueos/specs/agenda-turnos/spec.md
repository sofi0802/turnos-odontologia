# Spec Delta

## ADDED Requirements

### Requirement: Turno dentro del horario de trabajo
El sistema MUST rechazar con el código `FUERA_DE_HORARIO` un turno nuevo o reprogramado que no queda completo, de inicio a fin, dentro de una misma franja de trabajo del profesional para ese día de la semana, en `America/Argentina/Buenos_Aires`. Un profesional sin franjas no recibe turnos.

#### Scenario: Turno dentro de la franja
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 el martes 2026-12-01 a las 12:30
- **THEN** el turno se crea en estado `RESERVADO`

#### Scenario: Turno que termina después de la franja
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A el martes 2026-12-01 a las 12:45
- **THEN** el turno se rechaza con `FUERA_DE_HORARIO`

#### Scenario: Turno en un día sin franjas
- **GIVEN** la Dra. A con franjas solo de lunes a viernes
- **WHEN** se crea un turno de "Consulta" con la Dra. A el sábado 2026-12-05 a las 10:00
- **THEN** el turno se rechaza con `FUERA_DE_HORARIO`

#### Scenario: Turno que cruza el corte entre dos franjas
- **GIVEN** la Dra. A con franjas el martes de 08:00 a 12:00 y de 16:00 a 20:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A el martes 2026-12-01 a las 11:45
- **THEN** el turno se rechaza con `FUERA_DE_HORARIO`

#### Scenario: Turno en la segunda franja del día
- **GIVEN** la Dra. A con franjas el martes de 08:00 a 12:00 y de 16:00 a 20:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 el martes 2026-12-01 a las 16:00
- **THEN** el turno se crea en estado `RESERVADO`

#### Scenario: Profesional sin horarios cargados
- **GIVEN** el Dr. B sin ninguna franja de trabajo
- **WHEN** se crea un turno de "Consulta" con el Dr. B el martes 2026-12-01 a las 10:00
- **THEN** el turno se rechaza con `FUERA_DE_HORARIO`

#### Scenario: Reprogramar fuera de horario
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** un turno activo de "Paciente Uno" el martes 2026-12-01 de 10:00 a 10:30 con la Dra. A
- **WHEN** el personal lo reprograma a las 14:00 del mismo día
- **THEN** la operación se rechaza con `FUERA_DE_HORARIO`
- **AND** el turno original sigue en estado `RESERVADO`

### Requirement: Sin turnos durante una ausencia o licencia
El sistema MUST rechazar con el código `PROFESIONAL_AUSENTE` un turno nuevo, reprogramado, sobreturno o extensión cuyo intervalo se cruza con una ausencia o licencia registrada del profesional.

#### Scenario: Turno durante una licencia
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** una `LICENCIA` de la Dra. A del 2026-12-01 a las 00:00 al 2026-12-02 a las 00:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A el martes 2026-12-01 a las 10:00
- **THEN** el turno se rechaza con `PROFESIONAL_AUSENTE`

#### Scenario: Turno que termina justo cuando empieza la ausencia
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** una `AUSENCIA` de la Dra. A el 2026-12-01 de 11:00 a 13:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 el martes 2026-12-01 a las 10:30
- **THEN** el turno se crea en estado `RESERVADO`

#### Scenario: Sobreturno durante una ausencia
- **GIVEN** una `AUSENCIA` de la Dra. A el 2026-12-01 de 10:00 a 12:00
- **WHEN** el personal crea un sobreturno con la Dra. A el 2026-12-01 a las 10:30
- **THEN** el sobreturno se rechaza con `PROFESIONAL_AUSENTE`

### Requirement: Sin turnos durante un bloqueo de agenda
El sistema MUST rechazar con el código `AGENDA_BLOQUEADA` un turno nuevo, reprogramado, sobreturno o extensión cuyo intervalo se cruza con un bloqueo de agenda del profesional.

#### Scenario: Turno durante un bloqueo
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** un bloqueo de la Dra. A el 2026-12-01 de 11:00 a 12:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A el martes 2026-12-01 a las 10:45
- **THEN** el turno se rechaza con `AGENDA_BLOQUEADA`

#### Scenario: Extensión que entra en un bloqueo
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** un bloqueo de la Dra. A el 2026-12-01 de 11:00 a 12:00
- **AND** un turno activo de la Dra. A el 2026-12-01 de 10:00 a 10:30
- **WHEN** el personal extiende ese turno hasta las 11:15
- **THEN** la extensión se rechaza con `AGENDA_BLOQUEADA`
- **AND** el turno sigue terminando a las 10:30

### Requirement: Sobreturno y extensión fuera del horario de trabajo
El sobreturno (RN-AG-06) y la extensión de un turno (RN-AG-14) SHALL poder quedar fuera de las franjas de trabajo del profesional. Las ausencias, licencias y bloqueos sí los frenan.

#### Scenario: Sobreturno fuera de horario
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **WHEN** el personal crea un sobreturno con la Dra. A el martes 2026-12-01 a las 13:30
- **THEN** el sobreturno se crea

#### Scenario: Extensión que pasa el fin de la franja
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** un turno activo de la Dra. A el 2026-12-01 de 12:30 a 13:00
- **WHEN** el personal extiende ese turno hasta las 13:20
- **THEN** el turno termina a las 13:20

## MODIFIED Requirements

### Requirement: Un solo error con prioridad fija
Si un turno nuevo tiene más de un conflicto, el sistema SHALL informar uno solo, con esta prioridad: `PROFESIONAL_AUSENTE`, `AGENDA_BLOQUEADA`, `FUERA_DE_HORARIO`, después `SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON` y por último `MARGEN_INSUFICIENTE`.

#### Scenario: Choque por profesional y por sillón a la vez
- **GIVEN** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:15
- **THEN** el turno se rechaza con `SOLAPAMIENTO_PROFESIONAL`

#### Scenario: Ausencia y solapamiento a la vez
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 13:00
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **AND** una `AUSENCIA` de la Dra. A el 2026-12-01 de 10:00 a 12:00
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:15
- **THEN** el turno se rechaza con `PROFESIONAL_AUSENTE`

#### Scenario: Fuera de horario y solapamiento a la vez
- **GIVEN** la Dra. A con una franja el martes de 08:00 a 10:30
- **AND** un turno activo de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **WHEN** se crea un turno de "Consulta" con la Dra. A en el Sillón 1 a las 10:15
- **THEN** el turno se rechaza con `FUERA_DE_HORARIO`
