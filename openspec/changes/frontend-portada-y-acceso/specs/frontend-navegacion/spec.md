# Spec Delta

## MODIFIED Requirements

### Requirement: Páginas disponibles
La aplicación SHALL ofrecer cuatro páginas de menú, cada una con su dirección: Agenda (`#/agenda`) y Configuración (`#/configuracion`) para el personal, y Reservar turno (`#/reserva`) y Mis turnos (`#/mis-turnos`) para el paciente. Además ofrece la portada `#/inicio` y la cuenta `#/mi-cuenta`, que no figuran en el menú; `#/mi-cuenta` es la única página de ingreso, para pacientes y personal. La dirección vieja del ingreso del personal, `#/ingreso`, MUST llevar a `#/mi-cuenta`.

#### Scenario: Abrir la página de reserva por su dirección
- **WHEN** se abre la aplicación en la dirección `#/reserva`
- **THEN** se muestra la página con el título "Reservar turno"

#### Scenario: Abrir la portada por su dirección
- **WHEN** se abre la aplicación en la dirección `#/inicio`
- **THEN** se muestra la portada
- **AND** el menú no tiene un enlace a "Inicio"

#### Scenario: Abrir la página de ingreso por su dirección
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en la dirección `#/ingreso`
- **THEN** se muestra la página con el título "Mi cuenta"
- **AND** la dirección pasa a ser `#/mi-cuenta`

#### Scenario: Abrir la cuenta del paciente por su dirección
- **GIVEN** un navegador sin sesión
- **WHEN** se abre la aplicación en la dirección `#/mi-cuenta`
- **THEN** se muestra la página con el título "Mi cuenta"
- **AND** el menú no tiene un enlace a "Mi cuenta"

### Requirement: Página inicial y direcciones desconocidas
Sin dirección, o con una que no corresponde a ninguna página ni a la dirección vieja `#/ingreso`, la aplicación SHALL mostrar la portada y actualizar la dirección a `#/inicio`, con o sin sesión.

#### Scenario: Abrir la aplicación sin dirección
- **WHEN** se abre la aplicación sin dirección de página
- **THEN** se muestra la portada
- **AND** la dirección pasa a ser `#/inicio`

#### Scenario: Dirección desconocida
- **WHEN** se abre la aplicación en la dirección `#/no-existe`
- **THEN** se muestra la portada
- **AND** la dirección pasa a ser `#/inicio`

#### Scenario: Abrir la aplicación sin dirección con sesión del personal
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación sin dirección de página
- **THEN** se muestra la portada en `#/inicio`
- **AND** la portada ofrece "Ir a la agenda"
