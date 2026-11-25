# Spec Delta

## Purpose

Permite al consultorio mantener plantillas de consentimiento informado con versiones, registrar en el consultorio la firma electrónica del paciente (Ley 25.506, art. 5) con su evidencia y un hash verificable, conservar el documento firmado sin cambios y registrar su revocación. En los escenarios, con datos ficticios, "Paciente Uno" existe con DNI `99000001`, `ana.ejemplo` y `bruno.prueba` son usuarios `ODONTOLOGO` vinculados a un profesional, `admin` es `ADMIN`, `recepcion` es `RECEPCIONISTA` y `paciente.uno` es el `PACIENTE` de "Paciente Uno".

## ADDED Requirements

### Requirement: Crear una plantilla de consentimiento
Un usuario `ADMIN` SHALL poder crear una plantilla con un título de 1 a 120 caracteres y un texto plano de 1 a 20 000 caracteres. La plantilla nace activa y con su versión 1. El título MUST ser único entre las plantillas, sin distinguir mayúsculas; si no, la respuesta es `409` con `PLANTILLA_DUPLICADA`.

#### Scenario: Crear una plantilla
- **WHEN** `admin` crea la plantilla "Extracción dental" con un texto de 800 caracteres
- **THEN** la respuesta es `201` con la plantilla activa, su versión 1, el título y el texto
- **AND** la versión 1 registra quién la creó y cuándo

#### Scenario: Texto demasiado largo
- **WHEN** `admin` crea una plantilla con un texto de 20 001 caracteres
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** no se crea ninguna plantilla

#### Scenario: Título vacío
- **WHEN** `admin` crea una plantilla con el título en blanco
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

#### Scenario: Título repetido
- **GIVEN** la plantilla "Extracción dental"
- **WHEN** `admin` crea otra plantilla con el título "EXTRACCIÓN DENTAL"
- **THEN** la respuesta es `409` con el código `PLANTILLA_DUPLICADA`

#### Scenario: El odontólogo intenta crear una plantilla
- **WHEN** `ana.ejemplo` crea una plantilla
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** no se crea ninguna plantilla

### Requirement: Editar una plantilla crea una versión nueva
Un usuario `ADMIN` SHALL editar una plantilla indicando la versión sobre la que trabajó. Cada edición con cambios MUST crear una versión nueva con el número siguiente, quién y cuándo, sin modificar las anteriores. Si la versión indicada no es la última, la respuesta MUST ser `409` con `VERSION_DESACTUALIZADA`.

#### Scenario: Editar una plantilla
- **GIVEN** la plantilla "Extracción dental" en su versión 1
- **WHEN** `admin` la edita sobre la versión 1 con un texto distinto
- **THEN** la respuesta es `201` con la versión 2 y el texto nuevo
- **AND** la versión 1 sigue con su texto original

#### Scenario: Edición sobre una versión vieja
- **GIVEN** la plantilla "Extracción dental" en su versión 2
- **WHEN** `admin` la edita sobre la versión 1
- **THEN** la respuesta es `409` con el código `VERSION_DESACTUALIZADA`
- **AND** la plantilla sigue en su versión 2

#### Scenario: Edición sin cambios
- **GIVEN** la plantilla "Extracción dental" en su versión 1
- **WHEN** `admin` la edita sobre la versión 1 con el mismo título y el mismo texto
- **THEN** la respuesta es `200` con la versión 1
- **AND** no se crea una versión nueva

#### Scenario: La base rechaza modificar una versión
- **GIVEN** una versión de plantilla guardada en la base
- **WHEN** se intenta cambiar su texto o borrarla directamente en la base
- **THEN** la base rechaza la operación

### Requirement: Activar y desactivar una plantilla
Un usuario `ADMIN` SHALL poder desactivar y reactivar una plantilla. Una plantilla inactiva MUST NOT usarse para firmas nuevas y no aparece en el listado del odontólogo. Desactivar una plantilla MUST NOT cambiar los consentimientos ya firmados con ella. Las plantillas no se borran.

#### Scenario: Desactivar una plantilla
- **GIVEN** la plantilla activa "Extracción dental"
- **WHEN** `admin` la desactiva
- **THEN** la respuesta es `200` con la plantilla inactiva
- **AND** `ana.ejemplo` no la ve en el listado de plantillas

#### Scenario: Reactivar una plantilla
- **GIVEN** la plantilla inactiva "Extracción dental"
- **WHEN** `admin` la reactiva
- **THEN** la respuesta es `200` con la plantilla activa

#### Scenario: Los firmados no cambian al desactivar
- **GIVEN** un consentimiento de "Paciente Uno" firmado con "Extracción dental"
- **WHEN** `admin` desactiva la plantilla
- **THEN** el consentimiento sigue con su mismo texto y su hash sigue verificando

#### Scenario: No hay forma de borrar una plantilla
- **WHEN** `admin` pide borrar la plantilla "Extracción dental"
- **THEN** la respuesta no es exitosa
- **AND** la plantilla y sus versiones siguen disponibles

### Requirement: Consultar las plantillas
Un usuario `ADMIN` SHALL listar todas las plantillas, activas e inactivas, y ver todas las versiones de cada una. Un usuario `ODONTOLOGO` SHALL listar solo las plantillas activas con su última versión y ver cualquier versión de una plantilla. Los usuarios `RECEPCIONISTA` y `PACIENTE` MUST recibir `403` con `ACCESO_DENEGADO`.

#### Scenario: El odontólogo lista las plantillas activas
- **GIVEN** la plantilla activa "Extracción dental" y la plantilla inactiva "Blanqueamiento"
- **WHEN** `ana.ejemplo` lista las plantillas
- **THEN** la respuesta es `200` con "Extracción dental" y su última versión, sin "Blanqueamiento"

#### Scenario: El administrador ve las versiones
- **GIVEN** la plantilla "Extracción dental" con las versiones 1 y 2
- **WHEN** `admin` pide sus versiones
- **THEN** la respuesta es `200` con la versión 2 primero y la versión 1 después, cada una con su texto, quién y cuándo

#### Scenario: La recepción lista las plantillas
- **WHEN** `recepcion` lista las plantillas
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: Plantilla inexistente
- **WHEN** `admin` pide la plantilla con id `999999`
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Firmar un consentimiento en el consultorio
Un usuario `ODONTOLOGO` vinculado a un profesional SHALL registrar la firma de un paciente sobre una versión concreta de una plantilla activa, con el nombre completo y el DNI que escribe el paciente y su aceptación. La respuesta `201` devuelve el documento firmado con su hash. La firma es electrónica (Ley 25.506, art. 5) y MUST NOT presentarse como firma digital.

#### Scenario: Firmar un consentimiento
- **GIVEN** la plantilla activa "Extracción dental" en su versión 1
- **WHEN** `ana.ejemplo` registra la firma de "Paciente Uno" sobre la versión 1 con el nombre "Paciente Uno", el DNI `99000001` y la aceptación
- **THEN** la respuesta es `201` con el título y el texto de la versión 1, el firmante, el profesional de `ana.ejemplo`, la fecha y hora, el tipo de firma `ELECTRONICA`, el algoritmo `SHA-256` y el hash
- **AND** "Paciente Uno" tiene un consentimiento vigente

#### Scenario: Sin aceptación
- **WHEN** `ana.ejemplo` registra la firma de "Paciente Uno" sin la aceptación
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** "Paciente Uno" no tiene consentimientos nuevos

#### Scenario: Nombre del firmante vacío
- **WHEN** `ana.ejemplo` registra la firma de "Paciente Uno" con el nombre en blanco
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

#### Scenario: DNI distinto del paciente
- **WHEN** `ana.ejemplo` registra la firma de "Paciente Uno" con el DNI `99000002`
- **THEN** la respuesta es `422` con el código `FIRMANTE_NO_COINCIDE`
- **AND** "Paciente Uno" no tiene consentimientos nuevos

#### Scenario: DNI con puntos
- **WHEN** `ana.ejemplo` registra la firma de "Paciente Uno" con el DNI `99.000.001`
- **THEN** la respuesta es `201` con el DNI del firmante `99000001`

#### Scenario: Versión desactualizada
- **GIVEN** la plantilla "Extracción dental" en su versión 2
- **WHEN** `ana.ejemplo` registra la firma de "Paciente Uno" sobre la versión 1
- **THEN** la respuesta es `409` con el código `VERSION_DESACTUALIZADA`
- **AND** "Paciente Uno" no tiene consentimientos nuevos

#### Scenario: Plantilla inactiva
- **GIVEN** la plantilla inactiva "Blanqueamiento"
- **WHEN** `ana.ejemplo` registra la firma de "Paciente Uno" sobre su última versión
- **THEN** la respuesta es `409` con el código `PLANTILLA_INACTIVA`

#### Scenario: Paciente inexistente
- **WHEN** `ana.ejemplo` registra una firma para el paciente con id `999999`
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: Odontólogo sin profesional vinculado
- **GIVEN** un usuario `ODONTOLOGO` que no está vinculado a ningún profesional
- **WHEN** registra la firma de "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Paciente Uno" no tiene consentimientos nuevos

#### Scenario: El administrador intenta registrar una firma
- **WHEN** `admin` registra la firma de "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Paciente Uno" no tiene consentimientos nuevos

### Requirement: Evidencia de la firma
Al registrar una firma, el sistema SHALL guardar la fecha y hora del servidor, la dirección IP de origen del pedido tal como la ve el servidor y el dispositivo según el encabezado `User-Agent`, recortado a 512 caracteres. Si el pedido no trae `User-Agent`, el dispositivo MUST quedar vacío y la firma se registra igual.

#### Scenario: Se guardan la IP y el dispositivo
- **WHEN** `ana.ejemplo` registra una firma desde la IP `10.0.0.15` con el `User-Agent` "Navegador de prueba 1.0"
- **THEN** el consentimiento guarda la IP `10.0.0.15` y el dispositivo "Navegador de prueba 1.0"

#### Scenario: Sin User-Agent
- **WHEN** `ana.ejemplo` registra una firma sin el encabezado `User-Agent`
- **THEN** la respuesta es `201` con el dispositivo vacío

#### Scenario: User-Agent demasiado largo
- **WHEN** `ana.ejemplo` registra una firma con un `User-Agent` de 600 caracteres
- **THEN** el consentimiento guarda los primeros 512 caracteres

#### Scenario: La fecha es la del servidor
- **GIVEN** el reloj del servidor en 2026-11-24 10:30 en la zona `America/Argentina/Buenos_Aires`
- **WHEN** `ana.ejemplo` registra una firma
- **THEN** la fecha y hora de la firma es 2026-11-24 10:30 en esa zona

### Requirement: El documento firmado guarda su propia copia del texto
El consentimiento firmado SHALL guardar una copia del título y del texto de la versión firmada. Editar, versionar o desactivar la plantilla después MUST NOT cambiar el documento firmado.

#### Scenario: Editar la plantilla después de firmar
- **GIVEN** un consentimiento de "Paciente Uno" firmado sobre la versión 1 de "Extracción dental"
- **WHEN** `admin` edita la plantilla y crea la versión 2 con otro texto
- **THEN** el consentimiento sigue mostrando el título y el texto de la versión 1 y la referencia a la versión 1

### Requirement: Documento firmado bloqueado
Un consentimiento firmado MUST NOT modificarse ni borrarse, ni por la API ni directamente en la base. La API MUST NOT ofrecer operaciones para editar ni borrar consentimientos firmados.

#### Scenario: No hay forma de editar ni borrar un consentimiento
- **GIVEN** un consentimiento firmado de "Paciente Uno"
- **WHEN** `ana.ejemplo` pide modificarlo o borrarlo
- **THEN** la respuesta no es exitosa
- **AND** el consentimiento sigue igual y su hash sigue verificando

#### Scenario: La base rechaza modificar un consentimiento
- **GIVEN** un consentimiento firmado guardado en la base
- **WHEN** se intenta cambiar su texto, su firmante, su fecha o su hash, o borrarlo, directamente en la base
- **THEN** la base rechaza la operación

### Requirement: Hash del documento firmado
El sistema SHALL calcular al firmar un hash SHA-256 sobre un contenido canónico del documento, que incluye el paciente, la versión de la plantilla, el título y el texto firmados, el nombre y el DNI del firmante, el profesional, la fecha y hora, la IP y el dispositivo. El mismo contenido MUST dar siempre el mismo hash, y cualquier cambio en uno de esos datos MUST dar otro hash.

#### Scenario: El hash coincide con el contenido canónico
- **GIVEN** un consentimiento con datos fijos y su contenido canónico de referencia
- **WHEN** se calcula su hash
- **THEN** el hash es el SHA-256 de referencia de ese contenido

#### Scenario: Un cambio en el texto cambia el hash
- **GIVEN** dos consentimientos iguales salvo por una letra del texto
- **WHEN** se calculan sus hashes
- **THEN** los hashes son distintos

#### Scenario: Los campos no se pueden correr de lugar
- **GIVEN** dos consentimientos donde el final del nombre del firmante de uno es el principio del DNI del otro
- **WHEN** se calculan sus hashes
- **THEN** los hashes son distintos

### Requirement: Verificar el hash de un consentimiento
Un usuario `ADMIN` u `ODONTOLOGO` SHALL pedir la verificación de un consentimiento: el sistema recalcula el hash con los datos guardados y responde si coincide con el hash guardado, con los dos valores y el algoritmo. Una verificación que no coincide MUST responder `200` con el resultado negativo, sin ocultarlo.

#### Scenario: Consentimiento íntegro
- **GIVEN** un consentimiento firmado de "Paciente Uno"
- **WHEN** `admin` pide su verificación
- **THEN** la respuesta es `200` con el resultado íntegro, el algoritmo `SHA-256` y el hash guardado igual al calculado

#### Scenario: Consentimiento alterado en la base
- **GIVEN** un consentimiento de "Paciente Uno" cuyo texto se alteró en la base con los triggers desactivados
- **WHEN** `ana.ejemplo` pide su verificación
- **THEN** la respuesta es `200` con el resultado no íntegro y el hash calculado distinto del guardado

### Requirement: Listar y ver los consentimientos del paciente
Un usuario `ADMIN` u `ODONTOLOGO` SHALL listar los consentimientos de un paciente, del más nuevo al más viejo, con el título, la versión, la fecha, el profesional y si están vigentes o revocados, sin el texto. SHALL poder ver un consentimiento completo, con el texto, el firmante, la IP, el dispositivo, el hash y la revocación si la hay.

#### Scenario: Orden del listado
- **GIVEN** "Paciente Uno" con un consentimiento firmado el 2026-10-01 y otro firmado el 2026-11-01
- **WHEN** `ana.ejemplo` lista sus consentimientos
- **THEN** la respuesta es `200` con el del 2026-11-01 primero y el del 2026-10-01 después, sin el texto

#### Scenario: Ver un consentimiento completo
- **GIVEN** un consentimiento firmado de "Paciente Uno"
- **WHEN** `ana.ejemplo` lo pide
- **THEN** la respuesta es `200` con el texto, el firmante, la fecha, la IP, el dispositivo, el algoritmo y el hash

#### Scenario: Consentimiento de otro paciente
- **GIVEN** un consentimiento de "Paciente Uno"
- **WHEN** `ana.ejemplo` lo pide con el id de otro paciente
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: Paciente sin consentimientos
- **WHEN** `ana.ejemplo` lista los consentimientos de un paciente sin consentimientos
- **THEN** la respuesta es `200` con una lista vacía

### Requirement: Revocar un consentimiento con registro
Un usuario `ODONTOLOGO` vinculado a un profesional SHALL registrar la revocación de un consentimiento vigente con un motivo de 1 a 200 caracteres; quedan el profesional y el momento. La revocación ocurre una sola vez: revocar uno ya revocado MUST responder `409` con `CONSENTIMIENTO_YA_REVOCADO`. El documento firmado no cambia y su hash sigue verificando.

#### Scenario: Revocar un consentimiento
- **GIVEN** un consentimiento vigente de "Paciente Uno" registrado por `ana.ejemplo`
- **WHEN** `bruno.prueba` registra su revocación con el motivo "El paciente decidió no hacer el tratamiento"
- **THEN** la respuesta es `200` con el consentimiento revocado por el profesional de `bruno.prueba`, el momento y el motivo
- **AND** la verificación del consentimiento sigue dando íntegro con el mismo hash

#### Scenario: Revocar sin motivo
- **WHEN** `ana.ejemplo` revoca un consentimiento de "Paciente Uno" con el motivo vacío
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** el consentimiento sigue vigente

#### Scenario: Revocar dos veces
- **GIVEN** un consentimiento de "Paciente Uno" revocado por `ana.ejemplo`
- **WHEN** `bruno.prueba` lo revoca otra vez
- **THEN** la respuesta es `409` con el código `CONSENTIMIENTO_YA_REVOCADO`
- **AND** la revocación sigue siendo la de `ana.ejemplo`

#### Scenario: La base rechaza modificar una revocación
- **GIVEN** una revocación guardada en la base
- **WHEN** se intenta cambiar su motivo o borrarla directamente en la base
- **THEN** la base rechaza la operación

#### Scenario: El administrador intenta revocar
- **GIVEN** un consentimiento vigente de "Paciente Uno"
- **WHEN** `admin` registra su revocación con un motivo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el consentimiento sigue vigente

### Requirement: La recepción y el paciente no acceden a los consentimientos firmados
Los usuarios `RECEPCIONISTA` y `PACIENTE` MUST recibir `403` con el código `ACCESO_DENEGADO` al firmar, listar, ver, verificar o revocar consentimientos, y la respuesta MUST NOT contener datos de ningún consentimiento. Sin sesión, la respuesta MUST ser `401`.

#### Scenario: La recepción lista los consentimientos
- **GIVEN** "Paciente Uno" con un consentimiento
- **WHEN** `recepcion` lista sus consentimientos
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** la respuesta no contiene datos del consentimiento

#### Scenario: El paciente pide su propio consentimiento
- **GIVEN** un consentimiento de "Paciente Uno"
- **WHEN** `paciente.uno` pide el listado o el consentimiento
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: Sin sesión
- **WHEN** alguien sin sesión pide el listado de consentimientos de "Paciente Uno"
- **THEN** la respuesta es `401`

#### Scenario: Firma sin token CSRF
- **WHEN** `ana.ejemplo` registra la firma de "Paciente Uno" sin el token CSRF
- **THEN** la respuesta es `403`
- **AND** "Paciente Uno" no tiene consentimientos nuevos

### Requirement: Datos de los consentimientos fuera de los canales externos
El sistema MUST NOT escribir el texto, el nombre ni el DNI del firmante, la IP, el dispositivo ni el motivo de revocación en los logs, en los mensajes de error ni en la auditoría de turnos (regla dura 6, Ley 25.326). Un error de validación SHALL indicar solo el campo, sin repetir el valor rechazado.

#### Scenario: Un error no repite el DNI escrito
- **WHEN** `ana.ejemplo` registra la firma de "Paciente Uno" con el DNI `99000002`
- **THEN** la respuesta es `422` con el código `FIRMANTE_NO_COINCIDE`
- **AND** la respuesta no contiene `99000002` ni el DNI del paciente

#### Scenario: La firma no escribe datos en el log
- **WHEN** `ana.ejemplo` registra la firma de "Paciente Uno" con el nombre "Paciente Uno" y el DNI `99000001`
- **THEN** la salida del log de la aplicación no contiene "Paciente Uno", `99000001` ni el texto firmado
