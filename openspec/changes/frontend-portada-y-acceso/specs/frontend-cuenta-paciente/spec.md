# Spec Delta

## MODIFIED Requirements

### Requirement: Cuenta del paciente con sesión iniciada
Al abrir `#/mi-cuenta` con una sesión de paciente, la aplicación MUST mostrar la página "Mis turnos" en lugar de los formularios de ingreso y registro; con una sesión del personal, MUST mostrar la página "Agenda".

#### Scenario: Paciente con sesión abre su cuenta
- **GIVEN** una sesión de `paciente.uno@example.com`
- **WHEN** se abre la aplicación en `#/mi-cuenta`
- **THEN** se muestra la página "Mis turnos" en `#/mis-turnos`

#### Scenario: Personal con sesión abre su cuenta
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/mi-cuenta`
- **THEN** se muestra la página "Agenda" en `#/agenda`

#### Scenario: Personal con sesión abre la dirección vieja del ingreso
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/ingreso`
- **THEN** se muestra la página "Agenda" en `#/agenda`

### Requirement: Salida del paciente
Con una sesión de paciente, el encabezado SHALL mostrar el usuario y "Salir". Al elegir "Salir", la aplicación SHALL enviar `POST /api/v1/logout` y mostrar la portada en `#/inicio`, aunque el cierre falle en el servidor, y MUST borrar la reserva pendiente.

#### Scenario: El paciente sale
- **GIVEN** la página "Mis turnos" abierta con una sesión de `paciente.uno@example.com`
- **WHEN** se elige "Salir"
- **THEN** se envía `POST /api/v1/logout`
- **AND** se muestra la portada en `#/inicio` sin el usuario en el encabezado

## ADDED Requirements

### Requirement: Indicación para el personal en Mi cuenta
Sin sesión, `#/mi-cuenta` SHALL mostrar junto al formulario de ingreso un texto que indique que el personal del consultorio ingresa con su usuario en ese mismo formulario. La etiqueta "Email" y los mensajes de los campos MUST NOT cambiar.

#### Scenario: Se ve la indicación
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** se muestra el formulario de ingreso
- **THEN** se ve el texto "¿Sos del personal del consultorio? Ingresá con tu usuario en este mismo formulario."
- **AND** el campo sigue llamándose "Email"
