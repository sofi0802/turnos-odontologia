## ADDED Requirements

### Requirement: Ruta inexistente del personal
El sistema MUST responder `404` con el código `RECURSO_INEXISTENTE` en un `ProblemDetail` cuando una sesión del personal (`ADMIN`, `RECEPCIONISTA` u `ODONTOLOGO`) pide una ruta bajo `/api/v1/**` que no tiene endpoint. Un pedido sin sesión MUST seguir respondiendo `401 NO_AUTENTICADO` y una sesión `PACIENTE` MUST seguir respondiendo `403 ACCESO_DENEGADO`, aunque la ruta no exista. La ruta interna de errores pedida directamente MUST seguir denegada.

#### Scenario: Ruta inexistente con sesión del personal
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/no-existe`
- **THEN** la respuesta es `404` con el código `RECURSO_INEXISTENTE`

#### Scenario: Ruta inexistente con sesión del administrador
- **GIVEN** el usuario `admin` con sesión iniciada
- **WHEN** pide `GET /api/v1/no-existe`
- **THEN** la respuesta es `404` con el código `RECURSO_INEXISTENTE`

#### Scenario: Ruta inexistente sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** pide `GET /api/v1/no-existe`
- **THEN** la respuesta es `401` con el código `NO_AUTENTICADO`

#### Scenario: Ruta inexistente del personal pedida por un paciente
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide `GET /api/v1/no-existe`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: Ruta interna de errores pedida directamente
- **GIVEN** el usuario `admin` con sesión iniciada
- **WHEN** pide `GET /error`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Error interno con su código propio
El sistema MUST responder `500` con el código `ERROR_INTERNO` en un `ProblemDetail` cuando una operación falla por un error no controlado, en lugar de `403`. La respuesta MUST NOT incluir la traza, el mensaje de la excepción ni datos del pedido.

#### Scenario: Error no controlado en una operación del personal
- **GIVEN** el usuario `admin` con sesión iniciada
- **AND** una operación del personal que falla con un error no controlado cuyo mensaje es "detalle-interno-ficticio"
- **WHEN** pide esa operación
- **THEN** la respuesta es `500` con el código `ERROR_INTERNO`
- **AND** la respuesta no contiene "detalle-interno-ficticio" ni la traza
