# Spec Delta

## MODIFIED Requirements

### Requirement: Páginas disponibles
La aplicación SHALL ofrecer tres páginas en el menú, cada una con su dirección: Agenda (`#/agenda`), Reserva (`#/reserva`) y Configuración (`#/configuracion`), y además la página de ingreso (`#/ingreso`), que no figura en el menú. La página Agenda muestra la agenda del personal; Reserva y Configuración siguen mostrando solo su título.

#### Scenario: Abrir la página de reserva por su dirección
- **WHEN** se abre la aplicación en la dirección `#/reserva`
- **THEN** se muestra la página con el título "Reserva"

#### Scenario: Abrir la página de ingreso por su dirección
- **WHEN** se abre la aplicación en la dirección `#/ingreso`
- **THEN** se muestra la página con el título "Ingresar"
- **AND** el menú no tiene un enlace a "Ingresar"
