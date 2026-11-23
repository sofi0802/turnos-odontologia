# Spec Delta

## MODIFIED Requirements

### Requirement: Cuenta del personal en el ingreso del paciente
`#/mi-cuenta` SHALL ser la única página de ingreso de la aplicación, para pacientes y personal, con el mismo formulario y los mismos textos para los dos. Si el login desde `#/mi-cuenta` responde `200` con un rol del personal, la aplicación MUST mostrar la página "Agenda" en lugar de las páginas del paciente.

#### Scenario: Recepción ingresa desde la página del paciente
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se ingresa `recepcion` con su contraseña correcta y la API responde `200` con el rol `RECEPCIONISTA`
- **THEN** se muestra la página "Agenda" en la dirección `#/agenda`
