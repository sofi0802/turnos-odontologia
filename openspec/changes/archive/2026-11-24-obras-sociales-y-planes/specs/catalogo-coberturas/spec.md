# Spec Delta

## Purpose

Permitir que el administrador mantenga el catálogo de coberturas del consultorio: obras sociales y prepagas, sus planes y el nomenclador de prácticas, sin borrar nada para conservar el historial de las afiliaciones. En los escenarios se usan solo datos ficticios: "Obra Social Ejemplo" (`OSE`, tipo `OBRA_SOCIAL`) con "Plan Base" y "Plan Integral", "Prepaga Prueba" (tipo `PREPAGA`) con "Plan Oro", y los códigos `N-0101`, `N-0201` y `N-0301`; los usuarios `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`), `ana.ejemplo` (`ODONTOLOGO`) y `paciente.uno` (`PACIENTE`).

## ADDED Requirements

### Requirement: Alta y modificación de obras sociales con nombre único
El sistema SHALL permitir dar de alta una obra social con nombre, sigla opcional y tipo (`OBRA_SOCIAL` o `PREPAGA`), en estado activo, y modificar esos datos. El nombre MUST ser único entre todas las obras sociales, activas o no, sin distinguir mayúsculas ni espacios en los extremos. Un nombre repetido MUST responder `409` con el código `OBRA_SOCIAL_EXISTENTE` sin guardar nada.

#### Scenario: Alta de una obra social
- **WHEN** `admin` da de alta "Obra Social Ejemplo" con sigla `OSE` y tipo `OBRA_SOCIAL`
- **THEN** la respuesta es `201` con la obra social activa, su sigla y su tipo

#### Scenario: Alta de una prepaga sin sigla
- **WHEN** `admin` da de alta "Prepaga Prueba" con tipo `PREPAGA` y sin sigla
- **THEN** la respuesta es `201` con la obra social activa, de tipo `PREPAGA` y sin sigla

#### Scenario: Nombre repetido con otras mayúsculas
- **GIVEN** existe "Obra Social Ejemplo"
- **WHEN** `admin` da de alta " obra social ejemplo " con tipo `OBRA_SOCIAL`
- **THEN** la respuesta es `409` con el código `OBRA_SOCIAL_EXISTENTE`
- **AND** sigue existiendo una sola obra social con ese nombre

#### Scenario: Modificar el tipo y la sigla
- **GIVEN** existe "Prepaga Prueba" de tipo `PREPAGA` sin sigla
- **WHEN** `admin` la modifica con sigla `PP` y el mismo tipo
- **THEN** la respuesta es `200` y la consulta de "Prepaga Prueba" muestra la sigla `PP`

#### Scenario: Modificar con el nombre de otra obra social
- **GIVEN** existen "Obra Social Ejemplo" y "Prepaga Prueba"
- **WHEN** `admin` cambia el nombre de "Prepaga Prueba" a "Obra Social Ejemplo"
- **THEN** la respuesta es `409` con el código `OBRA_SOCIAL_EXISTENTE`

### Requirement: Planes con nombre único dentro de su obra social
El sistema SHALL permitir dar de alta y modificar planes de una obra social, en estado activo al crearse. El nombre del plan MUST ser único dentro de la misma obra social, sin distinguir mayúsculas; dos obras sociales distintas pueden tener planes con el mismo nombre. Un nombre repetido MUST responder `409` con el código `PLAN_EXISTENTE`. Un plan MUST NOT cambiar de obra social.

#### Scenario: Alta de un plan
- **GIVEN** existe "Obra Social Ejemplo"
- **WHEN** `admin` da de alta "Plan Base" en "Obra Social Ejemplo"
- **THEN** la respuesta es `201` con "Plan Base" activo, de "Obra Social Ejemplo"

#### Scenario: Plan repetido en la misma obra social
- **GIVEN** "Obra Social Ejemplo" tiene "Plan Base"
- **WHEN** `admin` da de alta "PLAN BASE" en "Obra Social Ejemplo"
- **THEN** la respuesta es `409` con el código `PLAN_EXISTENTE`

#### Scenario: Mismo nombre de plan en otra obra social
- **GIVEN** "Obra Social Ejemplo" tiene "Plan Base"
- **WHEN** `admin` da de alta "Plan Base" en "Prepaga Prueba"
- **THEN** la respuesta es `201`

#### Scenario: Plan de una obra social inexistente
- **WHEN** `admin` da de alta "Plan Oro" en la obra social con id `999999`
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: Modificar un plan desde otra obra social
- **GIVEN** "Plan Oro" pertenece a "Prepaga Prueba"
- **WHEN** `admin` modifica "Plan Oro" usando la ruta de "Obra Social Ejemplo"
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`
- **AND** "Plan Oro" sigue perteneciendo a "Prepaga Prueba" sin cambios

### Requirement: Nomenclador con código único
El sistema SHALL permitir dar de alta y modificar códigos del nomenclador con código, descripción y, opcionalmente, la prestación del consultorio a la que se vinculan, en estado activo al crearse. El código MUST ser único sin distinguir mayúsculas, con `409` y `CODIGO_NOMENCLADOR_EXISTENTE` si se repite. Una prestación inexistente MUST responder `422` con el código `PRESTACION_INEXISTENTE`.

#### Scenario: Alta de un código vinculado a una prestación
- **WHEN** `admin` da de alta `N-0101` "Consulta" vinculado a "Consulta o control"
- **THEN** la respuesta es `201` con el código activo y la prestación "Consulta o control"

#### Scenario: Alta de un código sin prestación
- **WHEN** `admin` da de alta `N-0301` "Extracción simple" sin prestación
- **THEN** la respuesta es `201` con el código activo y sin prestación

#### Scenario: Código repetido
- **GIVEN** existe `N-0101`
- **WHEN** `admin` da de alta `n-0101` "Otra consulta"
- **THEN** la respuesta es `409` con el código `CODIGO_NOMENCLADOR_EXISTENTE`

#### Scenario: Prestación inexistente
- **WHEN** `admin` da de alta `N-0201` "Limpieza" vinculado a la prestación con id `999999`
- **THEN** la respuesta es `422` con el código `PRESTACION_INEXISTENTE`
- **AND** no existe el código `N-0201`

#### Scenario: Quitar el vínculo con la prestación
- **GIVEN** `N-0101` está vinculado a "Consulta o control"
- **WHEN** `admin` modifica `N-0101` sin prestación
- **THEN** la respuesta es `200` y `N-0101` queda sin prestación

### Requirement: Desactivar en lugar de borrar
El sistema SHALL activar y desactivar obras sociales, planes y códigos del nomenclador, y MUST NOT ofrecer una operación para borrarlos. Un elemento desactivado MUST seguir apareciendo en las consultas, marcado como inactivo. Desactivar una obra social MUST NOT desactivar sus planes.

#### Scenario: Desactivar una obra social no desactiva sus planes
- **GIVEN** "Obra Social Ejemplo" activa con "Plan Base" activo
- **WHEN** `admin` desactiva "Obra Social Ejemplo"
- **THEN** la respuesta es `200` con la obra social inactiva
- **AND** "Plan Base" sigue activo

#### Scenario: Reactivar un plan
- **GIVEN** "Plan Integral" está inactivo
- **WHEN** `admin` activa "Plan Integral"
- **THEN** la respuesta es `200` con "Plan Integral" activo

#### Scenario: Un código desactivado sigue en la lista
- **GIVEN** `N-0301` está activo
- **WHEN** `admin` desactiva `N-0301` y luego consulta el nomenclador
- **THEN** la lista incluye `N-0301` marcado como inactivo

#### Scenario: No hay borrado
- **WHEN** `admin` pide `DELETE` sobre "Obra Social Ejemplo"
- **THEN** la respuesta no es exitosa y "Obra Social Ejemplo" sigue existiendo

### Requirement: Consulta del catálogo de coberturas
El sistema SHALL listar las obras sociales ordenadas por nombre con su tipo, sigla, estado y sus planes; consultar una obra social o un plan por id; y listar el nomenclador ordenado por código con su descripción, estado y prestación vinculada. Un id inexistente MUST responder `404` con el código `RECURSO_NO_ENCONTRADO`.

#### Scenario: Listar las obras sociales con sus planes
- **GIVEN** "Obra Social Ejemplo" con "Plan Base" y "Plan Integral", y "Prepaga Prueba" con "Plan Oro"
- **WHEN** `recepcion` lista las obras sociales
- **THEN** la respuesta es `200` con "Obra Social Ejemplo" y "Prepaga Prueba", en ese orden, cada una con sus planes

#### Scenario: Obra social inexistente
- **WHEN** `admin` consulta la obra social con id `999999`
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: Listar el nomenclador
- **GIVEN** existen `N-0201`, `N-0101` y `N-0301`
- **WHEN** `ana.ejemplo` lista el nomenclador
- **THEN** la respuesta es `200` con `N-0101`, `N-0201` y `N-0301`, en ese orden

### Requirement: Validación de los datos del catálogo
El sistema MUST rechazar con `422` y el código `DATOS_INVALIDOS`, indicando el campo, un pedido de obra social, plan o código del nomenclador con un campo obligatorio vacío, un tipo que no sea `OBRA_SOCIAL` ni `PREPAGA`, o un texto más largo que el máximo: nombre de obra social 120, sigla 20, nombre de plan 80, código 20 y descripción 200 caracteres.

#### Scenario: Obra social sin nombre
- **WHEN** `admin` da de alta una obra social con nombre en blanco y tipo `OBRA_SOCIAL`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `nombre`

#### Scenario: Tipo inválido
- **WHEN** `admin` da de alta "Obra Social Ejemplo" con tipo `MUTUAL`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

#### Scenario: Descripción demasiado larga
- **WHEN** `admin` da de alta `N-0101` con una descripción de 201 caracteres
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `descripcion`

### Requirement: Permisos sobre el catálogo de coberturas
Solo el rol `ADMIN` SHALL dar de alta, modificar, activar y desactivar obras sociales, planes y códigos del nomenclador. `RECEPCIONISTA` y `ODONTOLOGO` SHALL poder consultarlos. El sistema MUST responder `403` con el código `ACCESO_DENEGADO` a cualquier escritura de otro rol y a toda consulta del catálogo de una sesión `PACIENTE`.

#### Scenario: La recepción no modifica el catálogo
- **WHEN** `recepcion` da de alta "Prepaga Prueba" con tipo `PREPAGA`
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** no existe "Prepaga Prueba"

#### Scenario: El odontólogo no desactiva un plan
- **WHEN** `ana.ejemplo` desactiva "Plan Base"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Plan Base" sigue activo

#### Scenario: El odontólogo consulta el catálogo
- **WHEN** `ana.ejemplo` lista las obras sociales
- **THEN** la respuesta es `200`

#### Scenario: El paciente no consulta el catálogo
- **WHEN** `paciente.uno` lista las obras sociales
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
