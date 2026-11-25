# Design

## Context

El módulo `backend/.../clinica/` sigue puertos y adaptadores. Lo arrancó C-13 y C-25 lo extendió; el dueño es Emanuel.

- **Dominio puro**: `PiezaDental`, `ClinicaException` con `CodigoClinica`.
- **Servicios de aplicación** sobre los puertos `RegistroClinico` y `RegistroPeriodontal`, con adaptadores JPA y JDBC.
- **Controllers** bajo `/api/v1/pacientes/{id}` con `@PreAuthorize`.
- **Errores**: `ClinicaExceptionHandler`, limitado al paquete `clinica`. Responde `422` para cualquier código que no sea `RECURSO_NO_ENCONTRADO`.
- **Solo agregado**: el odontograma (V8) y el periodontograma (V16) lo garantizan con un trigger que rechaza `UPDATE` y `DELETE`, y cuelgan del paciente.

Hoy el proyecto no recibe archivos. No hay configuración de multipart ni almacén de archivos, y la única integración externa detrás de un puerto son las notificaciones (DD-04). Los requisitos están en `specs/imagenes-clinicas/spec.md` y la motivación en `proposal.md`.

## Goals / Non-Goals

**Goals:**
- Que la carga sea atómica desde el punto de vista del usuario: o quedan el archivo y su fila, o no queda ninguno visible.
- Que el adaptador de almacenamiento se pueda reemplazar (por S3 u otro) sin tocar el dominio, la aplicación ni la API.
- Reusar la forma del módulo clínico: permisos, manejador de errores y trigger de solo agregado.

**Non-Goals:**
- Adaptador S3, miniaturas, compresión, visor DICOM, antivirus y cifrado del archivo en reposo. Quedan como extensiones; en reposo cuenta el cifrado del disco del servidor.
- Asociar una imagen a un turno, al odontograma o al periodontograma. Solo paciente y pieza opcional.
- La pantalla, que es C-47.
- Que el paciente vea sus imágenes desde Mi cuenta: RN-CL-01 no lo permite hoy.

## Decisions

### D1. Recurso y endpoints
Un controlador propio, `ImagenClinicaController` en `clinica/api`, con las mismas expresiones de `@PreAuthorize` que el periodontograma: lectura para `ADMIN` u `ODONTOLOGO`, escritura solo para `ODONTOLOGO`.

| Método y ruta | Rol | Respuesta |
|---|---|---|
| `POST /api/v1/pacientes/{id}/imagenes` (`multipart/form-data`) | `ODONTOLOGO` | `201` con los metadatos |
| `GET /api/v1/pacientes/{id}/imagenes?pieza=&incluirAnuladas=false` | `ADMIN`, `ODONTOLOGO` | `200` con la lista |
| `GET /api/v1/pacientes/{id}/imagenes/{imagenId}/archivo?descarga=false` | `ADMIN`, `ODONTOLOGO` | `200` con los bytes |
| `POST /api/v1/pacientes/{id}/imagenes/{imagenId}/anulacion` (JSON `{ "motivo" }`) | `ODONTOLOGO` | `200` con los metadatos |

- **Partes del multipart**: `archivo`, `categoria`, `tomadaEl` (`YYYY-MM-DD`), `pieza` (opcional) y `descripcion` (opcional).
- **Metadatos de respuesta**: `id`, `categoria`, `pieza`, `tomadaEl`, `descripcion`, `tipoContenido`, `tamanoBytes`, `sha256`, `profesionalId`, `profesional`, `registradaEn` y `anulacion`. `anulacion` vale `null` o lleva `profesionalId`, `profesional`, `anuladaEn` y `motivo`.
- **Alternativa descartada: devolver el archivo en base64 dentro del JSON.** Agranda la respuesta un 33 % y el frontend no podría usar la URL directamente en un `<img>` o en un `<iframe>`.
- **Alternativa descartada: un `DELETE` que anule.** Se descarta porque el verbo promete un borrado que no ocurre. La anulación es un subrecurso con su propio cuerpo, el motivo.

### D2. Puerto de almacenamiento y adaptador en disco
El puerto `AlmacenArchivos` vive en `clinica/application` y tiene tres operaciones:
- `guardar(byte[]) → clave`;
- `leer(clave) → byte[]`;
- `borrar(clave)`, solo para compensar una carga fallida.

El adaptador `AlmacenArchivosDisco` vive en `clinica/infrastructure`. Escribe en `app.clinica.imagenes.directorio`, que toma el valor de la variable `IMAGENES_DIRECTORIO`.
- **Valor por defecto de desarrollo**: `./data/imagenes`, que se suma a `.gitignore`.
- **Perfil `test`**: usa una carpeta temporal por contexto.

La clave la genera el servidor con un UUID aleatorio, partido en dos niveles de carpeta (`ab/cd/<uuid>`) para no juntar miles de archivos en una sola carpeta.
- No lleva el id del paciente ni ningún dato suyo, y tampoco extensión.
- El adaptador rechaza una clave que no cumpla el formato o cuya ruta resuelta salga del directorio base. Así se evita el *path traversal*, aunque la clave nunca venga del cliente.
- Escribe primero a un archivo temporal en la misma carpeta y después lo mueve con `ATOMIC_MOVE`, para que una caída no deje un archivo a medias con el nombre final.

**Alternativa descartada: `bytea` en PostgreSQL.** Es más simple y transaccional, pero agranda la base y los respaldos, y no prepara el camino a S3 que pide CHANGES.md. Queda registrado en DD-17.

**Alternativa descartada: hacer ya el adaptador S3.** Suma una dependencia y un MinIO para los tests sin necesidad en el TP. El puerto alcanza para agregarlo después.

**Nota de nombres**: el UUID es solo una clave de almacén, no la identidad de la entidad. La fila usa `BIGINT` (DD-12).

### D3. Orden de la carga y compensación
`ServicioImagenes.cargar` sigue estos pasos:
1. Valida el permiso y el paciente.
2. Valida los metadatos y el contenido (D4).
3. Calcula el SHA-256.
4. Guarda el archivo en el almacén.
5. Inserta la fila en la transacción.
6. Si la inserción o el commit fallan, borra el archivo, con un `TransactionSynchronization.afterCompletion` con estado *rolled back*, y relanza el error.

Así una fila nunca apunta a un archivo inexistente.
- **Riesgo que se acepta**: una caída del proceso entre el paso 4 y el commit deja un archivo huérfano, invisible para la API. Se documenta como riesgo y no se agrega limpieza automática en este change.
- **Alternativa descartada: insertar la fila primero y guardar el archivo después.** Si el guardado falla después del commit, queda una fila sin archivo, que es peor que un archivo sin fila.

### D4. Validación del contenido en el dominio
`TipoArchivoClinico` es un enum de dominio (`JPEG`, `PNG`, `PDF`, cada uno con su `image/jpeg`, `image/png` o `application/pdf`). Tiene una función pura `detectar(byte[])` que reconoce el tipo por la firma de los primeros bytes:
- JPEG: `FF D8 FF`;
- PNG: `89 50 4E 47 0D 0A 1A 0A`;
- PDF: `25 50 44 46 2D`.

Ignora el nombre y el `Content-Type` de la parte multipart.

`ImagenClinica.nueva(...)` valida el resto:
- tamaño entre 1 y 10 485 760 bytes;
- fecha de la toma no futura, comparada con `LocalDate.now(reloj.withZone(zona))`;
- pieza con `PiezaDental.de`, que acepta permanentes y temporales;
- descripción de hasta 200 caracteres;
- categoría válida.

Códigos nuevos en `CodigoClinica`:

| Código | Estado | Cómo lo responde |
|---|---|---|
| `TIPO_ARCHIVO_NO_PERMITIDO` | `422` | El manejador actual ya lo cubre. |
| `ARCHIVO_DEMASIADO_GRANDE` | `413` | Hay que mapearlo en `ClinicaExceptionHandler`. |
| `IMAGEN_YA_ANULADA` | `409` | Hay que mapearlo; es un conflicto de estado, según la regla dura 9. |

Las demás validaciones reutilizan `DATOS_INVALIDOS`, `PIEZA_INVALIDA` y `RECURSO_NO_ENCONTRADO`.

La firma no prueba que el archivo esté bien formado; un PDF con contenido activo sigue siendo un PDF. Por eso D6 fuerza `nosniff` y la descarga se sirve con el tipo guardado. El riesgo queda en Riesgos.

### D5. Límite de tamaño en dos capas
Se configura `spring.servlet.multipart.max-file-size=10MB` y `max-request-size=11MB`, para dejar margen a las partes de texto.
- Si se supera, Spring lanza `MaxUploadSizeExceededException` al resolver el multipart, antes de elegir el controller. Por eso el `@RestControllerAdvice(basePackages = "...clinica")` no lo ve.
- Lo maneja `ProblemDetailGlobalHandler` (`shared/web`) con `413` y el código `ARCHIVO_DEMASIADO_GRANDE`. Es el único archivo fuera de `clinica/` que se toca, y se avisa en el PR.
- El dominio vuelve a validar el tamaño por si cambia la configuración.
- `server.tomcat.max-swallow-size` se deja en su valor por defecto (2 MB). Tomcat corta la conexión si el cuerpo rechazado es mucho mayor, y el test de integración usa un archivo de 10 MB + 1 byte, que queda dentro.

### D6. Entrega del archivo
La respuesta es un `ResponseEntity<byte[]>` con estos encabezados:
- `Content-Type`: el tipo guardado.
- `Content-Length`.
- `Cache-Control: no-store`.
- `X-Content-Type-Options: nosniff`.
- `Content-Disposition`: `inline`, o `attachment` con `?descarga=true`. El nombre es `imagen-<id>.<ext>` y no incluye el nombre del paciente, la categoría ni la pieza.

Antes de responder se recalcula el SHA-256 y se compara con el guardado. Si no coincide, se lanza un error no controlado, que termina en el `500` genérico de `ErrorInternoResolver`, sin cuerpo con datos. Con 10 MB como máximo, cargar el archivo en memoria es aceptable.
- **Alternativa descartada: `InputStreamResource` en streaming.** Ahorra memoria, pero impide verificar el hash antes de mandar el primer byte. Se puede revisar si el límite sube.

### D7. Tabla `imagen_clinica` (V17), de solo agregado con anulación única
Columnas:
- `id` (`BIGINT` identity), `paciente_id` (FK a `paciente`) y `profesional_id` (FK a `profesional`).
- `categoria`, con `CHECK` en los 3 valores.
- `pieza` (`SMALLINT NULL`), con `CHECK` de FDI permanente o temporal.
- `tomada_el` (`DATE`) y `descripcion` (`VARCHAR(200) NULL`).
- `tipo_contenido`, con `CHECK` en los 3 valores.
- `tamano_bytes`, con `CHECK` entre 1 y 10 485 760.
- `sha256` (`CHAR(64)`) y `clave_almacen` (`VARCHAR(80) UNIQUE`).
- `registrada_en` (`TIMESTAMPTZ`).
- `anulada_en`, `anulada_por` (FK a `profesional`) y `motivo_anulacion` (`VARCHAR(200)`). Las tres van juntas: `CHECK` de todas nulas o todas presentes, con un motivo que no esté en blanco.

Índice `(paciente_id, tomada_el DESC, registrada_en DESC, id DESC)` para el listado.

El trigger `imagen_clinica_solo_anulacion` rechaza:
- todo `DELETE`;
- un `UPDATE` que cambie cualquier columna que no sea de la anulación;
- un `UPDATE` sobre una fila que ya tenía `anulada_en`.

Así la base garantiza que la anulación ocurre una sola vez, aunque el dominio ya lo controla. La anulación concurrente se resuelve con `UPDATE ... WHERE id = ? AND paciente_id = ? AND anulada_en IS NULL`. Si actualiza 0 filas y la imagen existe, la respuesta es `IMAGEN_YA_ANULADA`.

**Alternativa descartada: tabla aparte `imagen_clinica_anulacion`.** Deja la imagen totalmente inmutable, pero complica el listado con un `LEFT JOIN` sin ganar garantías, porque el trigger ya las da.

### D8. Persistencia con JDBC
`RegistroImagenesJdbc` implementa el puerto `RegistroImagenes` con `JdbcClient`, como `RegistroPeriodontalJdbc` de C-25. Los nombres de los profesionales salen con un `JOIN` en la misma consulta.

### D9. Privacidad
- `ClinicaException` lleva mensajes fijos que nunca incluyen la descripción, el motivo, la categoría ni la pieza.
- El `422` de Bean Validation ya devuelve solo el campo.
- El servicio no registra nada en logs. Si en el futuro se agrega un log, solo podrá llevar ids.
- Nada de esto va a la auditoría de turnos.

## Risks / Trade-offs

- [Archivo huérfano si el proceso cae entre el guardado y el commit] → Es invisible para la API y no expone datos. Queda anotado para una tarea de limpieza futura que compare las claves del disco con la tabla.
- [Un PDF puede traer JavaScript o enlaces] → `nosniff` y el tipo guardado. El frontend de C-47 deberá mostrar los PDF en un `<iframe sandbox>` o como descarga, y se anota allí. No hay antivirus en el TP.
- [El disco local no sirve con varias instancias] → El despliegue es single-tenant de una instancia (DD-05). El puerto permite pasar a S3.
- [Respaldo] → Los archivos no están en la base: el respaldo tiene que incluir `IMAGENES_DIRECTORIO`. Se documenta en `08_arquitectura_propuesta.md`.
- [Memoria] → Hasta 10 MB por pedido en memoria, al cargar y al descargar. Es aceptable para un consultorio chico; se revisa si cambia el límite.
- [El `413` depende del manejador global] → Si otro change cambia `ProblemDetailGlobalHandler`, el test de integración del límite lo detecta.

## Migration Plan

1. Mergear el PR. Flyway aplica `V17__imagenes_clinicas.sql` con out-of-order y no toca tablas existentes.
2. En cada entorno, definir `IMAGENES_DIRECTORIO` apuntando a una carpeta con permisos de escritura solo para el proceso. En desarrollo alcanza el valor por defecto.
3. **Rollback**: la tabla es nueva. Se puede revertir el código y dejar la tabla y la carpeta, o borrarlas con una migración nueva si el equipo lo decide. Las migraciones mergeadas no se editan.
