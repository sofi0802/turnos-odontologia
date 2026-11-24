# Modelo de Datos

## Dominios

| Dominio | Descripción |
|---|---|
| **Configuración** | Profesionales, sillones o boxes, prestaciones con duración |
| **Agenda** | Turnos, bloqueos, sobreturnos y la verificación de solapamiento con margen |
| **Pacientes** | Datos de contacto y preferencia de canal |
| **Notificaciones** | Recordatorios y confirmaciones por WhatsApp o email |
| **Clínica** | Ficha básica y odontograma |
| **Seguridad y auditoría** | Usuarios del personal, roles, registro de cambios en turnos |

Un despliegue corresponde a **un consultorio** (single-tenant en v1, ver DD-05).

## ERD

```
 Usuario (personal) ─0..1──1─ Profesional
                                  │1
                                  │
 Prestacion ─1──*─ Turno ─*──1─ Profesional
                    │ *
                    ├──1─ Sillon
                    ├──1─ Paciente ─1──0..1─ FichaClinica ─1──*─ OdontogramaRegistro
                    ├──*─ Notificacion
                    └──*─ TurnoAuditoria

 Profesional ─1──*─ BloqueoAgenda
 Profesional ─1──*─ HorarioTrabajo
 Profesional ─1──*─ AusenciaProfesional
 Profesional ─*──*─ Prestacion   (vía ProfesionalPrestacion: especialidades)
 Profesional ─*──0..1─ Sillon    (sillón habitual)
 Usuario(PACIENTE) ─1──1─ Paciente
```

## Entidades

> **Identificadores** (DD-12, 2026-11-21): todas las tablas usan `id BIGINT GENERATED ALWAYS AS IDENTITY` y las claves foráneas son `BIGINT`. En Java se representan con `Long`, y el id lo asigna la base al insertar. El paciente nunca ve estos ids: se identifica con su **número de reserva** (RN-RO-08).

### Profesional
- `id` BIGINT identity (PK), `nombre` varchar(120), `matricula` varchar(30) (único, ficticio en seed), `sillon_habitual_id` FK nullable → `Sillon` (RN-RO-06), `activo` boolean.
- Relaciones: 1:N `Turno`, 1:N `BloqueoAgenda`, 1:N `HorarioTrabajo`, 1:N `AusenciaProfesional`, N:M `Prestacion` (vía `ProfesionalPrestacion`); 0..1 `Usuario`.

### ProfesionalPrestacion (especialidades)
- `profesional_id` FK, `prestacion_id` FK. PK compuesta.
- RN-AG-15: un profesional solo recibe turnos de las prestaciones en las que está habilitado.

### AusenciaProfesional
- `id` BIGINT identity, `profesional_id` FK, `tipo` enum (`AUSENCIA`, `LICENCIA`), `inicio` timestamptz, `fin` timestamptz, `motivo` varchar(255), `registrado_por` (usuario ADMIN o RECEPCIONISTA), `registrado_en`.
- RN-AG-16: bloquea la agenda del profesional en ese período.

### Sillon
- `id` BIGINT identity (PK), `nombre` varchar(60) (único, por ejemplo "Sillón 1"), `activo` boolean.
- Relaciones: 1:N `Turno`.

### Prestacion
- `id` BIGINT identity (PK), `nombre` varchar(120) (único), `duracion_minutos` int, `activa` boolean.
- Constraints: `duracion_minutos > 0` y múltiplo de 5 (supuesto SU-06).
- RN-AG-01: la duración del turno se toma de acá.

### Paciente
- `id` BIGINT identity (PK), `dni` varchar(15) (único), `nombre` varchar(120), `telefono` varchar(30) (nullable), `email` varchar(160) (nullable), `tiene_whatsapp` boolean, `consentimiento_comunicaciones` boolean, `consentimiento_fecha` timestamptz.
- Constraints: `telefono` o `email` presente (al menos un canal de contacto).

### Turno
- `id` BIGINT identity (PK)
- `paciente_id` FK, `profesional_id` FK, `sillon_id` FK, `prestacion_id` FK
- `inicio` timestamptz, `fin` timestamptz (= inicio + duración de la prestación; RN-AG-01)
- `estado` enum: `RESERVADO`, `CONFIRMADO`, `CANCELADO`, `ATENDIDO`, `AUSENTE`
- `es_sobreturno` boolean, `motivo_sobreturno` varchar(255). **Obligatorio** si `es_sobreturno = true` (RN-AG-06; CHECK en la base).
- `extendido` boolean, `motivo_extension` varchar(255) (RN-AG-14)
- `llegada_en` timestamptz (nullable): momento en que la recepción registró la llegada del paciente (RN-AG-20) [2026-11-24]. No cambia el estado: el turno sigue activo hasta que termina y el sistema lo pasa a `ATENDIDO`. Quién la registró queda en la auditoría del turno, igual que la ausencia y su deshacer (RN-AG-17).
- `origen` enum: `STAFF`, `ONLINE`
- `codigo_reserva` varchar(12) (único; para cancelar o reprogramar online)
- `creado_por` (usuario o "paciente-online"), `creado_en`, `cancelado_en`, `motivo_cancelacion`
- `version` int (bloqueo optimista)
- **Constraints**:
  - `fin > inicio`.
  - **Exclusión por sillón** (DD-03): no pueden coexistir dos turnos *no cancelados* del mismo `sillon_id` cuyos rangos `[inicio, fin + margen)` se intersequen. **Incluye los sobreturnos**: la limpieza del sillón es obligatoria (RN-AG-06).
  - **Exclusión por profesional**: la misma restricción con `profesional_id`, **excluyendo los sobreturnos**, que pueden superponerse con la agenda del profesional.
- Índices: `(profesional_id, inicio)`, `(sillon_id, inicio)`, `(paciente_id)`, `(estado, inicio)` para los recordatorios.

### HorarioTrabajo
- `id` BIGINT identity, `profesional_id` FK, `dia_semana` smallint (1 = lunes … 7 = domingo), `hora_desde` time, `hora_hasta` time.
- Constraints: `hora_hasta > hora_desde`; las franjas de un mismo profesional y día no se superponen. Un profesional puede tener varias franjas por día (por ejemplo, 8–12 y 16–20).
- RN-AG-13: un turno solo se acepta dentro de una franja.

### BloqueoAgenda
- `id` BIGINT identity, `profesional_id` FK, `inicio`, `fin`, `motivo`.
- Un turno nuevo no puede caer dentro de un bloqueo del profesional (RN-AG-10).

### Notificacion
- `id` BIGINT identity, `turno_id` FK, `canal` enum (`WHATSAPP`, `EMAIL`), `tipo` enum (`RECORDATORIO`, `CONFIRMACION`, `CANCELACION`), `estado` enum (`PENDIENTE`, `ENVIADA`, `FALLIDA`), `programada_para`, `enviada_en`, `token_confirmacion` (único).
- RN-NO-01: el contenido no incluye datos de salud.

### FichaClinica
- `id` BIGINT identity, `paciente_id` FK (único), `antecedentes` text, `alergias` text, `medicacion` text, `actualizado_por`, `actualizado_en`.

### OdontogramaRegistro
- `id` BIGINT identity, `ficha_id` FK, `pieza` smallint (notación FDI: 11–48 permanentes, 51–85 temporales), `cara` enum (`O`, `M`, `D`, `V`, `L`/`P`), `estado` varchar(40) (por ejemplo `CARIES`, `OBTURADO`, `AUSENTE`, `A_TRATAR`), `profesional_id`, `registrado_en`.
- Historial: se agrega un registro nuevo en lugar de sobrescribir el anterior (trazabilidad).

### Usuario
- `id` BIGINT identity, `username` (único), `password_hash`, `rol` enum (`ADMIN`, `RECEPCIONISTA`, `ODONTOLOGO`, `PACIENTE`), `profesional_id` FK nullable, `paciente_id` FK nullable, `activo`.
- Constraint: `rol = PACIENTE` ⇒ `paciente_id` obligatorio y `profesional_id` nulo. El rol `PACIENTE` solo accede a `/api/v1/paciente/**` y a sus propios turnos (RN-RO-08).

### Configuracion
- `clave` varchar (PK), `valor` varchar. Por ejemplo, `margen_minutos = 10`.
- Constraint aplicada en el servicio: `margen_minutos ≥ 10` (RN-AG-04).

### TurnoAuditoria
- `id`, `turno_id`, `accion` (`CREAR`, `CANCELAR`, `REPROGRAMAR`, `SOBRETURNO`, `CONFIRMAR`), `actor`, `fecha`, `detalle` jsonb.

## Seed data inicial

**Solo datos ficticios** (regla del proyecto; nunca datos reales de pacientes):

| Tipo | Datos |
|---|---|
| Profesionales | "Dra. Ana Ejemplo" (MP-0001), "Dr. Bruno Prueba" (MP-0002) |
| Sillones | "Sillón 1", "Sillón 2" |
| Sillón habitual | Dra. Ana Ejemplo → Sillón 1; Dr. Bruno Prueba → Sillón 2 |
| Especialidades | Los dos hacen Consulta, Limpieza y Extracción; **solo el Dr. Bruno Prueba hace Tratamiento de conducto** (para probar RN-AG-15) |
| Configuración | `margen_minutos = 10` |
| Horarios de trabajo | Dra. Ana Ejemplo: lunes a viernes de 08:00 a 13:00. Dr. Bruno Prueba: lunes a viernes de 10:00 a 18:00 (se superponen de 10 a 13, para probar "sin preferencia") |
| Prestaciones | Consulta o control (30 min), Limpieza (profilaxis) (45 min), Tratamiento de conducto (60 min), Extracción simple (30 min) |
| Usuarios | `admin`, `recepcion`, `ana.ejemplo` (contraseñas solo en el seed de desarrollo, nunca en producción) |
| Pacientes | 3 pacientes ficticios con DNI de la forma `99000001` y emails `@example.com`; uno con cuenta de paciente (`paciente.demo`) |
