# Spec Delta

## ADDED Requirements

### Requirement: Periodontograma en la ficha
La ficha SHALL mostrar una sección "Periodontograma" debajo del odontograma, que pide `GET /api/v1/pacientes/{id}/periodontograma` y, si hay versiones, `GET .../{versionId}` de la más nueva. La sección se carga aparte: si falla, MUST mostrar el error y "Reintentar" solo en esa sección, sin ocultar el resto de la ficha. Sin versiones, muestra "Todavía no hay periodontogramas de este paciente.".

#### Scenario: Abrir la ficha con periodontograma
- **GIVEN** que "Paciente Uno" tiene dos versiones, la más nueva (id 9) del 2026-12-01 a las 09:00 de Bruno Prueba
- **WHEN** `ana.ejemplo` abre su ficha
- **THEN** se piden `GET /api/v1/pacientes/1/periodontograma` y `GET /api/v1/pacientes/1/periodontograma/9`
- **AND** la sección muestra "Versión del 01/12/2026 a las 09:00 · Bruno Prueba"

#### Scenario: Paciente sin periodontograma
- **GIVEN** que `GET /periodontograma` de "Paciente Uno" responde una lista vacía
- **WHEN** se abre su ficha
- **THEN** la sección muestra "Todavía no hay periodontogramas de este paciente."
- **AND** no se pide ninguna versión

#### Scenario: Falla la carga del periodontograma
- **GIVEN** la ficha de "Paciente Uno"
- **WHEN** el pedido del periodontograma falla por la red
- **THEN** la sección muestra el error y "Reintentar"
- **AND** la ficha básica y el odontograma se siguen viendo
- **AND** al elegir "Reintentar" se vuelve a pedir solo el periodontograma

### Requirement: Mapa de piezas con su estado
La sección SHALL dibujar las 32 piezas permanentes por cuadrante (18 a 11 y 21 a 28 arriba, 48 a 41 y 31 a 38 abajo). Cada pieza cargada MUST mostrar su número y su estado con el nombre ("Sano", "Leve", "Moderado", "Severo" o "Ausente"), un color y una marca de nivel, de modo que el estado no dependa solo del color. Una pieza que la versión no tiene se ve como "Sin cargar".

#### Scenario: Estados dibujados
- **GIVEN** que la versión tiene la pieza 16 `SANO`, la 21 `SEVERO` y la 18 ausente
- **WHEN** se abre la ficha
- **THEN** la pieza 16 se ve como "Sano", la 21 como "Severo" y la 18 como "Ausente"
- **AND** las demás piezas se ven como "Sin cargar"

### Requirement: Detalle de una pieza
Al elegir una pieza cargada, la sección SHALL mostrar sus 6 sitios (mesiovestibular, vestibular, distovestibular, mesiolingual o mesiopalatino, lingual o palatino, distolingual o distopalatino según el maxilar) con la profundidad de sondaje, la recesión, la pérdida de inserción, el sangrado y la placa, más la movilidad, la furcación si la tiene y el estado.

#### Scenario: Ver los sitios de una pieza superior
- **GIVEN** que la versión tiene la pieza 16 con profundidad 4 mm y recesión 0 mm en el sitio `V`
- **WHEN** se elige la pieza 16
- **THEN** se ven los sitios mesiovestibular, vestibular, distovestibular, mesiopalatino, palatino y distopalatino
- **AND** el sitio vestibular muestra profundidad 4 mm, recesión 0 mm y pérdida de inserción 4 mm

### Requirement: Versiones anteriores
La sección SHALL ofrecer un selector con todas las versiones, de la más nueva a la más vieja, cada una con la fecha, la hora y el profesional. Al elegir una, MUST pedir esa versión y mostrarla en solo lectura, sin ofrecer editarla ni borrarla.

#### Scenario: Abrir una versión anterior
- **GIVEN** la ficha de "Paciente Uno" con las versiones 9 (01/12/2026 09:00) y 4 (20/11/2026 10:30)
- **WHEN** se elige en el selector la versión del 20/11/2026 a las 10:30
- **THEN** se pide `GET /api/v1/pacientes/1/periodontograma/4` y se muestra esa versión
- **AND** no hay botones para editarla ni borrarla

### Requirement: Nueva versión precargada
Con sesión `ODONTOLOGO`, la sección SHALL ofrecer "Nueva versión". El formulario MUST arrancar con las piezas y los valores de la versión más nueva, aunque en el selector se esté mirando una anterior; sin versiones, arranca sin piezas cargadas.

#### Scenario: La nueva versión copia la última
- **GIVEN** la sesión de `ana.ejemplo` y la versión más nueva con las piezas 16 y 21
- **WHEN** elige "Nueva versión"
- **THEN** el formulario tiene las piezas 16 y 21 con los mismos valores
- **AND** no se pidió nada a la API

#### Scenario: Primera versión
- **GIVEN** la sesión de `ana.ejemplo` y "Paciente Uno" sin periodontograma
- **WHEN** elige "Nueva versión"
- **THEN** el formulario no tiene piezas cargadas y se ve "Elegí una pieza para cargarla."

### Requirement: Cargar, marcar ausente y quitar piezas
En la nueva versión, al elegir una pieza el odontólogo SHALL poder cargarla con sus 6 sitios, la movilidad y, solo si es multirradicular (molares, 14 y 24), la furcación; marcarla ausente; o quitarla de la carga. Una pieza nueva arranca con los campos vacíos. Una pieza que no es multirradicular MUST NOT ofrecer furcación.

#### Scenario: Cargar una pieza nueva
- **GIVEN** la nueva versión abierta sin la pieza 36
- **WHEN** se elige la pieza 36 y "Cargar pieza"
- **THEN** se ven los 6 sitios con los campos vacíos, la movilidad y la furcación

#### Scenario: Una pieza sin furcación
- **GIVEN** la nueva versión abierta
- **WHEN** se elige la pieza 11
- **THEN** no se ofrece el campo de furcación

#### Scenario: Marcar una pieza ausente
- **GIVEN** la pieza 18 cargada en la nueva versión
- **WHEN** se marca "Pieza ausente"
- **THEN** se ocultan los sitios, la movilidad y la furcación, y la pieza se ve como "Ausente"

#### Scenario: Quitar una pieza de la carga
- **GIVEN** las piezas 16 y 21 en la nueva versión
- **WHEN** se elige "Quitar de esta versión" en la 21
- **THEN** la pieza 21 se ve como "Sin cargar" y no se va a enviar

### Requirement: Convención de la recesión
El formulario SHALL explicar junto a la recesión: "Recesión: del límite amelocementario al margen de la encía. Cargala en negativo si el margen está por encima del límite, como en una encía sana (por ejemplo, −2)." y MUST aceptar valores de −5 a 15 mm.

#### Scenario: Ayuda visible
- **GIVEN** la nueva versión con la pieza 16 cargada
- **WHEN** se ve la pieza 16
- **THEN** se lee la explicación de la recesión negativa junto a los campos de recesión

### Requirement: Validación antes de guardar
Antes de enviar, el formulario MUST validar con las mismas reglas que el backend: profundidad de 0 a 15 mm, recesión de −5 a 15 mm, enteros; movilidad y furcación de 0 a 3; los 6 sitios y la movilidad completos en cada pieza presente; al menos una pieza. Si algo falla, MUST NOT pedir nada a la API, MUST mostrar el error junto al campo y MUST llevar el foco a la primera pieza con errores.

#### Scenario: Falta completar una pieza
- **GIVEN** la pieza 36 cargada con el sitio `DL` vacío
- **WHEN** se elige "Guardar versión"
- **THEN** no se pide nada a la API
- **AND** se muestra "Completá los 6 sitios y la movilidad de la pieza 36." y el foco va a la pieza 36

#### Scenario: Valor fuera de rango
- **GIVEN** la pieza 11 con profundidad 16 en el sitio `MV`
- **WHEN** se elige "Guardar versión"
- **THEN** no se pide nada a la API y el campo muestra "Entre 0 y 15 mm."

#### Scenario: Versión sin piezas
- **GIVEN** la nueva versión sin piezas cargadas
- **WHEN** se elige "Guardar versión"
- **THEN** no se pide nada a la API y se muestra "Cargá al menos una pieza."

### Requirement: Guardar la versión
"Guardar versión" SHALL enviar todas las piezas cargadas en un solo `POST /api/v1/pacientes/{id}/periodontograma`, con la recesión negativa tal como se cargó. Con `201`, la sección MUST mostrar la versión que devolvió la API como la más nueva, sumarla al selector y mostrar "Periodontograma guardado.". Si la API rechaza el pedido, MUST mostrar el mensaje de su código y conservar lo cargado.

#### Scenario: Guardar una versión
- **GIVEN** la nueva versión de `ana.ejemplo` con las piezas 16 y 18 (ausente)
- **WHEN** elige "Guardar versión"
- **THEN** se pide `POST /api/v1/pacientes/1/periodontograma` con las dos piezas
- **AND** al responder `201` se muestra la versión nueva con su estado, primera en el selector, y "Periodontograma guardado."

#### Scenario: El servidor rechaza una furcación
- **GIVEN** una nueva versión lista para guardar
- **WHEN** la API responde `422` con el código `FURCACION_NO_APLICA`
- **THEN** se muestra "La furcación solo se carga en molares y en las piezas 14 y 24." y lo cargado se conserva

#### Scenario: El servidor rechaza sitios incompletos
- **GIVEN** una nueva versión lista para guardar
- **WHEN** la API responde `422` con el código `SITIOS_INCOMPLETOS`
- **THEN** se muestra "Cada pieza cargada necesita sus 6 sitios y la movilidad." y lo cargado se conserva

### Requirement: Descartar una carga sin guardar
Si hay una nueva versión con cambios sin guardar, "Cancelar" y "Cambiar de paciente" SHALL pedir confirmación con "Hay un periodontograma sin guardar. ¿Descartarlo?". Al confirmar, se descarta; al elegir "Seguir editando", nada cambia.

#### Scenario: Cambiar de paciente con una carga sin guardar
- **GIVEN** una nueva versión con la pieza 16 editada
- **WHEN** se elige "Cambiar de paciente"
- **THEN** se pregunta "Hay un periodontograma sin guardar. ¿Descartarlo?"
- **AND** al elegir "Seguir editando" la carga sigue igual

#### Scenario: Cancelar sin cambios
- **GIVEN** una nueva versión recién abierta, sin cambios
- **WHEN** se elige "Cancelar"
- **THEN** se vuelve a la versión más nueva sin preguntar

### Requirement: Periodontograma accesible
Cada pieza del mapa SHALL poder enfocarse y elegirse con el teclado, con un nombre accesible que diga la pieza y el estado (por ejemplo, "Pieza 21: Severo"). Cada campo del formulario MUST tener un nombre que diga la pieza, el sitio y el dato (por ejemplo, "Pieza 16, mesiovestibular: profundidad de sondaje en mm").

#### Scenario: Nombre accesible de una pieza
- **GIVEN** la pieza 21 con estado `SEVERO`
- **WHEN** el foco llega a la pieza 21
- **THEN** su nombre accesible es "Pieza 21: Severo"

#### Scenario: Nombre accesible de un campo
- **GIVEN** la nueva versión con la pieza 16 cargada
- **WHEN** el foco llega a la profundidad del sitio mesiovestibular
- **THEN** su nombre accesible es "Pieza 16, mesiovestibular: profundidad de sondaje en mm"

## MODIFIED Requirements

### Requirement: Solo lectura para el administrador
Con sesión `ADMIN`, la ficha SHALL mostrar la ficha básica, el odontograma, el historial, las ausencias y el periodontograma con sus versiones, y MUST NOT ofrecer "Guardar ficha", registrar cambios del odontograma, "Marcar ausente" ni "Nueva versión".

#### Scenario: El administrador no ve controles de edición
- **GIVEN** la sesión de `admin`
- **WHEN** abre la ficha de "Paciente Uno"
- **THEN** los datos de la ficha se ven como texto, sin campos editables
- **AND** no hay botones "Guardar ficha", "Guardar cambios" ni "Marcar ausente"

#### Scenario: El administrador consulta el periodontograma
- **GIVEN** la sesión de `admin` y "Paciente Uno" con una versión de periodontograma
- **WHEN** abre su ficha
- **THEN** ve el periodontograma y el selector de versiones
- **AND** no hay botón "Nueva versión"

### Requirement: Datos de salud solo en la pantalla
La aplicación MUST NOT guardar el contenido de la ficha, del odontograma, del periodontograma ni de las ausencias en la dirección, en `localStorage`, en `sessionStorage` ni en la consola del navegador. Al cerrar la sesión o salir de "Pacientes", la ficha MUST dejar de estar en la página.

#### Scenario: Nada queda guardado en el navegador
- **GIVEN** la sesión de `ana.ejemplo` con la ficha de "Paciente Uno" abierta, con alergias "Penicilina"
- **WHEN** se revisan la dirección, `localStorage`, `sessionStorage` y la consola
- **THEN** ninguno contiene "Penicilina" ni el estado de ninguna pieza

#### Scenario: Salir borra la ficha de la pantalla
- **GIVEN** la ficha de "Paciente Uno" abierta
- **WHEN** se elige "Salir"
- **THEN** la página ya no contiene la ficha

#### Scenario: Una carga del periodontograma no queda en el navegador
- **GIVEN** la sesión de `ana.ejemplo` con una nueva versión que tiene profundidad 7 mm en la pieza 21
- **WHEN** se revisan la dirección, `localStorage`, `sessionStorage` y la consola
- **THEN** ninguno contiene los valores ni el estado de la pieza 21
