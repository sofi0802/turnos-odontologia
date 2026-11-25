# frontend-coberturas Specification

## Purpose

Definir las pantallas de coberturas del frontend: el catálogo de obras sociales, planes y nomenclador que mantiene el administrador en Configuración, la cobertura y el historial de afiliaciones de cada paciente para el personal, y "Mi cobertura" del paciente, sobre la API de C-18 y sin datos de cobertura fuera de la pantalla.

Datos ficticios de los escenarios (salvo que el escenario diga otra cosa):

- **Usuarios:** `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`), `ana.ejemplo` (`ODONTOLOGO`), `paciente.uno` y `paciente.dos` (`PACIENTE`).
- **Catálogo:** "Obra Social Ejemplo" (id 1, sigla `OSE`, tipo `OBRA_SOCIAL`) con "Plan Base" (id 11) y "Plan Integral" (id 12, inactivo); "Prepaga Prueba" (id 2, tipo `PREPAGA`, sin sigla) con "Plan Oro" (id 21).
- **Nomenclador:** `N-0101` "Consulta" (vinculado a "Consulta o control"), `N-0201` "Limpieza" (vinculado a "Limpieza (profilaxis)") y `N-0301` "Extracción simple" (sin prestación, inactivo).
- **Pacientes:** "Paciente Uno" (id 1, DNI `99000001`) con "Plan Base", número `OSE-000001`, desde 2026-01-01 sin fecha de fin; "Paciente Dos" (id 2, DNI `99000002`), particular, que tuvo "Plan Oro" con el número `PP-77` del 2026-01-01 al 2026-10-31.
- **Hoy:** 2026-11-24 en `America/Argentina/Buenos_Aires`.

## Requirements

### Requirement: Pestaña "Obras sociales" solo para el administrador
Con sesión `ADMIN`, `#/configuracion` SHALL mostrar la pestaña "Obras sociales", que al elegirse pide `GET /api/v1/obras-sociales` y `GET /api/v1/nomenclador`. Con sesión `RECEPCIONISTA` u `ODONTOLOGO` la pestaña MUST NOT mostrarse y la página MUST NOT pedir esas rutas.

#### Scenario: El administrador abre la pestaña
- **GIVEN** una sesión de `admin` en `#/configuracion`
- **WHEN** elige la pestaña "Obras sociales"
- **THEN** se piden `GET /api/v1/obras-sociales` y `GET /api/v1/nomenclador`
- **AND** la pestaña tiene `aria-selected="true"`

#### Scenario: La recepción no ve la pestaña
- **GIVEN** una sesión de `recepcion`
- **WHEN** se abre `#/configuracion`
- **THEN** no hay una pestaña "Obras sociales"
- **AND** no se envía ningún pedido a `/api/v1/obras-sociales` ni a `/api/v1/nomenclador`

### Requirement: Lista de obras sociales y planes
La pestaña SHALL listar las obras sociales en el orden que devuelve la API, cada una con su nombre, su sigla si la tiene, su tipo escrito ("Obra social" o "Prepaga"), su estado y debajo sus planes con su estado. El estado inactivo MUST leerse por el texto "Inactiva" o "Inactivo", no solo por el color.

#### Scenario: Obras sociales con sus planes
- **GIVEN** una sesión de `admin` y el catálogo de los datos ficticios
- **WHEN** se abre la pestaña "Obras sociales"
- **THEN** se ve "Obra Social Ejemplo" con "OSE", "Obra social" y "Activa", y debajo "Plan Base" activo y "Plan Integral" con el texto "Inactivo"
- **AND** después se ve "Prepaga Prueba" con "Prepaga" y su plan "Plan Oro"

#### Scenario: Catálogo vacío
- **GIVEN** que `GET /api/v1/obras-sociales` responde una lista vacía
- **WHEN** se abre la pestaña "Obras sociales"
- **THEN** se ve "Todavía no hay obras sociales ni prepagas." y el botón "Agregar obra social"

### Requirement: Nomenclador en la pestaña
Debajo de las obras sociales, la pestaña SHALL mostrar el nomenclador en el orden de la API, con el código, la descripción, la prestación vinculada o "Sin prestación" y el estado escrito.

#### Scenario: Códigos del nomenclador
- **GIVEN** una sesión de `admin` y el nomenclador de los datos ficticios
- **WHEN** se abre la pestaña "Obras sociales"
- **THEN** se ven `N-0101` "Consulta" con "Consulta o control", `N-0201` "Limpieza" y `N-0301` "Extracción simple" con "Sin prestación" e "Inactivo", en ese orden

#### Scenario: Nomenclador vacío
- **GIVEN** que `GET /api/v1/nomenclador` responde una lista vacía
- **WHEN** se abre la pestaña "Obras sociales"
- **THEN** la sección del nomenclador dice "Todavía no hay códigos en el nomenclador." y ofrece "Agregar código"

### Requirement: Alta y edición de obras sociales
"Agregar obra social" y "Editar" SHALL abrir un diálogo con nombre, sigla opcional y tipo ("Obra social" o "Prepaga"), que envía `POST /api/v1/obras-sociales` o `PUT /api/v1/obras-sociales/{id}` y, al terminar bien, cierra y vuelve a pedir la lista. Un nombre vacío o de más de 120 caracteres, o una sigla de más de 20, MUST marcarse en su campo sin enviar nada.

#### Scenario: Alta de una prepaga sin sigla
- **GIVEN** una sesión de `admin` en la pestaña "Obras sociales"
- **WHEN** elige "Agregar obra social", escribe "Prepaga Prueba", elige "Prepaga" y confirma
- **THEN** se envía `POST /api/v1/obras-sociales` con `{"nombre":"Prepaga Prueba","sigla":null,"tipo":"PREPAGA"}`
- **AND** el diálogo se cierra y se vuelve a pedir `GET /api/v1/obras-sociales`

#### Scenario: Editar la sigla
- **GIVEN** una sesión de `admin` en la pestaña "Obras sociales"
- **WHEN** elige "Editar" en "Prepaga Prueba", escribe la sigla `PP` y confirma
- **THEN** se envía `PUT /api/v1/obras-sociales/2` con la sigla `PP` y el tipo `PREPAGA`

#### Scenario: Nombre vacío
- **GIVEN** el diálogo "Agregar obra social" abierto
- **WHEN** confirma sin escribir el nombre
- **THEN** el campo "Nombre" muestra "Completá el nombre." con `aria-invalid="true"`
- **AND** no se envía ningún pedido

### Requirement: Alta y edición de planes
Cada obra social SHALL ofrecer "Agregar plan", que abre un diálogo con el nombre del plan y envía `POST /api/v1/obras-sociales/{id}/planes`; cada plan SHALL ofrecer "Editar", que envía `PUT /api/v1/obras-sociales/{id}/planes/{planId}`. Un nombre vacío o de más de 80 caracteres MUST marcarse sin enviar nada.

#### Scenario: Agregar un plan
- **GIVEN** una sesión de `admin` en la pestaña "Obras sociales"
- **WHEN** elige "Agregar plan" en "Prepaga Prueba", escribe "Plan Plata" y confirma
- **THEN** se envía `POST /api/v1/obras-sociales/2/planes` con `{"nombre":"Plan Plata"}`
- **AND** el diálogo dice en su resumen que el plan es de "Prepaga Prueba"

#### Scenario: Renombrar un plan
- **GIVEN** una sesión de `admin` en la pestaña "Obras sociales"
- **WHEN** elige "Editar" en "Plan Base", escribe "Plan Base 2027" y confirma
- **THEN** se envía `PUT /api/v1/obras-sociales/1/planes/11` con `{"nombre":"Plan Base 2027"}`

### Requirement: Alta y edición de códigos del nomenclador
"Agregar código" y "Editar" SHALL abrir un diálogo con el código, la descripción y la prestación (las de `GET /api/v1/prestaciones` más "Sin prestación"), y enviar `POST /api/v1/nomenclador` o `PUT /api/v1/nomenclador/{id}` con `prestacionId` o `null`. Un código vacío o de más de 20 caracteres, o una descripción vacía o de más de 200, MUST marcarse sin enviar nada.

#### Scenario: Agregar un código vinculado
- **GIVEN** una sesión de `admin` en la pestaña "Obras sociales"
- **WHEN** elige "Agregar código", escribe `N-0401` y "Tratamiento de conducto", elige la prestación "Tratamiento de conducto" y confirma
- **THEN** se envía `POST /api/v1/nomenclador` con el código, la descripción y el `prestacionId` de esa prestación

#### Scenario: Quitar la prestación de un código
- **GIVEN** una sesión de `admin` en la pestaña "Obras sociales"
- **WHEN** elige "Editar" en `N-0101`, elige "Sin prestación" y confirma
- **THEN** se envía `PUT /api/v1/nomenclador/{id}` de `N-0101` con `"prestacionId":null`

#### Scenario: Descripción de 201 caracteres
- **GIVEN** el diálogo "Agregar código" abierto con el código `N-0501`
- **WHEN** escribe una descripción de 201 caracteres y confirma
- **THEN** el campo "Descripción" muestra "La descripción puede tener hasta 200 caracteres."
- **AND** no se envía ningún pedido

### Requirement: Activar y desactivar sin borrar
Cada obra social, plan y código SHALL ofrecer "Desactivar" si está activo o "Activar" si no, que envían `POST .../activar` o `POST .../desactivar` y, al terminar bien, vuelven a pedir la lista. La pestaña MUST NOT ofrecer ninguna acción de borrar. Desactivar una obra social MUST NOT cambiar cómo se muestran sus planes.

#### Scenario: Desactivar una obra social
- **GIVEN** una sesión de `admin` en la pestaña "Obras sociales"
- **WHEN** elige "Desactivar" en "Obra Social Ejemplo"
- **THEN** se envía `POST /api/v1/obras-sociales/1/desactivar` y se vuelve a pedir la lista
- **AND** con la respuesta nueva, "Obra Social Ejemplo" dice "Inactiva" y "Plan Base" sigue diciendo "Activo"

#### Scenario: Activar un plan
- **GIVEN** una sesión de `admin` en la pestaña "Obras sociales"
- **WHEN** elige "Activar" en "Plan Integral"
- **THEN** se envía `POST /api/v1/obras-sociales/1/planes/12/activar`

#### Scenario: No hay borrado
- **GIVEN** una sesión de `admin` en la pestaña "Obras sociales"
- **WHEN** se muestra el catálogo
- **THEN** no hay ningún botón "Borrar" ni "Eliminar"

### Requirement: Errores del catálogo
Mientras carga, la pestaña SHALL mostrar "Cargando…"; si falla, el mensaje del error y "Reintentar". Un rechazo de un diálogo MUST mostrarse dentro del diálogo, que queda abierto con los datos: `OBRA_SOCIAL_EXISTENTE`, `PLAN_EXISTENTE`, `CODIGO_NOMENCLADOR_EXISTENTE` y `PRESTACION_INEXISTENTE` con su mensaje, y `DATOS_INVALIDOS` junto al campo que indica la API.

#### Scenario: Falla al cargar el catálogo
- **GIVEN** que `GET /api/v1/obras-sociales` responde `500`
- **WHEN** se abre la pestaña "Obras sociales"
- **THEN** se ve un aviso `role="alert"` y el botón "Reintentar"
- **AND** al elegir "Reintentar" se vuelve a pedir `GET /api/v1/obras-sociales`

#### Scenario: Nombre repetido
- **GIVEN** el diálogo "Agregar obra social" con el nombre "obra social ejemplo"
- **WHEN** confirma y la API responde `409` con el código `OBRA_SOCIAL_EXISTENTE`
- **THEN** el diálogo sigue abierto y muestra "Ya existe una obra social o prepaga con ese nombre."

#### Scenario: Código repetido
- **GIVEN** el diálogo "Agregar código" con el código `n-0101`
- **WHEN** confirma y la API responde `409` con el código `CODIGO_NOMENCLADOR_EXISTENTE`
- **THEN** el diálogo muestra "Ya existe ese código en el nomenclador."

#### Scenario: Error de un campo
- **GIVEN** el diálogo "Agregar plan" de "Obra Social Ejemplo"
- **WHEN** confirma y la API responde `422` con `DATOS_INVALIDOS` y el campo `nombre`
- **THEN** el campo "Nombre" queda con `aria-invalid="true"` y su mensaje

### Requirement: Sección "Cobertura" en la ficha
En la ficha de `#/pacientes`, la aplicación SHALL mostrar la sección "Cobertura" arriba del odontograma, que pide `GET /api/v1/pacientes/{id}/cobertura` y `GET /api/v1/pacientes/{id}/afiliaciones` aparte del resto de la ficha. Si falla, MUST mostrar el error y "Reintentar" solo en la sección; el resto de la ficha se sigue viendo.

#### Scenario: La ficha muestra la cobertura
- **GIVEN** una sesión de `admin` y la ficha de "Paciente Uno" abierta
- **WHEN** la API responde la cobertura y el historial
- **THEN** la sección "Cobertura" se ve arriba del odontograma

#### Scenario: Falla de la cobertura en la ficha
- **GIVEN** una sesión de `ana.ejemplo` y la ficha de "Paciente Uno"
- **WHEN** `GET /api/v1/pacientes/1/cobertura` responde `500`
- **THEN** la sección "Cobertura" muestra un aviso `role="alert"` y "Reintentar"
- **AND** el odontograma y la ficha básica se siguen viendo

### Requirement: Página "Coberturas" sin datos clínicos
`#/coberturas` SHALL mostrar el buscador de pacientes y, al elegir uno, su nombre, su DNI, "Cambiar de paciente" y la sección "Cobertura". La página MUST NOT pedir ni mostrar la ficha, el odontograma, el periodontograma, las imágenes ni el historial clínico (RN-CL-01). El paciente elegido vive solo en memoria.

#### Scenario: La recepción ve la cobertura de un paciente
- **GIVEN** una sesión de `recepcion` en `#/coberturas`
- **WHEN** busca "99000002" y elige a "Paciente Dos"
- **THEN** se ven "Paciente Dos", "DNI 99000002" y la sección "Cobertura"
- **AND** se piden solo `GET /api/v1/pacientes?q=99000002`, `/api/v1/pacientes/2/cobertura` y `/api/v1/pacientes/2/afiliaciones`

#### Scenario: Ningún pedido clínico
- **GIVEN** una sesión de `recepcion` con "Paciente Uno" elegido en `#/coberturas`
- **WHEN** se muestra su cobertura
- **THEN** no se envía ningún pedido a `/ficha`, `/odontograma`, `/periodontograma`, `/imagenes` ni `/historial`

#### Scenario: Cambiar de paciente
- **GIVEN** "Paciente Uno" elegido en `#/coberturas`
- **WHEN** elige "Cambiar de paciente"
- **THEN** se vuelve al buscador sin datos de "Paciente Uno" en la página

### Requirement: Cobertura vigente o particular
La sección SHALL mostrar, si la API indica una afiliación vigente, la obra social (con su sigla), el plan, el número de afiliado y la vigencia ("Desde 01/01/2026, sin fecha de fin" o "Del 01/01/2026 al 31/12/2026"); si indica `particular`, el texto "Se atiende como particular".

#### Scenario: Paciente con cobertura vigente
- **GIVEN** que la cobertura de "Paciente Uno" es "Plan Base" con `OSE-000001` desde 2026-01-01
- **WHEN** se muestra la sección
- **THEN** se ven "Obra Social Ejemplo (OSE)", "Plan Base", "N.º de afiliado OSE-000001" y "Desde 01/01/2026, sin fecha de fin"

#### Scenario: Paciente particular
- **GIVEN** que la cobertura de "Paciente Dos" responde `{"particular":true,"afiliacion":null}`
- **WHEN** se muestra la sección
- **THEN** se ve "Se atiende como particular"

### Requirement: Historial de afiliaciones
La sección SHALL listar las afiliaciones en el orden de la API, cada una con la obra social, el plan, el número, la vigencia y una etiqueta escrita: "Vigente", "Próxima" (empieza después de hoy) o "Finalizada". Sin afiliaciones, MUST decir "Sin afiliaciones registradas.".

#### Scenario: Historial con una afiliación finalizada
- **GIVEN** que el historial de "Paciente Dos" tiene "Plan Oro" del 2026-01-01 al 2026-10-31
- **WHEN** se muestra la sección
- **THEN** el historial muestra "Plan Oro", "Prepaga Prueba", `PP-77`, "Del 01/01/2026 al 31/10/2026" y "Finalizada"

#### Scenario: Afiliación próxima
- **GIVEN** que el historial de "Paciente Uno" tiene "Plan Oro" desde 2026-12-01 y "Plan Base" del 2026-01-01 al 2026-11-30
- **WHEN** se muestra la sección
- **THEN** "Plan Oro" tiene la etiqueta "Próxima" y "Plan Base" la etiqueta "Vigente"

#### Scenario: Sin afiliaciones
- **GIVEN** que el historial de "Paciente Dos" es una lista vacía
- **WHEN** se muestra la sección
- **THEN** se ven "Se atiende como particular" y "Sin afiliaciones registradas."

### Requirement: Afiliar a un paciente
Con sesión `ADMIN` o `RECEPCIONISTA`, "Afiliar" SHALL abrir un diálogo con la obra social y el plan (solo los activos), el número de afiliado, "Vigente desde" (hoy, o el día siguiente al fin de la última afiliación cerrada si es posterior) y "Vigente hasta" opcional, que envía `POST /api/v1/pacientes/{id}/afiliaciones`. Al terminar bien, cierra, recarga la sección y avisa "Afiliación guardada.".

#### Scenario: Afiliar a un paciente particular
- **GIVEN** una sesión de `recepcion` con "Paciente Dos" elegido
- **WHEN** elige "Afiliar", elige "Obra Social Ejemplo" y "Plan Base", escribe `OSE-000002`, deja "Vigente desde" en 24/11/2026 y confirma
- **THEN** se envía `POST /api/v1/pacientes/2/afiliaciones` con `{"planId":11,"numeroAfiliado":"OSE-000002","vigenteDesde":"2026-11-24","vigenteHasta":null}`
- **AND** se vuelven a pedir la cobertura y el historial, y un `role="status"` dice "Afiliación guardada."

#### Scenario: Solo planes activos
- **GIVEN** el diálogo "Afiliar" abierto
- **WHEN** elige "Obra Social Ejemplo"
- **THEN** el selector de plan ofrece "Plan Base" y no "Plan Integral"

#### Scenario: Plan deshabilitado sin obra social
- **GIVEN** el diálogo "Afiliar" recién abierto
- **WHEN** todavía no eligió la obra social
- **THEN** el selector de plan está deshabilitado

### Requirement: Validar la afiliación antes de enviarla
Antes de enviar, el diálogo MUST validar que haya plan, que el número, sin los espacios de los extremos, tenga de 1 a 30 letras, números, `-`, `/` o `.`, que haya "Vigente desde" y que "Vigente hasta", si está, no sea anterior. Si algo falla, MUST NOT enviar nada, marcar cada campo con `aria-invalid` y su mensaje y llevar el foco al primero con error.

#### Scenario: Número con caracteres no permitidos
- **GIVEN** el diálogo "Afiliar" con "Plan Base" elegido
- **WHEN** escribe `OSE 0001#` y confirma
- **THEN** el campo "N.º de afiliado" muestra "Usá de 1 a 30 letras, números, guiones, barras o puntos."
- **AND** no se envía ningún pedido y el foco queda en ese campo

#### Scenario: Espacios en los extremos
- **GIVEN** el diálogo "Afiliar" con "Plan Base" elegido y "Vigente desde" 2026-01-01
- **WHEN** escribe "  OSE-000001  " y confirma
- **THEN** el pedido lleva `"numeroAfiliado":"OSE-000001"`

#### Scenario: Fin anterior al inicio
- **GIVEN** el diálogo "Afiliar" con "Vigente desde" 2026-06-01
- **WHEN** elige "Vigente hasta" 2026-05-31 y confirma
- **THEN** el campo "Vigente hasta" muestra "Tiene que ser igual o posterior a la fecha de inicio."
- **AND** no se envía ningún pedido

### Requirement: Errores de la API al afiliar o corregir
Un rechazo MUST dejar el diálogo abierto con los datos y mostrar su mensaje: `VIGENCIA_SUPERPUESTA`, `PLAN_INACTIVO`, `OBRA_SOCIAL_INACTIVA` y `VIGENCIA_INVALIDA` arriba del diálogo, y `AFILIADO_DUPLICADO` y `DATOS_INVALIDOS` de `numeroAfiliado` junto al número. Ningún mensaje MUST repetir el número de afiliado escrito.

#### Scenario: Vigencia superpuesta
- **GIVEN** el diálogo "Afiliar" de "Paciente Uno" con "Plan Oro", `PP-77` y "Vigente desde" 2026-11-01
- **WHEN** confirma y la API responde `409` con `VIGENCIA_SUPERPUESTA`
- **THEN** el diálogo muestra "El paciente ya tiene una cobertura en esas fechas. Cerrá la vigente o cambiá las fechas."

#### Scenario: Número de afiliado repetido
- **GIVEN** el diálogo "Afiliar" de "Paciente Dos" con "Plan Integral" y `ose-000001`
- **WHEN** confirma y la API responde `409` con `AFILIADO_DUPLICADO`
- **THEN** el campo "N.º de afiliado" muestra "Ese número ya lo tiene otro paciente de la misma obra social."
- **AND** la página no contiene `ose-000001` fuera del campo

#### Scenario: Plan inactivo
- **GIVEN** el diálogo "Afiliar" con "Plan Base" elegido
- **WHEN** confirma y la API responde `409` con `PLAN_INACTIVO`
- **THEN** el diálogo muestra "El plan está inactivo. Elegí otro."

### Requirement: Corregir una afiliación
Con sesión `ADMIN` o `RECEPCIONISTA`, cada afiliación SHALL ofrecer "Corregir", que abre un diálogo con el plan, el número y "Vigente desde" cargados y envía `PUT /api/v1/pacientes/{id}/afiliaciones/{afiliacionId}`. El plan actual MUST poder conservarse aunque esté inactivo; los otros planes inactivos no se ofrecen.

#### Scenario: Corregir el número
- **GIVEN** una sesión de `recepcion` con "Paciente Uno" elegido
- **WHEN** elige "Corregir" en "Plan Base", cambia el número a `OSE-000010` y confirma
- **THEN** se envía `PUT /api/v1/pacientes/1/afiliaciones/{id}` con `planId` 11, `OSE-000010` y `vigenteDesde` 2026-01-01

#### Scenario: Conservar un plan desactivado
- **GIVEN** que "Plan Base" se desactivó después de afiliar a "Paciente Uno"
- **WHEN** abre "Corregir" en esa afiliación
- **THEN** el selector de plan muestra "Plan Base (inactivo)" elegido

### Requirement: Cerrar una afiliación
Cada afiliación sin fecha de fin SHALL ofrecer "Cerrar afiliación", que abre un diálogo con el resumen y "Vigente hasta" (hoy por defecto) y envía `POST .../afiliaciones/{afiliacionId}/cerrar`. Una fecha anterior al inicio MUST marcarse sin enviar nada. Con `409 AFILIACION_CERRADA` MUST avisar y recargar la sección.

#### Scenario: Cerrar al cambiar de cobertura
- **GIVEN** una sesión de `recepcion` con "Paciente Uno" elegido
- **WHEN** elige "Cerrar afiliación" en "Plan Base", elige 30/11/2026 y confirma
- **THEN** se envía `POST /api/v1/pacientes/1/afiliaciones/{id}/cerrar` con `{"vigenteHasta":"2026-11-30"}`
- **AND** se recarga la sección y "Plan Base" muestra "Del 01/01/2026 al 30/11/2026"

#### Scenario: Cierre anterior al inicio
- **GIVEN** el diálogo "Cerrar afiliación" de "Plan Base", que empieza el 2026-01-01
- **WHEN** elige 31/12/2025 y confirma
- **THEN** el campo muestra "Tiene que ser igual o posterior a la fecha de inicio."
- **AND** no se envía ningún pedido

#### Scenario: Otra persona ya la cerró
- **GIVEN** el diálogo "Cerrar afiliación" abierto
- **WHEN** confirma y la API responde `409` con `AFILIACION_CERRADA`
- **THEN** el diálogo se cierra, la sección se recarga y avisa "Esa afiliación ya estaba cerrada."

#### Scenario: Sin cierre para una afiliación cerrada
- **GIVEN** el historial de "Paciente Dos" con "Plan Oro" del 2026-01-01 al 2026-10-31
- **WHEN** se muestra la sección
- **THEN** esa afiliación no tiene "Cerrar afiliación"

### Requirement: Solo lectura para el odontólogo
Con sesión `ODONTOLOGO`, la sección SHALL mostrar la cobertura y el historial con el aviso "Solo lectura: la cobertura la cargan la recepción y la administración." y MUST NOT ofrecer "Afiliar", "Corregir" ni "Cerrar afiliación".

#### Scenario: El odontólogo ve la cobertura
- **GIVEN** una sesión de `ana.ejemplo` y la ficha de "Paciente Uno"
- **WHEN** se muestra la sección "Cobertura"
- **THEN** se ven "Plan Base" y `OSE-000001`
- **AND** no hay botones "Afiliar", "Corregir" ni "Cerrar afiliación"

### Requirement: Mi cobertura del paciente
Con sesión `PACIENTE`, "Mi cuenta" SHALL mostrar "Mi cobertura" con la respuesta de `GET /api/v1/paciente/cobertura`: la obra social, el plan, el número y la vigencia, o "Te atendés como particular.". La sección MUST ser de solo lectura, MUST NOT mandar ids en el pedido y, si falla, MUST mostrar el error y "Reintentar".

#### Scenario: Paciente con cobertura
- **GIVEN** una sesión de `paciente.uno` con "Plan Base" vigente
- **WHEN** se abre `#/mi-cuenta`
- **THEN** se pide `GET /api/v1/paciente/cobertura` sin parámetros
- **AND** "Mi cobertura" muestra "Obra Social Ejemplo (OSE)", "Plan Base", `OSE-000001` y "Desde 01/01/2026, sin fecha de fin", sin botones para cambiarla

#### Scenario: Paciente particular
- **GIVEN** una sesión de `paciente.dos` sin afiliación vigente
- **WHEN** se abre `#/mi-cuenta`
- **THEN** "Mi cobertura" dice "Te atendés como particular."

#### Scenario: Falla de Mi cobertura
- **GIVEN** una sesión de `paciente.uno`
- **WHEN** `GET /api/v1/paciente/cobertura` responde `500`
- **THEN** "Mi cobertura" muestra un aviso `role="alert"` y "Reintentar"

### Requirement: Coberturas accesibles
Las secciones SHALL tener un título `h2` o `h3`, cada control su etiqueta visible, los estados ("Activa", "Vigente", "Finalizada"…) escritos y no solo por color, los avisos en `role="status"` o `role="alert"` y controles de al menos 44 px. Al cerrar un diálogo el foco MUST volver al botón que lo abrió, y con 390 px de ancho la página MUST NOT desplazarse en horizontal.

#### Scenario: Foco al cerrar el diálogo
- **GIVEN** el diálogo "Afiliar" abierto desde su botón
- **WHEN** elige "Cancelar"
- **THEN** el foco vuelve al botón "Afiliar"

#### Scenario: Etiquetas de los campos
- **GIVEN** el diálogo "Afiliar" abierto
- **WHEN** se inspeccionan sus controles
- **THEN** "Obra social", "Plan", "N.º de afiliado", "Vigente desde" y "Vigente hasta (opcional)" tienen cada uno su `label`

### Requirement: Cobertura solo en la pantalla
La obra social, el plan, el número de afiliado y el paciente elegido MUST NOT quedar en la dirección, en `localStorage`, en `sessionStorage` ni en la consola (RN-OS-07, reglas duras 1 y 6). La dirección MUST ser solo `#/coberturas`, `#/pacientes`, `#/configuracion` o `#/mi-cuenta`.

#### Scenario: Nada fuera de la pantalla al afiliar
- **GIVEN** una sesión de `recepcion` en `#/coberturas`
- **WHEN** afilia a "Paciente Dos" a "Plan Base" con `OSE-000002`
- **THEN** la dirección sigue siendo `#/coberturas`
- **AND** `localStorage` y `sessionStorage` no tienen datos nuevos y la consola no recibió mensajes con `OSE-000002`, "Plan Base" ni "Paciente Dos"
