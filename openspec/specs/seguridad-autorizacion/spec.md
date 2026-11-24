# seguridad-autorizacion Specification

## Purpose

Definir qué rutas y operaciones de la API puede usar cada rol (`ADMIN`, `RECEPCIONISTA`, `ODONTOLOGO`, `PACIENTE`), con acceso denegado por defecto, respuestas `401`/`403` con código estable, y la garantía de que un paciente solo accede a sus propios datos. En los escenarios, con datos ficticios, existen el usuario `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`) y `ana.ejemplo` (`ODONTOLOGO`), y dos pacientes con cuenta: "Paciente Uno" (DNI `99000001`, `paciente.uno@example.com`) y "Paciente Dos" (DNI `99000002`, `paciente.dos@example.com`).

## Requirements

### Requirement: Rutas públicas sin sesión
El sistema SHALL permitir sin sesión las rutas bajo `/api/v1/public/**`. Las demás rutas MUST exigir una sesión iniciada.

#### Scenario: Ruta pública sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** pide `GET /api/v1/public/sesion`
- **THEN** la respuesta es `200`

### Requirement: Pedido sin sesión a una ruta protegida
El sistema MUST responder `401` con el código `NO_AUTENTICADO` en un `ProblemDetail`, sin redirigir a una página de login, a todo pedido sin sesión a una ruta que no es pública.

#### Scenario: Ruta del personal sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** pide `GET /api/v1/turnos`
- **THEN** la respuesta es `401` con el código `NO_AUTENTICADO`

#### Scenario: Ruta del paciente sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** pide `GET /api/v1/paciente/perfil`
- **THEN** la respuesta es `401` con el código `NO_AUTENTICADO`

### Requirement: Rutas del paciente solo para el rol PACIENTE
El sistema MUST permitir las rutas bajo `/api/v1/paciente/**` solo a sesiones con rol `PACIENTE`, y MUST responder `403` con el código `ACCESO_DENEGADO` a cualquier rol del personal.

#### Scenario: El personal no usa las rutas del paciente
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/paciente/perfil`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Rutas del personal cerradas al paciente
El sistema MUST permitir el resto de las rutas bajo `/api/v1/**` solo a los roles del personal (`ADMIN`, `RECEPCIONISTA`, `ODONTOLOGO`), y MUST responder `403` con el código `ACCESO_DENEGADO` a una sesión `PACIENTE`, aunque la ruta todavía no tenga un endpoint.

#### Scenario: Un paciente pide la agenda del personal
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide `GET /api/v1/turnos`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: Un paciente pide los datos de otro paciente por la ruta del personal
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes/{id}` con el id de "Paciente Dos"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** la respuesta no contiene datos de "Paciente Dos"

### Requirement: Permisos por rol en cada operación del personal
Cada operación del personal SHALL declarar qué roles la pueden usar, según la matriz RBAC de la base de conocimiento. El sistema MUST responder `403` con el código `ACCESO_DENEGADO` cuando el rol de la sesión no está entre los permitidos.

#### Scenario: Operación de ADMIN pedida por recepción
- **GIVEN** una operación del personal permitida solo al rol `ADMIN`
- **AND** el usuario `recepcion` con sesión iniciada
- **WHEN** pide esa operación
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: Operación de ADMIN pedida por el administrador
- **GIVEN** una operación del personal permitida solo al rol `ADMIN`
- **AND** el usuario `admin` con sesión iniciada
- **WHEN** pide esa operación
- **THEN** la respuesta es `200`

### Requirement: Denegación por defecto
El sistema MUST denegar todo pedido a una ruta fuera de `/api/v1/**`, cualquiera sea el rol de la sesión: `401` con `NO_AUTENTICADO` si no hay sesión y `403` con `ACCESO_DENEGADO` si la hay.

#### Scenario: Ruta fuera de la API con sesión del administrador
- **GIVEN** el usuario `admin` con sesión iniciada
- **WHEN** pide `GET /actuator/env`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: El paciente se identifica solo por su sesión
Las rutas bajo `/api/v1/paciente/**` MUST identificar al paciente únicamente a partir de la sesión iniciada, y MUST NOT usar un identificador de paciente enviado en el pedido. Un paciente solo accede a sus propios datos.

#### Scenario: Perfil propio
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide `GET /api/v1/paciente/perfil`
- **THEN** la respuesta es `200` con el nombre "Paciente Uno", el DNI `99000001` y el email `paciente.uno@example.com`
- **AND** la respuesta no contiene datos de "Paciente Dos"

#### Scenario: Intento de pedir el perfil de otro paciente
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide `GET /api/v1/paciente/perfil?pacienteId={id}` con el id de "Paciente Dos"
- **THEN** la respuesta es `200` con los datos de "Paciente Uno"
- **AND** la respuesta no contiene datos de "Paciente Dos"

### Requirement: Orígenes permitidos para el frontend
El sistema SHALL aceptar pedidos de otro origen con credenciales solo desde los orígenes configurados (por ejemplo, el servidor de desarrollo del frontend), y MUST NOT permitir cualquier origen.

#### Scenario: Origen configurado
- **GIVEN** el origen `http://localhost:5173` configurado como permitido
- **WHEN** llega una consulta previa de CORS desde ese origen para `POST /api/v1/public/login`
- **THEN** la respuesta permite ese origen y el envío de credenciales

#### Scenario: Origen no configurado
- **GIVEN** el origen `http://localhost:5173` configurado como permitido
- **WHEN** llega una consulta previa de CORS desde `http://otro-origen.example.com`
- **THEN** la respuesta no permite ese origen

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
