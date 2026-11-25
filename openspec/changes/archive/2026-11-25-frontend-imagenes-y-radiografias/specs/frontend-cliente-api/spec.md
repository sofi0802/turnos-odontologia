# Spec Delta

## ADDED Requirements

### Requirement: Envío de un formulario con archivos
El cliente SHALL poder enviar un pedido `POST` con un formulario multipart (campos de texto y un archivo) sin fijar el `Content-Type`, para que el navegador agregue el límite del multipart. Ese pedido MUST llevar la cookie de sesión y el token CSRF igual que un pedido JSON, y su respuesta y sus errores se tratan igual.

#### Scenario: Carga de un archivo con token CSRF
- **GIVEN** la cookie `XSRF-TOKEN` con el valor `token-ficticio-123`
- **WHEN** una pantalla envía `POST /pacientes/1/imagenes` con un archivo ficticio y el campo `categoria`
- **THEN** el pedido lleva el cuerpo multipart con el archivo y el campo
- **AND** lleva el encabezado `X-XSRF-TOKEN: token-ficticio-123` y las credenciales del navegador
- **AND** no fija el encabezado `Content-Type` a mano

#### Scenario: Error de validación de un formulario
- **GIVEN** que la API responde `422` con `{"detail": "Solo se aceptan archivos JPEG, PNG o PDF", "codigo": "TIPO_ARCHIVO_NO_PERMITIDO"}`
- **WHEN** una pantalla envía un formulario con un archivo
- **THEN** la pantalla recibe un error de validación con el código `TIPO_ARCHIVO_NO_PERMITIDO`

### Requirement: Descarga de un archivo
El cliente SHALL poder pedir con `GET` un recurso binario y entregarlo como archivo, con su tipo de contenido, sin interpretarlo como JSON. El pedido MUST llevar las credenciales del navegador, y una respuesta no exitosa MUST traducirse a los mismos errores que un pedido JSON.

#### Scenario: Descarga de una radiografía
- **GIVEN** que la API responde `200` con `Content-Type: image/jpeg` y bytes ficticios
- **WHEN** una pantalla pide el archivo `GET /pacientes/1/imagenes/5/archivo`
- **THEN** la pantalla recibe un archivo de tipo `image/jpeg` con esos bytes

#### Scenario: Descarga sin permiso
- **GIVEN** que la API responde `403` con el código `ACCESO_DENEGADO`
- **WHEN** una pantalla pide un archivo
- **THEN** la pantalla recibe un error de permiso con el código `ACCESO_DENEGADO`

### Requirement: Error de validación ante un 413
Ante un `413`, el cliente MUST entregar un error de validación con el código de la propiedad `codigo` del `ProblemDetail` si viene (por ejemplo `ARCHIVO_DEMASIADO_GRANDE`) y su `detail`, igual que ante un `422`, para que la pantalla muestre que el archivo es demasiado grande.

#### Scenario: Archivo demasiado grande
- **GIVEN** que la API responde `413` con `{"detail": "El archivo supera los 10 MB", "codigo": "ARCHIVO_DEMASIADO_GRANDE"}`
- **WHEN** una pantalla envía un formulario con un archivo
- **THEN** la pantalla recibe un error de validación con el código `ARCHIVO_DEMASIADO_GRANDE`

#### Scenario: 413 sin cuerpo
- **GIVEN** que la API responde `413` sin cuerpo
- **WHEN** una pantalla envía un formulario con un archivo
- **THEN** la pantalla recibe un error de validación sin código

## MODIFIED Requirements

### Requirement: Otras fallas sin detalles internos
Ante cualquier respuesta no exitosa distinta de `401`, `403`, `404`, `409`, `413` y `422`, o si no se puede llegar al servidor, el cliente MUST entregar un error genérico con el estado HTTP (o ninguno si no hubo respuesta) y un mensaje fijo en español, sin incluir el cuerpo de la respuesta ni trazas del servidor.

#### Scenario: Error interno del servidor
- **GIVEN** que la API responde `500` con un cuerpo que contiene una traza
- **WHEN** una pantalla pide `GET /turnos`
- **THEN** la pantalla recibe un error genérico con estado `500`
- **AND** el mensaje del error no contiene el texto de la traza

#### Scenario: Servidor inalcanzable
- **GIVEN** que el pedido falla por un problema de red
- **WHEN** una pantalla pide `GET /turnos`
- **THEN** la pantalla recibe un error genérico sin estado HTTP
