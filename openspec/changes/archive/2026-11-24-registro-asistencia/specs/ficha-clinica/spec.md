## MODIFIED Requirements

### Requirement: Ausencias en la ficha
La ficha de un paciente SHALL mostrar los turnos en los que el paciente fue marcado como ausente, con su fecha y hora, y el profesional del turno. Si la recepción deshizo una ausencia, la ficha SHALL mostrarla como deshecha, con el momento y el usuario que la deshizo, en lugar de una ausencia vigente.

#### Scenario: La ficha muestra la ausencia
- **GIVEN** un turno de "Paciente Uno" con la Dra. Ana Ejemplo el 2026-12-01 a las 10:00, marcado como `AUSENTE`
- **WHEN** `ana.ejemplo` consulta la ficha de "Paciente Uno"
- **THEN** la ficha incluye una ausencia del 2026-12-01 a las 10:00 con la Dra. Ana Ejemplo

#### Scenario: La ficha muestra la ausencia deshecha
- **GIVEN** un turno de "Paciente Uno" con la Dra. Ana Ejemplo el 2026-12-01 a las 10:00, marcado como ausente y después deshecho por `recepcion` a las 10:22
- **WHEN** `ana.ejemplo` consulta la ficha de "Paciente Uno"
- **THEN** la ficha incluye esa ausencia del 2026-12-01 a las 10:00 como deshecha por `recepcion` a las 10:22
