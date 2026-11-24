# frontend-cuentas-personal Specification

## Purpose

Definir las pantallas con las que el administrador da, quita y restablece el acceso del personal, y con las que cada usuario del personal cambia su contraseña, obligatoriamente después de una contraseña temporal o cuando lo decide. En los escenarios, con datos ficticios, existen el `ADMIN` `admin`, la recepcionista `recepcion`, los profesionales Ana Ejemplo (sin cuenta) y Bruno Prueba (con la cuenta `bruno.prueba`).

## Requirements

### Requirement: Lista de cuentas en la pestaña Usuarios
La pestaña "Usuarios" de `#/configuracion`, que solo ve `ADMIN`, SHALL listar `GET /api/v1/usuarios` con el usuario, el rol en palabras ("Administrador", "Recepción" u "Odontólogo"), el nombre del profesional vinculado si lo hay, el estado ("Activo" o "Inactivo") y la marca "Contraseña temporal pendiente" cuando corresponde.

#### Scenario: Ver las cuentas del consultorio
- **GIVEN** una sesión de `admin`
- **WHEN** abre la pestaña "Usuarios"
- **THEN** se pide `GET /api/v1/usuarios`
- **AND** la fila de `bruno.prueba` dice "Odontólogo", "Bruno Prueba" y "Activo"
- **AND** la fila de `recepcion` dice "Recepción" sin profesional

### Requirement: Alta de una cuenta del personal
Para `ADMIN`, "Agregar usuario" SHALL pedir el usuario, el rol y el profesional. El selector de profesional ofrece solo los activos sin cuenta: es obligatorio con "Odontólogo", opcional con "Administrador" y no se muestra con "Recepción". Al guardar envía `POST /api/v1/usuarios` y suma la cuenta a la lista.

#### Scenario: Alta de un odontólogo
- **GIVEN** una sesión de `admin` en la pestaña "Usuarios"
- **WHEN** agrega el usuario `ana.ejemplo` con el rol "Odontólogo" y la profesional Ana Ejemplo
- **THEN** se envía `POST /api/v1/usuarios` con `rol` `ODONTOLOGO` y el `profesionalId` de Ana Ejemplo
- **AND** `ana.ejemplo` aparece en la lista con "Contraseña temporal pendiente"

#### Scenario: Profesionales ofrecidos
- **GIVEN** una sesión de `admin` en "Agregar usuario" con el rol "Odontólogo"
- **WHEN** abre el selector de profesional
- **THEN** ofrece a Ana Ejemplo y no ofrece a Bruno Prueba, que ya tiene cuenta

#### Scenario: Odontólogo sin profesional elegido
- **GIVEN** una sesión de `admin` en "Agregar usuario" con el rol "Odontólogo"
- **WHEN** guarda sin elegir profesional
- **THEN** el campo profesional muestra "Elegí el profesional de esta cuenta."
- **AND** no se envía ningún pedido

#### Scenario: Recepción no elige profesional
- **GIVEN** una sesión de `admin` en "Agregar usuario"
- **WHEN** elige el rol "Recepción"
- **THEN** el selector de profesional no se muestra

### Requirement: Contraseña temporal mostrada una sola vez
Cuando el alta o el restablecimiento responden con `contrasenaTemporal`, la página SHALL mostrarla en un diálogo con el usuario, el botón "Copiar" y el aviso "Esta contraseña no se vuelve a mostrar. Pasásela a la persona por un medio seguro.". Al cerrar el diálogo, la contraseña MUST borrarse de la página y no guardarse en el navegador.

#### Scenario: Diálogo después del alta
- **GIVEN** una sesión de `admin`
- **WHEN** el alta de `ana.ejemplo` responde `201` con una `contrasenaTemporal`
- **THEN** un diálogo muestra `ana.ejemplo`, la contraseña, "Copiar" y el aviso de que no se vuelve a mostrar

#### Scenario: Cerrar el diálogo
- **GIVEN** el diálogo de la contraseña temporal de `ana.ejemplo` abierto
- **WHEN** el administrador lo cierra
- **THEN** la contraseña ya no está en la página
- **AND** no quedó guardada en `localStorage` ni en `sessionStorage`

### Requirement: Dar acceso desde el detalle del profesional
Para `ADMIN`, el detalle de un profesional activo sin cuenta SHALL ofrecer "Dar acceso", que abre el alta con el rol "Odontólogo" y ese profesional ya elegidos. Si el profesional tiene cuenta, el detalle SHALL mostrar "Acceso: <usuario>" y su estado. `RECEPCIONISTA` y `ODONTOLOGO` no ven ninguna de las dos cosas.

#### Scenario: Profesional sin cuenta
- **GIVEN** una sesión de `admin`
- **WHEN** abre el detalle de Ana Ejemplo y usa "Dar acceso"
- **THEN** se abre "Agregar usuario" con el rol "Odontólogo" y Ana Ejemplo elegidos

#### Scenario: Profesional con cuenta
- **GIVEN** una sesión de `admin`
- **WHEN** abre el detalle de Bruno Prueba
- **THEN** ve "Acceso: bruno.prueba" y "Activo"
- **AND** no ve "Dar acceso"

#### Scenario: Recepción en el detalle
- **GIVEN** una sesión de `recepcion`
- **WHEN** abre el detalle de Ana Ejemplo
- **THEN** no ve "Dar acceso" ni datos de acceso

### Requirement: Desactivar, reactivar y restablecer desde la lista
Cada cuenta de la lista SHALL ofrecer "Desactivar" o "Activar" según su estado, y "Restablecer contraseña". Desactivar y restablecer piden confirmación, porque cierran las sesiones abiertas de esa cuenta. La fila de la cuenta de la sesión MUST NOT ofrecer "Desactivar" ni "Restablecer contraseña".

#### Scenario: Desactivar una cuenta
- **GIVEN** una sesión de `admin` en la pestaña "Usuarios"
- **WHEN** usa "Desactivar" en `recepcion` y confirma
- **THEN** se envía `POST /api/v1/usuarios/{id}/desactivar`
- **AND** la fila de `recepcion` pasa a "Inactivo" y ofrece "Activar"

#### Scenario: Restablecer una contraseña
- **GIVEN** una sesión de `admin` en la pestaña "Usuarios"
- **WHEN** usa "Restablecer contraseña" en `bruno.prueba` y confirma
- **THEN** se envía `POST /api/v1/usuarios/{id}/restablecer-contrasena`
- **AND** se muestra el diálogo con la contraseña temporal nueva

#### Scenario: La propia cuenta
- **GIVEN** una sesión de `admin` en la pestaña "Usuarios"
- **WHEN** mira la fila de `admin`
- **THEN** no hay "Desactivar" ni "Restablecer contraseña"

### Requirement: Errores de las cuentas
Si la API rechaza una operación de cuentas, la página SHALL mostrar el mensaje de su código: `USUARIO_EXISTENTE` "Ya existe una cuenta con ese usuario.", `PROFESIONAL_CON_CUENTA` "Ese profesional ya tiene una cuenta.", `PROFESIONAL_NO_DISPONIBLE` "El profesional no existe o está inactivo." y `OPERACION_SOBRE_CUENTA_PROPIA` "No podés hacer esto con tu propia cuenta.". El formulario conserva lo escrito.

#### Scenario: Usuario repetido
- **GIVEN** una sesión de `admin` en "Agregar usuario"
- **WHEN** el alta responde `409` con el código `USUARIO_EXISTENTE`
- **THEN** se muestra "Ya existe una cuenta con ese usuario."
- **AND** el formulario conserva el usuario y el rol

### Requirement: Cambio obligatorio de la contraseña temporal
Si la sesión informa `debeCambiarContrasena` en `true`, la aplicación SHALL llevar a `#/cambiar-contrasena` desde el ingreso y desde cualquier página privada, sin pedir datos de esa página. Hasta cambiarla, la página solo ofrece el formulario y "Salir". Si un pedido responde `403 CAMBIO_DE_CONTRASENA_PENDIENTE`, también lleva ahí.

#### Scenario: Ingreso con contraseña temporal
- **GIVEN** la cuenta `ana.ejemplo` con contraseña temporal
- **WHEN** ingresa en `#/mi-cuenta` con esa contraseña
- **THEN** la aplicación muestra `#/cambiar-contrasena` con el aviso "Antes de seguir, elegí una contraseña nueva."

#### Scenario: Intento de abrir la agenda
- **GIVEN** una sesión de `ana.ejemplo` con `debeCambiarContrasena` en `true`
- **WHEN** se abre `#/agenda`
- **THEN** se muestra `#/cambiar-contrasena`
- **AND** no se pide `GET /api/v1/turnos`

### Requirement: Formulario de cambio de contraseña
`#/cambiar-contrasena` SHALL pedir la contraseña actual, la nueva y su repetición, y enviar `POST /api/v1/cuenta/contrasena`. Si la nueva y su repetición no coinciden o la nueva tiene menos de 8 caracteres, MUST avisarlo sin enviar. Al responder `204` muestra "Tu contraseña se cambió." y lleva a `#/agenda`. El personal SHALL llegar acá desde "Mi contraseña" en el menú y, donde se ve, desde su usuario en el encabezado.

#### Scenario: Cambio correcto
- **GIVEN** una sesión de `ana.ejemplo` en `#/cambiar-contrasena`
- **WHEN** completa la actual, una nueva válida y la misma repetición, y guarda
- **THEN** se envía `POST /api/v1/cuenta/contrasena`
- **AND** se muestra "Tu contraseña se cambió." y la agenda

#### Scenario: Repetición distinta
- **GIVEN** una sesión en `#/cambiar-contrasena`
- **WHEN** la repetición no coincide con la nueva
- **THEN** se muestra "Las contraseñas nuevas no coinciden."
- **AND** no se envía ningún pedido

#### Scenario: Contraseña actual incorrecta
- **GIVEN** una sesión en `#/cambiar-contrasena`
- **WHEN** la API responde `422` con el código `CONTRASENA_ACTUAL_INCORRECTA`
- **THEN** se muestra "La contraseña actual no es correcta."
- **AND** se vacían los tres campos

#### Scenario: Cambio voluntario desde el menú
- **GIVEN** una sesión de `recepcion` sin contraseña temporal
- **WHEN** usa "Mi contraseña" en el menú
- **THEN** se muestra `#/cambiar-contrasena` con el formulario y un enlace para volver a la agenda
- **AND** "Mi contraseña" queda marcada como la página actual

#### Scenario: Cambio voluntario desde el encabezado
- **GIVEN** una sesión de `recepcion` sin contraseña temporal, en una pantalla donde el encabezado muestra el usuario
- **WHEN** usa su usuario en el encabezado
- **THEN** se muestra `#/cambiar-contrasena`
