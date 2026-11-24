# Spec Delta

## MODIFIED Requirements

### Requirement: Páginas disponibles
La aplicación SHALL ofrecer cuatro páginas de menú, cada una con su dirección: Agenda (`#/agenda`) y Configuración (`#/configuracion`) para el personal, y Reservar turno (`#/reserva`) y Mis turnos (`#/mis-turnos`) para el paciente. Además ofrece el ingreso del personal (`#/ingreso`) y la cuenta del paciente (`#/mi-cuenta`), que no figuran en el menú.

#### Scenario: Abrir la página de reserva por su dirección
- **WHEN** se abre la aplicación en la dirección `#/reserva`
- **THEN** se muestra la página con el título "Reservar turno"

#### Scenario: Abrir la página de ingreso por su dirección
- **WHEN** se abre la aplicación en la dirección `#/ingreso`
- **THEN** se muestra la página con el título "Ingresar"
- **AND** el menú no tiene un enlace a "Ingresar"

#### Scenario: Abrir la cuenta del paciente por su dirección
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en la dirección `#/mi-cuenta`
- **THEN** se muestra la página con el título "Mi cuenta"
- **AND** el menú no tiene un enlace a "Mi cuenta"
