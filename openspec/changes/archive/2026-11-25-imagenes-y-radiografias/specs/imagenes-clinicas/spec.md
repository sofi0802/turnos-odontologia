# Spec Delta

## Purpose

Permite al odontólogo guardar en la historia clínica las radiografías, fotografías y estudios de cada paciente, consultarlos y descargarlos, y anularlos con motivo sin borrarlos nunca, con el mismo control de acceso y la misma privacidad que la ficha clínica. En los escenarios, con datos ficticios, "Paciente Uno" existe, `ana.ejemplo` y `bruno.prueba` son usuarios `ODONTOLOGO` vinculados a un profesional, `admin` es `ADMIN`, `recepcion` es `RECEPCIONISTA` y `paciente.uno` es el `PACIENTE` de "Paciente Uno".

## ADDED Requirements

### Requirement: Cargar una imagen clínica
Un usuario `ODONTOLOGO` vinculado a un profesional SHALL poder cargar un archivo de un paciente con su categoría (`RADIOGRAFIA`, `FOTOGRAFIA` o `ESTUDIO`), la fecha de la toma y, opcionalmente, la pieza y una descripción de hasta 200 caracteres. La respuesta `201` devuelve los metadatos de la imagen con el profesional de la sesión y el momento de la carga, sin el contenido del archivo.

#### Scenario: Cargar una radiografía de una pieza
- **GIVEN** "Paciente Uno" sin imágenes
- **WHEN** `ana.ejemplo` carga un JPEG de 300 KB como `RADIOGRAFIA` de la pieza 36 tomada el día anterior
- **THEN** la respuesta es `201` con la categoría `RADIOGRAFIA`, la pieza 36, el tipo `image/jpeg`, el tamaño del archivo y el profesional de `ana.ejemplo`
- **AND** "Paciente Uno" tiene una imagen

#### Scenario: Cargar un estudio sin pieza
- **WHEN** `ana.ejemplo` carga un PDF como `ESTUDIO` sin pieza, tomado hoy
- **THEN** la respuesta es `201` con la pieza vacía y el tipo `application/pdf`

#### Scenario: Categoría inexistente
- **WHEN** `ana.ejemplo` carga un PNG con la categoría `TOMOGRAFIA`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** "Paciente Uno" no tiene imágenes nuevas

#### Scenario: Descripción demasiado larga
- **WHEN** `ana.ejemplo` carga un PNG con una descripción de 201 caracteres
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** "Paciente Uno" no tiene imágenes nuevas

#### Scenario: Paciente inexistente
- **WHEN** `ana.ejemplo` carga un JPEG para el paciente con id `999999`
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: Odontólogo sin profesional vinculado
- **GIVEN** un usuario `ODONTOLOGO` que no está vinculado a ningún profesional
- **WHEN** carga un JPEG para "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Paciente Uno" no tiene imágenes nuevas

### Requirement: Tipos de archivo permitidos
El sistema SHALL aceptar solo archivos JPEG, PNG y PDF, y MUST reconocer el tipo por el contenido del archivo, sin confiar en la extensión ni en el tipo que declara el cliente. Un archivo de otro tipo MUST responder `422` con el código `TIPO_ARCHIVO_NO_PERMITIDO`, y un archivo vacío o faltante `422` con `DATOS_INVALIDOS`, sin guardar nada.

#### Scenario: Archivo de un tipo no permitido
- **WHEN** `ana.ejemplo` carga un archivo GIF para "Paciente Uno"
- **THEN** la respuesta es `422` con el código `TIPO_ARCHIVO_NO_PERMITIDO`
- **AND** "Paciente Uno" no tiene imágenes nuevas

#### Scenario: Extensión engañosa
- **WHEN** `ana.ejemplo` carga un archivo de texto llamado `radiografia.jpg` y declarado como `image/jpeg`
- **THEN** la respuesta es `422` con el código `TIPO_ARCHIVO_NO_PERMITIDO`

#### Scenario: El tipo guardado es el del contenido
- **WHEN** `ana.ejemplo` carga un PNG llamado `foto.jpg` y declarado como `image/jpeg`
- **THEN** la respuesta es `201` con el tipo `image/png`

#### Scenario: Archivo vacío
- **WHEN** `ana.ejemplo` carga un archivo de 0 bytes
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`

### Requirement: Tamaño máximo del archivo
El sistema MUST rechazar un archivo de más de 10 MB (10 485 760 bytes) con `413` y el código `ARCHIVO_DEMASIADO_GRANDE`, sin guardar nada. Un archivo de exactamente 10 MB SHALL aceptarse.

#### Scenario: Archivo de más de 10 MB
- **WHEN** `ana.ejemplo` carga un JPEG de 10 485 761 bytes
- **THEN** la respuesta es `413` con el código `ARCHIVO_DEMASIADO_GRANDE`
- **AND** "Paciente Uno" no tiene imágenes nuevas

#### Scenario: Archivo de exactamente 10 MB
- **WHEN** `ana.ejemplo` carga un JPEG de 10 485 760 bytes
- **THEN** la respuesta es `201`

### Requirement: Fecha de la toma y pieza
La fecha de la toma SHALL ser obligatoria y MUST NOT ser posterior a la fecha actual en la zona `America/Argentina/Buenos_Aires`; si falta o es futura, la respuesta es `422` con `DATOS_INVALIDOS`. La pieza, si se indica, MUST ser una pieza válida en notación FDI, permanente o temporal; si no, la respuesta es `422` con `PIEZA_INVALIDA`.

#### Scenario: Fecha de la toma futura
- **WHEN** `ana.ejemplo` carga un JPEG con la fecha de mañana
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** "Paciente Uno" no tiene imágenes nuevas

#### Scenario: Pieza temporal válida
- **WHEN** `ana.ejemplo` carga una `FOTOGRAFIA` de la pieza 55
- **THEN** la respuesta es `201` con la pieza 55

#### Scenario: Pieza inexistente
- **WHEN** `ana.ejemplo` carga una `RADIOGRAFIA` de la pieza 19
- **THEN** la respuesta es `422` con el código `PIEZA_INVALIDA`
- **AND** "Paciente Uno" no tiene imágenes nuevas

### Requirement: Imágenes inmutables
Una imagen cargada MUST NOT modificarse ni borrarse, ni sus metadatos ni su archivo. Lo único que SHALL registrarse después de la carga es su anulación. La API MUST NOT ofrecer operaciones para editar ni borrar imágenes.

#### Scenario: No hay forma de borrar una imagen
- **GIVEN** una imagen de "Paciente Uno"
- **WHEN** `ana.ejemplo` pide borrar esa imagen
- **THEN** la respuesta no es exitosa
- **AND** la imagen y su archivo siguen disponibles

#### Scenario: La base rechaza borrar una imagen
- **GIVEN** una imagen de "Paciente Uno" guardada en la base
- **WHEN** se intenta borrar o cambiar su archivo, su categoría o su fecha directamente en la base
- **THEN** la base rechaza la operación

### Requirement: Listar las imágenes del paciente
Un usuario autorizado SHALL poder listar las imágenes de un paciente, ordenadas por la fecha de la toma de la más nueva a la más vieja y, con la misma fecha, por la carga más nueva primero. El listado MUST NOT incluir las anuladas salvo que se pidan, y SHALL poder filtrarse por pieza.

#### Scenario: Orden del listado
- **GIVEN** "Paciente Uno" con una radiografía tomada el 2026-10-01 y una fotografía tomada el 2026-11-01
- **WHEN** `ana.ejemplo` lista sus imágenes
- **THEN** la respuesta es `200` con la fotografía primero y la radiografía después

#### Scenario: Filtrar por pieza
- **GIVEN** "Paciente Uno" con una imagen de la pieza 36 y otra de la pieza 11
- **WHEN** `ana.ejemplo` lista sus imágenes de la pieza 36
- **THEN** la respuesta tiene solo la imagen de la pieza 36

#### Scenario: Las anuladas no se listan por defecto
- **GIVEN** "Paciente Uno" con una imagen vigente y una anulada
- **WHEN** `ana.ejemplo` lista sus imágenes
- **THEN** la respuesta tiene solo la imagen vigente

#### Scenario: Listar también las anuladas
- **GIVEN** "Paciente Uno" con una imagen vigente y una anulada
- **WHEN** `ana.ejemplo` lista sus imágenes incluyendo las anuladas
- **THEN** la respuesta tiene las dos, y la anulada muestra quién la anuló, cuándo y el motivo

#### Scenario: Paciente sin imágenes
- **WHEN** `ana.ejemplo` lista las imágenes de un paciente sin imágenes
- **THEN** la respuesta es `200` con una lista vacía

### Requirement: Descargar el archivo de una imagen
Un usuario autorizado SHALL poder obtener el archivo de una imagen del paciente, también si está anulada, con el tipo de contenido guardado y el contenido idéntico al cargado. La respuesta MUST NOT guardarse en caché, MUST pedir al navegador que no adivine el tipo y MUST usar un nombre de archivo genérico, sin datos del paciente.

#### Scenario: Ver una radiografía
- **GIVEN** un JPEG cargado para "Paciente Uno"
- **WHEN** `ana.ejemplo` pide su archivo
- **THEN** la respuesta es `200` con el tipo `image/jpeg` y los mismos bytes que se cargaron
- **AND** la respuesta indica que no se guarde en caché y que no se adivine el tipo

#### Scenario: Descargar como adjunto
- **GIVEN** un PDF cargado para "Paciente Uno"
- **WHEN** `ana.ejemplo` pide su archivo para descargarlo
- **THEN** la respuesta lo entrega como adjunto con un nombre genérico que no contiene el nombre ni el DNI del paciente

#### Scenario: Imagen de otro paciente
- **GIVEN** una imagen de "Paciente Uno"
- **WHEN** `ana.ejemplo` pide ese archivo con el id de otro paciente
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: Archivo de una imagen anulada
- **GIVEN** una imagen anulada de "Paciente Uno"
- **WHEN** `admin` pide su archivo
- **THEN** la respuesta es `200` con el archivo

### Requirement: Integridad del archivo
Al cargar una imagen, el sistema SHALL calcular el hash SHA-256 de su contenido, guardarlo y devolverlo con los metadatos. Si al entregar un archivo su contenido no coincide con el hash guardado, el sistema MUST NOT entregarlo y responde un error interno sin datos de la imagen.

#### Scenario: El hash se devuelve al cargar
- **WHEN** `ana.ejemplo` carga un PNG
- **THEN** la respuesta `201` incluye el SHA-256 de ese PNG

#### Scenario: Archivo alterado en el almacén
- **GIVEN** una imagen de "Paciente Uno" cuyo archivo fue alterado en el almacén
- **WHEN** `ana.ejemplo` pide su archivo
- **THEN** la respuesta es `500` y no contiene el contenido del archivo

### Requirement: Anular una imagen con motivo
Un usuario `ODONTOLOGO` vinculado a un profesional SHALL poder anular una imagen vigente con un motivo de 1 a 200 caracteres. El sistema registra el profesional y el momento de la anulación, y la imagen queda anulada para siempre. Anular una imagen ya anulada MUST responder `409` con el código `IMAGEN_YA_ANULADA` sin cambiar la anulación original.

#### Scenario: Anular una imagen
- **GIVEN** una imagen vigente de "Paciente Uno" cargada por `ana.ejemplo`
- **WHEN** `bruno.prueba` la anula con el motivo "Se cargó en el paciente equivocado"
- **THEN** la respuesta es `200` con la imagen anulada por el profesional de `bruno.prueba`, el momento y el motivo

#### Scenario: Anular sin motivo
- **WHEN** `ana.ejemplo` anula una imagen de "Paciente Uno" con el motivo vacío
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la imagen sigue vigente

#### Scenario: Anular dos veces
- **GIVEN** una imagen de "Paciente Uno" anulada por `ana.ejemplo`
- **WHEN** `bruno.prueba` la anula otra vez
- **THEN** la respuesta es `409` con el código `IMAGEN_YA_ANULADA`
- **AND** la anulación sigue siendo la de `ana.ejemplo`

#### Scenario: Anular una imagen inexistente
- **WHEN** `ana.ejemplo` anula la imagen con id `999999` de "Paciente Uno"
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Acceso del odontólogo y del administrador a las imágenes
Un usuario `ODONTOLOGO` SHALL cargar, listar, descargar y anular las imágenes de cualquier paciente. Un usuario `ADMIN` SHALL poder listarlas y descargarlas, y MUST recibir `403` con el código `ACCESO_DENEGADO` si intenta cargar o anular una imagen.

#### Scenario: El administrador lista y descarga
- **GIVEN** "Paciente Uno" con una imagen
- **WHEN** `admin` lista sus imágenes y pide el archivo
- **THEN** las dos respuestas son `200`

#### Scenario: El administrador intenta cargar
- **WHEN** `admin` carga un JPEG para "Paciente Uno"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Paciente Uno" no tiene imágenes nuevas

#### Scenario: El administrador intenta anular
- **GIVEN** una imagen vigente de "Paciente Uno"
- **WHEN** `admin` la anula con un motivo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** la imagen sigue vigente

### Requirement: La recepción y el paciente no acceden a las imágenes
Los usuarios `RECEPCIONISTA` y `PACIENTE` MUST recibir `403` con el código `ACCESO_DENEGADO` al cargar, listar, descargar o anular imágenes, y la respuesta MUST NOT contener metadatos ni contenido de ninguna imagen. Sin sesión, la respuesta MUST ser `401`.

#### Scenario: La recepción lista las imágenes
- **GIVEN** "Paciente Uno" con una imagen
- **WHEN** `recepcion` lista sus imágenes
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** la respuesta no contiene datos de la imagen

#### Scenario: La recepción pide un archivo
- **GIVEN** una imagen de "Paciente Uno"
- **WHEN** `recepcion` pide su archivo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: El paciente pide su propia imagen
- **GIVEN** una imagen de "Paciente Uno"
- **WHEN** `paciente.uno` pide el listado o el archivo
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

#### Scenario: Sin sesión
- **WHEN** alguien sin sesión pide el listado de imágenes de "Paciente Uno"
- **THEN** la respuesta es `401`

#### Scenario: Carga sin token CSRF
- **WHEN** `ana.ejemplo` carga un JPEG sin el token CSRF
- **THEN** la respuesta es `403`
- **AND** "Paciente Uno" no tiene imágenes nuevas

### Requirement: Datos de las imágenes fuera de los canales externos
El sistema MUST NOT escribir la descripción, el motivo de anulación, la categoría ni la pieza de una imagen en los logs, en los mensajes de error ni en la auditoría de turnos (regla dura 6, Ley 25.326). Un error de validación SHALL indicar solo el campo, sin repetir el valor rechazado. La ubicación del archivo en el almacén MUST NOT contener datos del paciente.

#### Scenario: Un error de validación no repite la descripción
- **WHEN** `ana.ejemplo` carga un JPEG con una descripción de 201 caracteres que empieza con "Lesión"
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS`
- **AND** la respuesta no contiene la palabra "Lesión"

#### Scenario: La ubicación del archivo no tiene datos del paciente
- **WHEN** `ana.ejemplo` carga un JPEG para "Paciente Uno"
- **THEN** el archivo guardado en el almacén no tiene en su nombre ni en su ruta el id, el nombre ni el DNI del paciente
