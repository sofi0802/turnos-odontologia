# frontend-cliente-api Specification

## Purpose
Definir cómo el frontend se comunica con la API REST del consultorio. Cubre a qué dirección envía los pedidos, cómo identifica la sesión, cómo se protege contra CSRF y cómo entrega a las pantallas los errores de negocio y de validación con su código estable, sin exponer detalles internos. En los escenarios, la URL base de ejemplo es `http://localhost:8080/api/v1`.

## Requirements

### Requirement: URL base configurable
El cliente SHALL armar la dirección de cada pedido como la URL base configurada en `VITE_API_BASE_URL` seguida de la ruta del recurso, sin duplicar ni omitir la barra entre las dos. La URL base MUST NOT estar escrita en el código.

#### Scenario: Pedido a un recurso de la API
- **GIVEN** la URL base `http://localhost:8080/api/v1`
- **WHEN** una pantalla pide `GET /turnos`
- **THEN** el pedido se envía a `http://localhost:8080/api/v1/turnos`

#### Scenario: URL base con barra final
- **GIVEN** la URL base `http://localhost:8080/api/v1/`
- **WHEN** una pantalla pide `GET /turnos`
- **THEN** el pedido se envía a `http://localhost:8080/api/v1/turnos`

### Requirement: Envío de la sesión
El cliente MUST enviar la cookie de sesión en todos los pedidos, incluidos los dirigidos a otro origen durante el desarrollo, y MUST NOT leer, guardar ni enviar tokens de sesión por su cuenta (DD-10).

#### Scenario: Pedido con la cookie de sesión
- **WHEN** una pantalla pide `GET /turnos`
- **THEN** el pedido se envía con las credenciales del navegador incluidas

### Requirement: Token CSRF en los pedidos que modifican datos
En los pedidos `POST`, `PUT`, `PATCH` y `DELETE`, el cliente SHALL leer el token de la cookie `XSRF-TOKEN` y enviarlo en el encabezado `X-XSRF-TOKEN`. En `GET` MUST NOT enviar ese encabezado. Si la cookie no existe, el pedido se envía sin el encabezado y el servidor decide.

#### Scenario: POST con token CSRF
- **GIVEN** la cookie `XSRF-TOKEN` con el valor `token-ficticio-123`
- **WHEN** una pantalla pide `POST /turnos`
- **THEN** el pedido lleva el encabezado `X-XSRF-TOKEN: token-ficticio-123`

#### Scenario: GET sin token CSRF
- **GIVEN** la cookie `XSRF-TOKEN` con el valor `token-ficticio-123`
- **WHEN** una pantalla pide `GET /turnos`
- **THEN** el pedido no lleva el encabezado `X-XSRF-TOKEN`

#### Scenario: POST sin cookie CSRF
- **GIVEN** que no existe la cookie `XSRF-TOKEN`
- **WHEN** una pantalla pide `POST /turnos`
- **THEN** el pedido se envía sin el encabezado `X-XSRF-TOKEN`

### Requirement: Cuerpo JSON
El cliente SHALL enviar el cuerpo de los pedidos como JSON con `Content-Type: application/json`. Ante una respuesta exitosa con cuerpo JSON, SHALL entregar el cuerpo interpretado. Ante una respuesta `204` o sin cuerpo, SHALL entregar un resultado vacío sin fallar.

#### Scenario: Respuesta exitosa con datos
- **GIVEN** que la API responde `201` con el cuerpo `{"id": 1, "estado": "RESERVADO"}`
- **WHEN** una pantalla pide `POST /turnos` con un turno ficticio
- **THEN** el pedido lleva el cuerpo en JSON y `Content-Type: application/json`
- **AND** la pantalla recibe el objeto con `id` 1 y `estado` `RESERVADO`

#### Scenario: Respuesta exitosa sin cuerpo
- **GIVEN** que la API responde `204` sin cuerpo
- **WHEN** una pantalla pide `POST /turnos/1/cancelar`
- **THEN** la pantalla recibe un resultado vacío, sin error

### Requirement: Error de negocio ante un 409
Ante un `409`, el cliente MUST entregar un error de negocio con el código de la propiedad `codigo` del `ProblemDetail` (por ejemplo `SOLAPAMIENTO_PROFESIONAL`) y su `detail`, para que la pantalla decida qué mostrar según el código. Si falta el código, usa `CONFLICTO_SIN_CODIGO`.

#### Scenario: Solapamiento por profesional
- **GIVEN** que la API responde `409` con `{"status": 409, "title": "Conflict", "detail": "El profesional ya tiene un turno en ese horario", "codigo": "SOLAPAMIENTO_PROFESIONAL"}`
- **WHEN** una pantalla pide `POST /turnos`
- **THEN** la pantalla recibe un error de negocio con el código `SOLAPAMIENTO_PROFESIONAL`
- **AND** el detalle `El profesional ya tiene un turno en ese horario`

#### Scenario: Conflicto sin código
- **GIVEN** que la API responde `409` con un cuerpo sin la propiedad `codigo`
- **WHEN** una pantalla pide `POST /turnos`
- **THEN** la pantalla recibe un error de negocio con el código `CONFLICTO_SIN_CODIGO`

### Requirement: Error de validación ante un 422
Ante un `422`, el cliente MUST entregar un error de validación con el `detail` del `ProblemDetail`, el código de la propiedad `codigo` si viene (por ejemplo `DATOS_INVALIDOS`) y la lista de la propiedad `errores` como pares de campo y mensaje, distinguible de un error de negocio. Si `errores` falta o no es una lista, la lista queda vacía; se ignoran los elementos sin campo o sin mensaje.

#### Scenario: Inicio en el pasado
- **GIVEN** que la API responde `422` con `{"status": 422, "detail": "El turno empieza en el pasado", "codigo": "INICIO_EN_EL_PASADO"}`
- **WHEN** una pantalla pide `POST /turnos`
- **THEN** la pantalla recibe un error de validación con el código `INICIO_EN_EL_PASADO`
- **AND** el detalle `El turno empieza en el pasado`
- **AND** una lista de errores de campo vacía

#### Scenario: Datos inválidos con errores de campo
- **GIVEN** que la API responde `422` con `{"status": 422, "detail": "Hay datos inválidos en el pedido", "codigo": "DATOS_INVALIDOS", "errores": [{"campo": "inicio", "mensaje": "es obligatorio"}]}`
- **WHEN** una pantalla pide `POST /turnos`
- **THEN** la pantalla recibe un error de validación con el código `DATOS_INVALIDOS`
- **AND** la lista de errores de campo tiene el campo `inicio` con el mensaje `es obligatorio`

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

### Requirement: Errores de sesión, permiso y recurso inexistente
Ante un `401`, `403` o `404`, el cliente MUST entregar un error propio de cada estado, con el código de la propiedad `codigo` del `ProblemDetail` si viene y un mensaje fijo en español, sin el cuerpo de la respuesta. Así una pantalla distingue "sin sesión", "sin permiso" y "no existe" de una falla genérica.

#### Scenario: Sin sesión
- **GIVEN** que la API responde `401` con `{"status": 401, "codigo": "NO_AUTENTICADO"}`
- **WHEN** una pantalla pide `GET /turnos`
- **THEN** la pantalla recibe un error de sesión con el código `NO_AUTENTICADO`

#### Scenario: Credenciales inválidas
- **GIVEN** que la API responde `401` con `{"status": 401, "codigo": "CREDENCIALES_INVALIDAS"}`
- **WHEN** una pantalla pide `POST /public/login`
- **THEN** la pantalla recibe un error de sesión con el código `CREDENCIALES_INVALIDAS`

#### Scenario: Acceso denegado
- **GIVEN** que la API responde `403` con `{"status": 403, "codigo": "ACCESO_DENEGADO"}`
- **WHEN** una pantalla pide `POST /turnos`
- **THEN** la pantalla recibe un error de permiso con el código `ACCESO_DENEGADO`

#### Scenario: Turno inexistente
- **GIVEN** que la API responde `404` con `{"status": 404, "codigo": "TURNO_INEXISTENTE"}`
- **WHEN** una pantalla pide `POST /turnos/999999/cancelar`
- **THEN** la pantalla recibe un error de recurso inexistente con el código `TURNO_INEXISTENTE`

### Requirement: Horarios alternativos en un error de negocio
Ante un `409` cuyo `ProblemDetail` trae la propiedad `alternativas`, el error de negocio SHALL incluir esos horarios, cada uno con su `inicio` y su `fin` tal como los envió la API. MUST descartar los elementos que no tengan los dos valores, y sin la propiedad SHALL entregar una lista vacía.

#### Scenario: Rechazo con alternativas
- **WHEN** un pedido recibe `409` con el código `SIN_DISPONIBILIDAD` y `alternativas` con un horario de `2026-12-01T11:00:00-03:00` a `2026-12-01T11:30:00-03:00`
- **THEN** el cliente entrega un error de negocio con el código `SIN_DISPONIBILIDAD`
- **AND** sus alternativas tienen un horario con `inicio` `2026-12-01T11:00:00-03:00` y `fin` `2026-12-01T11:30:00-03:00`

#### Scenario: Rechazo sin alternativas
- **WHEN** un pedido recibe `409` con el código `SOLAPAMIENTO_PROFESIONAL` y sin la propiedad `alternativas`
- **THEN** el error de negocio tiene una lista de alternativas vacía

#### Scenario: Alternativas incompletas
- **WHEN** un pedido recibe `409` con `alternativas` que contiene un elemento sin `fin`
- **THEN** ese elemento no aparece en las alternativas del error

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
