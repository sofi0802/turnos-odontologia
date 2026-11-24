# Spec Delta

## Purpose

Permite al odontólogo registrar el sondaje periodontal de cada pieza en cada visita, conservar todas las versiones y ver el estado periodontal calculado de cada pieza, con el mismo control de acceso que la ficha clínica.

## ADDED Requirements

### Requirement: Registrar una versión del periodontograma
Un usuario `ODONTOLOGO` vinculado a un profesional SHALL poder registrar el periodontograma de un paciente con una o más piezas. Cada registro crea una versión nueva con el profesional de la sesión y el momento del registro, y la respuesta `201` devuelve esa versión con sus piezas y el estado de cada una. Un pedido sin piezas o con una pieza repetida MUST responder `422` con el código `DATOS_INVALIDOS` sin guardar nada.

#### Scenario: Registrar la primera versión
- **GIVEN** "Paciente Uno" sin periodontograma
- **WHEN** `ana.ejemplo` registra la pieza 16 con sus 6 sitios, movilidad 0 y furcación 0
- **THEN** la respuesta es `201` con una versión registrada por `ana.ejemplo` que tiene la pieza 16
- **AND** "Paciente Uno" tiene una versión de periodontograma

#### Scenario: Registro sin piezas
- **WHEN** `ana.ejemplo` registra un periodontograma sin piezas para "Paciente Uno"
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** "Paciente Uno" no tiene versiones nuevas

#### Scenario: Pieza repetida en el mismo registro
- **WHEN** `ana.ejemplo` registra dos veces la pieza 21 en el mismo pedido
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** "Paciente Uno" no tiene versiones nuevas

#### Scenario: Paciente inexistente
- **WHEN** `ana.ejemplo` registra un periodontograma para el paciente con id `999999`
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: Odontólogo sin profesional vinculado
- **GIVEN** un usuario `ODONTOLOGO` que no está vinculado a ningún profesional
- **WHEN** registra un periodontograma para "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Paciente Uno" no tiene versiones nuevas

### Requirement: Versiones inmutables
Una versión registrada MUST NOT modificarse ni borrarse. Para corregir datos, el odontólogo SHALL registrar una versión nueva, y la anterior queda igual.

#### Scenario: Una versión nueva no cambia la anterior
- **GIVEN** "Paciente Uno" con una versión en la que la pieza 11 tiene profundidad 3 mm en el sitio `V`
- **WHEN** `bruno.prueba` registra otra versión con profundidad 5 mm en el sitio `V` de la pieza 11
- **THEN** "Paciente Uno" tiene dos versiones
- **AND** la primera sigue con profundidad 3 mm en el sitio `V` de la pieza 11

#### Scenario: La base rechaza modificar o borrar una versión
- **GIVEN** una versión registrada de "Paciente Uno"
- **WHEN** se intenta modificar o borrar en la base una fila de la versión, de sus piezas o de sus sitios
- **THEN** la base rechaza la operación y la versión queda igual

### Requirement: Solo piezas permanentes
El periodontograma SHALL aceptar solo piezas permanentes en notación FDI (11–18, 21–28, 31–38, 41–48). Una pieza temporal o un número que no existe en la notación FDI MUST responder `422` con el código `PIEZA_INVALIDA` sin guardar nada.

#### Scenario: Pieza temporal
- **WHEN** `ana.ejemplo` registra la pieza 55
- **THEN** la respuesta es `422` con el código `PIEZA_INVALIDA`
- **AND** "Paciente Uno" no tiene versiones nuevas

#### Scenario: Pieza fuera de la notación FDI
- **WHEN** `ana.ejemplo` registra la pieza 19
- **THEN** la respuesta es `422` con el código `PIEZA_INVALIDA`

### Requirement: Seis sitios por pieza presente
Cada pieza presente SHALL llevar exactamente los 6 sitios `MV`, `V`, `DV`, `ML`, `L` y `DL`, cada uno una sola vez, con profundidad de sondaje, recesión, sangrado y placa, y la movilidad de la pieza. Si falta un sitio, se repite o falta la movilidad, la respuesta MUST ser `422` con el código `SITIOS_INCOMPLETOS` sin guardar nada.

#### Scenario: Falta un sitio
- **WHEN** `ana.ejemplo` registra la pieza 36 con 5 sitios, sin `DL`
- **THEN** la respuesta es `422` con el código `SITIOS_INCOMPLETOS`
- **AND** "Paciente Uno" no tiene versiones nuevas

#### Scenario: Sitio repetido
- **WHEN** `ana.ejemplo` registra la pieza 36 con el sitio `V` dos veces y sin `MV`
- **THEN** la respuesta es `422` con el código `SITIOS_INCOMPLETOS`

#### Scenario: Falta la movilidad
- **WHEN** `ana.ejemplo` registra la pieza 11 con sus 6 sitios y sin movilidad
- **THEN** la respuesta es `422` con el código `SITIOS_INCOMPLETOS`

### Requirement: Rangos de los valores
La profundidad de sondaje MUST estar entre 0 y 15 mm, la recesión entre −5 y 15 mm (enteros), y la movilidad y la furcación entre 0 y 3. Un valor fuera de rango MUST responder `422` con el código `DATOS_INVALIDOS` sin guardar nada.

#### Scenario: Profundidad fuera de rango
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 16 mm en el sitio `MV`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** "Paciente Uno" no tiene versiones nuevas

#### Scenario: Recesión negativa permitida
- **WHEN** `ana.ejemplo` registra la pieza 11 con recesión −2 mm en el sitio `V`
- **THEN** la respuesta es `201`

#### Scenario: Recesión fuera de rango
- **WHEN** `ana.ejemplo` registra la pieza 11 con recesión −6 mm en el sitio `V`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

#### Scenario: Movilidad fuera de rango
- **WHEN** `ana.ejemplo` registra la pieza 11 con movilidad 4
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

### Requirement: Furcación solo en piezas multirradiculares
La furcación SHALL registrarse solo en los molares (16–18, 26–28, 36–38, 46–48) y en los primeros premolares superiores (14 y 24), y es opcional en ellos. Una furcación en otra pieza MUST responder `422` con el código `FURCACION_NO_APLICA` sin guardar nada.

#### Scenario: Furcación en un molar
- **WHEN** `ana.ejemplo` registra la pieza 46 con furcación 2
- **THEN** la respuesta es `201` y la pieza 46 tiene furcación 2

#### Scenario: Furcación en el primer premolar superior
- **WHEN** `ana.ejemplo` registra la pieza 14 con furcación 1
- **THEN** la respuesta es `201`

#### Scenario: Furcación en un incisivo
- **WHEN** `ana.ejemplo` registra la pieza 11 con furcación 1
- **THEN** la respuesta es `422` con el código `FURCACION_NO_APLICA`
- **AND** "Paciente Uno" no tiene versiones nuevas

#### Scenario: Molar sin furcación
- **WHEN** `ana.ejemplo` registra la pieza 36 con sus 6 sitios y movilidad, sin furcación
- **THEN** la respuesta es `201` y la pieza 36 no tiene furcación

### Requirement: Pieza ausente
Una pieza marcada ausente SHALL guardarse sin sitios, sin movilidad, sin furcación y sin estado. Una pieza ausente con sitios, movilidad o furcación MUST responder `422` con el código `DATOS_INVALIDOS` sin guardar nada.

#### Scenario: Registrar una pieza ausente
- **WHEN** `ana.ejemplo` registra la pieza 18 como ausente
- **THEN** la respuesta es `201` y la pieza 18 figura ausente y sin estado

#### Scenario: Pieza ausente con datos
- **WHEN** `ana.ejemplo` registra la pieza 18 como ausente con movilidad 1
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

### Requirement: Estado periodontal de cada pieza
El sistema SHALL calcular el estado de cada pieza presente por el peor de sus 6 sitios, con la pérdida de inserción igual a profundidad de sondaje + recesión: `SANO` si la pérdida máxima es 0 o menos, todas las profundidades son de 3 mm o menos y no hay sangrado; `LEVE` si la pérdida máxima es de 1 a 2 mm, o si es 0 o menos con sangrado o con alguna profundidad mayor de 3 mm; `MODERADO` de 3 a 4 mm; `SEVERO` de 5 mm o más.

#### Scenario: Pieza sana
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 2 mm, recesión −2 mm y sin sangrado en los 6 sitios
- **THEN** la pieza 11 tiene estado `SANO`

#### Scenario: Recesión negativa compensa la profundidad
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 3 mm, recesión −3 mm y sin sangrado en los 6 sitios
- **THEN** la pieza 11 tiene estado `SANO`

#### Scenario: Sangrado sin pérdida de inserción
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 2 mm y recesión −2 mm en los 6 sitios, y sangrado en el sitio `V`
- **THEN** la pieza 11 tiene estado `LEVE`

#### Scenario: Bolsa sin pérdida de inserción
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 4 mm y recesión −4 mm en `V`, y profundidad 2 mm y recesión −2 mm en el resto, sin sangrado
- **THEN** la pieza 11 tiene estado `LEVE`

#### Scenario: Pérdida leve
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 2 mm y recesión 0 mm en `MV` (pérdida de 2 mm), y profundidad 2 mm y recesión −2 mm en el resto, sin sangrado
- **THEN** la pieza 11 tiene estado `LEVE`

#### Scenario: Límite entre leve y moderado
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 2 mm y recesión 1 mm en `DV`, y profundidad 2 mm y recesión −2 mm en el resto
- **THEN** la pieza 11 tiene estado `MODERADO`

#### Scenario: Pérdida moderada
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 4 mm y recesión 0 mm en `L`, y profundidad 2 mm y recesión −2 mm en el resto
- **THEN** la pieza 11 tiene estado `MODERADO`

#### Scenario: Límite entre moderado y severo
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 3 mm y recesión 2 mm en `ML`, y profundidad 2 mm y recesión −2 mm en el resto
- **THEN** la pieza 11 tiene estado `SEVERO`

#### Scenario: Pérdida severa
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 7 mm y recesión 1 mm en `DL`
- **THEN** la pieza 11 tiene estado `SEVERO`

### Requirement: Consultar las versiones del periodontograma
El sistema SHALL listar las versiones de un paciente de la más nueva a la más vieja, con el momento y el profesional de cada una, y SHALL devolver una versión completa con sus piezas, sus sitios y el estado de cada pieza. Un paciente sin periodontograma devuelve una lista vacía. Un paciente inexistente, o una versión que no existe o es de otro paciente, MUST responder `404` con el código `RECURSO_NO_ENCONTRADO`.

#### Scenario: Listar las versiones
- **GIVEN** "Paciente Uno" con una versión de `ana.ejemplo` y otra posterior de `bruno.prueba`
- **WHEN** `ana.ejemplo` lista su periodontograma
- **THEN** la respuesta es `200` con dos versiones, primero la de `bruno.prueba`

#### Scenario: Paciente sin periodontograma
- **GIVEN** "Paciente Uno" sin periodontograma
- **WHEN** `ana.ejemplo` lista su periodontograma
- **THEN** la respuesta es `200` con una lista vacía

#### Scenario: Consultar una versión
- **GIVEN** "Paciente Uno" con una versión que tiene la pieza 46 con furcación 2 y la pieza 18 ausente
- **WHEN** `bruno.prueba` consulta esa versión
- **THEN** la respuesta es `200` con las piezas 46 y 18, los 6 sitios de la 46 y su estado, y la 18 ausente

#### Scenario: Versión de otro paciente
- **GIVEN** una versión de "Paciente Dos"
- **WHEN** `ana.ejemplo` la consulta como versión de "Paciente Uno"
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: Listar el periodontograma de un paciente inexistente
- **WHEN** `ana.ejemplo` lista el periodontograma del paciente con id `999999`
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Acceso del odontólogo y del administrador al periodontograma
Un usuario `ODONTOLOGO` SHALL registrar y consultar el periodontograma de cualquier paciente. Un usuario `ADMIN` SHALL poder consultarlo, y MUST recibir `403` con el código `ACCESO_DENEGADO` si intenta registrarlo.

#### Scenario: El administrador consulta el periodontograma
- **GIVEN** "Paciente Uno" con una versión de periodontograma
- **WHEN** `admin` lista su periodontograma
- **THEN** la respuesta es `200` con una versión

#### Scenario: El administrador intenta registrar
- **WHEN** `admin` registra un periodontograma para "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Paciente Uno" no tiene versiones nuevas

### Requirement: La recepción no accede al periodontograma
Un usuario `RECEPCIONISTA` MUST recibir `403` con el código `ACCESO_DENEGADO` al listar, consultar o registrar el periodontograma, y la respuesta MUST NOT contener datos del periodontograma.

#### Scenario: La recepción lista el periodontograma
- **GIVEN** "Paciente Uno" con una versión de periodontograma
- **WHEN** `recepcion` lista su periodontograma
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** la respuesta no contiene datos de la versión

#### Scenario: La recepción consulta una versión
- **GIVEN** una versión de "Paciente Uno"
- **WHEN** `recepcion` consulta esa versión
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: La recepción intenta registrar
- **WHEN** `recepcion` registra un periodontograma para "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Paciente Uno" no tiene versiones nuevas

### Requirement: Datos del periodontograma fuera de los canales externos
El sistema MUST NOT escribir los valores del periodontograma ni el estado de las piezas en los logs, en los mensajes de error ni en la auditoría de turnos (regla dura 6, Ley 25.326). Un error de validación SHALL indicar solo el campo, sin repetir el valor rechazado.

#### Scenario: Un error de validación no repite el valor
- **WHEN** `ana.ejemplo` registra la pieza 11 con profundidad 16 mm en el sitio `MV`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la respuesta no contiene el valor 16
