## MODIFIED Requirements

### Requirement: Menú según la sesión
El menú SHALL mostrar "Agenda" y "Configuración" con una sesión del personal, más las páginas que su rol puede abrir ("Pacientes" para `ADMIN` y `ODONTOLOGO`; "Reportes" para `ADMIN` y `RECEPCIONISTA`), "Reservar turno" y "Mis turnos" con una sesión de paciente, y solo "Reservar turno" sin sesión. Al iniciar o cerrar una sesión, el menú MUST actualizarse sin recargar el documento. Sin sesión, el encabezado SHALL ofrecer además el enlace "Ingresar" a `#/mi-cuenta`.

#### Scenario: Menú de un visitante
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/reserva`
- **THEN** el menú tiene solo el enlace "Reservar turno"
- **AND** el encabezado tiene el enlace "Ingresar" a `#/mi-cuenta`

#### Scenario: Menú del personal
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** el menú tiene solo los enlaces "Agenda", "Reportes" y "Configuración", en ese orden

#### Scenario: El menú cambia al ingresar como paciente
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** "Paciente Uno" ingresa correctamente
- **THEN** el menú pasa a tener los enlaces "Reservar turno" y "Mis turnos"
- **AND** el encabezado muestra `paciente.uno@example.com` y "Salir" en lugar de "Ingresar"

## ADDED Requirements

### Requirement: Página "Reportes" para administración y recepción
La aplicación SHALL ofrecer la página "Reportes" en `#/reportes`, con su opción en la barra lateral después de "Pacientes" (o de "Agenda" si no está "Pacientes") solo con sesión `ADMIN` o `RECEPCIONISTA`. Con sesión `ODONTOLOGO` o `PACIENTE`, abrir la dirección MUST mostrar "Esta sección es solo para administración y recepción." sin pedir los reportes. Sin sesión, lleva a Mi cuenta, como las demás páginas privadas.

#### Scenario: Menú del administrador
- **GIVEN** la sesión de `admin` con rol `ADMIN`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene los enlaces "Agenda", "Pacientes", "Reportes" y "Configuración", en ese orden

#### Scenario: El odontólogo no ve Reportes
- **GIVEN** la sesión de `ana.ejemplo` con rol `ODONTOLOGO`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral no tiene el enlace "Reportes"

#### Scenario: El odontólogo abre la dirección
- **GIVEN** la sesión de `ana.ejemplo`
- **WHEN** se abre la aplicación en `#/reportes`
- **THEN** se muestra "Esta sección es solo para administración y recepción."
- **AND** no se pide `GET /api/v1/reportes`

#### Scenario: Sin sesión en Reportes
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/reportes`
- **THEN** se muestra "Mi cuenta"
