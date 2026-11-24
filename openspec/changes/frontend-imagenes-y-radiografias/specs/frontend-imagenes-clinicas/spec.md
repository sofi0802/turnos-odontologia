# Spec Delta

## Purpose

Permitir que el odontólogo y el administrador consulten desde la ficha clínica las radiografías, fotografías y estudios del paciente, que el odontólogo los cargue y los anule con motivo, y que nada de eso quede fuera de la pantalla, sobre la API de C-26. En los escenarios, con datos ficticios, "Paciente Uno" (id 1) existe; `ana.ejemplo` es `ODONTOLOGO` de la Dra. Ana Ejemplo, `bruno.prueba` es `ODONTOLOGO` del Dr. Bruno Prueba y `admin` es `ADMIN`. El día actual es el 2026-12-01.

## ADDED Requirements

### Requirement: Sección de imágenes en la ficha
La ficha de un paciente en `#/pacientes` SHALL mostrar una sección "Imágenes y estudios" debajo del periodontograma, para `ODONTOLOGO` y `ADMIN`. La sección MUST pedir sus datos aparte del resto de la ficha: mientras carga muestra "Cargando imágenes…", y si falla muestra su propio error con "Reintentar" sin ocultar el resto de la ficha.

#### Scenario: La sección aparece en la ficha
- **GIVEN** la sesión de `ana.ejemplo`
- **WHEN** abre la ficha de "Paciente Uno"
- **THEN** la ficha muestra la sección "Imágenes y estudios" debajo del periodontograma

#### Scenario: Falla al cargar las imágenes
- **GIVEN** que el listado de imágenes responde `500` y el resto de la ficha responde bien
- **WHEN** `ana.ejemplo` abre la ficha de "Paciente Uno"
- **THEN** la sección muestra un error y el botón "Reintentar"
- **AND** el odontograma y la ficha básica se ven igual

#### Scenario: Reintentar la carga
- **GIVEN** la sección con el error de carga
- **WHEN** `ana.ejemplo` elige "Reintentar" y el listado responde bien
- **THEN** la sección muestra el listado

### Requirement: Listado de imágenes
La sección SHALL listar las imágenes vigentes del paciente en el orden de la API, de la toma más nueva a la más vieja, sin miniaturas. Cada fila muestra la categoría con su nombre, la fecha de la toma, la pieza si tiene, la descripción si tiene y el profesional que la cargó. Sin imágenes, MUST mostrar "Todavía no hay imágenes ni estudios cargados.".

#### Scenario: Filas del listado
- **GIVEN** "Paciente Uno" con una radiografía de la pieza 36 tomada el 2026-11-30 con la descripción "Control" y un estudio sin pieza tomado el 2026-10-01, cargados por `ana.ejemplo`
- **WHEN** `ana.ejemplo` abre su ficha
- **THEN** la primera fila dice "Radiografía", "30/11/2026", "Pieza 36", "Control" y "Ana Ejemplo"
- **AND** la segunda dice "Estudio" y "01/10/2026", sin pieza

#### Scenario: Paciente sin imágenes
- **GIVEN** "Paciente Uno" sin imágenes
- **WHEN** `ana.ejemplo` abre su ficha
- **THEN** la sección dice "Todavía no hay imágenes ni estudios cargados."

#### Scenario: Sin miniaturas
- **GIVEN** "Paciente Uno" con tres imágenes
- **WHEN** `ana.ejemplo` abre su ficha
- **THEN** no se pide el archivo de ninguna imagen hasta que se abre o se descarga

### Requirement: Filtrar por pieza y mostrar anuladas
La sección SHALL ofrecer un filtro por pieza (todas o una pieza FDI) y la opción "Mostrar anuladas". Al cambiar cualquiera de los dos, MUST volver a pedir el listado con ese filtro. Una imagen anulada SHALL mostrarse marcada como "Anulada", con quién la anuló, cuándo y el motivo, y sin la opción de anularla.

#### Scenario: Filtrar por una pieza
- **GIVEN** "Paciente Uno" con imágenes de las piezas 36 y 11
- **WHEN** `ana.ejemplo` elige la pieza 36 en el filtro
- **THEN** se pide el listado de la pieza 36 y solo se ve la imagen de la pieza 36

#### Scenario: Mostrar las anuladas
- **GIVEN** "Paciente Uno" con una imagen anulada por `bruno.prueba` el 2026-11-30 con el motivo "Duplicada"
- **WHEN** `ana.ejemplo` marca "Mostrar anuladas"
- **THEN** la imagen aparece marcada "Anulada" con "Bruno Prueba", "30/11/2026" y "Duplicada"
- **AND** no tiene la opción "Anular"

### Requirement: Ver una imagen
Para una imagen JPEG o PNG, la opción "Ver" SHALL pedir el archivo y mostrarlo en un visor dentro de la página, con un texto alternativo que dice la categoría, la pieza y la fecha, y la opción de descargarlo. Al cerrar el visor, la imagen MUST dejar de estar en la página. Si el pedido falla, el visor muestra el error.

#### Scenario: Ver una radiografía
- **GIVEN** una radiografía JPEG de la pieza 36 de "Paciente Uno"
- **WHEN** `ana.ejemplo` elige "Ver"
- **THEN** se abre el visor con la imagen y el texto alternativo "Radiografía de la pieza 36 del 30/11/2026"

#### Scenario: Cerrar el visor
- **GIVEN** el visor abierto con una imagen
- **WHEN** `ana.ejemplo` lo cierra
- **THEN** la página ya no contiene la imagen

#### Scenario: Falla al abrir el archivo
- **GIVEN** que el archivo de la imagen responde `500`
- **WHEN** `ana.ejemplo` elige "Ver"
- **THEN** el visor muestra un error en lugar de la imagen

### Requirement: Descargar un archivo
Cada fila SHALL ofrecer "Descargar", que pide el archivo y lo guarda en el equipo con el nombre genérico que da la API (`imagen-<id>.<ext>`), sin el nombre del paciente. Un PDF MUST NOT abrirse ni mostrarse dentro de la página: solo se descarga.

#### Scenario: Descargar un estudio en PDF
- **GIVEN** un estudio PDF con id 7 de "Paciente Uno"
- **WHEN** `ana.ejemplo` elige "Descargar"
- **THEN** el navegador guarda el archivo `imagen-7.pdf`

#### Scenario: Un PDF no se muestra en la página
- **GIVEN** un estudio PDF de "Paciente Uno"
- **WHEN** `ana.ejemplo` mira su fila
- **THEN** la fila ofrece "Descargar" y no ofrece "Ver"

### Requirement: Cargar una imagen
Solo para `ODONTOLOGO`, la sección SHALL ofrecer "Cargar imagen", que abre un diálogo con el archivo, la categoría (Radiografía, Fotografía o Estudio), la fecha de la toma (hoy por defecto), la pieza opcional y la descripción opcional de hasta 200 caracteres con su contador. Al guardar bien, MUST cerrar el diálogo, avisar "Imagen cargada." y volver a pedir el listado.

#### Scenario: Cargar una radiografía
- **GIVEN** la ficha de "Paciente Uno" abierta por `ana.ejemplo`
- **WHEN** carga un JPEG ficticio como Radiografía de la pieza 36 con fecha 2026-11-30 y elige "Cargar"
- **THEN** se envía el archivo con `categoria` `RADIOGRAFIA`, `tomadaEl` `2026-11-30` y `pieza` `36`
- **AND** el diálogo se cierra, se avisa "Imagen cargada." y el listado se vuelve a pedir

#### Scenario: Fecha por defecto
- **WHEN** `ana.ejemplo` abre "Cargar imagen"
- **THEN** la fecha de la toma dice 2026-12-01 y no permite elegir una fecha posterior

### Requirement: Validar el archivo antes de enviarlo
Antes de enviar, el diálogo MUST verificar que haya un archivo, que su tipo sea JPEG, PNG o PDF, que no supere los 10 MB, que haya categoría y fecha, que la fecha no sea futura, que la pieza (si se indica) sea FDI y que la descripción no pase de 200 caracteres. Si algo falla, MUST NOT enviar el pedido y SHALL marcar el campo con un mensaje.

#### Scenario: Archivo de un tipo no permitido
- **WHEN** `ana.ejemplo` elige un archivo GIF y elige "Cargar"
- **THEN** no se envía nada y el campo del archivo dice "Solo se aceptan archivos JPEG, PNG o PDF."

#### Scenario: Archivo de más de 10 MB
- **WHEN** `ana.ejemplo` elige un JPEG de 10 485 761 bytes y elige "Cargar"
- **THEN** no se envía nada y el campo del archivo dice "El archivo supera los 10 MB."

#### Scenario: Sin archivo
- **WHEN** `ana.ejemplo` elige "Cargar" sin elegir un archivo
- **THEN** no se envía nada y el campo del archivo dice "Elegí un archivo."

#### Scenario: Pieza inexistente
- **WHEN** `ana.ejemplo` escribe la pieza 19 y elige "Cargar"
- **THEN** no se envía nada y el campo de la pieza dice "La pieza no existe en la notación FDI."

### Requirement: Errores de la API al cargar
Si la API rechaza la carga, el diálogo SHALL quedar abierto con los datos y mostrar el mensaje del código: `TIPO_ARCHIVO_NO_PERMITIDO`, `ARCHIVO_DEMASIADO_GRANDE`, `PIEZA_INVALIDA` o `DATOS_INVALIDOS`. Ante un error genérico, MUST mostrar el mensaje genérico de la aplicación.

#### Scenario: El servidor rechaza el tipo
- **GIVEN** que la carga responde `422` con el código `TIPO_ARCHIVO_NO_PERMITIDO`
- **WHEN** `ana.ejemplo` elige "Cargar"
- **THEN** el diálogo sigue abierto y dice "Solo se aceptan archivos JPEG, PNG o PDF."

#### Scenario: El servidor rechaza el tamaño
- **GIVEN** que la carga responde `413` con el código `ARCHIVO_DEMASIADO_GRANDE`
- **WHEN** `ana.ejemplo` elige "Cargar"
- **THEN** el diálogo sigue abierto y dice "El archivo supera los 10 MB."

### Requirement: Descartar una carga sin guardar
Si el diálogo de carga tiene un archivo elegido o datos escritos, cerrarlo con "Cancelar" o elegir "Cambiar de paciente" MUST pedir confirmación con "Hay una imagen sin cargar. ¿Descartarla?". Sin datos, se cierra sin preguntar.

#### Scenario: Cancelar con un archivo elegido
- **GIVEN** el diálogo de carga con un archivo elegido
- **WHEN** `ana.ejemplo` elige "Cancelar"
- **THEN** se pregunta "Hay una imagen sin cargar. ¿Descartarla?"

#### Scenario: Cancelar sin datos
- **GIVEN** el diálogo de carga recién abierto
- **WHEN** `ana.ejemplo` elige "Cancelar"
- **THEN** el diálogo se cierra sin preguntar

### Requirement: Anular una imagen
Solo para `ODONTOLOGO`, cada imagen vigente SHALL ofrecer "Anular", que abre un diálogo con el motivo obligatorio de hasta 200 caracteres y explica que la imagen no se borra. Al confirmar, MUST enviar el motivo, avisar "Imagen anulada." y volver a pedir el listado. Si la API responde `IMAGEN_YA_ANULADA`, muestra "La imagen ya estaba anulada." y vuelve a pedir el listado.

#### Scenario: Anular con motivo
- **GIVEN** una imagen vigente de "Paciente Uno"
- **WHEN** `ana.ejemplo` elige "Anular", escribe "Se cargó en el paciente equivocado" y confirma
- **THEN** se envía la anulación con ese motivo, se avisa "Imagen anulada." y el listado se vuelve a pedir

#### Scenario: Anular sin motivo
- **WHEN** `ana.ejemplo` confirma la anulación con el motivo vacío
- **THEN** no se envía nada y el campo del motivo dice "Escribí el motivo de la anulación."

#### Scenario: La imagen ya estaba anulada
- **GIVEN** que la anulación responde `409` con el código `IMAGEN_YA_ANULADA`
- **WHEN** `ana.ejemplo` confirma la anulación
- **THEN** se muestra "La imagen ya estaba anulada." y el listado se vuelve a pedir

### Requirement: Solo lectura para el administrador
Para `ADMIN`, la sección SHALL mostrar el listado, los filtros, "Ver" y "Descargar", y MUST NOT ofrecer "Cargar imagen" ni "Anular".

#### Scenario: El administrador consulta
- **GIVEN** la sesión de `admin` con la ficha de "Paciente Uno" abierta y una imagen vigente
- **WHEN** mira la sección "Imágenes y estudios"
- **THEN** ve la imagen con "Ver" y "Descargar"
- **AND** no ve "Cargar imagen" ni "Anular"

### Requirement: Sección de imágenes accesible
Cada botón de una fila MUST tener un nombre accesible que identifique la imagen (por ejemplo "Ver radiografía de la pieza 36 del 30/11/2026"). Los diálogos y el visor MUST poder usarse con teclado, devolver el foco al botón que los abrió al cerrarse y anunciar los avisos de éxito y de error.

#### Scenario: Nombre accesible de los botones
- **GIVEN** una radiografía de la pieza 36 tomada el 2026-11-30
- **WHEN** se revisan los botones de su fila
- **THEN** se llaman "Ver radiografía de la pieza 36 del 30/11/2026" y "Descargar radiografía de la pieza 36 del 30/11/2026"

#### Scenario: El foco vuelve al cerrar el visor
- **GIVEN** el visor abierto desde "Ver" de una imagen
- **WHEN** se cierra con Escape
- **THEN** el foco vuelve al botón "Ver" de esa imagen

### Requirement: Imágenes solo en la pantalla
La aplicación MUST NOT guardar los datos ni los archivos de las imágenes en la dirección, en `localStorage`, en `sessionStorage` ni en la consola. Las direcciones temporales de los archivos MUST liberarse al cerrar el visor, al terminar una descarga y al salir de la ficha.

#### Scenario: Nada queda guardado en el navegador
- **GIVEN** la ficha de "Paciente Uno" con una imagen con la descripción "Control"
- **WHEN** `ana.ejemplo` la ve y la descarga, y se revisan la dirección, `localStorage`, `sessionStorage` y la consola
- **THEN** ninguno contiene "Control" ni el contenido del archivo

#### Scenario: Se liberan los archivos al cerrar el visor
- **GIVEN** el visor abierto con una imagen
- **WHEN** se cierra
- **THEN** la dirección temporal del archivo se libera
