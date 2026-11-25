## ADDED Requirements

### Requirement: Reserva con seña
La respuesta `201` de `POST /api/v1/paciente/reservas` SHALL incluir `sena`, con el formato de la spec `turnos-paciente`: el objeto de la seña pendiente si la reserva la creó (spec `senas`), o `null` si no. La seña MUST NOT cambiar las reglas de la reserva: el turno se crea `RESERVADO` y ocupa el hueco con las mismas garantías contra solapamientos.

#### Scenario: Reserva que crea una seña
- **GIVEN** las señas activas con el 30 % y 30 minutos
- **WHEN** "Paciente Dos" reserva online "Consulta o control" a las 10:00
- **THEN** la respuesta es `201` con el turno `RESERVADO` y `sena` con `estado` `PENDIENTE`, `importe` `4500.00` y `venceEn` a las 10:30

#### Scenario: Reserva sin seña
- **GIVEN** las señas desactivadas
- **WHEN** "Paciente Dos" reserva online "Consulta o control"
- **THEN** la respuesta es `201` con `sena` `null`

#### Scenario: Dos pacientes por el mismo hueco con señas activas
- **GIVEN** las señas activas
- **WHEN** "Paciente Uno" y "Paciente Dos" reservan a la vez el último hueco del mismo profesional y sillón
- **THEN** solo una reserva responde `201` con su seña, la otra recibe el rechazo de "Reservas simultáneas online" y hay una sola seña
