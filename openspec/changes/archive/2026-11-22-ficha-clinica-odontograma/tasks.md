# Tasks

> Governance ALTO (datos de salud): el apply arranca después de que Sofia apruebe la propuesta en el PR.
> Antes del apply, actualizar la rama con `upstream/master`. Trae C-04 (`out-of-order`, perfil `test` y `user.timezone`) y C-08 (V6 y los campos nuevos de `Turno`).

## 1. Migración y base del módulo

- [x] 1.1 Crear `db/migration/V8__ficha_clinica.sql` (design D8) con estos contenidos:
  - `ficha_clinica` y `odontograma_registro`, con sus `CHECK` de pieza FDI, cara y estado;
  - el índice `(paciente_id, pieza, cara, registrado_en DESC)`;
  - el trigger de solo agregado;
  - el `CHECK` de `turno_auditoria` borrado y vuelto a crear con la lista completa de siete acciones, igual a la de la V7 de C-11: `CREAR`, `CANCELAR`, `REPROGRAMAR`, `SOBRETURNO`, `EXTENDER`, `CONFIRMAR` y `AUSENTE`, con un comentario que pida copiar la lista entera en futuras migraciones (design D8).

  Verificar con `./gradlew integrationTest` que Flyway migra y que los tests de C-03, C-04, C-05, C-06 y C-08 siguen en verde.
- [x] 1.2 Verificar en la base, con SQL directo:
  - que rechaza un `UPDATE` y un `DELETE` sobre `odontograma_registro`;
  - que rechaza la pieza 19;
  - que rechaza la cara `P` en la pieza 31;
  - que `turno_auditoria` acepta las siete acciones y rechaza una desconocida.

  Dejarlo como test de integración del esquema.

## 2. Dominio del odontograma

- [x] 2.1 Crear `PiezaDental`, `CaraDental`, `EstadoPieza`, `CodigoClinica` y `ClinicaException` en `clinica/domain`, sin Spring ni JPA. Verificar con tests de unidad:
  - los bordes FDI de cada cuadrante, permanentes y temporales: 11, 18, 19, 51, 55 y 56;
  - `L` y `P` según el cuadrante;
  - un estado fuera del catálogo.

## 3. Ficha básica

- [x] 3.1 Implementar `FichaClinicaEntity`, su repositorio, el servicio de ficha y el `GET` y `PUT /api/v1/pacientes/{id}/ficha`, con DTOs `record`, Bean Validation (4000 caracteres) y `@PreAuthorize` (design D2 y D5). Crear `ClinicaExceptionHandler` limitado a `clinica`, sin valores rechazados en el `422` (D9). Verificar que compila y que ninguna respuesta expone una entidad JPA.
- [x] 3.2 Crear `IntegracionClinicaBase` (design D10) con:
  - los usuarios `ana.ejemplo`, `bruno.prueba`, `admin` y `recepcion`, y un odontólogo sin profesional;
  - los pacientes ficticios;
  - el reloj ajustable.
- [x] 3.3 Escribir los tests de integración de estos escenarios y verificar que pasan:
  - "Registrar la ficha por primera vez";
  - "Actualizar la ficha reemplaza el contenido";
  - "Ficha con un texto demasiado largo";
  - "Paciente sin ficha";
  - "Ficha de un paciente inexistente";
  - "El administrador lee la ficha";
  - "La recepción consulta la ficha";
  - "Un error de validación no repite el contenido", que también revisa los logs con `OutputCaptureExtension`.

## 4. Odontograma

- [x] 4.1 Implementar `OdontogramaRegistroEntity` (solo inserción), su repositorio con la consulta `DISTINCT ON` del estado vigente y la del historial, el servicio de odontograma y los endpoints `PUT /odontograma`, `GET /odontograma` y `GET /odontograma/historial`, con un pedido atómico de 1 a 32 cambios (design D3, D4 y D5). Verificar que compila.
- [x] 4.2 Escribir los tests de integración de estos escenarios y verificar que pasan:
  - "Registrar una caries";
  - "Un cambio posterior no sobrescribe el historial";
  - "Estado de la pieza completa";
  - "Pedido con un cambio inválido no guarda ninguno";
  - "Odontólogo sin profesional vinculado";
  - "Pieza temporal válida";
  - "Pieza fuera de la notación";
  - "Cara palatina en una pieza superior";
  - "Cara palatina en una pieza inferior";
  - "Estado desconocido";
  - "Odontograma vacío";
  - "El estado vigente muestra solo el último registro";
  - "El administrador intenta modificar el odontograma";
  - "La recepción consulta el odontograma".

## 5. Ausencia del paciente (agenda, con aviso a Sofia)

- [x] 5.1 Agregar a la agenda lo que define el design D6:
  - `Turno.ausente()`, `TURNO_NO_INICIADO` en `CodigoRechazo` y `AusenciaNoPermitidaException` en `agenda/domain`;
  - el caso de uso `MarcarAusencia` con su comando, y `AUSENTE` en `AuditoriaTurnos.Accion`.

  Verificar con tests de unidad (dobles en memoria y `Clock` fijo):
  - el orden de verificación de D6: inexistente u otro paciente, otro profesional, no activo, no iniciado y éxito;
  - que `ausente()` conserva `esSobreturno`, `motivoSobreturno`, `extendido` y `motivoExtension` de un sobreturno y de un turno extendido.

  Los tests de C-01, C-06 y C-08 tienen que seguir en verde.
- [x] 5.2 Agregar `ServicioAgenda.marcarAusencia` (transacción, auditoría `AUSENTE` y reintento) y el bean en `AgendaConfig`. Verificar que compila y que los tests de C-06 y C-08 siguen en verde.
- [x] 5.3 Implementar `POST /api/v1/pacientes/{id}/ausencias` en `clinica/api` y la traducción de los rechazos de la agenda en `ClinicaExceptionHandler`:
  - `404 TURNO_INEXISTENTE`;
  - `403 ACCESO_DENEGADO`;
  - `409 TURNO_NO_ACTIVO` y `TURNO_NO_INICIADO`.

  Verificar con los tests de integración de estos escenarios:
  - "La ausencia libera el resto del horario";
  - "Ausencia en un turno que ya terminó";
  - "Ausencia antes del inicio";
  - "Ausencia en un turno cancelado";
  - "Otro odontólogo marca la ausencia";
  - "La recepción marca la ausencia";
  - "Turno de otro paciente";
  - "Se registra la ausencia";
  - "Ausencia rechazada sin registro".
- [x] 5.4 Implementar el puerto `AusenciasPaciente` y su adaptador de lectura sobre `turno` y `profesional`, y sumar las ausencias al `GET /ficha` (design D7). Verificar con el test de integración "La ficha muestra la ausencia".

## 6. Verificación integral

- [x] 6.1 Correr `./gradlew test integrationTest` completo y verificar:
  - que los 32 escenarios (23 de `ficha-clinica`, 7 de `agenda-turnos` y 2 de `auditoria-turnos`) tienen un test con `@DisplayName` igual a su nombre, todos en verde;
  - que los tests de C-01, C-03, C-04, C-05, C-06 y C-08 siguen pasando.
- [x] 6.2 Verificar con `grep` que `clinica/domain` no importa `org.springframework` ni `jakarta.persistence`, que ningún controlador devuelve una entidad JPA y que el módulo no loguea cuerpos de pedidos.
- [x] 6.3 Correr `openspec validate ficha-clinica-odontograma --strict` y verificar que es válido.

## Workflow follow-up

- Abrir el PR de `change/C-13-ficha-clinica-odontograma` a `master` con estos avisos:
  - a **Sofia**, por los agregados en `agenda/domain`, `agenda/application` y `agenda/infrastructure`, y por el `CHECK` de `turno_auditoria`;
  - a **todo el equipo**, para que toda migración que sume una acción de auditoría copie la lista entera, incluido `AUSENTE` (design D8).
- Marcar `[x]` en C-13 de `CHANGES.md`.
- Archivar el change con `openspec archive ficha-clinica-odontograma` y verificar los specs `ficha-clinica`, `agenda-turnos` y `auditoria-turnos` en `openspec/specs/`.
