# Design

## Context

El módulo `backend/.../clinica/` (C-13, dueño Emanuel) sigue puertos y adaptadores: dominio puro (`PiezaDental`, `CaraDental`, `EstadoPieza`, `ClinicaException` con `CodigoClinica`), servicios de aplicación (`ServicioFicha`, `ServicioOdontograma`) sobre el puerto `RegistroClinico`, el adaptador JPA `RegistroClinicoJpa`, `FichaClinicaController` bajo `/api/v1/pacientes/{id}` con `@PreAuthorize` y `ClinicaExceptionHandler` limitado al paquete `clinica`. El odontograma (V8) ya resolvió el historial de solo agregado con un trigger que rechaza `UPDATE` y `DELETE`, y cuelga del paciente, no de la ficha. Las reglas están en RN-CL-01 y RN-CL-04 a RN-CL-06; los requisitos, en `specs/periodontograma/spec.md`.

## Goals / Non-Goals

**Goals:**
- Reusar la forma del módulo clínico (mismos permisos, mismo manejador de errores, mismo patrón de solo agregado) para que Emanuel lo revise como una extensión natural de C-13.
- Calcular el estado periodontal en el dominio, sin base de datos, para probar cada corte con tests unitarios.

**Non-Goals:**
- Pantalla del periodontograma (change de frontend posterior).
- Estadio y grado del paciente según AAP/EFP 2017, índices de placa o de sangrado del paciente, y comparación automática entre versiones.
- Editar o borrar versiones (RN-CL-04).

## Decisions

### D1. Recurso y endpoints
`POST /api/v1/pacientes/{id}/periodontograma` registra una versión (`201` con la versión completa), `GET /api/v1/pacientes/{id}/periodontograma` lista los resúmenes (`id`, `registradoEn`, `profesionalId`, `profesional`) de la más nueva a la más vieja, y `GET /api/v1/pacientes/{id}/periodontograma/{versionId}` devuelve la versión con piezas, sitios y estado. Van en un controlador propio (`PeriodontogramaController`) en `clinica/api`, con las mismas expresiones de `@PreAuthorize` que `FichaClinicaController` (lectura `ADMIN` u `ODONTOLOGO`, escritura `ODONTOLOGO`), para no agrandar ese controlador.
- *Alternativa*: `PUT` como el odontograma. Se descarta porque cada carga crea un recurso nuevo; `POST` + `201` lo dice mejor.
- *Alternativa*: devolver todas las versiones completas en el `GET` de la lista. Se descarta: con 32 piezas × 6 sitios por versión, la lista crece rápido; el resumen alcanza para elegir.

### D2. Las versiones cuelgan del paciente
`periodontograma_version.paciente_id` referencia `paciente`, igual que `odontograma_registro` en V8 (design D3 de C-13): no exige que exista la ficha básica. La KB (`04_modelo_de_datos.md`) decía `ficha_id`; se corrige en la tarea 1.2.

### D3. Tres tablas de solo agregado (V16)
`periodontograma_version` (paciente, profesional, `registrado_en`), `periodontograma_pieza` (versión, pieza, `ausente`, `movilidad`, `furcacion`; único por versión y pieza) y `periodontograma_sitio` (pieza, sitio, profundidad, recesión, sangrado, placa; único por pieza y sitio). Los `CHECK` repiten los rangos de RN-CL-05, las piezas permanentes y la furcación solo en 14, 24 y molares, como segunda barrera detrás del dominio. Un trigger por tabla rechaza `UPDATE` y `DELETE` con una función propia (`periodontograma_solo_agregado`), igual que V8. El `TRUNCATE` de los tests no dispara triggers de fila.
- *Alternativa*: una sola tabla con un `jsonb` por versión. Se descarta: pierde los `CHECK` y la restricción de unicidad, y el resto del proyecto usa columnas tipadas.
- La completitud de los 6 sitios no se puede expresar con un `CHECK` de fila; la garantiza el dominio y la inserción en una sola transacción.

### D4. Dominio
Tipos nuevos en `clinica/domain`: `SitioPeriodontal` (enum de los 6 sitios), `MedicionSitio` (record que valida rangos), `PiezaPeriodontal` (pieza permanente ausente o presente con 6 sitios, movilidad y furcación opcional), `EstadoPeriodontal` (`SANO`, `LEVE`, `MODERADO`, `SEVERO`) y `EstadoPeriodontal.de(sitios)` como función pura de RN-CL-06, que `PiezaPeriodontal.estado()` usa (`null` en una pieza ausente). `PiezaDental` gana `esPermanente()` y `esMultirradicular()`. Los errores usan `ClinicaException` con los códigos nuevos `SITIOS_INCOMPLETOS` y `FURCACION_NO_APLICA` (422), y reutilizan `PIEZA_INVALIDA`, `DATOS_INVALIDOS` y `RECURSO_NO_ENCONTRADO`. `ClinicaExceptionHandler` ya responde `422` para todo código que no sea `RECURSO_NO_ENCONTRADO`, así que los códigos nuevos no necesitan cambios en el manejador.
- El estado no se guarda (RN-CL-06): se calcula al armar la respuesta. Si el criterio cambia, las versiones viejas muestran el criterio nuevo; se acepta porque es orientativo (DD-13).

### D5. Validación en dos capas
Bean Validation en los DTOs para tipos y rangos (`@Min`/`@Max`, `@NotNull`, lista no vacía) responde `DATOS_INVALIDOS` con solo el campo, como ya hace `ClinicaExceptionHandler`. Las reglas que cruzan datos (sitios completos, furcación por pieza, ausente sin datos, pieza repetida, pieza temporal) viven en el dominio. Todo se valida antes de guardar: un pedido inválido no deja versión.

### D6. Puerto y adaptador
Puerto nuevo `RegistroPeriodontal` en `clinica/application` (no se agranda `RegistroClinico`), con `registrar(pacienteId, profesionalId, momento, piezas)`, `versiones(pacienteId)` y `version(pacienteId, versionId)`. Adaptador JDBC (`RegistroPeriodontalJdbc`) con `JdbcTemplate`, como `AusenciasPacienteJdbc`: tres inserts en una transacción y lecturas con join, sin entidades JPA nuevas. La existencia del paciente se consulta con `RegistroClinico.existePaciente`.
- *Alternativa*: entidades JPA con cascada. Se descarta por el costo de mapear tres niveles de solo agregado, cuando el mismo módulo ya usa JDBC para las ausencias.

### D7. Profesional de la sesión
El profesional sale de `UsuarioAutenticado.profesionalId()`, como en el odontograma; si es `null` el servicio lanza `AccessDeniedException` (`403 ACCESO_DENEGADO`).

## Risks / Trade-offs

- [Riesgo] Si el odontólogo carga recesión 0 en una encía sana (margen por encima del límite amelocementario), una profundidad normal de 1 a 3 mm da pérdida de inserción positiva y la pieza sale `LEVE`. → El criterio aprobado (RN-CL-06) asume la convención de recesión negativa; el change de frontend tiene que explicarla en la pantalla. Se deja anotado para la revisión de la líder.
- [Riesgo] `clinica/` es de Emanuel. → Se le avisa antes del apply y revisa el PR (regla dura 3).
- [Riesgo] V16 se aplica después de V21 y V22 en bases existentes. → `out-of-order` ya está activo, y V16 solo crea tablas nuevas sin tocar las existentes.
- [Trade-off] El estado se recalcula en cada lectura. Son a lo sumo 32 piezas × 6 sitios por versión, así que el costo es despreciable.

## Migration Plan

1. `V16__periodontograma.sql` crea las tres tablas, los `CHECK`, los índices (`paciente_id, registrado_en DESC`) y los triggers.
2. Rollback: no hay datos previos que migrar; en desarrollo alcanza con una migración nueva que haga `DROP` de las tres tablas y de la función. No se edita V16 una vez mergeada.
