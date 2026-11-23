# busqueda-pacientes Specification

## Purpose

Permitir que el personal del consultorio encuentre a un paciente por DNI o por nombre y apellido para darle un turno, con la mínima cantidad de datos personales (Ley 25.326). En los escenarios, con datos ficticios, existen los usuarios `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`) y `ana.ejemplo` (`ODONTOLOGO`), y los pacientes "Paciente Uno" (id 1, DNI `99000001`, `paciente.uno@example.com`, con cuenta), "Paciente Dos" (id 2, DNI `99000002`, `paciente.dos@example.com`), "Carla Ficticia" (id 3, DNI `99000003`) y "Julián Ejemplar" (id 4, DNI `99000004`).

## Requirements

### Requirement: Búsqueda de pacientes por DNI
`GET /api/v1/pacientes?q=` SHALL buscar por DNI cuando `q`, sin los espacios de los extremos, tiene solo dígitos. SHALL devolver `200` con los pacientes cuyo DNI empieza con `q`, lo que incluye el DNI exacto. Sin coincidencias, SHALL devolver `200` con una lista vacía.

#### Scenario: Búsqueda por DNI exacto
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=99000003`
- **THEN** la respuesta es `200` con un solo resultado: id 3, nombre "Carla", apellido "Ficticia" y DNI `99000003`

#### Scenario: Búsqueda por el comienzo del DNI
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=9900000`
- **THEN** la respuesta es `200` con los cuatro pacientes

#### Scenario: DNI sin coincidencias
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=12345678`
- **THEN** la respuesta es `200` con una lista vacía

### Requirement: Búsqueda de pacientes por nombre y apellido
Cuando `q` no tiene solo dígitos, el sistema SHALL separarla en palabras por los espacios y devolver los pacientes en los que cada palabra aparece como parte del nombre o del apellido. La comparación MUST NOT distinguir mayúsculas de minúsculas ni letras con y sin tilde (`á`, `é`, `í`, `ó`, `ú`, `ü`, `ñ`). Los caracteres `%` y `_` SHALL buscarse como texto y no como comodines.

#### Scenario: Búsqueda por parte del apellido
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=fict`
- **THEN** la respuesta es `200` con un solo resultado: "Carla Ficticia" (id 3)

#### Scenario: Búsqueda por nombre y apellido juntos
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=paciente%20dos`
- **THEN** la respuesta es `200` con un solo resultado: "Paciente Dos" (id 2)

#### Scenario: Sin distinguir mayúsculas ni tildes
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=JULIAN`
- **THEN** la respuesta es `200` con un solo resultado: "Julián Ejemplar" (id 4)

#### Scenario: Nombre sin coincidencias
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=zzz`
- **THEN** la respuesta es `200` con una lista vacía

#### Scenario: Los comodines se buscan como texto
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=%25%25` (el texto `%%`)
- **THEN** la respuesta es `200` con una lista vacía

### Requirement: Datos mínimos en la respuesta de la búsqueda
Cada resultado de la búsqueda MUST tener solo las propiedades `id`, `nombre`, `apellido` y `dni`. La respuesta MUST NOT incluir email, teléfono, WhatsApp, consentimiento, datos de la cuenta ni datos de salud del paciente.

#### Scenario: La respuesta no trae otros datos
- **GIVEN** el usuario `recepcion` con sesión iniciada y "Paciente Uno" con email `paciente.uno@example.com` y cuenta de paciente
- **WHEN** pide `GET /api/v1/pacientes?q=99000001`
- **THEN** el único resultado tiene exactamente las propiedades `id`, `nombre`, `apellido` y `dni`
- **AND** el cuerpo de la respuesta no contiene `paciente.uno@example.com`

### Requirement: Límite y orden de los resultados
La búsqueda SHALL devolver como máximo 10 resultados. Por DNI, SHALL ordenarlos por DNI. Por nombre y apellido, SHALL ordenarlos por apellido, después por nombre (sin distinguir mayúsculas ni tildes) y después por id.

#### Scenario: Más coincidencias que el límite
- **GIVEN** el usuario `recepcion` con sesión iniciada y 12 pacientes ficticios con apellido "Prueba"
- **WHEN** pide `GET /api/v1/pacientes?q=prueba`
- **THEN** la respuesta es `200` con 10 resultados

#### Scenario: Orden por apellido y nombre
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=paciente`
- **THEN** la respuesta es `200` con "Paciente Dos" (apellido "Dos") antes que "Paciente Uno" (apellido "Uno")

### Requirement: Consulta de búsqueda inválida
Si falta `q` o, sin los espacios de los extremos, está vacía, tiene menos de 2 caracteres, tiene solo dígitos y menos de 3, o tiene más de 50 caracteres, el sistema MUST responder `422` con el código `DATOS_INVALIDOS` y un error del campo `q` en la lista `errores`. La respuesta MUST NOT repetir el valor de `q`.

#### Scenario: Falta el término
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes` sin `q`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y un error del campo `q`

#### Scenario: Término de una sola letra
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=f`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y un error del campo `q`

#### Scenario: DNI demasiado corto
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=99`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y un error del campo `q`

#### Scenario: Término demasiado largo
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes` con una `q` de 51 letras
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y un error del campo `q`
- **AND** el cuerpo de la respuesta no contiene el valor de `q`

### Requirement: Búsqueda de pacientes solo para el personal
`GET /api/v1/pacientes` SHALL estar disponible para los roles `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`. Sin sesión, MUST responder `401` con el código `NO_AUTENTICADO`. Con una sesión `PACIENTE`, MUST responder `403` con el código `ACCESO_DENEGADO` sin datos de ningún paciente.

#### Scenario: El odontólogo busca un paciente
- **GIVEN** el usuario `ana.ejemplo` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=99000003`
- **THEN** la respuesta es `200` con "Carla Ficticia"

#### Scenario: El administrador busca un paciente
- **GIVEN** el usuario `admin` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=fict`
- **THEN** la respuesta es `200` con "Carla Ficticia"

#### Scenario: Un paciente intenta buscar pacientes
- **GIVEN** "Paciente Uno" con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=99000002`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el cuerpo de la respuesta no contiene `99000002` ni "Dos"

#### Scenario: Búsqueda sin sesión
- **GIVEN** un navegador sin sesión
- **WHEN** pide `GET /api/v1/pacientes?q=99000001`
- **THEN** la respuesta es `401` con el código `NO_AUTENTICADO`

### Requirement: El término buscado se trata como dato personal
El sistema MUST NOT escribir el valor de `q` ni los datos de los resultados en los logs de la aplicación. La respuesta de la búsqueda MUST llevar `Cache-Control` con `no-store`.

#### Scenario: La búsqueda no deja el DNI en los logs
- **GIVEN** el usuario `recepcion` con sesión iniciada y los logs de la aplicación capturados
- **WHEN** pide `GET /api/v1/pacientes?q=99000003` y después `GET /api/v1/pacientes?q=9`
- **THEN** ninguna línea de log contiene `99000003` ni "Ficticia"

#### Scenario: La respuesta no se guarda en caché
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** pide `GET /api/v1/pacientes?q=fict`
- **THEN** la respuesta lleva el encabezado `Cache-Control` con `no-store`
