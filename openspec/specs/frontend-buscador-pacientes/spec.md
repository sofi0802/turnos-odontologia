# frontend-buscador-pacientes Specification

## Purpose

Definir el buscador con el que el personal elige un paciente en los formularios de la agenda: cuándo busca, qué muestra en cada estado, cómo se maneja con el teclado y con un lector de pantalla, y qué pasa si la búsqueda falla. En los escenarios, con datos ficticios, hay una sesión de `recepcion`, el formulario "Nuevo turno" abierto, y los pacientes "Paciente Uno" (id 1, DNI `99000001`), "Paciente Dos" (id 2, DNI `99000002`) y "Carla Ficticia" (id 3, DNI `99000003`).

## Requirements

### Requirement: Búsqueda mientras se escribe
El campo "Paciente" SHALL pedir `GET /api/v1/pacientes?q=` con el texto sin los espacios de los extremos, codificado para la URL, 300 ms después de la última tecla. MUST NOT pedir nada mientras el texto tenga menos de 2 caracteres, o solo dígitos y menos de 3. En ese caso SHALL mostrar la ayuda "Escribí al menos 2 letras o 3 números del DNI."

#### Scenario: Busca después de la espera
- **GIVEN** el campo "Paciente" vacío
- **WHEN** se escribe "fict" y pasan 300 ms sin escribir
- **THEN** se pide una sola vez `GET /api/v1/pacientes?q=fict`

#### Scenario: Escritura rápida
- **GIVEN** el campo "Paciente" vacío
- **WHEN** se escriben "f", "fi", "fic" y "fict" con menos de 300 ms entre cada tecla
- **THEN** no se pide nada antes de los 300 ms posteriores a la última tecla
- **AND** después se pide solo `GET /api/v1/pacientes?q=fict`

#### Scenario: Texto demasiado corto
- **GIVEN** el campo "Paciente" vacío
- **WHEN** se escribe "f" o "99" y pasan 300 ms
- **THEN** no se pide nada a `/api/v1/pacientes`
- **AND** se ve la ayuda "Escribí al menos 2 letras o 3 números del DNI."

### Requirement: Estado de búsqueda en curso
Mientras espera la respuesta, el buscador SHALL mostrar "Buscando…" en una región que los lectores de pantalla anuncian (`role="status"`).

#### Scenario: Buscando
- **GIVEN** que se escribió "fict" y la API todavía no respondió
- **WHEN** pasan los 300 ms de espera
- **THEN** se ve "Buscando…" en la región de estado del buscador

### Requirement: Lista de coincidencias
Con resultados, el buscador SHALL mostrar una lista (`role="listbox"`) con una opción por paciente, con el texto "<apellido>, <nombre> · DNI <dni>", en el orden de la API. SHALL anunciar la cantidad, por ejemplo "2 pacientes encontrados." Con 10 resultados, SHALL agregar "Se muestran las primeras 10 coincidencias. Escribí más para acotar."

#### Scenario: Ver las coincidencias
- **GIVEN** que se escribió "paciente"
- **WHEN** la API responde `200` con "Paciente Dos" y "Paciente Uno"
- **THEN** la lista muestra "Dos, Paciente · DNI 99000002" y "Uno, Paciente · DNI 99000001", en ese orden
- **AND** la región de estado dice "2 pacientes encontrados."

#### Scenario: Límite de resultados alcanzado
- **GIVEN** que se escribió "prueba"
- **WHEN** la API responde `200` con 10 pacientes
- **THEN** la lista muestra 10 opciones
- **AND** se ve "Se muestran las primeras 10 coincidencias. Escribí más para acotar."

### Requirement: Búsqueda sin resultados
Si la API responde `200` con una lista vacía, el buscador SHALL mostrar "No encontramos pacientes con ese DNI o nombre." y no mostrar la lista.

#### Scenario: Sin coincidencias
- **GIVEN** que se escribió "zzz"
- **WHEN** la API responde `200` con una lista vacía
- **THEN** se ve "No encontramos pacientes con ese DNI o nombre."
- **AND** no hay opciones para elegir

### Requirement: Elegir un paciente
Al elegir una opción, el buscador SHALL guardar el id del paciente, cerrar la lista y mostrar el paciente elegido como "<apellido>, <nombre> · DNI <dni>" con un botón "Cambiar paciente". Si se cambia el texto o se usa "Cambiar paciente", la elección MUST borrarse.

#### Scenario: Elegir con el mouse
- **GIVEN** la lista con "Ficticia, Carla · DNI 99000003"
- **WHEN** se hace clic en esa opción
- **THEN** el buscador muestra "Ficticia, Carla · DNI 99000003" como paciente elegido y el botón "Cambiar paciente"
- **AND** la lista se cierra
- **AND** el paciente elegido es el id 3

#### Scenario: Cambiar el paciente elegido
- **GIVEN** "Carla Ficticia" elegida en el buscador
- **WHEN** se usa "Cambiar paciente"
- **THEN** el campo "Paciente" queda vacío y con el foco
- **AND** no hay ningún paciente elegido

### Requirement: Buscador manejable con el teclado
El campo SHALL seguir el patrón combobox de ARIA 1.2: `role="combobox"`, `aria-expanded` según la lista esté abierta, `aria-controls` hacia la lista y `aria-activedescendant` hacia la opción activa. Las flechas SHALL mover la opción activa, Enter SHALL elegirla y Escape SHALL cerrar la lista sin cerrar el diálogo.

#### Scenario: Elegir con el teclado
- **GIVEN** la lista con "Dos, Paciente · DNI 99000002" y "Uno, Paciente · DNI 99000001"
- **WHEN** se presiona flecha abajo dos veces y después Enter
- **THEN** el paciente elegido es "Paciente Uno" (id 1)
- **AND** el formulario no se envía

#### Scenario: Escape cierra la lista y no el diálogo
- **GIVEN** la lista de coincidencias abierta
- **WHEN** se presiona Escape
- **THEN** la lista se cierra y el campo tiene `aria-expanded="false"`
- **AND** el diálogo "Nuevo turno" sigue abierto con lo cargado

### Requirement: Solo cuenta la última búsqueda
El buscador MUST mostrar solo la respuesta de la última búsqueda pedida. Una respuesta de una búsqueda anterior que llega después MUST NOT cambiar la lista.

#### Scenario: Respuestas fuera de orden
- **GIVEN** que se pidió `q=pac` y después `q=paciente dos`
- **WHEN** la respuesta de `q=paciente dos` llega primero con "Paciente Dos" y después llega la de `q=pac` con dos pacientes
- **THEN** la lista muestra solo "Dos, Paciente · DNI 99000002"

### Requirement: Falla de la búsqueda
Si la búsqueda falla por un error del servidor o porque no hay conexión, el buscador SHALL mostrar "No pudimos buscar pacientes. Probá de nuevo." junto al campo, sin cerrar el formulario ni borrar lo cargado. Ante `401` MUST seguir la regla de sesión vencida de la agenda, y ante `403`, mostrar "No tenés permiso para esta acción."

#### Scenario: Error del servidor
- **GIVEN** que se escribió "fict" con el profesional "Ana Ejemplo" ya elegido
- **WHEN** la API responde `500`
- **THEN** se ve "No pudimos buscar pacientes. Probá de nuevo."
- **AND** el formulario sigue abierto y el profesional sigue siendo "Ana Ejemplo"

#### Scenario: Sesión vencida durante la búsqueda
- **GIVEN** que se escribió "fict"
- **WHEN** la API responde `401`
- **THEN** se muestra la página de ingreso con el aviso "Tu sesión terminó. Ingresá de nuevo."

#### Scenario: Búsqueda sin permiso
- **GIVEN** que se escribió "fict"
- **WHEN** la API responde `403` con el código `ACCESO_DENEGADO`
- **THEN** se ve "No tenés permiso para esta acción." junto al campo

### Requirement: El texto buscado no se guarda
El buscador MUST NOT guardar el texto buscado ni los resultados en `localStorage`, `sessionStorage`, cookies ni en la dirección de la página.

#### Scenario: Búsqueda sin rastros en el navegador
- **GIVEN** la agenda abierta en `#/agenda`
- **WHEN** se busca "99000003" y se elige "Carla Ficticia"
- **THEN** `localStorage` y `sessionStorage` no contienen `99000003` ni "Ficticia"
- **AND** la dirección de la página sigue siendo `#/agenda`

### Requirement: Buscador usable en el celular
En pantallas de menos de 768 px, el campo y cada opción SHALL ocupar todo el ancho del formulario y medir al menos 44 px de alto. Desde 320 px de ancho, MUST NOT haber desplazamiento horizontal con la lista abierta.

#### Scenario: Buscador en el celular
- **WHEN** se abre "Nuevo turno" en una pantalla de 390 × 844 px y se ven las coincidencias de "paciente"
- **THEN** cada opción mide al menos 44 px de alto
- **AND** la página no tiene desplazamiento horizontal
