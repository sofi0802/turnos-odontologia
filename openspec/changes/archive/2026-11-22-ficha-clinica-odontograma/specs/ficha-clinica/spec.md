# Spec Delta

## Purpose

Registrar los datos de salud de cada paciente que el odontólogo necesita para atender de forma segura: la ficha básica, el odontograma en notación FDI con su historial y las ausencias a sus turnos. Solo acceden los roles autorizados (RN-CL-01, Ley 26.529). En los escenarios, con datos ficticios, "Paciente Uno" (DNI `99000001`) existe, `ana.ejemplo` es un usuario `ODONTOLOGO` vinculado a la Dra. Ana Ejemplo, `bruno.prueba` es un `ODONTOLOGO` vinculado al Dr. Bruno Prueba, `admin` es `ADMIN` y `recepcion` es `RECEPCIONISTA`.

## ADDED Requirements

### Requirement: Registrar la ficha básica
Un usuario `ODONTOLOGO` SHALL poder registrar los antecedentes, las alergias y la medicación de un paciente. Cada registro reemplaza el contenido anterior de la ficha, y la ficha guarda el usuario y el momento de la última actualización.

#### Scenario: Registrar la ficha por primera vez
- **GIVEN** "Paciente Uno" sin ficha clínica
- **WHEN** `ana.ejemplo` registra antecedentes "Hipertensión", alergias "Penicilina" y medicación "Enalapril"
- **THEN** la respuesta es `200`
- **AND** la ficha de "Paciente Uno" tiene esos tres datos, actualizada por `ana.ejemplo`

#### Scenario: Actualizar la ficha reemplaza el contenido
- **GIVEN** "Paciente Uno" con alergias "Penicilina", registradas por `ana.ejemplo`
- **WHEN** `bruno.prueba` registra alergias "Penicilina y látex"
- **THEN** la ficha tiene alergias "Penicilina y látex", actualizada por `bruno.prueba`

#### Scenario: Ficha con un texto demasiado largo
- **WHEN** `ana.ejemplo` registra antecedentes de más de 4000 caracteres
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la ficha no cambia

### Requirement: Consultar la ficha básica
El sistema SHALL devolver la ficha de un paciente existente. Si el paciente todavía no tiene ficha, devuelve una ficha vacía en lugar de un error. Un paciente inexistente MUST responder `404` con el código `RECURSO_NO_ENCONTRADO`.

#### Scenario: Paciente sin ficha
- **GIVEN** "Paciente Uno" sin ficha clínica
- **WHEN** `ana.ejemplo` consulta su ficha
- **THEN** la respuesta es `200` con antecedentes, alergias y medicación vacíos

#### Scenario: Ficha de un paciente inexistente
- **WHEN** `ana.ejemplo` consulta la ficha del paciente con id `999999`
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Registrar el odontograma con historial
Un usuario `ODONTOLOGO` vinculado a un profesional SHALL poder registrar en un solo pedido uno o más cambios del odontograma, cada uno con pieza, cara opcional y estado. Cada cambio se guarda como un registro nuevo con el profesional de la sesión y el momento, y MUST NOT modificar ni borrar un registro anterior.

#### Scenario: Registrar una caries
- **WHEN** `ana.ejemplo` registra la pieza 16, cara `O`, estado `CARIES` en el odontograma de "Paciente Uno"
- **THEN** la respuesta es `200`
- **AND** el estado vigente de la pieza 16, cara `O` es `CARIES`, registrado por la Dra. Ana Ejemplo

#### Scenario: Un cambio posterior no sobrescribe el historial
- **GIVEN** la pieza 16, cara `O` con estado `CARIES`, registrada por la Dra. Ana Ejemplo
- **WHEN** `bruno.prueba` registra la pieza 16, cara `O`, estado `OBTURADO`
- **THEN** el estado vigente de la pieza 16, cara `O` es `OBTURADO`
- **AND** el historial de "Paciente Uno" tiene los dos registros, primero `OBTURADO` y después `CARIES`

#### Scenario: Estado de la pieza completa
- **WHEN** `ana.ejemplo` registra la pieza 36 sin cara, con estado `AUSENTE`
- **THEN** el estado vigente de la pieza 36 completa es `AUSENTE`

#### Scenario: Pedido con un cambio inválido no guarda ninguno
- **WHEN** `ana.ejemplo` registra en un mismo pedido la pieza 16, cara `O`, estado `CARIES`, y la pieza 19, cara `O`, estado `CARIES`
- **THEN** la respuesta es `422` con el código `PIEZA_INVALIDA`
- **AND** el odontograma de "Paciente Uno" no tiene registros nuevos

#### Scenario: Odontólogo sin profesional vinculado
- **GIVEN** un usuario `ODONTOLOGO` que no está vinculado a ningún profesional
- **WHEN** registra un cambio en el odontograma de "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Notación FDI
El sistema MUST aceptar solo piezas en notación FDI: permanentes de 11 a 18, 21 a 28, 31 a 38 y 41 a 48, y temporales de 51 a 55, 61 a 65, 71 a 75 y 81 a 85. Otra pieza MUST rechazarse con `422` y el código `PIEZA_INVALIDA`.

#### Scenario: Pieza temporal válida
- **WHEN** `ana.ejemplo` registra la pieza 54, cara `O`, estado `CARIES`
- **THEN** la respuesta es `200`

#### Scenario: Pieza fuera de la notación
- **WHEN** `ana.ejemplo` registra la pieza 56, cara `O`, estado `CARIES`
- **THEN** la respuesta es `422` con el código `PIEZA_INVALIDA`

### Requirement: Caras de la pieza
El sistema MUST aceptar solo las caras `O`, `M`, `D` y `V`, `P` (palatina) solo en piezas superiores (cuadrantes 1, 2, 5 y 6) y `L` (lingual) solo en piezas inferiores (cuadrantes 3, 4, 7 y 8). Otra cara MUST rechazarse con `422` y el código `CARA_INVALIDA`.

#### Scenario: Cara palatina en una pieza superior
- **WHEN** `ana.ejemplo` registra la pieza 21, cara `P`, estado `OBTURADO`
- **THEN** la respuesta es `200`

#### Scenario: Cara palatina en una pieza inferior
- **WHEN** `ana.ejemplo` registra la pieza 31, cara `P`, estado `OBTURADO`
- **THEN** la respuesta es `422` con el código `CARA_INVALIDA`

### Requirement: Catálogo de estados del odontograma
El sistema MUST aceptar solo los estados `SANO`, `CARIES`, `OBTURADO`, `A_TRATAR`, `AUSENTE`, `CORONA` y `ENDODONCIA`. Otro estado MUST rechazarse con `422` y el código `DATOS_INVALIDOS`.

#### Scenario: Estado desconocido
- **WHEN** `ana.ejemplo` registra la pieza 16, cara `O`, estado `ROTO`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

### Requirement: Consultar el odontograma
El sistema SHALL devolver el estado vigente del odontograma de un paciente, es decir, el último registro de cada pieza y cara, y por separado el historial completo, del registro más nuevo al más viejo.

#### Scenario: Odontograma vacío
- **GIVEN** "Paciente Uno" sin registros en el odontograma
- **WHEN** `ana.ejemplo` consulta su odontograma
- **THEN** la respuesta es `200` con una lista vacía

#### Scenario: El estado vigente muestra solo el último registro
- **GIVEN** la pieza 16, cara `O` con `CARIES` y después con `OBTURADO`
- **AND** la pieza 26, cara `M` con `A_TRATAR`
- **WHEN** `ana.ejemplo` consulta el odontograma de "Paciente Uno"
- **THEN** la respuesta tiene dos entradas: pieza 16, cara `O` con `OBTURADO`, y pieza 26, cara `M` con `A_TRATAR`

### Requirement: Ausencias en la ficha
La ficha de un paciente SHALL mostrar los turnos en los que el paciente fue marcado como ausente, con su fecha y hora, y el profesional del turno.

#### Scenario: La ficha muestra la ausencia
- **GIVEN** un turno de "Paciente Uno" con la Dra. Ana Ejemplo el 2026-12-01 a las 10:00, marcado como `AUSENTE`
- **WHEN** `ana.ejemplo` consulta la ficha de "Paciente Uno"
- **THEN** la ficha incluye una ausencia del 2026-12-01 a las 10:00 con la Dra. Ana Ejemplo

### Requirement: Acceso del odontólogo y del administrador
Un usuario `ODONTOLOGO` SHALL leer y modificar la ficha y el odontograma de cualquier paciente. Un usuario `ADMIN` SHALL poder leerlos, y MUST recibir `403` con el código `ACCESO_DENEGADO` si intenta modificarlos.

#### Scenario: El administrador lee la ficha
- **GIVEN** "Paciente Uno" con alergias "Penicilina"
- **WHEN** `admin` consulta su ficha
- **THEN** la respuesta es `200` con alergias "Penicilina"

#### Scenario: El administrador intenta modificar el odontograma
- **WHEN** `admin` registra la pieza 16, cara `O`, estado `CARIES` en el odontograma de "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el odontograma no tiene registros nuevos

### Requirement: La recepción no accede a datos de salud
Un usuario `RECEPCIONISTA` MUST recibir `403` con el código `ACCESO_DENEGADO` en toda consulta o modificación de la ficha, del odontograma y de las ausencias, y la respuesta MUST NOT contener datos de la ficha.

#### Scenario: La recepción consulta la ficha
- **GIVEN** "Paciente Uno" con alergias "Penicilina"
- **WHEN** `recepcion` consulta su ficha
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** la respuesta no contiene "Penicilina"

#### Scenario: La recepción consulta el odontograma
- **WHEN** `recepcion` consulta el odontograma de "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Datos de salud fuera de los canales externos
El sistema MUST NOT escribir el contenido de la ficha ni del odontograma en los logs, en los mensajes de error ni en la auditoría de turnos (regla dura 6, Ley 25.326).

#### Scenario: Un error de validación no repite el contenido
- **WHEN** `ana.ejemplo` registra una ficha con alergias "Penicilina" y antecedentes de más de 4000 caracteres
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la respuesta no contiene "Penicilina"
