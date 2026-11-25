# Spec Delta

## MODIFIED Requirements

### Requirement: Cuenta del paciente con sesión iniciada
Al abrir `#/mi-cuenta` con una sesión de paciente, la aplicación MUST mostrar la página "Mi cuenta" con la sección "Mi cobertura", en lugar de los formularios de ingreso y registro; con una sesión del personal, MUST mostrar la página "Agenda". Al ingresar desde el formulario, el paciente sigue yendo a "Mis turnos" (o a la reserva pendiente).

#### Scenario: Paciente con sesión abre su cuenta
- **GIVEN** una sesión de `paciente.uno@example.com`
- **WHEN** se abre la aplicación en `#/mi-cuenta`
- **THEN** se muestra la página "Mi cuenta" en `#/mi-cuenta` con la sección "Mi cobertura"
- **AND** no se muestran los formularios de ingreso ni de registro

#### Scenario: El paciente ingresa y va a Mis turnos
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión y sin reserva pendiente
- **WHEN** "Paciente Uno" ingresa correctamente
- **THEN** se muestra la página "Mis turnos" en `#/mis-turnos`

#### Scenario: Personal con sesión abre su cuenta
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/mi-cuenta`
- **THEN** se muestra la página "Agenda" en `#/agenda`

#### Scenario: Personal con sesión abre la dirección vieja del ingreso
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/ingreso`
- **THEN** se muestra la página "Agenda" en `#/agenda`
