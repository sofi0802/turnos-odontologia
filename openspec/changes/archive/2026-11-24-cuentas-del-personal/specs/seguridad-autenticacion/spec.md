# Spec Delta

## ADDED Requirements

### Requirement: Sesión con contraseña temporal
Las respuestas de `POST /api/v1/public/login` y de `GET /api/v1/public/sesion` con sesión del personal SHALL incluir `debeCambiarContrasena`. Mientras sea `true`, la sesión solo MAY usar `/api/v1/public/**`, `POST /api/v1/logout` y `POST /api/v1/cuenta/contrasena`. Cualquier otro pedido a `/api/v1/**` MUST responder `403 CAMBIO_DE_CONTRASENA_PENDIENTE` sin ejecutar la operación.

#### Scenario: Login con contraseña temporal
- **GIVEN** la cuenta `recepcion2` recién creada por el `ADMIN`, con su contraseña temporal
- **WHEN** envía `POST /api/v1/public/login` con esas credenciales
- **THEN** la respuesta es `200` con `rol` igual a `RECEPCIONISTA` y `debeCambiarContrasena` en `true`
- **AND** `GET /api/v1/public/sesion` informa `debeCambiarContrasena` en `true`

#### Scenario: Operación bloqueada hasta cambiar la contraseña
- **GIVEN** una sesión de `recepcion2` con `debeCambiarContrasena` en `true`
- **WHEN** pide `GET /api/v1/turnos`
- **THEN** la respuesta es `403` con el código `CAMBIO_DE_CONTRASENA_PENDIENTE`

#### Scenario: Salir con la contraseña pendiente
- **GIVEN** una sesión de `recepcion2` con `debeCambiarContrasena` en `true`
- **WHEN** envía `POST /api/v1/logout`
- **THEN** la respuesta es `204` y la sesión se cierra

#### Scenario: Cuenta sin contraseña temporal
- **GIVEN** el usuario `recepcion` con su contraseña definitiva
- **WHEN** inicia sesión
- **THEN** la respuesta informa `debeCambiarContrasena` en `false`
- **AND** puede pedir `GET /api/v1/turnos`

### Requirement: Cambio de la propia contraseña
`POST /api/v1/cuenta/contrasena` con `actual` y `nueva` SHALL cambiar la contraseña de la cuenta de la sesión, poner `debeCambiarContrasena` en `false` y responder `204`. La sesión actual sigue abierta. Si `actual` no coincide, MUST responder `422 CONTRASENA_ACTUAL_INCORRECTA`. Si `nueva` no cumple la política o es igual a `actual`, `422 DATOS_INVALIDOS`.

#### Scenario: Cambio después del primer ingreso
- **GIVEN** una sesión de `recepcion2` con `debeCambiarContrasena` en `true`
- **WHEN** envía `POST /api/v1/cuenta/contrasena` con la contraseña temporal como `actual` y una `nueva` válida
- **THEN** la respuesta es `204`
- **AND** `GET /api/v1/public/sesion` informa `debeCambiarContrasena` en `false`
- **AND** la misma sesión puede pedir `GET /api/v1/turnos`
- **AND** el login con la contraseña temporal responde `401` con el código `CREDENCIALES_INVALIDAS`

#### Scenario: Contraseña actual incorrecta
- **GIVEN** una sesión de `recepcion`
- **WHEN** envía `POST /api/v1/cuenta/contrasena` con una `actual` incorrecta
- **THEN** la respuesta es `422` con el código `CONTRASENA_ACTUAL_INCORRECTA`
- **AND** la contraseña no cambia

#### Scenario: Contraseña nueva demasiado corta
- **GIVEN** una sesión de `recepcion`
- **WHEN** envía `POST /api/v1/cuenta/contrasena` con la `actual` correcta y una `nueva` de 7 caracteres
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `nueva` con error

#### Scenario: Contraseña nueva igual a la actual
- **GIVEN** una sesión de `recepcion`
- **WHEN** envía `POST /api/v1/cuenta/contrasena` con la misma contraseña como `actual` y como `nueva`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `nueva` con error

#### Scenario: Un paciente no usa este cambio
- **GIVEN** una sesión de `paciente.uno@example.com`
- **WHEN** envía `POST /api/v1/cuenta/contrasena`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
