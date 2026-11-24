# Modelo de Datos

## Dominios

| Dominio | Descripción |
|---|---|
| **Configuración** | Profesionales, sillones o boxes, prestaciones con duración |
| **Agenda** | Turnos, bloqueos, sobreturnos y la verificación de solapamiento con margen |
| **Pacientes** | Datos de contacto y preferencia de canal |
| **Notificaciones** | Recordatorios y confirmaciones por WhatsApp o email |
| **Clínica** | Ficha básica, odontograma y periodontograma (C-25) |
| **Coberturas** | Obras sociales y prepagas, sus planes, el nomenclador de prácticas y la afiliación de cada paciente (C-18); aranceles, cobertura por plan y presupuestos con copago (C-19) |
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
                    │              └─1──*─ PeriodontogramaVersion ─1──*─ PeriodontogramaPieza ─1──*─ PeriodontogramaSitio
                    ├──*─ Notificacion
                    └──*─ TurnoAuditoria

 Profesional ─1──*─ BloqueoAgenda
 Profesional ─1──*─ HorarioTrabajo
 Profesional ─1──*─ AusenciaProfesional
 Profesional ─*──*─ Prestacion   (vía ProfesionalPrestacion: especialidades)
 Profesional ─*──0..1─ Sillon    (sillón habitual)
 ObraSocial ─1──*─ Plan ─1──*─ AfiliacionPaciente ─*──1─ Paciente   (C-18)
 Nomenclador ─*──0..1─ Prestacion   (código de la práctica, C-18)
 Prestacion ─1──0..1─ Arancel   (precio particular, C-19)
 Plan ─1──*─ CoberturaPlan ─*──1─ Nomenclador   (porcentaje cubierto, C-19)
 Paciente ─1──*─ Presupuesto ─1──*─ PresupuestoItem ─*──1─ Prestacion   (C-19)
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

### PeriodontogramaVersion [2026-11-24, C-25]
- `id` BIGINT identity, `paciente_id` FK, `profesional_id` FK, `registrado_en` timestamptz.
- Una fila por cada vez que se guarda el periodontograma (RN-CL-04). No se actualiza ni se borra. Cuelga del paciente y no de la ficha, igual que el odontograma de C-13: no exige que exista la ficha básica.

### PeriodontogramaPieza [2026-11-24, C-25]
- `id` BIGINT identity, `version_id` FK, `pieza` smallint (FDI 11–48, solo permanentes), `ausente` boolean, `movilidad` smallint nullable (0–3), `furcacion` smallint nullable (0–3, solo piezas multirradiculares).
- Única por (`version_id`, `pieza`). Si `ausente`, `movilidad` y `furcacion` son nulas y la pieza no tiene sitios (RN-CL-05).

### PeriodontogramaSitio [2026-11-24, C-25]
- `id` BIGINT identity, `pieza_id` FK, `sitio` enum (`MV`, `V`, `DV`, `ML`, `L`, `DL`), `profundidad_sondaje` smallint (0–15), `recesion` smallint (−5–15), `sangrado` boolean, `placa` boolean.
- Única por (`pieza_id`, `sitio`). El estado periodontal no se guarda: se calcula al leer (RN-CL-06).

### ObraSocial [2026-11-24, C-18]
- `id` BIGINT identity, `nombre` varchar(120) (único, sin distinguir mayúsculas), `sigla` varchar(20) (nullable), `tipo` enum (`OBRA_SOCIAL`, `PREPAGA`), `activa` boolean.
- Es un catálogo como los de C-04: se da de alta, se edita y se activa o desactiva, pero no se borra (RN-OS-01).

### Plan [2026-11-24, C-18]
- `id` BIGINT identity, `obra_social_id` FK, `nombre` varchar(80), `activo` boolean.
- Constraint: `nombre` único dentro de la misma obra social, sin distinguir mayúsculas. Un plan pertenece a una sola obra social y no cambia de obra social.

### Nomenclador [2026-11-24, C-18]
- `id` BIGINT identity, `codigo` varchar(20) (único), `descripcion` varchar(200), `prestacion_id` FK nullable, `activo` boolean.
- Es la lista de **códigos de práctica** con los que se informa a las obras sociales. Cada código puede vincularse a una `Prestacion` del consultorio (RN-OS-05). Los porcentajes de cobertura y el copago por plan quedan para C-19.

### AfiliacionPaciente [2026-11-24, C-18]
- `id` BIGINT identity, `paciente_id` FK, `plan_id` FK, `numero_afiliado` varchar(30), `vigente_desde` date, `vigente_hasta` date (nullable: sin fecha de fin).
- Constraints:
  - `vigente_hasta` es nulo o `≥ vigente_desde`.
  - Un mismo paciente no tiene dos afiliaciones con vigencias superpuestas (RN-OS-02). Se garantiza con `EXCLUDE USING gist (paciente_id WITH =, daterange(vigente_desde, vigente_hasta, '[]') WITH &&)`, con `btree_gist`, igual que las garantías de la agenda.
  - `numero_afiliado` es único por obra social (RN-OS-03), validado en el dominio y en la base.
- No se borra. Una afiliación que termina se cierra con `vigente_hasta` y queda como historial (RN-OS-02).

### Arancel [2026-11-24, C-19]
- `id` BIGINT identity, `prestacion_id` FK (único), `precio` numeric(12,2), `actualizado_en` timestamptz.
- Es el **precio particular** de cada prestación, en pesos argentinos. Constraint: `precio > 0`.
- Hay un solo precio vigente por prestación. Cambiarlo no modifica los presupuestos ya emitidos, porque cada presupuesto guarda una copia de los valores (RN-PR-04).

### CoberturaPlan [2026-11-24, C-19]
- `id` BIGINT identity, `plan_id` FK, `nomenclador_id` FK, `porcentaje` smallint.
- Constraints: `porcentaje BETWEEN 0 AND 100`, y `UNIQUE (plan_id, nomenclador_id)`.
- Indica qué parte del precio particular cubre un plan para un código del nomenclador. Si no hay fila, el plan no cubre ese código (0 %).

### Presupuesto [2026-11-24, C-19]
- `id` BIGINT identity, `paciente_id` FK, `profesional_id` FK, `fecha` date, `vence_el` date (`fecha` + 30 días, RN-PR-05), `afiliacion_id` FK nullable, `estado` enum (`PENDIENTE`, `ACEPTADO`, `RECHAZADO`), `total_precio`, `total_cobertura` y `total_copago` numeric(12,2), `creado_en` timestamptz.
- `afiliacion_id` es la afiliación vigente del paciente en la `fecha` del presupuesto, o nulo si es particular (RN-OS-04).
- Constraints: `total_precio = total_cobertura + total_copago` y `vence_el = fecha + 30`.
- "Vencido" no es un estado guardado: un `PENDIENTE` con `vence_el` anterior a hoy (en `America/Argentina/Buenos_Aires`) se informa como vencido.
- No se borra. Su contenido no cambia después de emitirlo; solo cambia el estado (RN-PR-05).

### PresupuestoItem [2026-11-24, C-19]
- `id` BIGINT identity, `presupuesto_id` FK, `prestacion_id` FK, `nomenclador_id` FK nullable, `pieza` smallint nullable (FDI, como el odontograma), `cara` varchar(1) nullable, `cantidad` int, `precio_unitario` numeric(12,2), `porcentaje_cobertura` smallint, `importe`, `cobertura` y `copago` numeric(12,2).
- Constraints: `cantidad ≥ 1`, `porcentaje_cobertura BETWEEN 0 AND 100`, `importe = precio_unitario × cantidad`, `importe = cobertura + copago`, y la pieza y la cara con los mismos valores válidos que `OdontogramaRegistro` (RN-CL-03).
- Guarda una **copia** del precio y del porcentaje del momento en que se emitió (RN-PR-04).

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
| Coberturas (C-18) | Obras sociales ficticias: "Obra Social Ejemplo" (`OSE`, tipo obra social) con los planes "Plan Base" y "Plan Integral", y "Prepaga Prueba" (tipo prepaga) con el plan "Plan Oro". Nomenclador ficticio: `N-0101` Consulta (vinculado a Consulta o control), `N-0201` Limpieza (vinculado a Limpieza) y `N-0301` Extracción simple. Afiliaciones: un paciente ficticio con "Plan Base" vigente y el número `OSE-000001`, y otro sin cobertura (particular) |
| Aranceles y cobertura (C-19) | Precios particulares ficticios: Consulta o control $15.000, Limpieza $25.000, Tratamiento de conducto $90.000 y Extracción simple $30.000. Cobertura: "Plan Base" cubre `N-0101` al 100 % y `N-0201` al 50 %; "Plan Integral" cubre `N-0101` y `N-0201` al 100 % y `N-0301` al 70 %; "Plan Oro" cubre los tres códigos al 80 % |
| Pacientes | 3 pacientes ficticios con DNI de la forma `99000001` y emails `@example.com`; uno con cuenta de paciente (`paciente.demo`) |
