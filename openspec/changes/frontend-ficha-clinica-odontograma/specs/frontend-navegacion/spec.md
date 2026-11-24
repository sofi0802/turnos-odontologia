## ADDED Requirements

### Requirement: Página "Pacientes" para odontólogos y administradores
La aplicación SHALL ofrecer la página "Pacientes" en `#/pacientes`, con su opción en la barra lateral (después de "Agenda") solo con sesión `ADMIN` u `ODONTOLOGO`. Con sesión `RECEPCIONISTA` o `PACIENTE`, abrir la dirección MUST mostrar "Esta sección es solo para odontólogos y administradores." sin pedir ningún dato de pacientes. Sin sesión, lleva a Mi cuenta, como las demás páginas privadas.

#### Scenario: Menú del odontólogo
- **GIVEN** la sesión de `ana.ejemplo` con rol `ODONTOLOGO`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral tiene los enlaces "Agenda", "Pacientes" y "Configuración", en ese orden

#### Scenario: Menú de la recepción
- **GIVEN** la sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** la barra lateral no tiene el enlace "Pacientes"

#### Scenario: La recepción abre la dirección
- **GIVEN** la sesión de `recepcion`
- **WHEN** se abre la aplicación en `#/pacientes`
- **THEN** se muestra "Esta sección es solo para odontólogos y administradores."
- **AND** no se pide `GET /api/v1/pacientes` ni ninguna ficha

#### Scenario: Sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en `#/pacientes`
- **THEN** se muestra "Mi cuenta"
