# Spec Delta

## ADDED Requirements

### Requirement: Página "Coberturas" para administración y recepción
La aplicación SHALL ofrecer la página "Coberturas" en `#/coberturas`, con su opción en la barra lateral después de "Pacientes" (o de "Lista de espera" si no está "Pacientes") y antes de "Reportes", solo con sesión `ADMIN` o `RECEPCIONISTA`. Con sesión `ODONTOLOGO` o `PACIENTE`, abrir la dirección MUST mostrar "Esta sección es solo para administración y recepción." sin pedir datos de pacientes. Sin sesión, lleva a Mi cuenta.

#### Scenario: Menú de la recepción con Coberturas
- **GIVEN** la sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene el enlace "Coberturas" entre "Lista de espera" y "Reportes"

#### Scenario: Menú del administrador con Coberturas
- **GIVEN** la sesión de `admin` con rol `ADMIN`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene el enlace "Coberturas" entre "Pacientes" y "Reportes"

#### Scenario: El odontólogo abre la dirección
- **GIVEN** la sesión de `ana.ejemplo` con rol `ODONTOLOGO`
- **WHEN** se abre la aplicación en `#/coberturas`
- **THEN** se muestra "Esta sección es solo para administración y recepción."
- **AND** la barra lateral no tiene el enlace "Coberturas" y no se pide `GET /api/v1/pacientes`

#### Scenario: Coberturas sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/coberturas`
- **THEN** se muestra "Mi cuenta"

### Requirement: "Mi cuenta" en el menú del paciente
Con una sesión `PACIENTE`, la barra lateral SHALL tener el enlace "Mi cuenta" a `#/mi-cuenta` después de "Mis turnos", marcado como página actual cuando está abierta. El personal y los visitantes MUST NOT ver ese enlace en la barra lateral.

#### Scenario: El paciente ve Mi cuenta en el menú
- **GIVEN** una sesión de `paciente.uno@example.com` con rol `PACIENTE`
- **WHEN** se abre la aplicación en `#/mis-turnos`
- **THEN** la barra lateral tiene los enlaces "Reservar turno", "Mis turnos" y "Mi cuenta", en ese orden

#### Scenario: El personal no ve Mi cuenta en el menú
- **GIVEN** la sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral no tiene el enlace "Mi cuenta"
