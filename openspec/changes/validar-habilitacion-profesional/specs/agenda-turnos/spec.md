# Spec Delta

## ADDED Requirements

### Requirement: Profesional habilitado para la prestación
El sistema MUST rechazar con `PROFESIONAL_NO_HABILITADO` un turno nuevo, un sobreturno o una reprogramación a otro profesional si ese profesional no está habilitado para la prestación del turno (RN-AG-15). Una reprogramación que conserva el profesional del turno original no vuelve a exigirlo. Un rechazo no crea ningún turno y deja intacto el original.

#### Scenario: Crear un turno con un profesional no habilitado
- **GIVEN** que la Dra. A no está habilitada para "Tratamiento de conducto"
- **WHEN** se crea un turno de "Tratamiento de conducto" con la Dra. A en el Sillón 1 a las 10:00
- **THEN** el turno se rechaza con `PROFESIONAL_NO_HABILITADO`
- **AND** no se crea ningún turno

#### Scenario: Crear un turno con un profesional habilitado
- **GIVEN** que el Dr. B está habilitado para "Tratamiento de conducto"
- **WHEN** se crea un turno de "Tratamiento de conducto" con el Dr. B en el Sillón 1 a las 10:00
- **THEN** el turno se crea en estado `RESERVADO`

#### Scenario: Reprogramar a un profesional no habilitado
- **GIVEN** un turno activo de "Paciente Uno" de "Tratamiento de conducto" de 10:00 a 10:30 con el Dr. B en el Sillón 1
- **AND** que la Dra. A no está habilitada para "Tratamiento de conducto"
- **WHEN** el personal lo reprograma a las 11:00 con la Dra. A
- **THEN** la operación se rechaza con `PROFESIONAL_NO_HABILITADO`
- **AND** el turno original sigue en estado `RESERVADO` de 10:00 a 10:30 con el Dr. B
- **AND** no se crea ningún turno nuevo

#### Scenario: Reprogramar con el mismo profesional
- **GIVEN** un turno activo de "Paciente Uno" de "Consulta" de 10:00 a 10:30 con la Dra. A en el Sillón 1
- **AND** que la Dra. A dejó de estar habilitada para "Consulta" después de darse el turno
- **WHEN** el personal lo reprograma a las 11:00 sin cambiar de profesional
- **THEN** el turno original queda en estado `CANCELADO`
- **AND** existe un turno nuevo de "Paciente Uno" de 11:00 a 11:30 con la Dra. A en estado `RESERVADO`

#### Scenario: Sobreturno con un profesional no habilitado
- **GIVEN** que la Dra. A no está habilitada para "Tratamiento de conducto"
- **AND** un motivo administrativo "Urgencia con el Paciente Uno"
- **WHEN** el personal crea un sobreturno de "Tratamiento de conducto" con la Dra. A a las 10:00
- **THEN** la operación se rechaza con `PROFESIONAL_NO_HABILITADO`
- **AND** no se crea ningún turno

#### Scenario: Profesional inactivo y no habilitado
- **GIVEN** que la Dra. A está inactiva y no está habilitada para "Tratamiento de conducto"
- **WHEN** se crea un turno de "Tratamiento de conducto" con la Dra. A en el Sillón 1 a las 10:00
- **THEN** el turno se rechaza con `RECURSO_INACTIVO`

## MODIFIED Requirements

### Requirement: Un solo error con prioridad fija
Si un turno nuevo tiene más de un conflicto, el sistema SHALL informar uno solo, con esta prioridad: `PROFESIONAL_NO_HABILITADO`, `PROFESIONAL_AUSENTE`, `AGENDA_BLOQUEADA`, `FUERA_DE_HORARIO`, después `SOLAPAMIENTO_PROFESIONAL`, `SOLAPAMIENTO_SILLON` y por último `MARGEN_INSUFICIENTE`.

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

#### Scenario: Profesional no habilitado y ausente a la vez
- **GIVEN** que la Dra. A no está habilitada para "Tratamiento de conducto"
- **AND** una `AUSENCIA` de la Dra. A el 2026-12-01 de 10:00 a 12:00
- **WHEN** se crea un turno de "Tratamiento de conducto" con la Dra. A en el Sillón 1 a las 10:15
- **THEN** el turno se rechaza con `PROFESIONAL_NO_HABILITADO`
