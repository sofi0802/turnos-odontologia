## ADDED Requirements

### Requirement: Ver la ficha desde un turno
Con sesión `ADMIN` u `ODONTOLOGO`, cada turno de la agenda del día y de la semana SHALL ofrecer "Ver ficha", que abre la ficha clínica de su paciente en "Pacientes". Con sesión `RECEPCIONISTA` MUST NOT ofrecerlo.

#### Scenario: El odontólogo abre la ficha
- **GIVEN** la sesión de `ana.ejemplo` y el turno 7 de "Paciente Uno" en su agenda del día
- **WHEN** elige "Ver ficha" en el turno 7
- **THEN** se muestra la página "Pacientes" con la ficha de "Paciente Uno"

#### Scenario: El administrador ve el acceso
- **GIVEN** la sesión de `admin`
- **WHEN** se abre la agenda de la semana con el turno 7
- **THEN** el turno 7 ofrece "Ver ficha"

#### Scenario: La recepción no ve el acceso
- **GIVEN** la sesión de `recepcion`
- **WHEN** se abre la agenda del día con el turno 7
- **THEN** el turno 7 no ofrece "Ver ficha"
