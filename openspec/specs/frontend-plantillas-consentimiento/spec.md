# frontend-plantillas-consentimiento Specification

## Purpose

Permitir que el administrador cree, edite con versiones, consulte y active o desactive las plantillas de consentimiento informado desde la pestaña "Consentimientos" de Configuración, sobre la API de C-27, sin borrar nada. En los escenarios, con datos ficticios, `admin` es `ADMIN`, `recepcion` es `RECEPCIONISTA` y `ana.ejemplo` es `ODONTOLOGO`; la plantilla "Consentimiento para extracción dentaria" (id 4) está activa en la versión 3 y "Consentimiento para blanqueamiento" (id 5) está inactiva en la versión 1. El día actual es el 2026-11-24.

## Requirements

### Requirement: Pestaña "Consentimientos" solo para el administrador
Con sesión `ADMIN`, `#/configuracion` SHALL tener la pestaña "Consentimientos", que lista las plantillas activas e inactivas. Con sesión `RECEPCIONISTA` u `ODONTOLOGO`, la pestaña MUST NOT aparecer y la página MUST NOT pedir `/api/v1/consentimientos/plantillas`.

#### Scenario: El administrador abre la pestaña
- **GIVEN** la sesión de `admin`
- **WHEN** elige la pestaña "Consentimientos" en `#/configuracion`
- **THEN** se pide `GET /api/v1/consentimientos/plantillas?incluirInactivas=true`
- **AND** se ven las dos plantillas

#### Scenario: El odontólogo no ve la pestaña
- **GIVEN** la sesión de `ana.ejemplo`
- **WHEN** se abre `#/configuracion`
- **THEN** no hay una pestaña "Consentimientos"
- **AND** no se envía ningún pedido a `/api/v1/consentimientos/plantillas`

#### Scenario: La recepción no ve la pestaña
- **GIVEN** la sesión de `recepcion`
- **WHEN** se abre `#/configuracion`
- **THEN** no hay una pestaña "Consentimientos"

### Requirement: Lista de plantillas
Cada plantilla de la lista SHALL mostrar su título, "Versión N", la fecha de su última versión y su estado escrito, "Activa" o "Inactiva", sin depender solo del color. La lista MUST conservar el orden de la API. Sin plantillas, MUST mostrar "Todavía no hay plantillas de consentimiento.".

#### Scenario: Filas de la lista
- **GIVEN** la sesión de `admin` en la pestaña "Consentimientos"
- **WHEN** se dibuja la lista
- **THEN** la primera fila dice "Consentimiento para extracción dentaria", "Versión 3", "Actualizada el 24/11/2026" y "Activa"
- **AND** la segunda dice "Consentimiento para blanqueamiento", "Versión 1" e "Inactiva"

#### Scenario: Sin plantillas
- **GIVEN** que la API devuelve una lista vacía
- **WHEN** `admin` abre la pestaña "Consentimientos"
- **THEN** se ve "Todavía no hay plantillas de consentimiento." y el botón "Nueva plantilla"

#### Scenario: Falla al cargar la lista
- **GIVEN** que el listado responde `500`
- **WHEN** `admin` abre la pestaña "Consentimientos"
- **THEN** se ve un error con "Reintentar" y las otras pestañas siguen funcionando

### Requirement: Crear una plantilla
"Nueva plantilla" SHALL abrir un diálogo con el título y el texto vacíos. Al guardar bien, MUST enviar el título y el texto, cerrar el diálogo, avisar "Plantilla creada." y volver a pedir la lista.

#### Scenario: Crear una plantilla
- **GIVEN** la sesión de `admin` en la pestaña "Consentimientos"
- **WHEN** elige "Nueva plantilla", escribe el título "Consentimiento para implante" y un texto ficticio de tres párrafos, y elige "Guardar"
- **THEN** se envía `POST /api/v1/consentimientos/plantillas` con ese título y ese texto
- **AND** el diálogo se cierra, se avisa "Plantilla creada." y la lista se vuelve a pedir

#### Scenario: Título repetido
- **GIVEN** que el alta responde `409` con el código `PLANTILLA_DUPLICADA`
- **WHEN** `admin` elige "Guardar"
- **THEN** el diálogo sigue abierto con los datos y el campo del título dice "Ya existe una plantilla con ese título."

### Requirement: Editor de texto plano con contador
El texto de una plantilla SHALL escribirse en un área de texto grande, con un contador visible "N/20.000" y la ayuda de que es texto plano. El editor MUST NOT recortar un texto pegado que supere el límite: lo marca como error. El título lleva su contador de 120 caracteres.

#### Scenario: El contador sigue al texto
- **GIVEN** el diálogo de la plantilla abierto
- **WHEN** `admin` escribe un texto de 1.234 caracteres
- **THEN** el contador dice "1.234/20.000"

#### Scenario: Texto que supera el límite
- **WHEN** `admin` pega un texto de 20.001 caracteres y elige "Guardar"
- **THEN** no se envía nada, el texto queda entero en el área y el campo dice "El texto supera los 20.000 caracteres."

#### Scenario: Título o texto vacío
- **WHEN** `admin` deja el título o el texto vacíos (o solo con espacios) y elige "Guardar"
- **THEN** no se envía nada y el campo vacío dice "Escribí el título." o "Escribí el texto del consentimiento."

### Requirement: Editar una plantilla crea una versión nueva
"Editar" SHALL abrir el diálogo con el título y el texto de la última versión y explicar que se guarda como una versión nueva y que lo ya firmado no cambia. Al guardar, MUST enviar `versionBase` con el número de la versión que se abrió.

#### Scenario: Guardar una versión nueva
- **GIVEN** la plantilla "Consentimiento para extracción dentaria" en la versión 3
- **WHEN** `admin` elige "Editar", cambia un párrafo y elige "Guardar"
- **THEN** se envía `POST /api/v1/consentimientos/plantillas/4/versiones` con `versionBase` 3 y el título y el texto
- **AND** con la respuesta `201`, el diálogo se cierra y se avisa "Plantilla guardada como versión 4."

#### Scenario: Guardar sin cambios
- **GIVEN** que la edición responde `200` con la versión 3
- **WHEN** `admin` elige "Guardar"
- **THEN** el diálogo se cierra y se avisa "No hubo cambios: la plantilla sigue en la versión 3."

### Requirement: Conflicto de versión al editar
Si la edición responde `409 VERSION_DESACTUALIZADA`, el diálogo SHALL quedar abierto con lo que escribió el administrador y MUST explicar que otra persona guardó una versión nueva mientras editaba, sin enviar nada más. La lista MUST volver a pedirse.

#### Scenario: Otra persona guardó antes
- **GIVEN** que la edición con `versionBase` 3 responde `409` con el código `VERSION_DESACTUALIZADA`
- **WHEN** `admin` elige "Guardar"
- **THEN** el diálogo sigue abierto con su texto y dice "Otra persona guardó una versión nueva de esta plantilla mientras la editabas. Copiá tus cambios, cerrá y volvé a abrirla."
- **AND** la lista se vuelve a pedir

### Requirement: Descartar una edición sin guardar
Si el título o el texto del diálogo cambiaron, "Cancelar" o Escape MUST preguntar "Hay cambios sin guardar en la plantilla. ¿Descartarlos?". Sin cambios, el diálogo se cierra sin preguntar.

#### Scenario: Cancelar con cambios
- **GIVEN** el diálogo de edición con el texto modificado
- **WHEN** `admin` elige "Cancelar"
- **THEN** se pregunta "Hay cambios sin guardar en la plantilla. ¿Descartarlos?"

#### Scenario: Cancelar sin cambios
- **GIVEN** el diálogo de edición recién abierto
- **WHEN** `admin` elige "Cancelar"
- **THEN** el diálogo se cierra sin preguntar

### Requirement: Ver las versiones de una plantilla
"Versiones" SHALL abrir un diálogo con todas las versiones de la plantilla, de la más nueva a la más vieja, cada una con su número, fecha y hora, quién la creó y su texto desplegable. Las versiones MUST mostrarse en solo lectura.

#### Scenario: Lista de versiones
- **GIVEN** la plantilla "Consentimiento para extracción dentaria" con tres versiones creadas por `admin`
- **WHEN** `admin` elige "Versiones"
- **THEN** se pide `GET /api/v1/consentimientos/plantillas/4/versiones`
- **AND** se ven "Versión 3", "Versión 2" y "Versión 1", en ese orden, cada una con "admin" y su fecha

#### Scenario: Leer el texto de una versión anterior
- **GIVEN** el diálogo de versiones abierto
- **WHEN** `admin` despliega "Versión 1"
- **THEN** se ve el texto de esa versión, sin controles para editarlo

### Requirement: Texto de la plantilla como texto plano
En la pestaña y en las versiones, el título y el texto de una plantilla MUST mostrarse como texto, respetando los saltos de línea, y MUST NOT interpretarse como HTML.

#### Scenario: Un texto con marcas de HTML
- **GIVEN** una versión cuyo texto es `Primera línea` y, en la línea siguiente, `<b>Segunda</b>`
- **WHEN** `admin` la despliega en "Versiones"
- **THEN** se ven dos líneas y la segunda dice literalmente `<b>Segunda</b>`, sin negrita

### Requirement: Activar y desactivar una plantilla
Cada plantilla SHALL ofrecer "Desactivar" si está activa o "Activar" si está inactiva, sin opción de borrarla. Al elegirlo, MUST enviar el estado nuevo, avisar "Plantilla desactivada." o "Plantilla activada." y actualizar la fila.

#### Scenario: Desactivar una plantilla
- **GIVEN** la plantilla "Consentimiento para extracción dentaria" activa
- **WHEN** `admin` elige "Desactivar"
- **THEN** se envía `PUT /api/v1/consentimientos/plantillas/4/estado` con `activa` `false`
- **AND** se avisa "Plantilla desactivada." y la fila dice "Inactiva" y ofrece "Activar"

#### Scenario: Sin opción de borrar
- **WHEN** `admin` mira cualquier plantilla de la lista
- **THEN** ninguna ofrece "Borrar" ni "Eliminar"

### Requirement: Pestaña de plantillas accesible
Los botones de cada fila MUST tener un nombre accesible con el título de la plantilla (por ejemplo "Editar Consentimiento para extracción dentaria"). Los diálogos MUST usarse con teclado, devolver el foco al botón que los abrió y anunciar los avisos. Los contadores MUST estar asociados a su campo.

#### Scenario: Nombre accesible de los botones
- **GIVEN** la plantilla "Consentimiento para extracción dentaria"
- **WHEN** se revisan los botones de su fila
- **THEN** se llaman "Editar Consentimiento para extracción dentaria", "Versiones de Consentimiento para extracción dentaria" y "Desactivar Consentimiento para extracción dentaria"

#### Scenario: El foco vuelve al cerrar el diálogo
- **GIVEN** el diálogo de edición abierto desde "Editar" sin cambios
- **WHEN** se cierra con Escape
- **THEN** el foco vuelve al botón "Editar" de esa plantilla
