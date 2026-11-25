# Spec Delta

## MODIFIED Requirements

### Requirement: Menú según la sesión
La barra lateral SHALL mostrar al personal "Agenda", "Configuración" y las páginas de su rol ("Pacientes" para `ADMIN` y `ODONTOLOGO`; "Lista de espera", "Coberturas", "Reportes" y "Caja" para `ADMIN` y `RECEPCIONISTA`), y al paciente "Reservar turno", "Mis turnos" y "Mi cuenta". Sin sesión MUST NOT haber barra lateral: el encabezado ofrece "Reservar turno" y un único "Ingresar" a `#/mi-cuenta`, también en la portada. Al iniciar o cerrar sesión, la barra lateral MUST actualizarse sin recargar.

#### Scenario: Menú de un visitante
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/reserva`
- **THEN** no hay barra lateral
- **AND** el encabezado tiene el enlace "Reservar turno" y el enlace "Ingresar" a `#/mi-cuenta`

#### Scenario: Menú del personal
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene solo los enlaces "Agenda", "Lista de espera", "Coberturas", "Reportes", "Caja" y "Configuración", en ese orden
- **AND** el enlace de la página abierta, "Agenda", está marcado como activo

#### Scenario: El menú cambia al ingresar como paciente
- **GIVEN** la página `#/mi-cuenta` abierta sin sesión
- **WHEN** "Paciente Uno" ingresa correctamente
- **THEN** aparece la barra lateral con los enlaces "Reservar turno", "Mis turnos" y "Mi cuenta"
- **AND** el encabezado muestra `paciente.uno@example.com` y "Salir" en lugar de "Ingresar"

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
