# Spec Delta

## MODIFIED Requirements

### Requirement: Cierre de sesión
Con una sesión del personal, el encabezado SHALL mostrar el usuario y un botón "Salir". Al usarlo, la aplicación SHALL enviar `POST /api/v1/logout` con el token CSRF y mostrar la portada en `#/inicio` aunque la respuesta falle.

#### Scenario: Recepción cierra la sesión
- **GIVEN** la agenda abierta con una sesión de `recepcion`
- **WHEN** se usa el botón "Salir"
- **THEN** se envía `POST /api/v1/logout` con el encabezado `X-XSRF-TOKEN`
- **AND** se muestra la portada en la dirección `#/inicio`
- **AND** el encabezado ya no muestra el usuario `recepcion`

#### Scenario: El cierre falla en el servidor
- **GIVEN** la agenda abierta con una sesión de `recepcion`
- **WHEN** se usa el botón "Salir" y `POST /api/v1/logout` falla
- **THEN** se muestra igualmente la portada en `#/inicio`
