# Spec Delta

## MODIFIED Requirements

### Requirement: Permisos sobre los bloqueos
Un `ODONTOLOGO` MUST recibir `403` con el código `ACCESO_DENEGADO` al operar sobre los bloqueos de otro profesional. La recepción SHALL poder consultar los bloqueos de cualquier profesional, para reasignar los turnos que afectan (RN-AG-19), y MUST recibir `403` con el código `ACCESO_DENEGADO` al registrar o borrar un bloqueo.

#### Scenario: Un odontólogo bloquea la agenda de otro
- **WHEN** `bruno.prueba` bloquea la agenda de la Dra. Ana Ejemplo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: La recepción consulta los bloqueos
- **GIVEN** un bloqueo de la Dra. Ana Ejemplo el 2026-12-01 de 11:00 a 12:00
- **WHEN** `recepcion` consulta los bloqueos de la Dra. Ana Ejemplo
- **THEN** la respuesta es `200` e incluye ese bloqueo

#### Scenario: La recepción registra un bloqueo
- **WHEN** `recepcion` bloquea la agenda de la Dra. Ana Ejemplo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: La recepción borra un bloqueo
- **GIVEN** un bloqueo de la Dra. Ana Ejemplo el 2026-12-01 de 11:00 a 12:00
- **WHEN** `recepcion` lo borra
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el bloqueo sigue registrado

## ADDED Requirements

### Requirement: Reasignar un turno afectado por un bloqueo
El `ADMIN` y la recepción SHALL poder reasignar un turno activo afectado por un bloqueo de agenda (RN-AG-19) con `POST /api/v1/profesionales/{id}/bloqueos/{bloqueoId}/turnos/{turnoId}/reasignar`, con el mismo cuerpo, las mismas reglas, la misma respuesta y el mismo aviso al paciente que la reasignación por una ausencia. El turno original pasa a `CANCELADO` y se crea uno nuevo, en una sola operación. El `ODONTOLOGO` MUST recibir `403` con el código `ACCESO_DENEGADO`.

#### Scenario: Reasignar a otro profesional el turno de un bloqueo
- **GIVEN** un bloqueo de la Dra. Ana Ejemplo el 2026-12-09 de 09:00 a 12:00
- **AND** un turno activo de "Paciente Uno" de "Consulta o control" con la Dra. Ana Ejemplo el 2026-12-09 de 10:00 a 10:30 en el Sillón 1
- **AND** el Dr. Bruno Prueba habilitado para "Consulta o control" y con franja ese día
- **WHEN** `recepcion` reasigna ese turno al Dr. Bruno Prueba citando ese bloqueo
- **THEN** la respuesta es `200`
- **AND** el turno original queda en estado `CANCELADO`
- **AND** existe un turno nuevo de "Paciente Uno" con el Dr. Bruno Prueba el 2026-12-09 de 10:00 a 10:30, con otro número de reserva
- **AND** se pide un aviso de reasignación para el turno nuevo

#### Scenario: Turno que no está afectado por el bloqueo
- **GIVEN** un bloqueo de la Dra. Ana Ejemplo el 2026-12-09 de 09:00 a 12:00
- **AND** un turno activo de la Dra. Ana Ejemplo el 2026-12-09 a las 12:30
- **WHEN** `recepcion` reasigna ese turno citando ese bloqueo
- **THEN** la respuesta es `409` con el código `TURNO_NO_AFECTADO`
- **AND** el turno sigue activo

#### Scenario: El nuevo horario también cae en el bloqueo
- **GIVEN** un bloqueo de la Dra. Ana Ejemplo el 2026-12-09 de 09:00 a 12:00
- **AND** un turno activo de la Dra. Ana Ejemplo el 2026-12-09 a las 10:00 afectado por él
- **WHEN** `admin` reasigna ese turno al 2026-12-09 a las 11:00 con la Dra. Ana Ejemplo
- **THEN** la respuesta es `409` con el código `AGENDA_BLOQUEADA`
- **AND** el turno original sigue activo

#### Scenario: Bloqueo inexistente
- **WHEN** `recepcion` reasigna un turno citando un bloqueo que no existe para la Dra. Ana Ejemplo
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: El odontólogo intenta reasignar el turno de su bloqueo
- **GIVEN** un turno activo afectado por un bloqueo de la Dra. Ana Ejemplo
- **WHEN** `ana.ejemplo` reasigna ese turno al Dr. Bruno Prueba
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
