# cuentas-personal Specification

## Purpose

Permitir que el administrador del consultorio dé, quite y restablezca el acceso del personal (`ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`) sin tocar la base de datos. Las cuentas se crean con una contraseña temporal y la de `ODONTOLOGO` queda vinculada a su profesional. En los escenarios, con datos ficticios, existen el `ADMIN` `admin`, la recepcionista `recepcion`, los profesionales activos Ana Ejemplo (sin cuenta) y Bruno Prueba (con la cuenta `bruno.prueba`), el profesional inactivo Carla Inactiva y la cuenta de paciente `paciente.uno@example.com`. Los pedidos que modifican datos llevan un token CSRF válido.

## Requirements

### Requirement: Operaciones de cuentas solo para el administrador
Las operaciones bajo `/api/v1/usuarios` SHALL estar permitidas solo a sesiones con rol `ADMIN`. Con sesión `RECEPCIONISTA` u `ODONTOLOGO` MUST responder `403 ACCESO_DENEGADO`, y sin sesión `401 NO_AUTENTICADO`, sin crear ni cambiar ninguna cuenta.

#### Scenario: Recepción intenta crear una cuenta
- **GIVEN** una sesión de `recepcion`
- **WHEN** envía `POST /api/v1/usuarios` con un usuario, el rol `RECEPCIONISTA` y sin profesional
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** no se crea ninguna cuenta

#### Scenario: Un odontólogo intenta listar las cuentas
- **GIVEN** una sesión de `bruno.prueba` (rol `ODONTOLOGO`)
- **WHEN** pide `GET /api/v1/usuarios`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: Pedido sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** pide `GET /api/v1/usuarios`
- **THEN** la respuesta es `401` con el código `NO_AUTENTICADO`

### Requirement: Lista de las cuentas del personal
`GET /api/v1/usuarios` SHALL devolver las cuentas con rol `ADMIN`, `RECEPCIONISTA` u `ODONTOLOGO`, cada una con `id`, `username`, `rol`, `profesionalId` (o nulo), `activo` y `debeCambiarContrasena`. MUST NOT incluir cuentas `PACIENTE`, contraseñas ni hashes.

#### Scenario: Listar las cuentas
- **GIVEN** una sesión de `admin`
- **WHEN** pide `GET /api/v1/usuarios`
- **THEN** la respuesta es `200` con las cuentas `admin`, `recepcion` y `bruno.prueba`
- **AND** la cuenta `bruno.prueba` trae el `profesionalId` de Bruno Prueba
- **AND** no aparece `paciente.uno@example.com`
- **AND** ninguna cuenta incluye la contraseña ni su hash

### Requirement: Alta de una cuenta con contraseña temporal
`POST /api/v1/usuarios` con `username`, `rol` y `profesionalId` opcional SHALL crear una cuenta activa, con `debeCambiarContrasena` en `true`, y responder `201` con la cuenta y `contrasenaTemporal`. La contraseña la genera el servidor, cumple la política de contraseñas y solo se guarda su hash. La respuesta MUST llevar `Cache-Control: no-store`.

#### Scenario: Alta de una recepcionista
- **GIVEN** una sesión de `admin`
- **WHEN** envía `POST /api/v1/usuarios` con `username` `recepcion2`, `rol` `RECEPCIONISTA` y sin `profesionalId`
- **THEN** la respuesta es `201` con `username` igual a `recepcion2`, `activo` en `true` y `debeCambiarContrasena` en `true`
- **AND** la respuesta incluye una `contrasenaTemporal` de 8 a 72 caracteres
- **AND** la respuesta tiene el encabezado `Cache-Control: no-store`
- **AND** `recepcion2` puede iniciar sesión con esa contraseña

#### Scenario: La contraseña temporal no se vuelve a mostrar
- **GIVEN** que `admin` creó la cuenta `recepcion2`
- **WHEN** pide `GET /api/v1/usuarios`
- **THEN** la cuenta `recepcion2` aparece sin `contrasenaTemporal`

#### Scenario: Usuario normalizado
- **GIVEN** una sesión de `admin`
- **WHEN** crea una cuenta con `username` ` Recepcion2 `
- **THEN** la cuenta queda con `username` igual a `recepcion2`

### Requirement: Vínculo de la cuenta con un profesional
Una cuenta `ODONTOLOGO` MUST vincularse a un profesional activo sin cuenta. Una cuenta `ADMIN` MAY vincularse a un profesional con esas mismas condiciones. Una cuenta `RECEPCIONISTA` MUST NOT tener profesional. Si falta el vínculo o sobra, la respuesta MUST ser `422 DATOS_INVALIDOS`. Si el profesional no existe o está inactivo, `409 PROFESIONAL_NO_DISPONIBLE`. Si ya tiene cuenta, `409 PROFESIONAL_CON_CUENTA`.

#### Scenario: Alta de un odontólogo vinculado
- **GIVEN** una sesión de `admin`
- **WHEN** crea una cuenta `ana.ejemplo` con `rol` `ODONTOLOGO` y el `profesionalId` de Ana Ejemplo
- **THEN** la respuesta es `201` con ese `profesionalId`
- **AND** al iniciar sesión, `GET /api/v1/public/sesion` informa el `profesionalId` de Ana Ejemplo

#### Scenario: Odontólogo sin profesional
- **GIVEN** una sesión de `admin`
- **WHEN** crea una cuenta con `rol` `ODONTOLOGO` y sin `profesionalId`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `profesionalId` con error

#### Scenario: Recepcionista con profesional
- **GIVEN** una sesión de `admin`
- **WHEN** crea una cuenta con `rol` `RECEPCIONISTA` y el `profesionalId` de Ana Ejemplo
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `profesionalId` con error

#### Scenario: Profesional que ya tiene cuenta
- **GIVEN** una sesión de `admin`
- **WHEN** crea una cuenta `ODONTOLOGO` con el `profesionalId` de Bruno Prueba
- **THEN** la respuesta es `409` con el código `PROFESIONAL_CON_CUENTA`

#### Scenario: Profesional inactivo
- **GIVEN** una sesión de `admin`
- **WHEN** crea una cuenta `ODONTOLOGO` con el `profesionalId` de Carla Inactiva
- **THEN** la respuesta es `409` con el código `PROFESIONAL_NO_DISPONIBLE`

#### Scenario: Administrador que también atiende
- **GIVEN** una sesión de `admin`
- **WHEN** crea una cuenta `dueno` con `rol` `ADMIN` y el `profesionalId` de Ana Ejemplo
- **THEN** la respuesta es `201` con ese `profesionalId`

### Requirement: Validación del alta
El alta MUST responder `422 DATOS_INVALIDOS` con la lista de campos con error si `username` falta o no tiene entre 3 y 150 caracteres, o si `rol` falta o es `PACIENTE` u otro valor desconocido. Si el usuario normalizado ya existe, en cualquier rol, MUST responder `409 USUARIO_EXISTENTE`. En ningún caso se crea la cuenta.

#### Scenario: Rol de paciente
- **GIVEN** una sesión de `admin`
- **WHEN** crea una cuenta con `rol` `PACIENTE`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `rol` con error

#### Scenario: Usuario repetido
- **GIVEN** una sesión de `admin`
- **WHEN** crea una cuenta con `username` `Recepcion`
- **THEN** la respuesta es `409` con el código `USUARIO_EXISTENTE`

#### Scenario: Usuario demasiado corto
- **GIVEN** una sesión de `admin`
- **WHEN** crea una cuenta con `username` `ab`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `username` con error

### Requirement: Desactivar y reactivar una cuenta
`POST /api/v1/usuarios/{id}/desactivar` SHALL dejar la cuenta inactiva y `POST /api/v1/usuarios/{id}/activar` SHALL reactivarla, y ambas responden `200` con la cuenta. Una cuenta inactiva MUST NOT poder iniciar sesión. Las cuentas no se borran. Un `id` inexistente o de un paciente MUST responder `404 USUARIO_INEXISTENTE`.

#### Scenario: Desactivar una cuenta
- **GIVEN** una sesión de `admin`
- **WHEN** envía `POST /api/v1/usuarios/{id de recepcion}/desactivar`
- **THEN** la respuesta es `200` con `activo` en `false`
- **AND** el login de `recepcion` con su contraseña responde `401` con el código `CREDENCIALES_INVALIDAS`

#### Scenario: Reactivar una cuenta
- **GIVEN** la cuenta `recepcion` desactivada y una sesión de `admin`
- **WHEN** envía `POST /api/v1/usuarios/{id de recepcion}/activar`
- **THEN** la respuesta es `200` con `activo` en `true`
- **AND** `recepcion` puede iniciar sesión con su contraseña

#### Scenario: Cuenta de un paciente
- **GIVEN** una sesión de `admin`
- **WHEN** envía `POST /api/v1/usuarios/{id de paciente.uno@example.com}/desactivar`
- **THEN** la respuesta es `404` con el código `USUARIO_INEXISTENTE`

### Requirement: Restablecer la contraseña de una cuenta
`POST /api/v1/usuarios/{id}/restablecer-contrasena` SHALL reemplazar la contraseña por una temporal nueva, poner `debeCambiarContrasena` en `true` y responder `200` con `contrasenaTemporal` y `Cache-Control: no-store`. La contraseña anterior MUST dejar de servir.

#### Scenario: Restablecer la contraseña de un odontólogo
- **GIVEN** una sesión de `admin`
- **WHEN** envía `POST /api/v1/usuarios/{id de bruno.prueba}/restablecer-contrasena`
- **THEN** la respuesta es `200` con una `contrasenaTemporal` nueva
- **AND** el login de `bruno.prueba` con su contraseña anterior responde `401` con el código `CREDENCIALES_INVALIDAS`
- **AND** el login con la contraseña temporal informa `debeCambiarContrasena` en `true`

### Requirement: Cierre de las sesiones de una cuenta
Al desactivar una cuenta o restablecer su contraseña, el sistema MUST cerrar todas las sesiones abiertas de esa cuenta. El siguiente pedido con cualquiera de esas sesiones MUST tratarse como sin sesión.

#### Scenario: Sesión abierta de una cuenta desactivada
- **GIVEN** `recepcion` con una sesión abierta
- **WHEN** `admin` desactiva la cuenta `recepcion`
- **THEN** el siguiente pedido de esa sesión a `GET /api/v1/turnos` responde `401` con el código `NO_AUTENTICADO`

#### Scenario: Sesión abierta de una cuenta restablecida
- **GIVEN** `bruno.prueba` con una sesión abierta
- **WHEN** `admin` restablece la contraseña de `bruno.prueba`
- **THEN** el siguiente pedido de esa sesión a `GET /api/v1/turnos` responde `401` con el código `NO_AUTENTICADO`

### Requirement: Protección de la propia cuenta
El `ADMIN` MUST NOT desactivar ni restablecer su propia cuenta: responde `409 OPERACION_SOBRE_CUENTA_PROPIA` y la cuenta no cambia. Para su propia contraseña usa el cambio de contraseña. Como quien desactiva es siempre un `ADMIN` activo, esta regla garantiza que el consultorio nunca se queda sin un `ADMIN` activo.

#### Scenario: El administrador intenta desactivarse
- **GIVEN** una sesión de `admin`
- **WHEN** envía `POST /api/v1/usuarios/{id de admin}/desactivar`
- **THEN** la respuesta es `409` con el código `OPERACION_SOBRE_CUENTA_PROPIA`
- **AND** `admin` sigue activo

#### Scenario: El administrador intenta restablecer su contraseña
- **GIVEN** una sesión de `admin`
- **WHEN** envía `POST /api/v1/usuarios/{id de admin}/restablecer-contrasena`
- **THEN** la respuesta es `409` con el código `OPERACION_SOBRE_CUENTA_PROPIA`

#### Scenario: Un administrador desactiva a otro
- **GIVEN** las cuentas `ADMIN` activas `admin` y `dueno`, y una sesión de `admin`
- **WHEN** envía `POST /api/v1/usuarios/{id de dueno}/desactivar`
- **THEN** la respuesta es `200` con `activo` en `false`
- **AND** `admin` sigue activo

### Requirement: Contraseñas fuera de los logs
El sistema MUST NOT escribir en los logs la contraseña temporal, la contraseña nueva ni ningún hash. Puede registrar qué `ADMIN` creó, desactivó, reactivó o restableció qué cuenta.

#### Scenario: Alta registrada sin contraseña
- **GIVEN** una sesión de `admin`
- **WHEN** crea la cuenta `recepcion2`
- **THEN** los logs pueden nombrar a `admin` y a `recepcion2`
- **AND** no contienen la `contrasenaTemporal` devuelta ni su hash
