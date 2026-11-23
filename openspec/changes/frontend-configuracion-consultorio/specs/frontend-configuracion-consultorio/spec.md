# Spec Delta

## Purpose

Definir la página "Configuración" del personal, que usa la API de C-04 y C-07 para gestionar:

- profesionales, sillones y prestaciones;
- especialidades y sillón habitual;
- el margen entre turnos;
- los horarios de trabajo, las ausencias y los bloqueos.

Cada rol ve y edita solo lo que le permite la matriz RBAC.

Datos ficticios de los escenarios (salvo que el escenario diga otra cosa):

- **Usuarios:** `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`) y `ana.ejemplo` (`ODONTOLOGO`, vinculado a la Dra. Ana Ejemplo).
- **Profesionales:**
  - la Dra. Ana Ejemplo (id 1, matrícula `MP-0001`, sillón habitual "Sillón 1");
  - el Dr. Bruno Prueba (id 2, `MP-0002`, "Sillón 2").
- **Sillones:** "Sillón 1" (id 1) y "Sillón 2" (id 2).
- **Prestaciones:**
  - "Consulta o control" (id 1, 30 min);
  - "Tratamiento de conducto" (id 3, 60 min), que hace solo el Dr. Bruno Prueba.
- **Margen:** 10 minutos.
- **Horario de la Dra. Ana Ejemplo:** lunes a viernes de 08:00 a 13:00.
- **Zona horaria:** `America/Argentina/Buenos_Aires`.

## ADDED Requirements

### Requirement: Secciones según el rol
`#/configuracion` SHALL mostrar estas pestañas:

- **Para `ADMIN`:** "Profesionales", "Sillones", "Prestaciones", "Margen", "Horarios", "Ausencias" y "Bloqueos".
- **Para `RECEPCIONISTA`:** las mismas siete.
- **Para `ODONTOLOGO`:** las mismas siete.

La pestaña elegida MUST quedar marcada con `aria-selected` y solo su contenido MUST estar visible. Como Agenda, la página MUST exigir una sesión del personal: sin sesión lleva a `#/ingreso` sin pedir datos de configuración.

#### Scenario: Abrir Configuración sin sesión
- **GIVEN** que `GET /api/v1/public/sesion` informa que no hay sesión
- **WHEN** se abre la aplicación en `#/configuracion`
- **THEN** se muestra la página de ingreso en la dirección `#/ingreso`
- **AND** no se envía ningún pedido a `/api/v1/profesionales`

#### Scenario: Pestañas del administrador
- **GIVEN** una sesión de `admin`
- **WHEN** se abre `#/configuracion`
- **THEN** se ven las pestañas "Profesionales", "Sillones", "Prestaciones", "Margen", "Horarios", "Ausencias" y "Bloqueos"
- **AND** "Profesionales" está seleccionada y muestra la lista de profesionales

#### Scenario: Recepción ve los bloqueos sin poder registrarlos
- **GIVEN** una sesión de `recepcion`
- **WHEN** abre la pestaña "Bloqueos" y elige a la Dra. Ana Ejemplo
- **THEN** se pide `GET /api/v1/profesionales/1/bloqueos`
- **AND** no hay botones para registrar ni borrar bloqueos

#### Scenario: Cambiar de pestaña
- **GIVEN** una sesión de `admin` en `#/configuracion`
- **WHEN** elige la pestaña "Margen"
- **THEN** la pestaña "Margen" tiene `aria-selected="true"` y se muestra el margen
- **AND** la lista de profesionales ya no está visible

### Requirement: Catálogos del consultorio
Las pestañas "Profesionales", "Sillones" y "Prestaciones" SHALL listar lo que devuelven:

- `GET /api/v1/profesionales`: nombre, apellido y matrícula;
- `GET /api/v1/sillones`: nombre;
- `GET /api/v1/prestaciones`: nombre y duración en minutos.

Cada elemento MUST mostrar si está "Activo" o "Inactivo".

#### Scenario: Lista de prestaciones
- **GIVEN** una sesión de `recepcion`
- **WHEN** abre la pestaña "Prestaciones"
- **THEN** se ve "Consulta o control" con "30 min" y "Activo"

### Requirement: Edición de catálogos por el administrador
Para `ADMIN`, cada pestaña de catálogo SHALL ofrecer:

- **"Agregar"**, que envía `POST`;
- **"Editar"** en cada elemento, que envía `PUT` a `/{id}`;
- **"Desactivar"** o **"Activar"** según el estado, que envía `POST` a `/{id}/desactivar` o `/{id}/activar`.

Después de cada cambio, la lista MUST actualizarse. Un error de la API MUST mostrarse en el formulario, que sigue abierto con sus datos.

#### Scenario: El administrador agrega un sillón
- **GIVEN** una sesión de `admin` en la pestaña "Sillones"
- **WHEN** elige "Agregar", escribe "Sillón 3" y guarda
- **THEN** se envía `POST /api/v1/sillones` con `{"nombre":"Sillón 3"}`
- **AND** la lista muestra "Sillón 3" como "Activo"

#### Scenario: El administrador edita un profesional
- **GIVEN** una sesión de `admin` en la pestaña "Profesionales"
- **WHEN** elige "Editar" en la Dra. Ana Ejemplo, cambia el apellido a "Ejemplar" y guarda
- **THEN** se envía `PUT /api/v1/profesionales/1` con nombre, apellido "Ejemplar" y matrícula `MP-0001`
- **AND** la lista muestra "Ana Ejemplar"

#### Scenario: El administrador desactiva una prestación
- **GIVEN** una sesión de `admin` en la pestaña "Prestaciones"
- **WHEN** elige "Desactivar" en "Consulta o control"
- **THEN** se envía `POST /api/v1/prestaciones/1/desactivar`
- **AND** "Consulta o control" pasa a "Inactivo" y ofrece "Activar"

#### Scenario: Matrícula repetida
- **GIVEN** una sesión de `admin` agregando un profesional con la matrícula `MP-0002`
- **WHEN** guarda y la API responde `409` con el código `MATRICULA_DUPLICADA`
- **THEN** el formulario sigue abierto con sus datos
- **AND** muestra "Ya existe un profesional con esa matrícula."

#### Scenario: Duración que no es múltiplo de 5
- **GIVEN** una sesión de `admin` agregando una prestación de 32 minutos
- **WHEN** guarda y la API responde `422` con el código `DURACION_INVALIDA`
- **THEN** el formulario muestra "La duración tiene que ser mayor que 0 y múltiplo de 5."

### Requirement: Alta completa de un profesional
Para `ADMIN`, el formulario "Agregar" de "Profesionales" SHALL sumar a nombre, apellido y matrícula las casillas de prestaciones activas y el selector de sillón habitual. Al guardar envía `POST /api/v1/profesionales` y, con el id devuelto, `PUT .../prestaciones` si se marcó alguna y `PUT .../sillon-habitual` si se eligió un sillón.

#### Scenario: Alta con prestaciones y sillón
- **GIVEN** una sesión de `admin` en la pestaña "Profesionales", con el "Sillón 3" (id 3) libre
- **WHEN** elige "Agregar", completa Carla Muestra con la matrícula `MP-0003`, marca "Consulta o control", elige el "Sillón 3" y guarda
- **AND** la API responde al alta con el id 3
- **THEN** se envía `POST /api/v1/profesionales`
- **AND** después se envía `PUT /api/v1/profesionales/3/prestaciones` con `{"prestacionIds":[1]}`
- **AND** después se envía `PUT /api/v1/profesionales/3/sillon-habitual` con `{"sillonId":3}`
- **AND** la lista muestra "Carla Muestra"

#### Scenario: Alta sin prestaciones ni sillón
- **GIVEN** una sesión de `admin` agregando un profesional
- **WHEN** no marca prestaciones, deja "Sin sillón habitual" y guarda
- **THEN** solo se envía `POST /api/v1/profesionales`

### Requirement: Falla parcial del alta de un profesional
Si el alta se acepta pero falla un pedido siguiente, el profesional MUST quedar en la lista y la página MUST mostrar "El profesional se creó, pero no se pudieron guardar sus prestaciones o su sillón." con el mensaje del error. Si falla el `POST`, el formulario sigue abierto y no se envían los otros pedidos.

#### Scenario: Falla el sillón después del alta
- **GIVEN** una sesión de `admin` agregando un profesional con el "Sillón 3"
- **WHEN** el alta responde `201` y `PUT .../sillon-habitual` responde `409` con el código `SILLON_HABITUAL_OCUPADO`
- **THEN** la lista muestra al profesional nuevo
- **AND** se muestra "El profesional se creó, pero no se pudieron guardar sus prestaciones o su sillón." con "Ese sillón ya es el habitual de otro profesional."

### Requirement: Especialidades y sillón habitual
En la pestaña "Profesionales", el detalle de cada profesional SHALL mostrar:

- sus prestaciones habilitadas, de `GET /api/v1/profesionales/{id}/prestaciones`;
- su sillón habitual, según `sillonHabitualId`, o "Sin sillón habitual".

#### Scenario: Ver las especialidades
- **GIVEN** una sesión de `recepcion` en la pestaña "Profesionales"
- **WHEN** abre el detalle del Dr. Bruno Prueba
- **THEN** ve "Tratamiento de conducto" entre sus prestaciones y "Sillón 2" como sillón habitual
- **AND** no hay casillas ni botones para cambiarlos

### Requirement: Edición de especialidades y sillón habitual
Para `ADMIN`, el detalle de un profesional SHALL permitir:

- marcar las prestaciones activas que hace y guardarlas con `PUT /api/v1/profesionales/{id}/prestaciones` y `{"prestacionIds":[...]}`;
- elegir el sillón habitual entre los sillones activos, o ninguno, y guardarlo con `PUT /api/v1/profesionales/{id}/sillon-habitual` y `{"sillonId":...}`.

#### Scenario: El administrador habilita una prestación
- **GIVEN** una sesión de `admin` en el detalle de la Dra. Ana Ejemplo
- **WHEN** marca "Tratamiento de conducto" y guarda las prestaciones
- **THEN** se envía `PUT /api/v1/profesionales/1/prestaciones` con los ids de sus prestaciones anteriores más el 3
- **AND** se muestra "Prestaciones guardadas."

#### Scenario: El administrador quita el sillón habitual
- **GIVEN** una sesión de `admin` en el detalle de la Dra. Ana Ejemplo
- **WHEN** elige "Sin sillón habitual" y guarda
- **THEN** se envía `PUT /api/v1/profesionales/1/sillon-habitual` con `{"sillonId":null}`

### Requirement: Sillones habituales ocupados
En el detalle y en el alta de un profesional, un sillón que ya es el habitual de otro profesional (RN-RO-09) SHALL aparecer deshabilitado y con el nombre de quien lo tiene, por ejemplo "Sillón 2 (de Bruno Prueba)". Si igual la API responde `SILLON_HABITUAL_OCUPADO`, se muestra "Ese sillón ya es el habitual de otro profesional." y el sillón anterior sigue elegido.

#### Scenario: El sillón de otro profesional no se puede elegir
- **GIVEN** una sesión de `admin` en el detalle de la Dra. Ana Ejemplo
- **WHEN** abre el selector de sillón habitual
- **THEN** "Sillón 2 (de Bruno Prueba)" está deshabilitado
- **AND** "Sillón 1" está habilitado y elegido

#### Scenario: El sillón lo tomó otro profesional mientras tanto
- **GIVEN** una sesión de `admin` en el detalle de la Dra. Ana Ejemplo
- **WHEN** guarda el sillón habitual y la API responde `409` con el código `SILLON_HABITUAL_OCUPADO`
- **THEN** se muestra "Ese sillón ya es el habitual de otro profesional."

### Requirement: Margen entre turnos
La pestaña "Margen" SHALL mostrar los minutos de `GET /api/v1/configuracion/margen`. Para `ADMIN`, MUST permitir cambiarlos con `PUT /api/v1/configuracion/margen` y `{"minutos":N}`. El campo avanza de 5 en 5 (`step="5"`).

Si la API rechaza el valor con `MARGEN_INVALIDO`, la pestaña MUST mostrar "El margen tiene que ser de 10 minutos o más." y conservar el valor anterior.

#### Scenario: El administrador cambia el margen
- **GIVEN** una sesión de `admin` en la pestaña "Margen", que muestra 10 minutos
- **WHEN** escribe 15 y guarda
- **THEN** se envía `PUT /api/v1/configuracion/margen` con `{"minutos":15}`
- **AND** se muestra "Margen guardado: 15 minutos."

#### Scenario: Margen menor que el mínimo
- **GIVEN** una sesión de `admin` en la pestaña "Margen"
- **WHEN** escribe 5, guarda y la API responde `422` con el código `MARGEN_INVALIDO`
- **THEN** se muestra "El margen tiene que ser de 10 minutos o más."
- **AND** el margen vigente sigue siendo 10 minutos

#### Scenario: Recepción solo ve el margen
- **GIVEN** una sesión de `recepcion`
- **WHEN** abre la pestaña "Margen"
- **THEN** ve "10 minutos"
- **AND** no hay un campo ni un botón para cambiarlo

### Requirement: Profesional de las secciones de agenda
Las pestañas "Horarios", "Ausencias" y "Bloqueos" SHALL trabajar sobre un profesional por vez:

- **Para `ADMIN` y `RECEPCIONISTA`:** se elige en un selector con los profesionales activos.
- **Para `ODONTOLOGO`:** la pestaña MUST usar el `profesionalId` de su sesión, sin ofrecer un selector. Si su sesión no tiene `profesionalId`, MUST mostrar "Tu usuario no está vinculado a un profesional." y no pedir nada a la API.

#### Scenario: El odontólogo ve su propio horario
- **GIVEN** una sesión de `ana.ejemplo`, cuya sesión informa `profesionalId` 1
- **WHEN** abre la pestaña "Horarios"
- **THEN** se pide `GET /api/v1/profesionales/1/horarios`
- **AND** no hay un selector de profesional

#### Scenario: Odontólogo sin profesional vinculado
- **GIVEN** una sesión de `ODONTOLOGO` sin `profesionalId`
- **WHEN** abre la pestaña "Bloqueos"
- **THEN** se muestra "Tu usuario no está vinculado a un profesional."
- **AND** no se envía ningún pedido de bloqueos

### Requirement: Todos los profesionales en ausencias y bloqueos
Para `ADMIN` y `RECEPCIONISTA`, el selector de "Ausencias" y "Bloqueos" SHALL ofrecer "Todos los profesionales", elegido al abrir la pestaña. Con esa opción, la pestaña pide los períodos de cada profesional activo y los muestra juntos, ordenados por inicio, con una columna "Profesional". Para registrar, el formulario pide el profesional.

#### Scenario: Ausencias de todos los profesionales
- **GIVEN** una sesión de `recepcion` con una ausencia de la Dra. Ana Ejemplo el 2026-12-07 y otra del Dr. Bruno Prueba el 2026-12-03
- **WHEN** abre la pestaña "Ausencias"
- **THEN** "Todos los profesionales" está elegido
- **AND** se piden `GET /api/v1/profesionales/1/ausencias` y `GET /api/v1/profesionales/2/ausencias`
- **AND** la lista muestra primero la del Dr. Bruno Prueba y después la de la Dra. Ana Ejemplo, cada una con el nombre de su profesional

#### Scenario: Bloqueos de todos los profesionales
- **GIVEN** una sesión de `admin` con un bloqueo del Dr. Bruno Prueba
- **WHEN** abre la pestaña "Bloqueos"
- **THEN** "Todos los profesionales" está elegido y la lista muestra el bloqueo con "Bruno Prueba"

#### Scenario: Registrar con todos los profesionales elegidos
- **GIVEN** una sesión de `admin` en "Ausencias" con "Todos los profesionales" elegido
- **WHEN** elige "Registrar ausencia"
- **THEN** el formulario pide el profesional
- **AND** al guardar con la Dra. Ana Ejemplo se envía `POST /api/v1/profesionales/1/ausencias`

#### Scenario: El odontólogo no ve la opción de todos
- **GIVEN** una sesión de `ana.ejemplo`
- **WHEN** abre la pestaña "Ausencias"
- **THEN** no hay un selector de profesional ni la opción "Todos los profesionales"

#### Scenario: Falla la carga de un profesional en la vista de todos
- **GIVEN** una sesión de `recepcion` en "Ausencias" con "Todos los profesionales"
- **WHEN** `GET /api/v1/profesionales/2/ausencias` responde `500`
- **THEN** la pestaña muestra un mensaje de error y el botón "Reintentar"

### Requirement: Horarios de trabajo
La pestaña "Horarios" SHALL mostrar las franjas de `GET /api/v1/profesionales/{id}/horarios`, agrupadas por día de la semana (de lunes a domingo), cada una con su hora de inicio y fin. Un día sin franjas MUST mostrarse como "No atiende".

#### Scenario: Ver el horario de un profesional
- **GIVEN** una sesión de `recepcion` en la pestaña "Horarios"
- **WHEN** elige a la Dra. Ana Ejemplo
- **THEN** lunes a viernes muestran "08:00 a 13:00"
- **AND** sábado y domingo muestran "No atiende"
- **AND** no hay botones para agregar, quitar ni guardar franjas

### Requirement: Edición de horarios por el administrador
Para `ADMIN`, la pestaña "Horarios" SHALL permitir:

- agregar franjas (ver "Franja para varios días") y quitarlas;
- guardar el conjunto completo con `PUT /api/v1/profesionales/{id}/horarios`, `{"franjas":[{"dia":1,"desde":"08:00","hasta":"13:00"}, ...]}`.

Los días van de 1 (lunes) a 7 (domingo). Si la API responde `FRANJAS_SUPERPUESTAS` o `HORARIO_INVALIDO`, la pestaña MUST mostrar el mensaje correspondiente y conservar lo que se estaba editando.

#### Scenario: El administrador agrega una franja
- **GIVEN** una sesión de `admin` con el horario de la Dra. Ana Ejemplo
- **WHEN** agrega el sábado de 09:00 a 12:00 y guarda
- **THEN** se envía `PUT /api/v1/profesionales/1/horarios` con las cinco franjas de lunes a viernes más `{"dia":6,"desde":"09:00","hasta":"12:00"}`
- **AND** se muestra "Horario guardado."

#### Scenario: Franjas superpuestas
- **GIVEN** una sesión de `admin` que agregó el lunes de 12:00 a 15:00 a la Dra. Ana Ejemplo
- **WHEN** guarda y la API responde `422` con el código `FRANJAS_SUPERPUESTAS`
- **THEN** se muestra "Hay franjas del mismo día que se superponen."
- **AND** la franja de 12:00 a 15:00 sigue en la lista sin guardar

### Requirement: Franja para varios días
"Agregar franja" SHALL pedir uno o más días con casillas de lunes a domingo, más "desde" y "hasta", y agregar una franja con ese horario por cada día marcado. Sin ningún día marcado, MUST mostrar "Elegí al menos un día." y no agregar nada.

#### Scenario: Una franja para varios días
- **GIVEN** una sesión de `admin` con el Dr. Bruno Prueba sin franjas
- **WHEN** elige "Agregar franja", marca lunes, miércoles y viernes, elige de 14:00 a 18:30 y guarda el horario
- **THEN** se envía `PUT /api/v1/profesionales/2/horarios` con `{"dia":1,"desde":"14:00","hasta":"18:30"}`, `{"dia":3,...}` y `{"dia":5,...}`

#### Scenario: Agregar franja sin días
- **GIVEN** una sesión de `admin` en "Agregar franja"
- **WHEN** no marca ningún día y confirma
- **THEN** se muestra "Elegí al menos un día."
- **AND** no se agrega ninguna franja

### Requirement: Horas de 5 en 5
Todo campo de hora de la página SHALL elegirse con un selector de hora (00 a 23) y otro de minutos que solo ofrece 00, 05, 10 … 55. Esto incluye las franjas, el inicio y el fin de ausencias y bloqueos, y el inicio opcional de una reasignación. Al mostrar un valor que no es múltiplo de 5 (por ejemplo, una franja cargada antes por la API), se muestra tal cual y no se redondea.

#### Scenario: Los minutos avanzan de 5 en 5
- **GIVEN** una sesión de `admin` en "Agregar franja"
- **WHEN** abre el selector de minutos de "desde"
- **THEN** las opciones son exactamente 00, 05, 10, 15, 20, 25, 30, 35, 40, 45, 50 y 55

#### Scenario: Minutos del inicio de una ausencia
- **GIVEN** una sesión de `recepcion` registrando una ausencia
- **WHEN** abre el selector de minutos del inicio
- **THEN** solo ofrece múltiplos de 5

### Requirement: Ausencias y licencias
La pestaña "Ausencias" SHALL listar los períodos de `GET /api/v1/profesionales/{id}/ausencias`, cada uno con:

- tipo ("Ausencia" o "Licencia");
- inicio y fin con fecha y hora;
- motivo.

`ODONTOLOGO` solo ve las propias, sin botones para registrar ni borrar.

#### Scenario: El odontólogo solo ve sus ausencias
- **GIVEN** una sesión de `ana.ejemplo`
- **WHEN** abre la pestaña "Ausencias"
- **THEN** se pide `GET /api/v1/profesionales/1/ausencias`
- **AND** no hay botones para registrar ni borrar

### Requirement: Registro y borrado de ausencias
Para `ADMIN` y `RECEPCIONISTA`, la pestaña "Ausencias" SHALL permitir:

- **Registrar** una ausencia con tipo, inicio, fin y motivo opcional, con `POST /api/v1/profesionales/{id}/ausencias`.
- **Borrar** una ausencia, previa confirmación, con `DELETE /api/v1/profesionales/{id}/ausencias/{ausenciaId}`.

Si la API responde `PERIODO_INVALIDO`, la pestaña MUST mostrar "El fin tiene que ser posterior al inicio."

#### Scenario: Recepción registra una licencia
- **GIVEN** una sesión de `recepcion` con la Dra. Ana Ejemplo elegida en la pestaña "Ausencias"
- **WHEN** registra una "Licencia" del 2026-12-07 08:00 al 2026-12-11 13:00 con el motivo "Congreso"
- **THEN** se envía `POST /api/v1/profesionales/1/ausencias` con `tipo` `LICENCIA`, `inicio` `2026-12-07T08:00:00-03:00`, `fin` `2026-12-11T13:00:00-03:00` y `motivo` "Congreso"
- **AND** la lista muestra la licencia

#### Scenario: Fin anterior al inicio
- **GIVEN** una sesión de `recepcion` registrando una ausencia
- **WHEN** guarda y la API responde `422` con el código `PERIODO_INVALIDO`
- **THEN** se muestra "El fin tiene que ser posterior al inicio."

### Requirement: Turnos afectados por una ausencia
Los turnos afectados de una ausencia o de un bloqueo SHALL mostrarse:

- **al registrar el período**, con los `turnosAfectados` de la respuesta;
- **con "Ver turnos afectados"** (`ADMIN` y `RECEPCIONISTA`), con `GET /api/v1/turnos` y el período como `desde`, `hasta` y `profesionalId`, solo los `RESERVADO` o `CONFIRMADO`.

Cada turno afectado MUST mostrar fecha, hora y prestación, sin datos de salud.

#### Scenario: Registrar una ausencia con turnos dados
- **GIVEN** una sesión de `recepcion` que registra una ausencia de la Dra. Ana Ejemplo el 2026-12-07 de 08:00 a 13:00
- **WHEN** la API responde `201` con un turno afectado de "Consulta o control" a las 09:00
- **THEN** la pestaña muestra "Turnos afectados: 1" y el turno del lunes 7 de diciembre a las 09:00
- **AND** el turno tiene el botón "Reasignar"

#### Scenario: Ver los turnos afectados de una ausencia ya registrada
- **GIVEN** una sesión de `admin` con una ausencia de la Dra. Ana Ejemplo el 2026-12-07 de 08:00 a 13:00
- **WHEN** elige "Ver turnos afectados"
- **THEN** se pide `GET /api/v1/turnos` con `desde` `2026-12-07T08:00:00-03:00`, `hasta` `2026-12-07T13:00:00-03:00` y `profesionalId` 1
- **AND** se listan solo los turnos `RESERVADO` o `CONFIRMADO`

### Requirement: Reasignación de un turno afectado
Para `ADMIN` y `RECEPCIONISTA`, cada turno afectado SHALL ofrecer "Reasignar": se elige otro profesional activo habilitado para la prestación y, opcionalmente, otro inicio y otro sillón. Se envía `POST /api/v1/profesionales/{id}/{ausencias|bloqueos}/{periodoId}/turnos/{turnoId}/reasignar` (RN-AG-16, RN-AG-19).

Si la API lo acepta, el turno MUST salir de la lista con el aviso "Turno reasignado.". Si responde un error de negocio, MUST mostrarse su mensaje y el turno queda en la lista.

#### Scenario: Reasignar un turno afectado
- **GIVEN** una sesión de `recepcion` con el turno afectado 10 de la ausencia 4 de la Dra. Ana Ejemplo
- **WHEN** elige "Reasignar", elige al Dr. Bruno Prueba y confirma
- **THEN** se envía `POST /api/v1/profesionales/1/ausencias/4/turnos/10/reasignar` con `profesionalId` 2
- **AND** se muestra "Turno reasignado." y el turno sale de la lista

#### Scenario: El otro profesional no hace la prestación
- **GIVEN** una sesión de `recepcion` reasignando un turno afectado de "Tratamiento de conducto"
- **WHEN** abre "Reasignar"
- **THEN** el selector de profesionales no ofrece a quienes no hacen "Tratamiento de conducto"

#### Scenario: Reasignar un turno afectado por un bloqueo
- **GIVEN** una sesión de `recepcion` con el turno afectado 12 del bloqueo 7 de la Dra. Ana Ejemplo
- **WHEN** elige "Reasignar", elige al Dr. Bruno Prueba y confirma
- **THEN** se envía `POST /api/v1/profesionales/1/bloqueos/7/turnos/12/reasignar` con `profesionalId` 2
- **AND** se muestra "Turno reasignado." y el turno sale de la lista

### Requirement: Bloqueos de agenda
La pestaña "Bloqueos" SHALL listar `GET /api/v1/profesionales/{id}/bloqueos` (inicio, fin y motivo), registrar con `POST` al mismo recurso (inicio, fin y motivo opcional) y borrar, previa confirmación, con `DELETE .../bloqueos/{bloqueoId}`. `ADMIN` opera sobre cualquier profesional, `ODONTOLOGO` solo sobre el suyo y `RECEPCIONISTA` solo los lee.

#### Scenario: El odontólogo bloquea su agenda
- **GIVEN** una sesión de `ana.ejemplo` en la pestaña "Bloqueos"
- **WHEN** registra un bloqueo del 2026-12-09 de 11:00 a 12:00 con el motivo "Trámite"
- **THEN** se envía `POST /api/v1/profesionales/1/bloqueos` con `inicio` `2026-12-09T11:00:00-03:00`, `fin` `2026-12-09T12:00:00-03:00` y `motivo` "Trámite"
- **AND** la lista muestra el bloqueo

#### Scenario: Borrar un bloqueo
- **GIVEN** una sesión de `admin` con un bloqueo 7 del Dr. Bruno Prueba
- **WHEN** elige "Borrar" y confirma
- **THEN** se envía `DELETE /api/v1/profesionales/2/bloqueos/7`
- **AND** el bloqueo sale de la lista

### Requirement: Turnos afectados por un bloqueo
Si el bloqueo se cruza con turnos dados, la pestaña MUST mostrar cuántos hay ("El bloqueo se cruza con 2 turnos dados.") y listarlos como en "Turnos afectados por una ausencia". Para `ADMIN`, cada uno ofrece "Reasignar". Para `ODONTOLOGO`, la lista no ofrece "Reasignar" y se suma "Avisale a la recepción para reasignarlos.".

#### Scenario: Bloqueo que se cruza con turnos
- **GIVEN** una sesión de `ana.ejemplo` que registra un bloqueo
- **WHEN** la API responde `201` con 2 `turnosAfectados`
- **THEN** se muestra "El bloqueo se cruza con 2 turnos dados." y "Avisale a la recepción para reasignarlos."
- **AND** los dos turnos se listan sin el botón "Reasignar"

#### Scenario: El administrador reasigna desde un bloqueo nuevo
- **GIVEN** una sesión de `admin` que registra un bloqueo de la Dra. Ana Ejemplo
- **WHEN** la API responde `201` con 1 turno afectado
- **THEN** el turno se lista con el botón "Reasignar"

#### Scenario: Recepción ve los turnos afectados de un bloqueo
- **GIVEN** una sesión de `recepcion` con el bloqueo 7 de la Dra. Ana Ejemplo el 2026-12-09 de 09:00 a 12:00
- **WHEN** elige "Ver turnos afectados"
- **THEN** se pide `GET /api/v1/turnos` con `desde` `2026-12-09T09:00:00-03:00`, `hasta` `2026-12-09T12:00:00-03:00` y `profesionalId` 1

### Requirement: Errores al cargar o guardar
Si una lista no se puede cargar, su pestaña MUST mostrar el mensaje del error y un botón "Reintentar", sin afectar a las demás pestañas.

Un `403` MUST mostrar "No tenés permiso para esta acción.". Un `401` MUST seguir la regla de sesión vencida de `frontend-sesion-personal`.

Mientras se guarda, el botón de guardar MUST quedar deshabilitado para no enviar el pedido dos veces.

#### Scenario: Falla la carga de sillones
- **GIVEN** una sesión de `admin`
- **WHEN** abre la pestaña "Sillones" y `GET /api/v1/sillones` responde `500`
- **THEN** la pestaña muestra un mensaje de error y el botón "Reintentar"
- **AND** la pestaña "Prestaciones" sigue cargando su lista

#### Scenario: Acción rechazada por permisos
- **GIVEN** una sesión de `admin` editando un sillón
- **WHEN** guarda y la API responde `403`
- **THEN** se muestra "No tenés permiso para esta acción."

#### Scenario: Doble envío
- **GIVEN** una sesión de `admin` que guarda un sillón nuevo
- **WHEN** hace clic dos veces seguidas en "Guardar"
- **THEN** se envía un solo `POST /api/v1/sillones`
