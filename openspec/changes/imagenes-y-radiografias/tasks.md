# Tasks

> Rama `change/C-26-imagenes-y-radiografias`.
> - Cada escenario de `specs/imagenes-clinicas/spec.md` tiene su test con el mismo nombre (regla dura 7).
> - En cada grupo, los tests van primero.
> - Los tests de integración llevan `@Tag("integration")` y usan PostgreSQL real.
> - Los archivos de prueba son ficticios y se generan en el test: bytes con la firma de cada tipo. No se usan imágenes reales de personas.

## 1. Preparación

- [x] 1.1 Avisar en la descripción del PR a Juan Sebastián (autor de `shared/web/ProblemDetailGlobalHandler`, C-05) que C-26 le suma el manejo de `MaxUploadSizeExceededException` (design D5). Verificar que el aviso figure en el PR.
- [x] 1.2 Sumar `data/` y `backend/data/` a `.gitignore` y la variable `IMAGENES_DIRECTORIO`, sin valor real, a `.env.example`. Verificar con `git status` que una carga en desarrollo no deja archivos para commitear.

## 2. Dominio

- [x] 2.1 Agregar `TIPO_ARCHIVO_NO_PERMITIDO`, `ARCHIVO_DEMASIADO_GRANDE` e `IMAGEN_YA_ANULADA` a `CodigoClinica`. Crear `TipoArchivoClinico` con `detectar(byte[])` por firma (design D4). Agregar tests unitarios:
  - JPEG, PNG y PDF válidos;
  - GIF y texto rechazados;
  - un archivo de menos de 3 bytes rechazado;
  - un PNG con nombre `.jpg`.

  Verificar con `./gradlew test`.
- [x] 2.2 Crear `CategoriaImagen` y `ImagenClinica` (creación y anulación). Validan el tamaño entre 1 y 10 485 760 bytes, la fecha no futura en la zona del consultorio, la pieza FDI con `PiezaDental`, la descripción de hasta 200 caracteres y el motivo de 1 a 200 caracteres. Agregar tests unitarios de los escenarios de "Fecha de la toma y pieza", de los límites de "Tamaño máximo del archivo" (10 485 760 y 10 485 761) y de "Anular sin motivo". Verificar con `./gradlew test`.

## 3. Migración V17

- [x] 3.1 Escribir `V17MigrationIntegrationTest`:
  - los `CHECK` rechazan categoría, tipo, pieza (19), tamaño (0 y 10 485 761) y una anulación incompleta;
  - la clave del almacén es única;
  - el trigger rechaza un `DELETE`, un `UPDATE` de `categoria`, `tomada_el` o `clave_almacen`, y una segunda anulación;
  - el trigger permite la primera anulación.

  Cubre el escenario "La base rechaza borrar una imagen".
- [x] 3.2 Crear `backend/src/main/resources/db/migration/V17__imagenes_clinicas.sql` según design D7. Verificar que el test 3.1 pase con `./gradlew integrationTest --tests '*V17MigrationIntegrationTest'`.

## 4. Almacén de archivos

- [x] 4.1 Crear el puerto `AlmacenArchivos` y el adaptador `AlmacenArchivosDisco` (design D2), con la propiedad `app.clinica.imagenes.directorio` tomada de `IMAGENES_DIRECTORIO`. Sumar el límite multipart a `application.yml` (design D5) y una carpeta temporal en el perfil `test`. Agregar tests unitarios con `@TempDir`:
  - guardar y leer devuelve los mismos bytes;
  - la clave tiene el formato `ab/cd/<uuid>` sin datos del paciente;
  - una clave con `..` o fuera del formato se rechaza;
  - `borrar` elimina el archivo.

  Verificar con `./gradlew test`.

## 5. Aplicación y persistencia

- [x] 5.1 Crear el puerto `RegistroImagenes` y `ServicioImagenes`, con tests unitarios sobre un puerto y un almacén en memoria. Verificar con `./gradlew test`. El servicio cubre:
  - **carga** con hash SHA-256 y compensación (design D3);
  - **listado** con filtro de pieza y de anuladas;
  - **lectura** del archivo con verificación del hash (design D6);
  - **anulación**;
  - **paciente o imagen inexistentes** → `RECURSO_NO_ENCONTRADO`;
  - **profesional `null`** → `AccessDeniedException`;
  - **imagen ya anulada** → `IMAGEN_YA_ANULADA`;
  - **falla de la inserción**: el archivo se borra del almacén.
- [x] 5.2 Crear `RegistroImagenesJdbc` (design D8), con el orden del listado y la anulación condicional (`anulada_en IS NULL`). Verificar con un test de integración:
  - una imagen guardada se lee igual;
  - una imagen de otro paciente no se encuentra;
  - dos anulaciones seguidas dejan la primera.

## 6. API REST

- [x] 6.1 Crear `ImagenClinicaController` con los cuatro endpoints, los DTOs de respuesta y los encabezados de entrega (design D1 y D6).
  - En `ClinicaExceptionHandler`, mapear `ARCHIVO_DEMASIADO_GRANDE` a `413` e `IMAGEN_YA_ANULADA` a `409`.
  - En `ProblemDetailGlobalHandler`, manejar `MaxUploadSizeExceededException` con `413`.
  - Verificar que los `422` lleven solo el campo, sin el valor.
- [x] 6.2 Escribir `ImagenClinicaIntegrationTest` (sobre `IntegracionClinicaBase`), con un test por escenario de "Cargar una imagen clínica", "Tipos de archivo permitidos", "Tamaño máximo del archivo", "Fecha de la toma y pieza", "No hay forma de borrar una imagen", "Listar las imágenes del paciente", "Descargar el archivo de una imagen", "Integridad del archivo" y "Anular una imagen con motivo". Verificar con `./gradlew integrationTest --tests '*ImagenClinicaIntegrationTest'`.
- [x] 6.3 Escribir los tests de acceso y privacidad. Verificar que pasen con `./gradlew integrationTest`. Cubren:
  - "Acceso del odontólogo y del administrador a las imágenes";
  - "La recepción y el paciente no acceden a las imágenes", incluidos el `401` sin sesión, el `403` sin CSRF y que el `403` no tenga datos;
  - "Datos de las imágenes fuera de los canales externos", revisando que la respuesta no repita la descripción y que el archivo del almacén no tenga datos del paciente en su ruta.

## 7. Verificación final

- [x] 7.1 Correr `./gradlew clean build integrationTest` con PostgreSQL 16. Verificar que todo pase sin fallas ni tests omitidos y guardar el resumen en `docs/evidencia/c26-backend.txt`.
- [x] 7.2 Revisar el código nuevo para que ningún log ni mensaje de error escriba la descripción, el motivo, la categoría ni la pieza (regla dura 6). Verificar con `git grep -n "log\." -- backend/src/main/java/ar/turnos/odontologia/clinica` que no haya logs nuevos con datos.
- [x] 7.3 Probar a mano contra el backend levantado que una carga de JPEG queda en `IMAGENES_DIRECTORIO` y se descarga idéntica. Verificar comparando el SHA-256 del archivo descargado con el de la respuesta.
- [x] 7.4 Marcar C-26 en `CHANGES.md` como implementado pendiente de merge y de archive, y abrir el PR a `master`. Verificar que el PR enlace este change y avise a Juan Sebastián (tarea 1.1).

## Workflow follow-up

- Archivar el change después de que la líder mergee el PR.
- Arrancar C-47 `frontend-imagenes-y-radiografias` con mockups antes del apply. Anotar allí que los PDF se muestran en un `<iframe sandbox>` o como descarga (design, Riesgos).
