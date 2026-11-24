# seguridad-autenticacion Specification

## Purpose

Definir cómo el personal y los pacientes inician y cierran sesión en la API del consultorio, cómo el navegador mantiene esa sesión con una cookie del servidor (sin JWT) y cómo se protegen contra CSRF los pedidos que modifican datos. En los escenarios, con datos ficticios, existen el usuario del personal `recepcion` (rol `RECEPCIONISTA`, activo) y la cuenta de paciente `paciente.uno@example.com` (rol `PACIENTE`), y los pedidos que modifican datos llevan un token CSRF válido salvo que el escenario diga otra cosa.

## Requirements

### Requirement: Inicio de sesión con usuario y contraseña
El sistema SHALL iniciar sesión con `POST /api/v1/public/login`, que recibe usuario y contraseña. Si son correctos y el usuario está activo, MUST crear una sesión del servidor y responder `200` con el usuario y su rol, sin la contraseña ni su hash. El usuario se compara sin distinguir mayúsculas ni espacios al inicio o al final.

#### Scenario: Login correcto del personal
- **GIVEN** el usuario `recepcion` con su contraseña correcta
- **WHEN** envía `POST /api/v1/public/login` con esas credenciales
- **THEN** la respuesta es `200` con `username` igual a `recepcion` y `rol` igual a `RECEPCIONISTA`
- **AND** la respuesta no incluye la contraseña ni su hash
- **AND** un pedido posterior con la misma sesión a `GET /api/v1/public/sesion` informa ese usuario y rol

#### Scenario: Login con el usuario en mayúsculas
- **GIVEN** el usuario `recepcion` con su contraseña correcta
- **WHEN** envía `POST /api/v1/public/login` con el usuario ` Recepcion ` y esa contraseña
- **THEN** la respuesta es `200` con `username` igual a `recepcion`

#### Scenario: Login correcto de un paciente
- **GIVEN** la cuenta de paciente `paciente.uno@example.com` con su contraseña correcta
- **WHEN** envía `POST /api/v1/public/login` con esas credenciales
- **THEN** la respuesta es `200` con `rol` igual a `PACIENTE`

### Requirement: Rechazo de credenciales inválidas sin revelar usuarios
El sistema MUST responder `401` con el código `CREDENCIALES_INVALIDAS` y el mismo mensaje cuando la contraseña es incorrecta, el usuario no existe o el usuario está inactivo, y MUST NOT crear una sesión autenticada.

#### Scenario: Contraseña incorrecta
- **GIVEN** el usuario `recepcion`
- **WHEN** envía `POST /api/v1/public/login` con una contraseña incorrecta
- **THEN** la respuesta es `401` con el código `CREDENCIALES_INVALIDAS`
- **AND** un pedido posterior con la misma cookie a `GET /api/v1/public/sesion` informa que no hay sesión

#### Scenario: Usuario inexistente
- **GIVEN** que no existe el usuario `nadie`
- **WHEN** envía `POST /api/v1/public/login` con el usuario `nadie`
- **THEN** la respuesta es `401` con el código `CREDENCIALES_INVALIDAS` y el mismo `detail` que con una contraseña incorrecta

#### Scenario: Usuario inactivo
- **GIVEN** el usuario `recepcion` desactivado
- **WHEN** envía `POST /api/v1/public/login` con su contraseña correcta
- **THEN** la respuesta es `401` con el código `CREDENCIALES_INVALIDAS`

### Requirement: Validación del pedido de login
El sistema MUST responder `422` con el código `DATOS_INVALIDOS` y la lista de campos con error cuando falta el usuario o la contraseña en el pedido de login, sin intentar autenticar.

#### Scenario: Login sin contraseña
- **WHEN** se envía `POST /api/v1/public/login` con el usuario `recepcion` y la contraseña vacía
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la lista de errores incluye el campo `password`

### Requirement: Cookie de sesión protegida
La sesión SHALL identificarse solo con una cookie del servidor marcada `HttpOnly` y `SameSite=Lax`, y `Secure` cuando el entorno lo configura. El sistema MUST NOT emitir tokens de sesión en el cuerpo de la respuesta (sin JWT) y MUST cambiar el identificador de sesión al iniciar sesión.

#### Scenario: Atributos de la cookie de sesión
- **WHEN** el usuario `recepcion` inicia sesión correctamente
- **THEN** la respuesta fija la cookie de sesión con los atributos `HttpOnly` y `SameSite=Lax`
- **AND** el cuerpo de la respuesta no contiene ningún token

#### Scenario: Cookie segura en producción
- **GIVEN** el entorno configurado con la cookie segura activada
- **WHEN** el usuario `recepcion` inicia sesión correctamente
- **THEN** la cookie de sesión lleva además el atributo `Secure`

#### Scenario: Nuevo identificador de sesión al iniciar sesión
- **GIVEN** un navegador con una sesión anónima ya creada
- **WHEN** el usuario `recepcion` inicia sesión correctamente desde ese navegador
- **THEN** el identificador de sesión después del login es distinto del anterior

### Requirement: Estado de la sesión
El sistema SHALL exponer `GET /api/v1/public/sesion`, que responde `200` indicando si hay una sesión iniciada y, si la hay, el usuario y el rol. Si el rol es `ODONTOLOGO`, la respuesta SHALL incluir también `profesionalId`, el profesional vinculado a ese usuario; para los demás roles MUST NOT incluirlo. Esta respuesta MUST incluir la cookie del token CSRF para que el frontend pueda enviar pedidos que modifican datos, incluido el login.

#### Scenario: Sin sesión iniciada
- **GIVEN** un navegador sin sesión
- **WHEN** pide `GET /api/v1/public/sesion`
- **THEN** la respuesta es `200` e indica que no hay sesión iniciada
- **AND** la respuesta fija la cookie `XSRF-TOKEN`

#### Scenario: Sesión de un odontólogo
- **GIVEN** el usuario `ana.ejemplo` (rol `ODONTOLOGO`), vinculado a la Dra. A, con sesión iniciada
- **WHEN** pide `GET /api/v1/public/sesion`
- **THEN** la respuesta es `200` con `rol` igual a `ODONTOLOGO` y `profesionalId` igual al id de la Dra. A

#### Scenario: Sesión de recepción sin profesional
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/public/sesion`
- **THEN** la respuesta es `200` con `rol` igual a `RECEPCIONISTA`
- **AND** la respuesta no incluye `profesionalId`

### Requirement: Protección CSRF en los pedidos que modifican datos
El sistema MUST rechazar con `403` y el código `CSRF_INVALIDO` todo `POST`, `PUT`, `PATCH` o `DELETE` que no traiga en el encabezado `X-XSRF-TOKEN` el valor de la cookie `XSRF-TOKEN`, incluidos el login, el logout y el registro. La cookie `XSRF-TOKEN` es legible por el frontend; la de sesión, no. El token MUST renovarse al iniciar sesión.

#### Scenario: Login sin token CSRF
- **WHEN** se envía `POST /api/v1/public/login` con credenciales correctas y sin el encabezado `X-XSRF-TOKEN`
- **THEN** la respuesta es `403` con el código `CSRF_INVALIDO`
- **AND** no se inicia la sesión

#### Scenario: Token CSRF que no coincide
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** envía `POST /api/v1/logout` con un `X-XSRF-TOKEN` distinto del de su cookie
- **THEN** la respuesta es `403` con el código `CSRF_INVALIDO`
- **AND** la sesión sigue iniciada

#### Scenario: Token CSRF renovado al iniciar sesión
- **GIVEN** un navegador que obtuvo un token CSRF sin sesión
- **WHEN** el usuario `recepcion` inicia sesión correctamente desde ese navegador
- **THEN** la respuesta fija una cookie `XSRF-TOKEN` con un valor nuevo

### Requirement: Cierre de sesión
El sistema SHALL cerrar la sesión con `POST /api/v1/logout`, para cualquier rol: invalida la sesión del servidor, borra la cookie de sesión y responde `204`. Un pedido posterior con la cookie anterior MUST tratarse como sin sesión.

#### Scenario: Logout con sesión iniciada
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** envía `POST /api/v1/logout`
- **THEN** la respuesta es `204`
- **AND** un pedido posterior con la cookie anterior a `GET /api/v1/public/sesion` informa que no hay sesión

#### Scenario: Logout de un paciente
- **GIVEN** la cuenta `paciente.uno@example.com` con sesión iniciada
- **WHEN** envía `POST /api/v1/logout`
- **THEN** la respuesta es `204`

### Requirement: Contraseñas almacenadas con hash
El sistema MUST guardar las contraseñas solo como hash BCrypt y MUST NOT devolverlas, en claro ni con hash, en ninguna respuesta de la API, ni escribirlas en los logs.

#### Scenario: Hash guardado al crear un usuario
- **WHEN** se crea una cuenta con una contraseña válida
- **THEN** lo guardado para esa cuenta es un hash BCrypt distinto de la contraseña
- **AND** esa contraseña verifica contra el hash guardado

### Requirement: Alta del primer administrador
*(Propuesta, a confirmar.)* Al arrancar, si no existe ningún usuario `ADMIN` y están definidos el usuario y la contraseña del administrador inicial en la configuración del entorno, el sistema SHALL crear ese `ADMIN` activo. Si ya existe un `ADMIN`, o falta alguno de los dos valores, MUST NOT crear ni modificar usuarios.

#### Scenario: Primer arranque con las variables definidas
- **GIVEN** una base sin usuarios `ADMIN`
- **AND** la configuración con el usuario `admin` y una contraseña para el administrador inicial
- **WHEN** arranca la aplicación
- **THEN** existe el usuario `admin` con rol `ADMIN`, activo, y puede iniciar sesión con esa contraseña

#### Scenario: Ya existe un administrador
- **GIVEN** un usuario `ADMIN` existente
- **AND** la configuración con otro usuario y contraseña para el administrador inicial
- **WHEN** arranca la aplicación
- **THEN** no se crea ningún usuario y el `ADMIN` existente no cambia

#### Scenario: Variables sin definir
- **GIVEN** una base sin usuarios `ADMIN`
- **AND** la configuración sin contraseña para el administrador inicial
- **WHEN** arranca la aplicación
- **THEN** no se crea ningún usuario

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
