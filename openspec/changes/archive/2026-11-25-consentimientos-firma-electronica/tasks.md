# Tasks

> Rama `change/C-27-consentimientos-firma-electronica`.
> - Cada escenario de `specs/consentimientos-informados/spec.md` tiene su test con el mismo nombre (regla dura 7).
> - En cada grupo, los tests van primero.
> - Los tests de integración llevan `@Tag("integration")` y usan PostgreSQL real.
> - Los textos de las plantillas, los nombres, los DNI, las IP y los `User-Agent` de los tests son ficticios ("Paciente Uno", DNI `99000001`, IP `10.0.0.15`, "Navegador de prueba 1.0").
> - Antes del apply, confirmar con Emanuel las propuestas PA-43 a PA-52 de `knowledge-base/10_preguntas_abiertas.md`. Si alguna cambia, actualizar specs y design con `opsx update` antes de seguir.

## 1. Dominio: plantillas y versiones

- [x] 1.1 Agregar a `CodigoClinica` `PLANTILLA_DUPLICADA`, `VERSION_DESACTUALIZADA`, `PLANTILLA_INACTIVA`, `CONSENTIMIENTO_YA_REVOCADO` y `FIRMANTE_NO_COINCIDE`, y mapearlos en `ClinicaExceptionHandler` según design D9. Verificar con un test unitario del manejador que cada código devuelve su estado y que el cuerpo no repite el valor rechazado.
- [x] 1.2 Crear `PlantillaConsentimiento` y `VersionPlantilla` con la normalización de design D3 (saltos de línea, NFC, recorte) y los límites de título (1–120) y texto (1–20 000). Agregar tests unitarios de los escenarios "Texto demasiado largo", "Título vacío", "Edición sin cambios" y de la normalización de `\r\n` y NFC. Verificar con `./gradlew test`.

## 2. Dominio: firma, contenido canónico y hash

- [x] 2.1 Crear `ContenidoCanonico` y el cálculo SHA-256 (design D4). Guardar en `src/test/resources/clinica/consentimiento-canonico-v1.txt` el contenido esperado de un consentimiento ficticio fijo y su SHA-256 calculado con `sha256sum`. Agregar tests unitarios de "El hash coincide con el contenido canónico", "Un cambio en el texto cambia el hash" y "Los campos no se pueden correr de lugar". Verificar con `./gradlew test`.
- [x] 2.2 Crear `ConsentimientoFirmado`, `EvidenciaFirma` y `Revocacion`. Normalizan el nombre (3–200) y el DNI (7 u 8 dígitos sin puntos), exigen la aceptación, comparan el DNI con el del paciente, validan la IP literal, recortan el `User-Agent` a 512 caracteres y truncan el instante a microsegundos con un `Clock` fijo. Agregar tests unitarios de "Sin aceptación", "Nombre del firmante vacío", "DNI distinto del paciente", "DNI con puntos", "User-Agent demasiado largo", "Sin User-Agent", "La fecha es la del servidor" y "Revocar sin motivo". Verificar con `./gradlew test`.

## 3. Migración V18

- [x] 3.1 Escribir `V18MigrationIntegrationTest`. Verificar que:
  - los `CHECK` rechacen un título de 121 caracteres, un texto vacío, un DNI con letras, un hash que no sea hexadecimal, otro `tipo_firma`, otro algoritmo y un motivo en blanco;
  - `UNIQUE (plantilla_id, numero)` y `UNIQUE (consentimiento_id)` rechacen duplicados;
  - los triggers rechacen el `UPDATE` y el `DELETE` de una versión, de un consentimiento firmado (texto, firmante, fecha y hash) y de una revocación, y el `DELETE` de una plantilla;
  - el trigger de la plantilla permita cambiar solo `activa`.

  Cubre "La base rechaza modificar una versión", "La base rechaza modificar un consentimiento" y "La base rechaza modificar una revocación".
- [x] 3.2 Crear `backend/src/main/resources/db/migration/V18__consentimientos.sql` según design D5. Verificar que el test 3.1 pase con `./gradlew integrationTest --tests '*V18MigrationIntegrationTest'`.

## 4. Aplicación y persistencia

- [x] 4.1 Crear el puerto `RegistroPlantillas`, `ServicioPlantillas` y `RegistroPlantillasJdbc` (design D6 y D10): crear, editar con `versionBase` y bloqueo `FOR UPDATE`, título único sin distinguir mayúsculas, activar y desactivar, listar y leer versiones. Agregar tests unitarios del servicio con un puerto en memoria y un test de integración del adaptador con dos ediciones concurrentes sobre la misma versión base, que deje una sola versión nueva y responda `VERSION_DESACTUALIZADA` a la otra. Verificar con `./gradlew test integrationTest --tests '*Plantilla*'`.
- [x] 4.2 Crear el puerto `RegistroConsentimientos`, `ServicioConsentimientos` y `RegistroConsentimientosJdbc`: firmar con `FOR SHARE` sobre la plantilla, copia del título y del texto, listar, leer, verificar el hash con `MessageDigest.isEqual` y revocar (el `UNIQUE` duplicado → `CONSENTIMIENTO_YA_REVOCADO`). Agregar tests unitarios con puertos en memoria para paciente o consentimiento inexistentes (`RECURSO_NO_ENCONTRADO`), profesional `null` (`AccessDeniedException`), plantilla inactiva y versión desactualizada. Agregar un test de integración del adaptador: lo firmado se lee igual y vuelve a dar el mismo hash; un consentimiento de otro paciente no se encuentra. Verificar con `./gradlew test integrationTest --tests '*Consentimiento*'`.

## 5. API REST

- [x] 5.1 Crear `PlantillaConsentimientoController`, `ConsentimientoController` y sus DTOs con el contrato de design D2. El controller arma `EvidenciaFirma` desde `getRemoteAddr()` y `User-Agent` (design D7), sin leer `X-Forwarded-For`. Verificar con `./gradlew build` que compile y que ningún DTO exponga una entidad.
- [x] 5.2 Escribir `PlantillaConsentimientoIntegrationTest` (sobre `IntegracionClinicaBase`) con un test por escenario de "Crear una plantilla de consentimiento", "Editar una plantilla crea una versión nueva", "Activar y desactivar una plantilla" y "Consultar las plantillas". Verificar con `./gradlew integrationTest --tests '*PlantillaConsentimientoIntegrationTest'`.
- [x] 5.3 Escribir `ConsentimientoIntegrationTest` con un test por escenario de "Firmar un consentimiento en el consultorio", "Evidencia de la firma" (la IP con `MockMvc` `with(remoteAddr("10.0.0.15"))`), "El documento firmado guarda su propia copia del texto", "Documento firmado bloqueado", "Verificar el hash de un consentimiento" (el alterado, con `ALTER TABLE ... DISABLE TRIGGER` dentro del test), "Listar y ver los consentimientos del paciente" y "Revocar un consentimiento con registro". Verificar con `./gradlew integrationTest --tests '*ConsentimientoIntegrationTest'`.
- [x] 5.4 Escribir los tests de acceso y privacidad de "La recepción y el paciente no acceden a los consentimientos firmados" (incluidos el `401` sin sesión, el `403` sin CSRF y que el `403` no tenga datos) y de "Datos de los consentimientos fuera de los canales externos" (con `OutputCaptureExtension` para el log). Verificar con `./gradlew integrationTest`.

## 6. Verificación final

- [x] 6.1 Correr `./gradlew clean build integrationTest` con PostgreSQL 16. Verificar que todo pase sin fallas ni tests omitidos y guardar el resumen en `docs/evidencia/c27-backend.txt`.
- [x] 6.2 Revisar que ningún log ni mensaje de error nuevo escriba el texto, el nombre, el DNI, la IP, el dispositivo ni el motivo (regla dura 6). Verificar con `git grep -n "log\." -- backend/src/main/java/ar/turnos/odontologia/clinica` que no haya logs nuevos con datos.
- [x] 6.3 Probar a mano contra el backend levantado: el `ADMIN` crea una plantilla, el odontólogo registra una firma de "Paciente Uno" y la verificación da íntegro. Verificar recalculando el SHA-256 del contenido canónico armado a mano con los datos de la respuesta y comparándolo con el hash devuelto.
- [x] 6.4 Revisar que ningún texto de la API, de la KB ni de los mensajes llame "firma digital" a esta firma. Verificar con `git grep -n -i "firma digital"` que solo aparezca para decir que no lo es.
- [x] 6.5 Marcar C-27 en `CHANGES.md` como implementado pendiente de merge y de archive, y abrir el PR a `master`. Verificar que el PR enlace este change y liste las PA confirmadas.

## Workflow follow-up

- Archivar el change después de que la líder mergee el PR.
- Arrancar C-48 `frontend-consentimientos` con mockups antes del apply, usando el contrato de design D2. Anotar allí que el texto se muestra como texto plano, sin interpretar HTML, y que la pantalla de firma dice "firma electrónica", nunca "firma digital".
