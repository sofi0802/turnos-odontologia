# Spec Delta

## ADDED Requirements

### Requirement: Sección de la cuenta en el menú del personal
Con una sesión del personal, la barra lateral SHALL mostrar, debajo del menú principal y en una navegación aparte llamada "Tu cuenta", el enlace "Mi contraseña" a `#/cambiar-contrasena`, marcado como página actual cuando está abierta. Los enlaces del menú principal no cambian. Un paciente o un visitante MUST NOT ver esta sección.

#### Scenario: El personal ve "Mi contraseña" aparte
- **GIVEN** una sesión de `recepcion` con rol `RECEPCIONISTA`
- **WHEN** se abre la aplicación en `#/agenda`
- **THEN** el menú principal tiene solo los enlaces "Agenda" y "Configuración"
- **AND** la navegación "Tu cuenta" tiene el enlace "Mi contraseña"

#### Scenario: Un paciente no ve la sección de la cuenta
- **GIVEN** una sesión de `paciente.uno@example.com` con rol `PACIENTE`
- **WHEN** se abre la aplicación en `#/mis-turnos`
- **THEN** la barra lateral no muestra "Mi contraseña"
