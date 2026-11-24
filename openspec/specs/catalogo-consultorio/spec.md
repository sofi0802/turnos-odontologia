# catalogo-consultorio Specification

## Purpose

Permitir que el administrador mantenga los recursos con los que trabaja la agenda: profesionales, sillones y prestaciones, las prestaciones que hace cada profesional y su sillón habitual. En los escenarios se usan solo datos ficticios: Dra. Ana Ejemplo (MP-0001), Dr. Bruno Prueba (MP-0002), Sillón 1 y Sillón 2, y las prestaciones "Consulta o control" (30 min) y "Tratamiento de conducto" (60 min).

## Requirements

### Requirement: Alta de profesionales con matrícula única
El sistema SHALL dar de alta un profesional con nombre, apellido y matrícula, en estado activo. La matrícula MUST ser única entre todos los profesionales, activos o no. Una matrícula repetida se rechaza con `409` y el código `MATRICULA_DUPLICADA`.

#### Scenario: Alta de un profesional
- **GIVEN** que no hay ningún profesional con la matrícula MP-0001
- **WHEN** el administrador da de alta a Ana Ejemplo con la matrícula MP-0001
- **THEN** el profesional queda creado y activo
- **AND** la respuesta incluye su id, nombre, apellido, matrícula y estado

#### Scenario: Matrícula duplicada
- **GIVEN** la Dra. Ana Ejemplo con la matrícula MP-0001
- **WHEN** el administrador da de alta a otro profesional con la matrícula MP-0001
- **THEN** el alta se rechaza con `409` y el código `MATRICULA_DUPLICADA`

### Requirement: Modificación de profesionales
El sistema SHALL permitir modificar el nombre, el apellido y la matrícula de un profesional existente, con la misma regla de matrícula única.

#### Scenario: Cambio de matrícula a una que ya existe
- **GIVEN** la Dra. Ana Ejemplo con MP-0001 y el Dr. Bruno Prueba con MP-0002
- **WHEN** el administrador cambia la matrícula del Dr. Bruno Prueba a MP-0001
- **THEN** el cambio se rechaza con `409` y el código `MATRICULA_DUPLICADA`

### Requirement: Sillones con nombre único
El sistema SHALL dar de alta y modificar sillones identificados por un nombre. El nombre MUST ser único entre todos los sillones. Un nombre repetido se rechaza con `409` y el código `NOMBRE_DUPLICADO`.

#### Scenario: Nombre de sillón duplicado
- **GIVEN** un sillón llamado "Sillón 1"
- **WHEN** el administrador da de alta otro sillón llamado "Sillón 1"
- **THEN** el alta se rechaza con `409` y el código `NOMBRE_DUPLICADO`

### Requirement: Prestaciones con nombre único y duración válida
El sistema SHALL dar de alta y modificar prestaciones con nombre y duración en minutos. El nombre MUST ser único, con `409` y `NOMBRE_DUPLICADO` si se repite. La duración MUST ser mayor que 0 y múltiplo de 5; si no, se rechaza con `422` y el código `DURACION_INVALIDA`.

#### Scenario: Prestación con duración válida
- **WHEN** el administrador da de alta "Limpieza (profilaxis)" con 45 minutos
- **THEN** la prestación queda creada y activa, con 45 minutos

#### Scenario: Duración que no es múltiplo de 5
- **WHEN** el administrador da de alta una prestación de 32 minutos
- **THEN** el alta se rechaza con `422` y el código `DURACION_INVALIDA`

#### Scenario: Duración cero
- **WHEN** el administrador da de alta una prestación de 0 minutos
- **THEN** el alta se rechaza con `422` y el código `DURACION_INVALIDA`

### Requirement: Cambiar la duración no modifica los turnos creados
El sistema MUST conservar el inicio y el fin de los turnos ya creados cuando cambia la duración de su prestación. La nueva duración solo se aplica a los turnos que se creen después.

#### Scenario: Turno existente después de cambiar la duración
- **GIVEN** un turno de "Consulta o control" de 10:00 a 10:30
- **WHEN** el administrador cambia la duración de "Consulta o control" a 40 minutos
- **THEN** el turno existente sigue terminando a las 10:30

### Requirement: Desactivar en lugar de borrar
El sistema SHALL desactivar profesionales, sillones y prestaciones en lugar de borrarlos, para conservar el historial de turnos. Un recurso desactivado MUST seguir apareciendo en las consultas, marcado como inactivo, y SHALL poder reactivarse.

#### Scenario: Desactivar un profesional
- **GIVEN** la Dra. Ana Ejemplo activa
- **WHEN** el administrador la desactiva
- **THEN** la Dra. Ana Ejemplo figura como inactiva al consultarla
- **AND** sus datos siguen disponibles

#### Scenario: Reactivar un sillón
- **GIVEN** el "Sillón 2" desactivado
- **WHEN** el administrador lo reactiva
- **THEN** el "Sillón 2" figura como activo

### Requirement: Recurso inexistente
El sistema MUST responder `404` con el código `RECURSO_NO_ENCONTRADO` cuando se consulta, modifica o configura un profesional, sillón o prestación que no existe.

#### Scenario: Profesional inexistente
- **WHEN** el administrador consulta un profesional con un id que no existe
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Datos de entrada obligatorios
El sistema MUST rechazar con `422` y el código `DATOS_INVALIDOS` un alta o modificación con un campo obligatorio vacío o más largo de lo permitido, e informar qué campo falló.

#### Scenario: Profesional sin apellido
- **WHEN** el administrador da de alta un profesional sin apellido
- **THEN** el alta se rechaza con `422` y el código `DATOS_INVALIDOS`
- **AND** la respuesta indica que el campo con error es el apellido

### Requirement: Especialidades de cada profesional
El sistema SHALL permitir definir el conjunto completo de prestaciones que hace un profesional, reemplazando el anterior. Solo se aceptan prestaciones activas; una prestación inactiva se rechaza con `422` y el código `PRESTACION_INACTIVA`, sin cambiar el conjunto anterior.

#### Scenario: Definir las especialidades
- **GIVEN** al Dr. Bruno Prueba sin especialidades
- **WHEN** el administrador le asigna "Consulta o control" y "Tratamiento de conducto"
- **THEN** el Dr. Bruno Prueba queda habilitado para esas dos prestaciones y para ninguna otra

#### Scenario: Especialidad con una prestación inactiva
- **GIVEN** la prestación "Extracción simple" desactivada
- **WHEN** el administrador asigna "Extracción simple" a la Dra. Ana Ejemplo
- **THEN** el cambio se rechaza con `422` y el código `PRESTACION_INACTIVA`
- **AND** las especialidades de la Dra. Ana Ejemplo no cambian

### Requirement: Consulta de habilitación por prestación
El sistema SHALL informar si un profesional está habilitado para una prestación, según sus especialidades, para que la agenda y la reserva online apliquen RN-AG-15.

#### Scenario: Profesional no habilitado
- **GIVEN** que la Dra. Ana Ejemplo hace "Consulta o control" pero no "Tratamiento de conducto"
- **WHEN** se consulta si la Dra. Ana Ejemplo está habilitada para "Tratamiento de conducto"
- **THEN** la respuesta es que no está habilitada

#### Scenario: Profesional habilitado
- **GIVEN** que el Dr. Bruno Prueba hace "Tratamiento de conducto"
- **WHEN** se consulta si el Dr. Bruno Prueba está habilitado para "Tratamiento de conducto"
- **THEN** la respuesta es que está habilitado

### Requirement: Sillón habitual del profesional
El sistema SHALL permitir asignar a un profesional un sillón habitual activo, o quitárselo. Un sillón inactivo se rechaza con `422` y el código `SILLON_INACTIVO`.

Un sillón MUST ser el habitual de un solo profesional (RN-RO-09), activo o inactivo. Si el sillón ya es habitual de otro profesional, el cambio se rechaza con `409` y el código `SILLON_HABITUAL_OCUPADO`, y ninguno de los dos profesionales cambia. Volver a asignar a un profesional el sillón que ya tiene MUST aceptarse.

#### Scenario: Asignar el sillón habitual
- **WHEN** el administrador asigna el "Sillón 1" como sillón habitual de la Dra. Ana Ejemplo
- **THEN** la Dra. Ana Ejemplo figura con el "Sillón 1" como sillón habitual

#### Scenario: Sillón habitual inactivo
- **GIVEN** el "Sillón 2" desactivado
- **WHEN** el administrador asigna el "Sillón 2" como sillón habitual del Dr. Bruno Prueba
- **THEN** el cambio se rechaza con `422` y el código `SILLON_INACTIVO`

#### Scenario: Quitar el sillón habitual
- **GIVEN** la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
- **WHEN** el administrador le quita el sillón habitual
- **THEN** la Dra. Ana Ejemplo figura sin sillón habitual

#### Scenario: Sillón habitual de otro profesional
- **GIVEN** la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
- **WHEN** el administrador asigna el "Sillón 1" como sillón habitual del Dr. Bruno Prueba
- **THEN** el cambio se rechaza con `409` y el código `SILLON_HABITUAL_OCUPADO`
- **AND** la Dra. Ana Ejemplo sigue con el "Sillón 1" y el Dr. Bruno Prueba conserva el sillón que tenía

#### Scenario: Sillón habitual de un profesional inactivo
- **GIVEN** un profesional inactivo con el "Sillón 2" como sillón habitual
- **WHEN** el administrador asigna el "Sillón 2" como sillón habitual de la Dra. Ana Ejemplo
- **THEN** el cambio se rechaza con `409` y el código `SILLON_HABITUAL_OCUPADO`

#### Scenario: Volver a asignar el mismo sillón
- **GIVEN** la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
- **WHEN** el administrador le asigna otra vez el "Sillón 1"
- **THEN** el cambio se acepta y la Dra. Ana Ejemplo sigue con el "Sillón 1"

#### Scenario: Liberar un sillón y asignarlo a otro profesional
- **GIVEN** la Dra. Ana Ejemplo con el "Sillón 1" como sillón habitual
- **AND** el administrador le quitó el sillón habitual
- **WHEN** el administrador asigna el "Sillón 1" como sillón habitual del Dr. Bruno Prueba
- **THEN** el Dr. Bruno Prueba figura con el "Sillón 1" como sillón habitual

### Requirement: Solo el administrador modifica el catálogo
El sistema SHALL permitir consultar profesionales, sillones, prestaciones y especialidades a los roles `ADMIN`, `RECEPCIONISTA` y `ODONTOLOGO`, y MUST permitir altas, modificaciones, activaciones, desactivaciones, especialidades y sillón habitual solo al rol `ADMIN`. Otro rol recibe `403` con el código `ACCESO_DENEGADO`, sin cambios.

#### Scenario: Recepción consulta los profesionales
- **GIVEN** la Dra. Ana Ejemplo dada de alta
- **AND** el usuario `recepcion` con sesión iniciada
- **WHEN** consulta los profesionales
- **THEN** la respuesta es `200` e incluye a la Dra. Ana Ejemplo

#### Scenario: Recepción intenta dar de alta un profesional
- **GIVEN** el usuario `recepcion` con sesión iniciada
- **WHEN** da de alta a Ana Ejemplo con la matrícula MP-0001
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** no se crea ningún profesional

#### Scenario: Odontólogo intenta desactivar un sillón
- **GIVEN** el "Sillón 1" activo
- **AND** el usuario `ana.ejemplo` (`ODONTOLOGO`) con sesión iniciada
- **WHEN** desactiva el "Sillón 1"
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** el "Sillón 1" sigue activo
