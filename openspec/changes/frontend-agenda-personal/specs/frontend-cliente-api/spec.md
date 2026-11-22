# Spec Delta

## MODIFIED Requirements

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
Ante cualquier respuesta no exitosa distinta de `401`, `403`, `404`, `409` y `422`, o si no se puede llegar al servidor, el cliente MUST entregar un error genérico con el estado HTTP (o ninguno si no hubo respuesta) y un mensaje fijo en español, sin incluir el cuerpo de la respuesta ni trazas del servidor.

#### Scenario: Error interno del servidor
- **GIVEN** que la API responde `500` con un cuerpo que contiene una traza
- **WHEN** una pantalla pide `GET /turnos`
- **THEN** la pantalla recibe un error genérico con estado `500`
- **AND** el mensaje del error no contiene el texto de la traza

#### Scenario: Servidor inalcanzable
- **GIVEN** que el pedido falla por un problema de red
- **WHEN** una pantalla pide `GET /turnos`
- **THEN** la pantalla recibe un error genérico sin estado HTTP

## ADDED Requirements

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
