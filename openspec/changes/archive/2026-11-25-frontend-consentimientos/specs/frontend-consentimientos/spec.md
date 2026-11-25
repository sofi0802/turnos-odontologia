# Spec Delta

## Purpose

Permitir que el odontólogo haga firmar al paciente un consentimiento informado con firma electrónica en el consultorio, sin que el paciente vuelva a la ficha ni a la sesión del odontólogo, y que el odontólogo y el administrador consulten, verifiquen y (solo el odontólogo) revoquen los consentimientos firmados desde la ficha clínica, sobre la API de C-27, sin que ningún dato quede fuera de la pantalla. En los escenarios, con datos ficticios, "Paciente Uno" (id 1, DNI 99000001) existe; `ana.ejemplo` es `ODONTOLOGO` de la Dra. Ana Ejemplo (profesional 1) y `admin` es `ADMIN`; la plantilla activa "Consentimiento para extracción dentaria" (id 4) está en la versión 3. El día actual es el 2026-11-24.

## ADDED Requirements

### Requirement: Sección de consentimientos en la ficha
La ficha de un paciente en `#/pacientes` SHALL mostrar la sección "Consentimientos" después de "Imágenes y estudios", para `ODONTOLOGO` y `ADMIN`. La sección MUST pedir sus datos aparte del resto de la ficha: mientras carga muestra "Cargando consentimientos…" y, si falla, su propio error con "Reintentar", sin ocultar el resto de la ficha.

#### Scenario: La sección aparece en la ficha
- **GIVEN** la sesión de `ana.ejemplo`
- **WHEN** abre la ficha de "Paciente Uno"
- **THEN** la ficha muestra la sección "Consentimientos" después de "Imágenes y estudios"
- **AND** se pide `GET /api/v1/pacientes/1/consentimientos`

#### Scenario: Falla al cargar los consentimientos
- **GIVEN** que el listado de consentimientos responde `500` y el resto de la ficha responde bien
- **WHEN** `ana.ejemplo` abre la ficha de "Paciente Uno"
- **THEN** la sección muestra un error y "Reintentar"
- **AND** el odontograma y las imágenes se ven igual

#### Scenario: Reintentar la carga
- **GIVEN** la sección con el error de carga
- **WHEN** `ana.ejemplo` elige "Reintentar" y el listado responde bien
- **THEN** la sección muestra el listado

### Requirement: Listado de consentimientos firmados
La sección SHALL listar los consentimientos en el orden de la API, del más nuevo al más viejo. Cada fila muestra el título, "Versión N", la fecha y hora de la firma, el profesional y el estado escrito en una etiqueta, "Vigente" o "Revocado", sin depender solo del color. Sin consentimientos, MUST mostrar "Todavía no hay consentimientos firmados.".

#### Scenario: Filas del listado
- **GIVEN** "Paciente Uno" con un consentimiento vigente "Consentimiento para extracción dentaria" versión 3 firmado el 2026-11-24 a las 10:30 con `ana.ejemplo` y otro revocado "Consentimiento para blanqueamiento" versión 1 firmado el 2026-10-01
- **WHEN** `ana.ejemplo` abre su ficha
- **THEN** la primera fila dice "Consentimiento para extracción dentaria", "Versión 3", "Firmado el 24/11/2026 a las 10:30", "Ana Ejemplo" y "Vigente"
- **AND** la segunda dice "Consentimiento para blanqueamiento" y "Revocado"

#### Scenario: Paciente sin consentimientos
- **GIVEN** "Paciente Uno" sin consentimientos
- **WHEN** `ana.ejemplo` abre su ficha
- **THEN** la sección dice "Todavía no hay consentimientos firmados."

### Requirement: Elegir la plantilla para firmar
Solo para `ODONTOLOGO`, la sección SHALL ofrecer "Firmar consentimiento", que pide las plantillas activas y abre un diálogo para elegir una. Al continuar, MUST pedir la plantilla elegida con su última versión y, a la vez, el listado de consentimientos del paciente, y después abrir la vista de firma. Sin plantillas activas, el diálogo MUST decir "No hay plantillas activas. Pedile al administrador que cargue una en Configuración → Consentimientos.".

#### Scenario: Elegir una plantilla
- **GIVEN** la ficha de "Paciente Uno" abierta por `ana.ejemplo`
- **WHEN** elige "Firmar consentimiento", elige "Consentimiento para extracción dentaria" y "Continuar"
- **THEN** se piden `GET /api/v1/consentimientos/plantillas/4` y `GET /api/v1/pacientes/1/consentimientos`
- **AND** se abre la vista de firma con el texto de la versión 3

#### Scenario: Sin plantillas activas
- **GIVEN** que el listado de plantillas devuelve una lista vacía
- **WHEN** `ana.ejemplo` elige "Firmar consentimiento"
- **THEN** el diálogo dice "No hay plantillas activas. Pedile al administrador que cargue una en Configuración → Consentimientos." y no ofrece "Continuar"

#### Scenario: La plantilla se desactivó antes de abrirla
- **GIVEN** que `GET /api/v1/consentimientos/plantillas/4` responde `404`
- **WHEN** `ana.ejemplo` elige "Continuar"
- **THEN** el diálogo dice "Esa plantilla ya no está disponible. Elegí otra." y vuelve a pedir las plantillas activas

### Requirement: Aviso antes de firmar dos veces
Antes de abrir la vista de firma, si el paciente tiene un consentimiento vigente de la misma plantilla y la misma versión firmado el mismo día, la aplicación SHALL preguntar "Ya firmó este consentimiento hoy" con el título, la versión y la hora de esa firma, y las opciones "No firmar" y "Firmar otra vez". "No firmar" MUST tener el foco al abrirse y cerrar sin abrir la vista. Una firma revocada, de otra versión o de otro día MUST NOT avisar.

#### Scenario: Ya firmó hoy la misma versión
- **GIVEN** que "Paciente Uno" tiene vigente "Consentimiento para extracción dentaria" versión 3 firmado el 2026-11-24 a las 10:30
- **WHEN** `ana.ejemplo` elige esa plantilla y "Continuar"
- **THEN** se pregunta "Ya firmó este consentimiento hoy" con "“Consentimiento para extracción dentaria”, versión 3, quedó firmado hoy a las 10:30 y está vigente. ¿Hacerlo firmar otra vez?"
- **AND** la vista de firma no se abre hasta elegir "Firmar otra vez"

#### Scenario: No firmar otra vez
- **GIVEN** la pregunta "Ya firmó este consentimiento hoy"
- **WHEN** `ana.ejemplo` elige "No firmar"
- **THEN** la pregunta se cierra, la vista de firma no se abre y no se envía nada

#### Scenario: Una firma revocada no avisa
- **GIVEN** que la única firma de esa plantilla de hoy está revocada
- **WHEN** `ana.ejemplo` elige la plantilla y "Continuar"
- **THEN** se abre la vista de firma sin preguntar

#### Scenario: Una firma de otro día no avisa
- **GIVEN** que la firma vigente de la versión 3 es del 2026-11-20
- **WHEN** `ana.ejemplo` elige la plantilla y "Continuar"
- **THEN** se abre la vista de firma sin preguntar

### Requirement: Vista de firma a pantalla completa
La vista de firma SHALL ocupar toda la pantalla y tapar la ficha y el menú, y MUST mostrar solo el título y el texto de la versión elegida, el nombre y el apellido del paciente ("Para: …"), "Nombre completo", "DNI", la casilla "Leí y acepto este consentimiento", "Cancelar" y "Firmar". MUST NOT mostrar datos clínicos ni el DNI del paciente, y el resto de la página MUST quedar fuera del alcance del teclado mientras está abierta.

#### Scenario: Lo que ve el paciente
- **GIVEN** la vista de firma abierta para "Paciente Uno"
- **WHEN** se revisa lo que está en pantalla y alcanzable con el teclado
- **THEN** solo están el título, el texto, "Para: Paciente Uno", los dos campos, la casilla, "Cancelar" y "Firmar"
- **AND** no se ven el odontograma ni las imágenes, y la vista no contiene "99000001" ni en el texto ni en sus atributos

#### Scenario: El texto se muestra como texto plano
- **GIVEN** una versión cuyo texto tiene dos párrafos separados por una línea en blanco y la cadena `<b>riesgos</b>`
- **WHEN** se abre la vista de firma
- **THEN** se ven los dos párrafos separados y la cadena `<b>riesgos</b>` literal, sin negrita

### Requirement: Nombre y DNI escritos por el paciente
Los campos "Nombre completo" y "DNI" MUST empezar vacíos: el paciente escribe los dos. La aplicación MUST NOT precargar ni sugerir el DNI ni el nombre de la ficha en esos campos.

#### Scenario: Campos vacíos al abrir
- **GIVEN** la ficha de "Paciente Uno", con DNI 99000001
- **WHEN** se abre la vista de firma
- **THEN** "Nombre completo" y "DNI" están vacíos

### Requirement: Firma electrónica, no firma digital
La vista de firma SHALL decir que es una "firma electrónica" y explicar qué se guarda: el nombre y el DNI escritos, la fecha y hora, el profesional, la dirección IP y el dispositivo. Ninguna pantalla de consentimientos MUST decir "firma digital".

#### Scenario: Texto de la firma
- **WHEN** se abre la vista de firma
- **THEN** dice "Firma electrónica" y menciona el nombre, el DNI, la fecha y hora, el profesional, la IP y el dispositivo
- **AND** ninguna pantalla de consentimientos contiene "firma digital"

### Requirement: Validar la firma antes de enviarla
Antes de enviar, la vista MUST verificar que el nombre tenga de 3 a 200 caracteres, que el DNI, sin puntos ni espacios, tenga 7 u 8 dígitos y que la casilla esté marcada. Si algo falla, MUST NOT enviar el pedido, SHALL marcar el campo con su mensaje y llevar el foco al primero con error.

#### Scenario: Sin aceptar
- **GIVEN** el nombre y el DNI completos
- **WHEN** se elige "Firmar" sin marcar la casilla
- **THEN** no se envía nada y la casilla dice "Para firmar, marcá que leíste y aceptás el consentimiento."

#### Scenario: DNI con letras
- **WHEN** se escribe el DNI "99A00001" y se elige "Firmar"
- **THEN** no se envía nada y el campo dice "Escribí el DNI sin letras: 7 u 8 números."

#### Scenario: Nombre demasiado corto
- **WHEN** se escribe el nombre "Pa" y se elige "Firmar"
- **THEN** no se envía nada y el campo dice "Escribí tu nombre completo."

### Requirement: Firmar el consentimiento
Al elegir "Firmar" con los datos válidos, la vista SHALL enviar la plantilla, el número de la versión mostrada, el nombre, el DNI y `aceptado`. MUST haber un solo pedido de firma a la vez: mientras se envía, "Firmar" dice "Firmando…" y queda deshabilitado, "Cancelar" también, y otro "Firmar" o Enter no envía nada. Con `201`, la vista MUST cerrarse y mostrar el bloqueo con "Listo: la firma quedó registrada.".

#### Scenario: Firma correcta
- **GIVEN** la vista de firma con la versión 3 de la plantilla 4
- **WHEN** se escribe "Paciente Uno", el DNI "99.000.001", se marca la casilla y se elige "Firmar"
- **THEN** se envía `POST /api/v1/pacientes/1/consentimientos` con `plantillaId` 4, `versionNumero` 3, `firmanteNombre` "Paciente Uno", `firmanteDni` "99.000.001" y `aceptado` `true`
- **AND** con la respuesta `201`, la vista se cierra y el bloqueo dice "Devolvé el equipo al profesional" y "Listo: la firma quedó registrada."

#### Scenario: Un solo envío
- **GIVEN** la vista de firma con los datos válidos y la firma todavía sin respuesta
- **WHEN** se elige "Firmar" dos veces y se presiona Enter
- **THEN** se envía un solo `POST /api/v1/pacientes/1/consentimientos`
- **AND** "Firmar" dice "Firmando…" y está deshabilitado

#### Scenario: Volver a la ficha después de firmar
- **GIVEN** el bloqueo después de una firma con `201`
- **WHEN** `ana.ejemplo` escribe su contraseña y elige "Desbloquear"
- **THEN** se ve la ficha de "Paciente Uno" con el aviso "Consentimiento firmado." con el título, la versión y la hora
- **AND** el listado se vuelve a pedir y muestra el consentimiento nuevo como "Vigente"

### Requirement: DNI que no coincide
Si la firma responde `422 FIRMANTE_NO_COINCIDE`, la vista SHALL quedar abierta con los datos y marcar el DNI con "El DNI no coincide con el del paciente. Revisalo.". El mensaje MUST NOT repetir el DNI escrito ni el del paciente.

#### Scenario: El DNI no es el del paciente
- **GIVEN** que la firma responde `422` con el código `FIRMANTE_NO_COINCIDE`
- **WHEN** se elige "Firmar" con el DNI "99000002"
- **THEN** la vista sigue abierta y el campo del DNI dice "El DNI no coincide con el del paciente. Revisalo."
- **AND** ningún texto de la pantalla fuera del campo contiene "99000002" ni "99000001"

### Requirement: El texto cambió mientras se leía
Si la firma responde `409 VERSION_DESACTUALIZADA`, la vista SHALL pedir de nuevo la plantilla, mostrar el texto de la versión nueva, desmarcar la casilla y avisar "El texto del consentimiento cambió mientras se leía. Leé la versión nueva antes de firmar.". Los campos de nombre y DNI se conservan.

#### Scenario: Versión desactualizada
- **GIVEN** que la firma de la versión 3 responde `409` con el código `VERSION_DESACTUALIZADA` y la plantilla ya está en la versión 4
- **WHEN** se elige "Firmar"
- **THEN** se pide de nuevo `GET /api/v1/consentimientos/plantillas/4` y se muestra el texto de la versión 4
- **AND** la casilla queda desmarcada y se avisa "El texto del consentimiento cambió mientras se leía. Leé la versión nueva antes de firmar."
- **AND** la próxima firma envía `versionNumero` 4

### Requirement: Plantilla desactivada durante la firma
Si la firma responde `409 PLANTILLA_INACTIVA`, la vista SHALL avisar "Esta plantilla se desactivó y ya no se puede firmar." y MUST ofrecer solo "Terminar", sin "Firmar". "Terminar" MUST cerrar la vista y mostrar el bloqueo con "La firma no se completó.".

#### Scenario: Plantilla inactiva
- **GIVEN** que la firma responde `409` con el código `PLANTILLA_INACTIVA`
- **WHEN** se elige "Firmar"
- **THEN** la vista dice "Esta plantilla se desactivó y ya no se puede firmar." y solo ofrece "Terminar"

#### Scenario: Terminar después de una plantilla inactiva
- **GIVEN** la vista con el aviso de plantilla inactiva
- **WHEN** se elige "Terminar"
- **THEN** la vista se cierra y el bloqueo dice "La firma no se completó."

### Requirement: Cancelar la firma
El paciente SHALL poder cancelar antes de firmar con "Cancelar" o Escape, sin enviar nada. Si escribió algo o marcó la casilla, MUST preguntar antes "¿Cancelar la firma? Lo escrito se descarta.". Al cerrar, lo escrito MUST quitarse de la página y MUST mostrarse el bloqueo con "La firma se canceló. No se guardó nada.".

#### Scenario: Cancelar con datos escritos
- **GIVEN** la vista de firma con el nombre escrito
- **WHEN** se elige "Cancelar" y se confirma
- **THEN** la vista se cierra sin enviar nada y la página ya no contiene el nombre escrito
- **AND** se muestra el bloqueo con "La firma se canceló. No se guardó nada."

#### Scenario: Cancelar sin datos
- **GIVEN** la vista de firma recién abierta
- **WHEN** se presiona Escape
- **THEN** la vista se cierra sin preguntar y se muestra el bloqueo

#### Scenario: Seguir firmando
- **GIVEN** la pregunta "¿Cancelar la firma? Lo escrito se descarta."
- **WHEN** se elige "Seguir firmando"
- **THEN** la vista sigue abierta con lo escrito

### Requirement: Cierre de la vista por el navegador
Si el navegador cierra la vista de firma sin que la aplicación lo pida, la aplicación SHALL tratarlo como una cancelación sin enviar o, si había un envío en curso, como un envío dudoso, y MUST mostrar el bloqueo. Mientras se envía la firma, "Cancelar" y Escape MUST NOT hacer nada.

#### Scenario: El navegador cierra la vista
- **GIVEN** la vista de firma con el nombre escrito y sin envío en curso
- **WHEN** la vista se cierra sin que la aplicación lo pida
- **THEN** no se envía nada, la página ya no contiene el nombre y se muestra el bloqueo

#### Scenario: Escape durante el envío
- **GIVEN** la vista con "Firmando…"
- **WHEN** se presiona Escape una vez
- **THEN** la vista sigue abierta y "Cancelar" está deshabilitado

### Requirement: Devolver el equipo al profesional
Al terminar la firma por cualquier camino, la aplicación SHALL quitar la ficha de la página, mostrar la pantalla de bloqueo opaca "Devolvé el equipo al profesional" y pedir `POST /api/v1/logout`. El bloqueo MUST mostrar el usuario como texto fijo, "Contraseña" y "Desbloquear", MUST NOT mostrar datos del paciente ni del consentimiento y MUST dejar el resto de la página fuera del alcance del teclado.

#### Scenario: El bloqueo tapa todo y cierra la sesión
- **GIVEN** la vista de firma de "Paciente Uno" abierta por `ana.ejemplo`
- **WHEN** la firma responde `201`
- **THEN** se muestra "Devolvé el equipo al profesional", "Usuario: ana.ejemplo", "Contraseña" y "Desbloquear"
- **AND** se envía `POST /api/v1/logout`
- **AND** la página no contiene "Paciente Uno", "99000001" ni el título del consentimiento

#### Scenario: Escape no quita el bloqueo
- **GIVEN** el bloqueo
- **WHEN** se presiona Escape dos veces
- **THEN** el bloqueo sigue visible

#### Scenario: Atrás no quita el bloqueo
- **GIVEN** el bloqueo
- **WHEN** se vuelve atrás con el navegador
- **THEN** el bloqueo sigue visible y no se ve la ficha

### Requirement: Desbloquear con la contraseña del profesional
"Desbloquear" SHALL enviar `POST /api/v1/public/login` con el usuario de la sesión que abrió la ficha y la contraseña escrita; el usuario MUST NOT poder cambiarse desde el bloqueo. Con éxito, si la sesión nueva es del mismo usuario y con rol `ODONTOLOGO`, MUST quitar el bloqueo y volver a mostrar la ficha del mismo paciente; si no, MUST cerrar la sesión y llevar al ingreso.

#### Scenario: Desbloquear con la contraseña correcta
- **GIVEN** el bloqueo de la sesión de `ana.ejemplo`
- **WHEN** escribe su contraseña y elige "Desbloquear" y el ingreso responde con `ana.ejemplo` y rol `ODONTOLOGO`
- **THEN** se envió `POST /api/v1/public/login` con `username` "ana.ejemplo"
- **AND** el bloqueo desaparece y se ve la ficha de "Paciente Uno"

#### Scenario: El usuario no se puede cambiar
- **WHEN** se revisa el bloqueo
- **THEN** no hay un campo editable para el usuario

#### Scenario: La sesión nueva es de otro usuario
- **GIVEN** el bloqueo de la sesión de `ana.ejemplo`
- **WHEN** el ingreso responde con otro usuario
- **THEN** se envía `POST /api/v1/logout` y se muestra el ingreso sin la ficha

### Requirement: Contraseña incorrecta en el bloqueo
Con `401 CREDENCIALES_INVALIDAS`, el bloqueo SHALL seguir, vaciar el campo y decir "Contraseña incorrecta. Te quedan N intentos.". Al tercer fallo, o ante otro error que no sea de red, MUST llevar al ingreso (`#/mi-cuenta`) con "Por seguridad, cerramos la sesión. Ingresá de nuevo.", sin nada de la ficha. Sin conexión o con `5xx`, MUST decir "No pudimos comprobar la contraseña. Probá de nuevo." sin contar el intento.

#### Scenario: Contraseña incorrecta
- **GIVEN** el bloqueo
- **WHEN** se escribe una contraseña mala y el ingreso responde `401` con el código `CREDENCIALES_INVALIDAS`
- **THEN** el bloqueo sigue, el campo queda vacío y dice "Contraseña incorrecta. Te quedan 2 intentos."

#### Scenario: Demasiados intentos
- **GIVEN** el bloqueo con dos intentos fallidos
- **WHEN** el tercer intento responde `401` con el código `CREDENCIALES_INVALIDAS`
- **THEN** se muestra el ingreso en `#/mi-cuenta` con "Por seguridad, cerramos la sesión. Ingresá de nuevo."
- **AND** la página no contiene "Paciente Uno"

#### Scenario: Sin conexión al desbloquear
- **GIVEN** el bloqueo con un intento fallido
- **WHEN** el ingreso falla por la red
- **THEN** el bloqueo dice "No pudimos comprobar la contraseña. Probá de nuevo." y todavía quedan 2 intentos

### Requirement: Marca de firma en curso
Al abrir la vista de firma, la aplicación SHALL guardar en `localStorage` la marca `turnos.firmaEnCurso` con el valor `"1"`, sin ningún otro dato, y MUST borrarla solo al desbloquear con éxito o al llevar al ingreso.

#### Scenario: La marca no tiene datos
- **GIVEN** la vista de firma abierta para "Paciente Uno"
- **WHEN** se revisa `localStorage`
- **THEN** solo tiene `turnos.firmaEnCurso` con el valor `"1"`

### Requirement: Recargar durante la firma o el bloqueo
Si la aplicación arranca con la marca `turnos.firmaEnCurso`, SHALL pedir `POST /api/v1/logout` antes de dibujar cualquier página, borrar la marca y mostrar el ingreso con "Por seguridad, la sesión se cerró. Si estabas haciendo firmar un consentimiento, revisá la ficha del paciente para ver si quedó guardado.".

#### Scenario: Recargar durante la firma
- **GIVEN** la vista de firma abierta, con la sesión de `ana.ejemplo` todavía abierta
- **WHEN** se recarga la página
- **THEN** se envía `POST /api/v1/logout` antes de pedir datos de otra página
- **AND** se ve el ingreso con el aviso de seguridad y no se ven la ficha ni el buscador de pacientes

#### Scenario: Recargar durante el bloqueo
- **GIVEN** el bloqueo
- **WHEN** se recarga la página
- **THEN** se ve el ingreso sin nada de la ficha
- **AND** `localStorage` no contiene la marca `turnos.firmaEnCurso`

### Requirement: Sesión vencida durante la firma
Si la firma responde `401` o `403`, la aplicación SHALL tratarla como no guardada: bloqueo con "La firma no se completó." y, después de desbloquear, "La firma no se guardó porque la sesión había vencido. Si hace falta, hacela firmar de nuevo.".

#### Scenario: La sesión venció al firmar
- **GIVEN** la vista de firma con los datos completos
- **WHEN** se elige "Firmar" y la API responde `401`
- **THEN** la vista se cierra, la página ya no contiene lo escrito y el bloqueo dice "La firma no se completó."
- **AND** después de desbloquear, la ficha dice "La firma no se guardó porque la sesión había vencido. Si hace falta, hacela firmar de nuevo."

### Requirement: Envío dudoso de la firma
Si la firma falla por la red, no responde en 30 segundos, responde `5xx` o una respuesta que no se puede leer, la aplicación MUST NOT decir que se guardó ni que no se guardó: SHALL mostrar el bloqueo con "No pudimos confirmar si la firma se guardó. El profesional lo va a revisar." y comprobar después de desbloquear.

#### Scenario: Falla la red al firmar
- **GIVEN** la vista de firma con los datos completos
- **WHEN** se elige "Firmar" y el pedido falla por la red
- **THEN** el bloqueo dice "No pudimos confirmar si la firma se guardó. El profesional lo va a revisar."
- **AND** no dice "quedó guardada" ni "no se guardó"

#### Scenario: La firma tarda demasiado
- **GIVEN** que la firma no responde
- **WHEN** pasan 30 segundos
- **THEN** el pedido se cancela y el bloqueo dice "No pudimos confirmar si la firma se guardó. El profesional lo va a revisar."

### Requirement: Comprobar si la firma quedó guardada
Después de desbloquear tras un envío dudoso, la sección SHALL mostrar "Comprobando si la firma quedó guardada…" y pedir el listado. La firma MUST considerarse guardada solo si hay un consentimiento de la misma plantilla y versión, del profesional de la sesión, cuyo id no estaba en el listado pedido al elegir la plantilla. Si lo hay, dice "La firma quedó guardada."; si no, "La firma no se guardó."; si el listado falla, "No pudimos comprobar si la firma quedó guardada.".

#### Scenario: La firma quedó guardada
- **GIVEN** que antes de la firma el listado tenía los consentimientos 10 y 11, y la firma de la versión 3 de la plantilla 4 falló por la red
- **WHEN** `ana.ejemplo` desbloquea y el listado trae el consentimiento 12 de la plantilla 4, versión 3, con la Dra. Ana Ejemplo
- **THEN** la sección dice "La firma quedó guardada." con "Consentimiento para extracción dentaria", "Versión 3" y la hora, y ofrece "Ver"

#### Scenario: La firma no se guardó
- **GIVEN** que antes de la firma el listado tenía los consentimientos 10 y 11, y la firma falló por la red
- **WHEN** `ana.ejemplo` desbloquea y el listado trae solo los consentimientos 10 y 11
- **THEN** la sección dice "La firma no se guardó." y ofrece "Hacer firmar de nuevo"

#### Scenario: No se puede comprobar
- **GIVEN** una firma con un envío dudoso
- **WHEN** `ana.ejemplo` desbloquea y el listado responde `500`
- **THEN** la sección dice "No pudimos comprobar si la firma quedó guardada." y ofrece "Comprobar de nuevo"
- **AND** no dice "La firma no se guardó."

#### Scenario: Comprobando
- **GIVEN** una firma con un envío dudoso
- **WHEN** `ana.ejemplo` desbloquea y el listado todavía no respondió
- **THEN** la sección dice "Comprobando si la firma quedó guardada…"

### Requirement: Ver un consentimiento firmado
"Ver" SHALL abrir el documento firmado con su título, versión, estado, "Firma electrónica", el texto firmado como texto plano, el nombre y el DNI del firmante, la fecha y hora y el profesional. La evidencia (IP, dispositivo y hash con su algoritmo) MUST mostrarse en un bloque "Evidencia de la firma" plegado al abrir.

#### Scenario: Abrir un consentimiento
- **GIVEN** el consentimiento vigente 12 de "Paciente Uno"
- **WHEN** `ana.ejemplo` elige "Ver"
- **THEN** se pide `GET /api/v1/pacientes/1/consentimientos/12`
- **AND** se ven el texto firmado, "Paciente Uno", el DNI "99000001", "24/11/2026 10:30", "Ana Ejemplo" y "Firma electrónica"
- **AND** la IP, el dispositivo y el hash están dentro de "Evidencia de la firma", plegado

#### Scenario: Ver un consentimiento revocado
- **GIVEN** un consentimiento revocado por `ana.ejemplo` el 2026-11-20 con el motivo "El paciente decidió no hacer el tratamiento"
- **WHEN** `ana.ejemplo` elige "Ver"
- **THEN** el documento dice "Revocado" y "Revocado el 20/11/2026 por Ana Ejemplo: El paciente decidió no hacer el tratamiento"
- **AND** el texto firmado se ve completo

### Requirement: Verificar al abrir
Al abrir un consentimiento, la aplicación SHALL pedir su verificación y mostrar al lado del documento "Íntegro" si el hash coincide o "Alterado" si no, con la fecha y hora de la verificación. "Alterado" MUST mostrarse como alerta, con el hash guardado y el calculado. Si la verificación falla, MUST mostrar "Sin verificar" con "Verificar de nuevo".

#### Scenario: Consentimiento íntegro
- **GIVEN** que la verificación del consentimiento 12 responde `integro` `true`
- **WHEN** `ana.ejemplo` elige "Ver"
- **THEN** se pide `GET /api/v1/pacientes/1/consentimientos/12/verificacion`
- **AND** al lado del documento dice "Íntegro" y "El contenido coincide con su hash SHA-256."

#### Scenario: Consentimiento alterado
- **GIVEN** que la verificación responde `integro` `false` con dos hash distintos
- **WHEN** `admin` elige "Ver"
- **THEN** al lado del documento dice "Alterado" en una alerta, con el hash guardado y el calculado

#### Scenario: Falla la verificación
- **GIVEN** que el documento responde bien y la verificación responde `500`
- **WHEN** `ana.ejemplo` elige "Ver"
- **THEN** el documento se muestra con "Sin verificar" y "Verificar de nuevo"
- **AND** nunca se muestra "Íntegro" sin una verificación correcta

### Requirement: Revocar un consentimiento
Solo para `ODONTOLOGO`, cada consentimiento vigente SHALL ofrecer "Revocar", que abre un diálogo con el motivo obligatorio de hasta 200 caracteres y explica que el documento no se borra ni cambia. Al confirmar, MUST enviar el motivo, avisar "Consentimiento revocado." y volver a pedir el listado.

#### Scenario: Revocar con motivo
- **GIVEN** el consentimiento vigente 12 de "Paciente Uno"
- **WHEN** `ana.ejemplo` elige "Revocar", escribe "El paciente decidió no hacer el tratamiento" y confirma
- **THEN** se envía `POST /api/v1/pacientes/1/consentimientos/12/revocacion` con ese motivo
- **AND** se avisa "Consentimiento revocado." y la fila dice "Revocado"

#### Scenario: Revocar sin motivo
- **WHEN** `ana.ejemplo` confirma la revocación con el motivo vacío
- **THEN** no se envía nada y el campo dice "Escribí el motivo de la revocación."

#### Scenario: Ya estaba revocado
- **GIVEN** que la revocación responde `409` con el código `CONSENTIMIENTO_YA_REVOCADO`
- **WHEN** `ana.ejemplo` confirma
- **THEN** se muestra "El consentimiento ya estaba revocado." y el listado se vuelve a pedir

#### Scenario: Un revocado no se revoca
- **GIVEN** un consentimiento revocado
- **WHEN** `ana.ejemplo` mira su fila
- **THEN** la fila ofrece "Ver" y no ofrece "Revocar"

### Requirement: Solo lectura para el administrador
Para `ADMIN`, la sección SHALL mostrar el listado y "Ver" con la verificación, y MUST NOT ofrecer "Firmar consentimiento" ni "Revocar".

#### Scenario: El administrador consulta
- **GIVEN** la sesión de `admin` con la ficha de "Paciente Uno" abierta y un consentimiento vigente
- **WHEN** mira la sección "Consentimientos" y abre el consentimiento
- **THEN** ve el documento con su verificación
- **AND** no ve "Firmar consentimiento" ni "Revocar"

### Requirement: Sin acceso para la recepción y el paciente
Con sesión `RECEPCIONISTA` o `PACIENTE`, la aplicación MUST NOT mostrar ni pedir consentimientos de pacientes ni plantillas.

#### Scenario: La recepción no pide consentimientos
- **GIVEN** la sesión de `recepcion`
- **WHEN** recorre `#/agenda`, `#/configuracion` y abre `#/pacientes`
- **THEN** no se envía ningún pedido a `/api/v1/pacientes/1/consentimientos` ni a `/api/v1/consentimientos/plantillas`

### Requirement: Consentimientos accesibles
Cada botón de una fila MUST tener un nombre accesible que identifique el consentimiento (por ejemplo "Ver Consentimiento para extracción dentaria del 24/11/2026"). La vista de firma MUST llevar el foco a su título al abrirse, el bloqueo MUST llevarlo a "Contraseña" y los diálogos MUST devolver el foco al botón que los abrió. Los avisos de éxito, de error, del bloqueo y de la comprobación MUST anunciarse y los controles MUST medir al menos 44 px.

#### Scenario: Nombre accesible de los botones
- **GIVEN** el consentimiento "Consentimiento para extracción dentaria" firmado el 2026-11-24
- **WHEN** se revisan los botones de su fila
- **THEN** se llaman "Ver Consentimiento para extracción dentaria del 24/11/2026" y "Revocar Consentimiento para extracción dentaria del 24/11/2026"

#### Scenario: Foco al abrir la vista de firma
- **WHEN** se abre la vista de firma
- **THEN** el foco está en el título del consentimiento

#### Scenario: Foco en el bloqueo
- **WHEN** se muestra el bloqueo
- **THEN** el foco está en "Contraseña" y el aviso del caso se anuncia

### Requirement: Consentimientos solo en la pantalla
La aplicación MUST NOT guardar el texto, el firmante, el DNI, la evidencia, el motivo ni el paciente de un consentimiento en la dirección, `localStorage`, `sessionStorage` ni la consola; lo único que guarda es la marca `turnos.firmaEnCurso` con el valor `"1"`. Los campos de nombre y DNI de la firma MUST desactivar el autocompletado del navegador.

#### Scenario: Nada queda guardado en el navegador
- **GIVEN** que `ana.ejemplo` hace firmar, desbloquea, abre y revoca un consentimiento de "Paciente Uno"
- **WHEN** se revisan la dirección, `localStorage`, `sessionStorage` y la consola
- **THEN** ninguno contiene el texto, "Paciente Uno", "99000001", la IP ni el motivo
- **AND** la dirección sigue siendo `#/pacientes`

#### Scenario: Sin autocompletado en la firma
- **WHEN** se abre la vista de firma
- **THEN** los campos "Nombre completo" y "DNI" tienen el autocompletado desactivado
