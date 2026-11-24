# Tasks

> Rama `change/C-25-periodontograma`. Cada escenario de `specs/periodontograma/spec.md` tiene su test con el mismo nombre (regla dura 7). Tests primero en cada grupo. Los de integración llevan `@Tag("integration")` y usan PostgreSQL real.

## 1. Preparación

- [x] 1.1 Avisar a Emanuel (dueño de `clinica/`) que C-25 toca su módulo y que va a revisar el PR; verificar que el aviso quede registrado en la descripción del PR
- [x] 1.2 Corregir `knowledge-base/04_modelo_de_datos.md` para que `PeriodontogramaVersion` cuelgue de `paciente_id` y no de `ficha_id` (design D2), y el ERD; verificar con `git diff knowledge-base/` que no cambie nada más

## 2. Dominio y cálculo del estado

- [x] 2.1 Agregar `SITIOS_INCOMPLETOS` y `FURCACION_NO_APLICA` a `CodigoClinica`, y `esPermanente()` y `esMultirradicular()` a `PiezaDental`, con tests unitarios que cubran 11, 14, 15, 24, 36, 48 y 55; verificar con `./gradlew test`
- [x] 2.2 Crear `SitioPeriodontal`, `MedicionSitio` y `PiezaPeriodontal` con las validaciones de RN-CL-05 (piezas permanentes, 6 sitios únicos, movilidad obligatoria, furcación solo en multirradiculares, ausente sin datos, rangos), con tests unitarios de los escenarios de "Solo piezas permanentes", "Seis sitios por pieza presente", "Rangos de los valores", "Furcación solo en piezas multirradiculares" y "Pieza ausente"; verificar con `./gradlew test`
- [x] 2.3 Crear `EstadoPeriodontal` y el cálculo por el peor sitio (RN-CL-06), con un test unitario por cada escenario de "Estado periodontal de cada pieza", incluidos los límites 0/1, 2/3 y 4/5 mm; verificar con `./gradlew test`

## 3. Migración V16

- [x] 3.1 Escribir `V16MigrationIntegrationTest`: crea las tres tablas, los `CHECK` rechazan pieza temporal, profundidad 16, recesión −6, movilidad 4 y furcación en la pieza 11, la unicidad rechaza pieza y sitio repetidos, y los triggers rechazan `UPDATE` y `DELETE` (escenario "La base rechaza modificar o borrar una versión")
- [x] 3.2 Crear `backend/src/main/resources/db/migration/V16__periodontograma.sql` según design D3 y verificar que el test 3.1 pase con `./gradlew integrationTest --tests '*V16MigrationIntegrationTest'`

## 4. Aplicación y persistencia

- [x] 4.1 Crear el puerto `RegistroPeriodontal` y el servicio `ServicioPeriodontograma` (registrar, listar versiones, consultar una versión; paciente inexistente → `RECURSO_NO_ENCONTRADO`; profesional `null` → `AccessDeniedException`; pieza repetida o pedido vacío → `DATOS_INVALIDOS`), con tests unitarios sobre un puerto en memoria; verificar con `./gradlew test`
- [x] 4.2 Crear el adaptador `RegistroPeriodontalJdbc` (design D6), que inserta versión, piezas y sitios en una transacción y lee la lista de la más nueva a la más vieja; verificar con un test de integración que una versión guardada se lea igual y que una versión de otro paciente no se encuentre

## 5. API REST

- [x] 5.1 Crear los DTOs con Bean Validation y `PeriodontogramaController` con `POST`, `GET` lista y `GET` versión (design D1), y sumar al `ClinicaExceptionHandler` los códigos nuevos (verificar que el `422` lleve solo el campo, sin el valor)
- [x] 5.2 Escribir `PeriodontogramaIntegrationTest` (sobre `IntegracionClinicaBase`) con un test por escenario de "Registrar una versión del periodontograma", "Versiones inmutables", "Consultar las versiones del periodontograma" y los casos `201` y `422` de punta a punta de los grupos de validación; verificar con `./gradlew integrationTest --tests '*PeriodontogramaIntegrationTest'`
- [x] 5.3 Escribir los tests de acceso por rol: escenarios de "Acceso del odontólogo y del administrador al periodontograma" y "La recepción no accede al periodontograma" (incluido que la respuesta `403` no tenga datos), y "Un error de validación no repite el valor"; verificar que pasen con `./gradlew integrationTest`

## 6. Verificación final

- [x] 6.1 Correr `./gradlew clean build integrationTest` con PostgreSQL 16 y verificar que todo pase sin fallas ni tests omitidos
- [x] 6.2 Buscar en el código nuevo que ningún log ni mensaje de error escriba valores del periodontograma (regla dura 6) y verificar con `git grep -n "log\." -- backend/src/main/java/ar/turnos/odontologia/clinica` que no haya logs nuevos con datos
- [x] 6.3 Marcar C-25 en `CHANGES.md` como mergeado pendiente de archive y abrir el PR a `master` con Emanuel como revisor del módulo

## Workflow follow-up

- Archivar el change después de que la líder mergee el PR.
- Abrir el change de frontend del periodontograma (módulo de Enzo), con mockups antes del apply, que explique en la pantalla la convención de recesión negativa (design, Riesgos).
