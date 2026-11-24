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
Con sesión `ADMIN`, la ficha SHALL mostrar la ficha básica, el odontograma, el historial y las ausencias, y MUST NOT ofrecer "Guardar ficha", registrar cambios del odontograma ni "Marcar ausente".

#### Scenario: El administrador no ve controles de edición
- **GIVEN** la sesión de `admin`
- **WHEN** abre la ficha de "Paciente Uno"
- **THEN** los datos de la ficha se ven como texto, sin campos editables
- **AND** no hay botones "Guardar ficha", "Guardar cambios" ni "Marcar ausente"

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
La aplicación MUST NOT guardar el contenido de la ficha, del odontograma ni de las ausencias en la dirección, en `localStorage`, en `sessionStorage` ni en la consola del navegador. Al cerrar la sesión o salir de "Pacientes", la ficha MUST dejar de estar en la página.

#### Scenario: Nada queda guardado en el navegador
- **GIVEN** la sesión de `ana.ejemplo` con la ficha de "Paciente Uno" abierta, con alergias "Penicilina"
- **WHEN** se revisan la dirección, `localStorage`, `sessionStorage` y la consola
- **THEN** ninguno contiene "Penicilina" ni el estado de ninguna pieza

#### Scenario: Salir borra la ficha de la pantalla
- **GIVEN** la ficha de "Paciente Uno" abierta
- **WHEN** se elige "Salir"
- **THEN** la página ya no contiene la ficha
