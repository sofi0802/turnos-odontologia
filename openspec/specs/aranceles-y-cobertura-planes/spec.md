# aranceles-y-cobertura-planes Specification

## Purpose

Definir cuánto cuesta cada prestación para un paciente particular (arancel) y qué porcentaje de ese precio cubre cada plan por código del nomenclador, que son los datos con los que se calculan los presupuestos. En los escenarios se usan solo datos ficticios del seed de la KB: las prestaciones "Consulta o control", "Limpieza (profilaxis)", "Tratamiento de conducto" y "Extracción simple"; "Obra Social Ejemplo" con "Plan Base" y "Plan Integral", "Prepaga Prueba" con "Plan Oro"; los códigos `N-0101`, `N-0201` y `N-0301`; y los usuarios `admin`, `recepcion`, `ana.ejemplo` (`ODONTOLOGO`) y `paciente.uno` (`PACIENTE`).

## Requirements

### Requirement: Precio particular de cada prestación
El `ADMIN` SHALL poder cargar y cambiar el precio particular de una prestación, en pesos argentinos. Cada prestación MUST tener como máximo un precio vigente: cargarlo de nuevo reemplaza el anterior y registra el momento del cambio. Una prestación inexistente MUST responder `404` con el código `RECURSO_NO_ENCONTRADO`.

#### Scenario: Cargar el precio de una prestación
- **GIVEN** "Consulta o control" no tiene precio
- **WHEN** `admin` carga el precio de "Consulta o control" en 15000.00
- **THEN** la respuesta es `200` con "Consulta o control" y el precio 15000.00

#### Scenario: Cambiar el precio reemplaza el anterior
- **GIVEN** "Limpieza (profilaxis)" tiene el precio 25000.00
- **WHEN** `admin` carga el precio de "Limpieza (profilaxis)" en 27000.00
- **THEN** la respuesta es `200` con el precio 27000.00
- **AND** la lista de aranceles muestra una sola vez "Limpieza (profilaxis)", con 27000.00

#### Scenario: Precio de una prestación inexistente
- **WHEN** `admin` carga el precio de la prestación con id `999999` en 15000.00
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Precio válido
El precio MUST ser mayor que cero, tener como máximo dos decimales y no superar 9999999999.99. Un precio ausente o inválido MUST responder `422` con el código `DATOS_INVALIDOS` y el campo `precio`, sin cambiar el precio vigente.

#### Scenario: Precio cero
- **GIVEN** "Consulta o control" tiene el precio 15000.00
- **WHEN** `admin` carga el precio de "Consulta o control" en 0
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `precio`
- **AND** "Consulta o control" sigue con 15000.00

#### Scenario: Precio negativo
- **WHEN** `admin` carga el precio de "Consulta o control" en -100.00
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `precio`

#### Scenario: Precio con tres decimales
- **WHEN** `admin` carga el precio de "Consulta o control" en 15000.005
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `precio`

### Requirement: Consulta de los aranceles
El sistema SHALL listar todas las prestaciones ordenadas por nombre, cada una con su estado, su precio particular y la fecha del último cambio, o sin precio si no tiene uno cargado.

#### Scenario: Prestación sin precio en la lista
- **GIVEN** "Consulta o control" tiene el precio 15000.00 y "Tratamiento de conducto" no tiene precio
- **WHEN** `recepcion` lista los aranceles
- **THEN** la respuesta es `200` con "Consulta o control" y 15000.00, y "Tratamiento de conducto" sin precio

### Requirement: Porcentaje de cobertura por plan y código
El `ADMIN` SHALL poder cargar y cambiar, para un plan y un código del nomenclador, el porcentaje del precio particular que cubre el plan. Cada par de plan y código MUST tener como máximo un porcentaje: cargarlo de nuevo lo reemplaza. Un plan que no pertenece a la obra social de la ruta, o un plan o un código inexistentes, MUST responder `404` con `RECURSO_NO_ENCONTRADO`.

#### Scenario: Cargar la cobertura de un código
- **GIVEN** "Plan Base" no tiene cobertura cargada para `N-0201`
- **WHEN** `admin` carga para "Plan Base" de "Obra Social Ejemplo" el código `N-0201` al 50 %
- **THEN** la respuesta es `200` con `N-0201` y el porcentaje 50

#### Scenario: Cambiar el porcentaje lo reemplaza
- **GIVEN** "Plan Oro" cubre `N-0101` al 80 %
- **WHEN** `admin` carga para "Plan Oro" el código `N-0101` al 90 %
- **THEN** la respuesta es `200` con el porcentaje 90
- **AND** la cobertura de "Plan Oro" muestra una sola vez `N-0101`, con 90

#### Scenario: Plan de otra obra social
- **WHEN** `admin` carga la cobertura de `N-0101` al 80 % en la ruta de "Obra Social Ejemplo" con el id de "Plan Oro"
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: Código del nomenclador inexistente
- **WHEN** `admin` carga para "Plan Base" el código con id `999999` al 50 %
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Porcentaje válido
El porcentaje MUST ser un entero de 0 a 100. Un porcentaje ausente, decimal o fuera de ese rango MUST responder `422` con el código `DATOS_INVALIDOS` y el campo `porcentaje`, sin cambiar la cobertura cargada.

#### Scenario: Porcentaje mayor que cien
- **WHEN** `admin` carga para "Plan Base" el código `N-0101` al 101 %
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `porcentaje`

#### Scenario: Porcentaje negativo
- **WHEN** `admin` carga para "Plan Base" el código `N-0101` al -1 %
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `porcentaje`

#### Scenario: Porcentaje con decimales
- **WHEN** `admin` carga para "Plan Base" el código `N-0201` al 50.5 %
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `porcentaje`

#### Scenario: Porcentaje cero
- **WHEN** `admin` carga para "Plan Base" el código `N-0301` al 0 %
- **THEN** la respuesta es `200` con `N-0301` y el porcentaje 0

### Requirement: Consulta de la cobertura de un plan
El sistema SHALL listar la cobertura cargada de un plan, ordenada por código, con el código, su descripción, su estado y el porcentaje. Un código sin porcentaje cargado MUST NOT figurar y MUST considerarse no cubierto (0 %) al calcular un presupuesto.

#### Scenario: Cobertura del seed de "Plan Base"
- **GIVEN** "Plan Base" cubre `N-0201` al 50 % y `N-0101` al 100 %, y no tiene cargado `N-0301`
- **WHEN** `ana.ejemplo` consulta la cobertura de "Plan Base"
- **THEN** la respuesta es `200` con `N-0101` al 100 % y `N-0201` al 50 %, en ese orden, y sin `N-0301`

### Requirement: Sin borrado de aranceles ni coberturas
El sistema MUST NOT ofrecer una operación para borrar un precio particular ni un porcentaje de cobertura. Dejar de cubrir un código MUST hacerse cargando 0 %.

#### Scenario: No se borra un precio
- **GIVEN** "Consulta o control" tiene el precio 15000.00
- **WHEN** `admin` pide `DELETE` sobre el arancel de "Consulta o control"
- **THEN** la respuesta no es exitosa y "Consulta o control" sigue con 15000.00

#### Scenario: No se borra una cobertura
- **GIVEN** "Plan Base" cubre `N-0101` al 100 %
- **WHEN** `admin` pide `DELETE` sobre la cobertura de `N-0101` de "Plan Base"
- **THEN** la respuesta no es exitosa y "Plan Base" sigue cubriendo `N-0101` al 100 %

### Requirement: Permisos sobre aranceles y coberturas
Solo el rol `ADMIN` SHALL cargar y cambiar precios y porcentajes. `RECEPCIONISTA` y `ODONTOLOGO` SHALL poder consultarlos. El sistema MUST responder `403` con el código `ACCESO_DENEGADO`, sin cambiar nada, a una escritura de otro rol y a toda consulta de una sesión `PACIENTE`.

#### Scenario: La recepción no cambia un precio
- **GIVEN** "Consulta o control" tiene el precio 15000.00
- **WHEN** `recepcion` carga el precio de "Consulta o control" en 1.00
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Consulta o control" sigue con 15000.00

#### Scenario: El odontólogo no cambia una cobertura
- **GIVEN** "Plan Base" cubre `N-0201` al 50 %
- **WHEN** `ana.ejemplo` carga para "Plan Base" el código `N-0201` al 100 %
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Plan Base" sigue cubriendo `N-0201` al 50 %

#### Scenario: El paciente no consulta los aranceles
- **WHEN** `paciente.uno` lista los aranceles
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
