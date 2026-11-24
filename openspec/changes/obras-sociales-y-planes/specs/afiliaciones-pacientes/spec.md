# Spec Delta

## Purpose

Registrar con qué obra social o prepaga se atiende cada paciente: su afiliación a un plan con número de afiliado y vigencia, el historial de coberturas, la cobertura vigente en una fecha (o la atención como particular) y la consulta de la cobertura propia por el paciente, cuidando la privacidad de esos datos. En los escenarios se usan solo datos ficticios: "Paciente Uno" (DNI `99000001`, con la cuenta `paciente.uno`) y "Paciente Dos" (DNI `99000002`, con la cuenta `paciente.dos`); "Obra Social Ejemplo" (`OSE`) con "Plan Base" y "Plan Integral", y "Prepaga Prueba" con "Plan Oro"; los usuarios `admin`, `recepcion` y `ana.ejemplo` (`ODONTOLOGO`). Salvo que el escenario diga otra cosa, hoy es 2026-11-24 en `America/Argentina/Buenos_Aires`.

## ADDED Requirements

### Requirement: Afiliar un paciente a un plan
`ADMIN` y `RECEPCIONISTA` SHALL poder afiliar un paciente a un plan con su número de afiliado, una fecha `vigenteDesde` y, opcionalmente, una fecha `vigenteHasta`. La respuesta `201` MUST devolver la afiliación con el plan, la obra social, el número y la vigencia. Un paciente o un plan inexistentes MUST responder `404` con el código `RECURSO_NO_ENCONTRADO`.

#### Scenario: Afiliación sin fecha de fin
- **GIVEN** "Paciente Uno" sin afiliaciones
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Base" con el número `OSE-000001` desde 2026-01-01
- **THEN** la respuesta es `201` con "Plan Base", "Obra Social Ejemplo", el número `OSE-000001`, `vigenteDesde` 2026-01-01 y sin `vigenteHasta`

#### Scenario: Afiliación con fecha de fin
- **WHEN** `admin` afilia a "Paciente Dos" a "Plan Oro" con el número `PP-77` desde 2026-01-01 hasta 2026-12-31
- **THEN** la respuesta es `201` con `vigenteHasta` 2026-12-31

#### Scenario: Paciente inexistente
- **WHEN** `recepcion` afilia al paciente con id `999999` a "Plan Base" con el número `OSE-000009` desde 2026-01-01
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

#### Scenario: Plan inexistente
- **WHEN** `recepcion` afilia a "Paciente Uno" al plan con id `999999` con el número `OSE-000001` desde 2026-01-01
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`

### Requirement: Número de afiliado válido
El número de afiliado MUST ser obligatorio y tener de 1 a 30 caracteres entre letras, números, `-`, `/` y `.`, después de quitar los espacios de los extremos, que no se guardan. Un número inválido MUST responder `422` con el código `DATOS_INVALIDOS` y el campo `numeroAfiliado`, sin repetir el valor recibido.

#### Scenario: Espacios en los extremos
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Base" con el número "  OSE-000001  " desde 2026-01-01
- **THEN** la respuesta es `201` con el número `OSE-000001`

#### Scenario: Número con caracteres no permitidos
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Base" con el número `OSE 0001#` desde 2026-01-01
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `numeroAfiliado`
- **AND** el cuerpo de la respuesta no contiene `OSE 0001#`

#### Scenario: Número vacío
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Base" con el número en blanco desde 2026-01-01
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `numeroAfiliado`

#### Scenario: Número de 31 caracteres
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Base" con un número de 31 caracteres válidos desde 2026-01-01
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `numeroAfiliado`

### Requirement: Vigencia válida
La vigencia de una afiliación MUST tener `vigenteDesde`, y `vigenteHasta` MUST ser nula o igual o posterior a `vigenteDesde`. Ambas fechas son días completos e inclusivos. Una vigencia con `vigenteHasta` anterior a `vigenteDesde` MUST responder `422` con el código `VIGENCIA_INVALIDA`, y la falta de `vigenteDesde` `422` con `DATOS_INVALIDOS`.

#### Scenario: Fecha de fin anterior a la de inicio
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Base" con el número `OSE-000001` desde 2026-06-01 hasta 2026-05-31
- **THEN** la respuesta es `422` con el código `VIGENCIA_INVALIDA`

#### Scenario: Vigencia de un solo día
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Base" con el número `OSE-000001` desde 2026-06-01 hasta 2026-06-01
- **THEN** la respuesta es `201`

#### Scenario: Sin fecha de inicio
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Base" con el número `OSE-000001` sin `vigenteDesde`
- **THEN** la respuesta es `422` con el código `DATOS_INVALIDOS` y el campo `vigenteDesde`

### Requirement: Una sola afiliación vigente por fecha
Un paciente MUST tener como máximo una afiliación vigente en cada fecha: dos vigencias del mismo paciente MUST NOT superponerse, ni siquiera en un día. El sistema MUST rechazar con `409` y el código `VIGENCIA_SUPERPUESTA`, sin guardar nada, una afiliación que se superpone con otra del mismo paciente, aunque los pedidos lleguen a la vez. Pacientes distintos no se afectan entre sí.

#### Scenario: Superposición con una afiliación abierta
- **GIVEN** "Paciente Uno" tiene "Plan Base" desde 2026-01-01 sin fecha de fin
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Oro" con el número `PP-77` desde 2026-11-01
- **THEN** la respuesta es `409` con el código `VIGENCIA_SUPERPUESTA`
- **AND** "Paciente Uno" sigue con una sola afiliación

#### Scenario: Superposición de un día en el borde
- **GIVEN** "Paciente Uno" tiene "Plan Base" desde 2026-01-01 hasta 2026-06-30
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Oro" con el número `PP-77` desde 2026-06-30
- **THEN** la respuesta es `409` con el código `VIGENCIA_SUPERPUESTA`

#### Scenario: Vigencias contiguas
- **GIVEN** "Paciente Uno" tiene "Plan Base" desde 2026-01-01 hasta 2026-06-30
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Oro" con el número `PP-77` desde 2026-07-01
- **THEN** la respuesta es `201`

#### Scenario: Otro paciente en las mismas fechas
- **GIVEN** "Paciente Uno" tiene "Plan Base" desde 2026-01-01 sin fecha de fin
- **WHEN** `recepcion` afilia a "Paciente Dos" a "Plan Base" con el número `OSE-000002` desde 2026-01-01
- **THEN** la respuesta es `201`

#### Scenario: Dos pedidos superpuestos a la vez
- **GIVEN** "Paciente Uno" sin afiliaciones
- **WHEN** llegan a la vez dos afiliaciones de "Paciente Uno" desde 2026-01-01, una a "Plan Base" y otra a "Plan Oro"
- **THEN** una responde `201` y la otra `409` con el código `VIGENCIA_SUPERPUESTA`
- **AND** "Paciente Uno" tiene una sola afiliación

#### Scenario: La base rechaza una superposición escrita directo
- **GIVEN** "Paciente Uno" tiene "Plan Base" desde 2026-01-01 sin fecha de fin
- **WHEN** se inserta directamente en la base otra afiliación de "Paciente Uno" desde 2026-03-01
- **THEN** la base rechaza la inserción por la restricción de vigencias superpuestas

### Requirement: Número de afiliado único por obra social
El número de afiliado MUST ser único dentro de la misma obra social entre pacientes distintos, sin distinguir mayúsculas, aunque estén en planes distintos de esa obra social o en vigencias distintas. Un número repetido MUST responder `409` con el código `AFILIADO_DUPLICADO`, sin revelar a qué paciente pertenece. El mismo paciente MAY conservar su número al cambiar de plan dentro de la misma obra social.

#### Scenario: Número repetido en otro plan de la misma obra social
- **GIVEN** "Paciente Uno" tiene "Plan Base" con el número `OSE-000001`
- **WHEN** `recepcion` afilia a "Paciente Dos" a "Plan Integral" con el número `ose-000001` desde 2026-01-01
- **THEN** la respuesta es `409` con el código `AFILIADO_DUPLICADO`
- **AND** el cuerpo de la respuesta no menciona a "Paciente Uno"

#### Scenario: Mismo número en otra obra social
- **GIVEN** "Paciente Uno" tiene "Plan Base" con el número `OSE-000001`
- **WHEN** `recepcion` afilia a "Paciente Dos" a "Plan Oro" con el número `OSE-000001` desde 2026-01-01
- **THEN** la respuesta es `201`

#### Scenario: El mismo paciente conserva su número al cambiar de plan
- **GIVEN** "Paciente Uno" tuvo "Plan Base" con el número `OSE-000001` desde 2026-01-01 hasta 2026-06-30
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Integral" con el número `OSE-000001` desde 2026-07-01
- **THEN** la respuesta es `201`

### Requirement: No se afilia a un plan o una obra social inactivos
El sistema MUST rechazar una afiliación nueva a un plan inactivo con `409` y el código `PLAN_INACTIVO`, y a un plan activo de una obra social inactiva con `409` y el código `OBRA_SOCIAL_INACTIVA`. Las afiliaciones existentes MUST NOT cambiar cuando después se desactiva su plan o su obra social.

#### Scenario: Plan inactivo
- **GIVEN** "Plan Integral" está inactivo
- **WHEN** `recepcion` afilia a "Paciente Uno" a "Plan Integral" con el número `OSE-000001` desde 2026-01-01
- **THEN** la respuesta es `409` con el código `PLAN_INACTIVO`

#### Scenario: Obra social inactiva
- **GIVEN** "Prepaga Prueba" está inactiva y "Plan Oro" activo
- **WHEN** `recepcion` afilia a "Paciente Dos" a "Plan Oro" con el número `PP-77` desde 2026-01-01
- **THEN** la respuesta es `409` con el código `OBRA_SOCIAL_INACTIVA`

#### Scenario: Desactivar el plan no cambia la afiliación existente
- **GIVEN** "Paciente Uno" tiene "Plan Base" vigente desde 2026-01-01
- **WHEN** `admin` desactiva "Plan Base" y `recepcion` consulta la cobertura de "Paciente Uno"
- **THEN** la cobertura sigue siendo "Plan Base" con el número `OSE-000001`

### Requirement: Cerrar una afiliación y cambiar de cobertura
`ADMIN` y `RECEPCIONISTA` SHALL poder cerrar una afiliación abierta fijando su `vigenteHasta`. Para cambiar de plan o de obra social se cierra la actual y se crea otra desde el día siguiente; la anterior queda en el historial. Cerrar con una fecha anterior a `vigenteDesde` MUST responder `422` con `VIGENCIA_INVALIDA`, y cerrar una ya cerrada `409` con `AFILIACION_CERRADA`.

#### Scenario: Cambio de obra social a prepaga
- **GIVEN** "Paciente Uno" tiene "Plan Base" desde 2026-01-01 sin fecha de fin
- **WHEN** `recepcion` cierra esa afiliación al 2026-11-30 y afilia a "Paciente Uno" a "Plan Oro" con el número `PP-77` desde 2026-12-01
- **THEN** las dos respuestas son exitosas
- **AND** el historial de "Paciente Uno" tiene "Plan Oro" desde 2026-12-01 y "Plan Base" desde 2026-01-01 hasta 2026-11-30

#### Scenario: Cierre anterior al inicio
- **GIVEN** "Paciente Uno" tiene "Plan Base" desde 2026-01-01 sin fecha de fin
- **WHEN** `recepcion` cierra esa afiliación al 2025-12-31
- **THEN** la respuesta es `422` con el código `VIGENCIA_INVALIDA`

#### Scenario: Cerrar una afiliación ya cerrada
- **GIVEN** "Paciente Uno" tiene "Plan Base" desde 2026-01-01 hasta 2026-06-30
- **WHEN** `recepcion` cierra esa afiliación al 2026-07-31
- **THEN** la respuesta es `409` con el código `AFILIACION_CERRADA`

#### Scenario: Afiliación de otro paciente
- **GIVEN** la afiliación de "Paciente Uno" a "Plan Base"
- **WHEN** `recepcion` la cierra usando la ruta de "Paciente Dos"
- **THEN** la respuesta es `404` con el código `RECURSO_NO_ENCONTRADO`
- **AND** la afiliación de "Paciente Uno" no cambia

### Requirement: Corregir una afiliación
`ADMIN` y `RECEPCIONISTA` SHALL poder corregir el plan, el número de afiliado y `vigenteDesde` de una afiliación, con las mismas reglas que al crearla: número válido y único por obra social, vigencia válida y sin superposición con las otras afiliaciones del paciente, y plan y obra social activos cuando cambia el plan. Las afiliaciones MUST NOT borrarse.

#### Scenario: Corregir el número de afiliado
- **GIVEN** "Paciente Uno" tiene "Plan Base" con el número `OSE-000010`
- **WHEN** `recepcion` corrige el número a `OSE-000001`
- **THEN** la respuesta es `200` con el número `OSE-000001`

#### Scenario: Corrección que superpone vigencias
- **GIVEN** "Paciente Uno" tiene "Plan Base" desde 2026-01-01 hasta 2026-06-30 y "Plan Oro" desde 2026-07-01
- **WHEN** `recepcion` corrige `vigenteDesde` de "Plan Oro" a 2026-06-15
- **THEN** la respuesta es `409` con el código `VIGENCIA_SUPERPUESTA`

#### Scenario: No hay borrado de afiliaciones
- **WHEN** `admin` pide `DELETE` sobre la afiliación de "Paciente Uno"
- **THEN** la respuesta no es exitosa y la afiliación sigue existiendo

### Requirement: Historial de afiliaciones del paciente
El personal SHALL poder listar todas las afiliaciones de un paciente, de la más reciente a la más antigua por `vigenteDesde`, cada una con su plan, su obra social, su número, su vigencia y si está vigente hoy. Un paciente sin afiliaciones MUST devolver una lista vacía y uno inexistente `404` con `RECURSO_NO_ENCONTRADO`.

#### Scenario: Historial con dos afiliaciones
- **GIVEN** "Paciente Uno" tuvo "Plan Base" desde 2026-01-01 hasta 2026-06-30 y tiene "Plan Oro" desde 2026-07-01
- **WHEN** `ana.ejemplo` lista las afiliaciones de "Paciente Uno"
- **THEN** la respuesta es `200` con "Plan Oro" vigente y luego "Plan Base" no vigente

#### Scenario: Paciente sin afiliaciones
- **WHEN** `recepcion` lista las afiliaciones de "Paciente Dos", que no tiene ninguna
- **THEN** la respuesta es `200` con una lista vacía

### Requirement: Cobertura vigente o particular
El personal SHALL poder consultar la cobertura de un paciente en una fecha, por defecto hoy en `America/Argentina/Buenos_Aires`. Si una afiliación está vigente en esa fecha, la respuesta MUST traer su obra social, plan, número y vigencia; si no, MUST indicar que el paciente se atiende como particular, sin error.

#### Scenario: Paciente con afiliación vigente hoy
- **GIVEN** "Paciente Uno" tiene "Plan Base" con el número `OSE-000001` desde 2026-01-01 sin fecha de fin
- **WHEN** `recepcion` consulta la cobertura de "Paciente Uno"
- **THEN** la respuesta es `200` con "Obra Social Ejemplo", "Plan Base", el número `OSE-000001` y no particular

#### Scenario: Paciente sin cobertura
- **WHEN** `recepcion` consulta la cobertura de "Paciente Dos", que no tiene afiliaciones
- **THEN** la respuesta es `200` e indica que se atiende como particular

#### Scenario: Afiliación vencida
- **GIVEN** "Paciente Dos" tuvo "Plan Oro" desde 2026-01-01 hasta 2026-10-31
- **WHEN** `recepcion` consulta la cobertura de "Paciente Dos"
- **THEN** la respuesta es `200` e indica que se atiende como particular

#### Scenario: Cobertura en una fecha pasada
- **GIVEN** "Paciente Dos" tuvo "Plan Oro" desde 2026-01-01 hasta 2026-10-31
- **WHEN** `recepcion` consulta la cobertura de "Paciente Dos" en la fecha 2026-10-31
- **THEN** la respuesta es `200` con "Plan Oro"

#### Scenario: Hoy se calcula en la zona de Buenos Aires
- **GIVEN** "Paciente Dos" tiene "Plan Oro" desde 2026-11-25 y el reloj marca 2026-11-25T02:00Z (23:00 del 24 en Buenos Aires)
- **WHEN** `recepcion` consulta la cobertura de "Paciente Dos" sin fecha
- **THEN** la respuesta indica que se atiende como particular

### Requirement: El paciente consulta su propia cobertura
Un usuario `PACIENTE` SHALL poder consultar en "Mi cuenta" su cobertura vigente hoy (obra social, plan, número y vigencia) o que se atiende como particular. El paciente MUST identificarse solo por la sesión, sin ids en el pedido, y MUST NOT poder ver la cobertura de otro paciente ni modificar la suya.

#### Scenario: Paciente con cobertura
- **GIVEN** "Paciente Uno" tiene "Plan Base" vigente con el número `OSE-000001`
- **WHEN** `paciente.uno` consulta su cobertura
- **THEN** la respuesta es `200` con "Obra Social Ejemplo", "Plan Base", `OSE-000001` y la vigencia

#### Scenario: Paciente particular
- **WHEN** `paciente.dos`, sin afiliaciones, consulta su cobertura
- **THEN** la respuesta es `200` e indica que se atiende como particular

#### Scenario: El paciente no ve la cobertura de otro
- **GIVEN** "Paciente Uno" tiene "Plan Base" vigente con el número `OSE-000001`
- **WHEN** `paciente.dos` consulta su cobertura agregando el id de "Paciente Uno" al pedido
- **THEN** la respuesta indica que se atiende como particular y no contiene `OSE-000001`

#### Scenario: El paciente no usa las rutas del personal
- **WHEN** `paciente.uno` lista las afiliaciones de "Paciente Uno" por la ruta del personal
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`

### Requirement: Permisos sobre las afiliaciones
`ADMIN` y `RECEPCIONISTA` SHALL crear, corregir y cerrar afiliaciones; `ODONTOLOGO` SHALL solo consultarlas. El sistema MUST responder `403` con el código `ACCESO_DENEGADO`, sin cambiar nada, a una escritura de `ODONTOLOGO` y a cualquier ruta de afiliaciones del personal pedida por un `PACIENTE`.

#### Scenario: El odontólogo no afilia
- **WHEN** `ana.ejemplo` afilia a "Paciente Uno" a "Plan Base" con el número `OSE-000001` desde 2026-01-01
- **THEN** la respuesta es `403` con el código `ACCESO_DENEGADO`
- **AND** "Paciente Uno" no tiene afiliaciones

#### Scenario: El odontólogo consulta la cobertura
- **GIVEN** "Paciente Uno" tiene "Plan Base" vigente
- **WHEN** `ana.ejemplo` consulta la cobertura de "Paciente Uno"
- **THEN** la respuesta es `200` con "Plan Base"

### Requirement: Privacidad de la cobertura
La obra social, el plan y el número de afiliado MUST NOT aparecer en logs, recordatorios, mensajes de WhatsApp ni emails (RN-NO-01), y las respuestas de error MUST NOT repetir el número de afiliado recibido ni datos de otro paciente. Dar un turno MUST NOT exigir cobertura (RN-OS-04).

#### Scenario: El recordatorio no lleva la cobertura
- **GIVEN** "Paciente Uno" tiene "Plan Base" vigente con el número `OSE-000001` y un turno mañana
- **WHEN** se genera el recordatorio del turno
- **THEN** el mensaje no contiene "Obra Social Ejemplo", "Plan Base" ni `OSE-000001`

#### Scenario: Turno de un paciente particular
- **GIVEN** "Paciente Dos" no tiene afiliaciones
- **WHEN** `recepcion` le da un turno válido
- **THEN** el turno se crea igual que antes de C-18

#### Scenario: El error de duplicado no repite el número
- **GIVEN** "Paciente Uno" tiene "Plan Base" con el número `OSE-000001`
- **WHEN** `recepcion` afilia a "Paciente Dos" a "Plan Base" con el número `OSE-000001` desde 2026-01-01
- **THEN** la respuesta es `409` con el código `AFILIADO_DUPLICADO` y su cuerpo no contiene `OSE-000001`
