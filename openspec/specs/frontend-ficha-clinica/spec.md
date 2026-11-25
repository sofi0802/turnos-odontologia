# frontend-ficha-clinica Specification

## Purpose
Permitir que el odontólogo y el administrador busquen a un paciente y usen su ficha clínica desde la aplicación web: la ficha básica, el odontograma en notación FDI con su historial y las ausencias, con los permisos de RN-CL-01 y sin exponer datos de salud fuera de la pantalla. En los escenarios, con datos ficticios, "Paciente Uno" (id 1, DNI `99000001`) existe; `ana.ejemplo` es `ODONTOLOGO` de la Dra. Ana Ejemplo (profesional 1), `bruno.prueba` es `ODONTOLOGO` del Dr. Bruno Prueba (profesional 2), `admin` es `ADMIN` y `recepcion` es `RECEPCIONISTA`.

## Requirements

### Requirement: Elegir al paciente en "Pacientes"
La página "Pacientes" SHALL ofrecer el buscador de pacientes (por DNI o por nombre y apellido) y, al elegir un paciente, mostrar su ficha con su nombre, apellido y DNI. Sin un paciente elegido, MUST mostrar solo el buscador y el texto "Buscá un paciente para ver su ficha.".

#### Scenario: Abrir la ficha desde el buscador
- **GIVEN** la sesión de `ana.ejemplo` en `#/pacientes`
- **WHEN** escribe `99000001` y elige "Paciente Uno" de la lista
- **THEN** se piden `GET /api/v1/pacientes/1/ficha` y `GET /api/v1/pacientes/1/odontograma`
- **AND** la página muestra "Paciente Uno", DNI `99000001`, y su ficha

#### Scenario: Página sin paciente elegido
- **GIVEN** la sesión de `admin`
- **WHEN** abre `#/pacientes`
- **THEN** la página muestra el buscador y "Buscá un paciente para ver su ficha."
- **AND** no se pide ninguna ficha

#### Scenario: Recargar la página
- **GIVEN** la ficha de "Paciente Uno" abierta en `#/pacientes`
- **WHEN** se recarga el navegador
- **THEN** la página vuelve a mostrar solo el buscador
- **AND** la dirección sigue siendo `#/pacientes`, sin el id ni datos del paciente

### Requirement: Abrir la ficha desde un turno de la agenda
Al elegir "Ver ficha" en un turno de la agenda, la aplicación SHALL ir a "Pacientes" con la ficha del paciente de ese turno ya abierta, sin pasar por el buscador, y recordar ese turno para "Marcar ausente".

#### Scenario: Ver la ficha del paciente del turno
- **GIVEN** la sesión de `ana.ejemplo` y el turno 7 de "Paciente Uno" en su agenda
- **WHEN** elige "Ver ficha" en el turno 7
- **THEN** se muestra "Pacientes" con la ficha de "Paciente Uno"
- **AND** la ficha indica que se abrió desde el turno del 2026-12-01 a las 10:00

### Requirement: Ficha básica
La ficha SHALL mostrar los antecedentes, las alergias y la medicación del paciente, y quién la actualizó por última vez y cuándo. Un paciente sin ficha la MUST mostrar con los tres datos vacíos y el texto "Todavía no se cargó la ficha.", no como un error.

#### Scenario: Ver una ficha cargada
- **GIVEN** que "Paciente Uno" tiene alergias "Penicilina", actualizadas por `ana.ejemplo` el 2026-12-01 a las 09:40
- **WHEN** `admin` abre su ficha
- **THEN** la ficha muestra alergias "Penicilina" y "Actualizada por ana.ejemplo el 01/12/2026 a las 09:40"

#### Scenario: Paciente sin ficha
- **GIVEN** que la API responde la ficha de "Paciente Uno" con los textos vacíos y sin firma
- **WHEN** `ana.ejemplo` abre su ficha
- **THEN** la ficha muestra "Todavía no se cargó la ficha." y los tres campos vacíos

### Requirement: Editar la ficha básica
Con sesión `ODONTOLOGO`, la ficha SHALL ofrecer editar los tres datos y guardarlos con `PUT /api/v1/pacientes/{id}/ficha`, con hasta 4000 caracteres cada uno. Al responder `200`, MUST mostrar la ficha devuelta y el aviso "Ficha guardada.".

#### Scenario: Guardar la ficha
- **GIVEN** la sesión de `ana.ejemplo` con la ficha de "Paciente Uno" abierta
- **WHEN** escribe alergias "Penicilina y látex" y elige "Guardar ficha"
- **THEN** se pide `PUT /api/v1/pacientes/1/ficha` con los tres datos
- **AND** al responder `200` se muestra "Ficha guardada." y la firma nueva

#### Scenario: Texto demasiado largo
- **GIVEN** la sesión de `ana.ejemplo` con la ficha abierta
- **WHEN** escribe antecedentes de 4001 caracteres y elige "Guardar ficha"
- **THEN** el campo muestra "Máximo 4000 caracteres." y no se envía el pedido

#### Scenario: El servidor rechaza los datos
- **GIVEN** la sesión de `ana.ejemplo` con la ficha abierta
- **WHEN** guarda la ficha y la API responde `422` con el código `DATOS_INVALIDOS`
- **THEN** se muestra "Revisá los datos de la ficha." y los textos escritos siguen en los campos

### Requirement: Odontograma
La ficha SHALL dibujar el odontograma en notación FDI, con un selector entre piezas permanentes (11 a 48) y temporales (51 a 85). Cada pieza MUST mostrar su número y sus caras oclusal, mesial, distal, vestibular y lingual (piezas inferiores) o palatina (superiores), cada una con el color y el nombre de su estado vigente, y una leyenda con los siete estados.

#### Scenario: Estado vigente dibujado
- **GIVEN** que `GET /odontograma` de "Paciente Uno" devuelve pieza 16, cara `O`, `OBTURADO`, y pieza 26, cara `M`, `A_TRATAR`
- **WHEN** `admin` abre su ficha
- **THEN** la cara oclusal de la pieza 16 se ve con el color de "Obturado"
- **AND** la cara mesial de la pieza 26 se ve con el color de "A tratar"
- **AND** las demás caras se ven sin estado

#### Scenario: Ver las piezas temporales
- **GIVEN** la ficha de "Paciente Uno" abierta
- **WHEN** se elige "Temporales"
- **THEN** el odontograma muestra las piezas 51 a 55, 61 a 65, 71 a 75 y 81 a 85

#### Scenario: Odontograma vacío
- **GIVEN** que `GET /odontograma` responde una lista vacía
- **WHEN** se abre la ficha
- **THEN** todas las piezas se ven sin estado y se muestra "Sin registros en el odontograma."

### Requirement: Estado de pieza completa
Un registro sin cara SHALL mostrarse como el estado de la pieza completa (por ejemplo, `AUSENTE` o `CORONA`) sobre toda la pieza. Si una cara tiene un registro posterior al de la pieza completa, esa cara MUST mostrar su propio estado.

#### Scenario: Pieza ausente
- **GIVEN** que `GET /odontograma` devuelve la pieza 36 sin cara con estado `AUSENTE`
- **WHEN** se abre la ficha
- **THEN** la pieza 36 se ve marcada entera como "Ausente"

### Requirement: Odontograma accesible
Cada pieza y cada cara del odontograma SHALL poder enfocarse y elegirse con el teclado, y su nombre accesible MUST decir la pieza, la cara y el estado (por ejemplo, "Pieza 16, cara oclusal: Obturado"). El estado MUST NOT depender solo del color.

#### Scenario: Nombre accesible de una cara
- **GIVEN** la pieza 16, cara `O`, con estado `OBTURADO`
- **WHEN** el foco llega a esa cara
- **THEN** su nombre accesible es "Pieza 16, cara oclusal: Obturado"

#### Scenario: Elegir una cara con el teclado
- **GIVEN** la sesión de `ana.ejemplo` con el foco en la pieza 16, cara oclusal
- **WHEN** presiona Enter
- **THEN** queda elegida la pieza 16, cara oclusal, para registrar un cambio

### Requirement: Registrar cambios en el odontograma
Con sesión `ODONTOLOGO`, la ficha SHALL permitir elegir una pieza, una cara o "Pieza completa", y un estado del catálogo, y sumar el cambio a una lista de cambios pendientes que se puede revisar y quitar. "Guardar cambios" MUST enviar todos los pendientes en un solo `PUT /api/v1/pacientes/{id}/odontograma`, hasta 32 por pedido.

#### Scenario: Guardar varios cambios juntos
- **GIVEN** la sesión de `ana.ejemplo` con la ficha de "Paciente Uno" abierta
- **WHEN** suma pieza 16, cara `O`, `CARIES` y pieza 36, pieza completa, `AUSENTE`, y elige "Guardar cambios"
- **THEN** se pide `PUT /api/v1/pacientes/1/odontograma` con esos dos cambios, el segundo sin cara
- **AND** al responder `200` el odontograma muestra los dos estados nuevos, se vacía la lista de pendientes y se muestra "Odontograma guardado."

#### Scenario: Quitar un cambio pendiente
- **GIVEN** dos cambios pendientes
- **WHEN** se quita el primero
- **THEN** queda un cambio pendiente y no se pidió nada a la API

#### Scenario: Límite de cambios
- **GIVEN** 32 cambios pendientes
- **WHEN** se intenta sumar otro
- **THEN** se muestra "Guardá los cambios antes de sumar más (máximo 32)." y no se suma

#### Scenario: Caras según el maxilar
- **GIVEN** la sesión de `ana.ejemplo`
- **WHEN** elige la pieza 16
- **THEN** las caras ofrecidas son oclusal, mesial, distal, vestibular y palatina, sin lingual

#### Scenario: El servidor rechaza un cambio
- **GIVEN** cambios pendientes
- **WHEN** elige "Guardar cambios" y la API responde `422` con el código `CARA_INVALIDA`
- **THEN** se muestra "Hay una cara que no corresponde a esa pieza." y los cambios siguen pendientes

### Requirement: Historial del odontograma
La ficha SHALL mostrar el historial del odontograma (`GET /odontograma/historial`) del registro más nuevo al más viejo, con la fecha y hora, la pieza, la cara (o "Pieza completa"), el estado y el profesional. El historial MUST NOT ofrecer editar ni borrar registros.

#### Scenario: Un cambio posterior no borra el anterior
- **GIVEN** que el historial tiene pieza 16, cara `O`, `CARIES`, de la Dra. Ana Ejemplo, y después `OBTURADO` del Dr. Bruno Prueba
- **WHEN** se abre el historial
- **THEN** se muestran los dos registros, primero `OBTURADO` del Dr. Bruno Prueba
- **AND** ningún registro tiene botones para editar o borrar

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

### Requirement: Ausencias en la ficha
La ficha SHALL listar las ausencias del paciente con la fecha y hora del turno y el profesional. Una ausencia que deshizo la recepción MUST mostrarse como "Deshecha por <usuario> el <fecha> a las <hora>". Sin ausencias, muestra "Sin ausencias registradas.".

#### Scenario: Ausencia vigente y ausencia deshecha
- **GIVEN** que la ficha trae una ausencia del 2026-12-01 a las 10:00 con la Dra. Ana Ejemplo y otra del 2026-11-20 a las 09:00, deshecha por `recepcion` el 20/11/2026 a las 09:22
- **WHEN** se abre la ficha
- **THEN** la primera se muestra como "Ausente"
- **AND** la segunda como "Deshecha por recepcion el 20/11/2026 a las 09:22"

### Requirement: Marcar ausente desde la ficha
Con sesión `ODONTOLOGO` y la ficha abierta desde un turno de su profesional que sigue activo y sin llegada, la ficha SHALL ofrecer "Marcar ausente", habilitado a partir de los 15 minutos del inicio (antes, deshabilitado con "Desde las HH:mm"). Envía `POST /api/v1/pacientes/{id}/ausencias` con el `turnoId` y, con `200`, recarga la ficha.

#### Scenario: Marcar ausente
- **GIVEN** la sesión de `ana.ejemplo`, el momento actual el 2026-12-01 a las 10:20 y la ficha abierta desde su turno 7 de 10:00 a 10:30
- **WHEN** elige "Marcar ausente"
- **THEN** se pide `POST /api/v1/pacientes/1/ausencias` con `turnoId` 7
- **AND** al responder `200` la ficha se vuelve a pedir y muestra la ausencia

#### Scenario: Antes de la tolerancia
- **GIVEN** la sesión de `ana.ejemplo`, el momento actual el 2026-12-01 a las 10:10 y la ficha abierta desde su turno 7 de 10:00 a 10:30
- **WHEN** se muestra la ficha
- **THEN** "Marcar ausente" está deshabilitado y dice "Desde las 10:15"

#### Scenario: Turno de otro profesional
- **GIVEN** la sesión de `bruno.prueba` y la ficha abierta desde un turno de la Dra. Ana Ejemplo
- **WHEN** se muestra la ficha
- **THEN** no se ofrece "Marcar ausente"

#### Scenario: Ficha abierta desde el buscador
- **GIVEN** la sesión de `ana.ejemplo` y la ficha de "Paciente Uno" abierta desde el buscador
- **WHEN** se muestra la ficha
- **THEN** no se ofrece "Marcar ausente"

#### Scenario: El servidor rechaza la ausencia
- **GIVEN** la ficha abierta desde el turno 7 y "Marcar ausente" habilitado
- **WHEN** lo elige y la API responde `409` con el código `LLEGADA_REGISTRADA`
- **THEN** se muestra "El paciente ya tiene la llegada registrada." y la ficha se vuelve a pedir

### Requirement: Errores al cargar la ficha
Si falla la carga de la ficha o del odontograma, la página SHALL mostrar el mensaje del error y "Reintentar", sin datos parciales. Un `404 RECURSO_NO_ENCONTRADO` MUST mostrar "No encontramos a ese paciente." y un `403`, "No tenés permiso para ver la ficha clínica.".

#### Scenario: Paciente inexistente
- **GIVEN** la sesión de `ana.ejemplo`
- **WHEN** se abre la ficha y la API responde `404` con el código `RECURSO_NO_ENCONTRADO`
- **THEN** se muestra "No encontramos a ese paciente." y el buscador

#### Scenario: Falla y reintento
- **GIVEN** la sesión de `ana.ejemplo`
- **WHEN** el pedido del odontograma falla por la red
- **THEN** se muestra el error y "Reintentar", sin la ficha básica
- **AND** al elegir "Reintentar" se vuelven a pedir la ficha y el odontograma

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
La sección SHALL dibujar las 32 piezas permanentes por cuadrante (18 a 11 y 21 a 28 arriba, 48 a 41 y 31 a 38 abajo). Cada pieza cargada MUST mostrar su número y su estado con el nombre ("Sano", "Leve", "Moderado", "Severo" o "Ausente") y un color, de modo que el estado no dependa solo del color. Una pieza que la versión no tiene se ve como "Sin cargar".

#### Scenario: Estados dibujados
- **GIVEN** que la versión tiene la pieza 16 `SANO`, la 21 `SEVERO` y la 18 ausente
- **WHEN** se abre la ficha
- **THEN** la pieza 16 se ve como "Sano", la 21 como "Severo" y la 18 como "Ausente"
- **AND** las demás piezas se ven como "Sin cargar"

### Requirement: Dibujo de las piezas
Cada pieza del mapa SHALL dibujarse como un diente según su tipo (incisivo, canino, premolar o molar), con la raíz hacia arriba en el maxilar superior y hacia abajo en el inferior. Sobre la cara vestibular de una pieza cargada SHALL trazar el margen de la encía y el fondo de la bolsa a partir de la recesión y la profundidad de sus sitios. Una pieza ausente se dibuja tachada y sin líneas, y una sin cargar, con el contorno punteado.

#### Scenario: Líneas del margen y de la bolsa
- **GIVEN** que la pieza 16 tiene en el sitio `V` recesión 0 mm y profundidad 4 mm
- **WHEN** se abre la ficha
- **THEN** en el dibujo de la pieza 16 el margen de la encía pasa por el límite amelocementario en el sitio vestibular
- **AND** el fondo de la bolsa queda 4 mm más hacia la raíz

### Requirement: Detalle de una pieza
Al elegir una pieza cargada, la sección SHALL mostrar sus 6 sitios (mesiovestibular, vestibular, distovestibular, mesiolingual o mesiopalatino, lingual o palatino, distolingual o distopalatino según el maxilar) con la profundidad de sondaje, la recesión, la pérdida de inserción, el sangrado y la placa, más la movilidad, la furcación si la tiene y el estado.

#### Scenario: Ver los sitios de una pieza superior
- **GIVEN** que la versión tiene la pieza 16 con profundidad 4 mm y recesión 0 mm en el sitio `V`
- **WHEN** se elige la pieza 16
- **THEN** se ven los sitios mesiovestibular, vestibular, distovestibular, mesiopalatino, palatino y distopalatino
- **AND** el sitio vestibular muestra profundidad 4 mm, recesión 0 mm y pérdida de inserción 4 mm

### Requirement: Vista grande de la pieza
Al elegir una pieza cargada, la sección SHALL dibujarla en grande por vestibular y por palatino o lingual, con una grilla de 1 mm y las líneas del margen y de la bolsa de cada cara. El dibujo es decorativo: los valores se leen en el detalle.

#### Scenario: Pieza inferior en grande
- **GIVEN** que la versión tiene la pieza 46 cargada
- **WHEN** se elige la pieza 46
- **THEN** se ven dos dibujos rotulados "Vestibular" y "Lingual", con la raíz hacia abajo

#### Scenario: Cara con un sitio vacío
- **GIVEN** una nueva versión con la pieza 36 y el sitio `DL` vacío
- **WHEN** se mira la pieza 36
- **THEN** el dibujo lingual se ve sin líneas y el vestibular con las suyas

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
