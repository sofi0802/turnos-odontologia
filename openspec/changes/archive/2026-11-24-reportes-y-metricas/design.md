# Design: reportes-y-metricas (C-32)

## Context

- Las reglas están en la KB aprobada (PR #111): RN-RE-01 a RN-RE-07, US-210 y DD-16. Ver proposal.md (Why).
- Datos disponibles, sin migración:
  - `turno` (`profesional_id`, `sillon_id`, `inicio`, `fin`, `estado`, `es_sobreturno`).
  - `turno_auditoria` (`turno_id`, `accion`, `actor`, `fecha`, `turno_relacionado_id`). La reserva online usa el actor `paciente-online:<id>` (`reserva/application/ActorOnline`). Una reprogramación registra `REPROGRAMAR` en el turno original y `CREAR` en el nuevo, con el mismo actor. Un sobreturno registra `SOBRETURNO` en lugar de `CREAR`.
  - `horario_trabajo` (`profesional_id`, `dia_semana` 1 a 7, `hora_desde`, `hora_hasta`), `ausencia_profesional` (`tipo`, `inicio`, `fin`), `bloqueo_agenda` (`inicio`, `fin`), `profesional` (`sillon_habitual_id`, `activo`) y `sillon`.
- El backend ya tiene los beans `ZoneId zonaHoraria` y `Clock reloj` (`agenda/infrastructure/AgendaConfig`), y los tests de integración reemplazan el reloj por uno fijo. La seguridad usa `@EnableMethodSecurity` y `/api/v1/**` está abierto al personal: cada módulo restringe con `@PreAuthorize`, como `coberturas/`.
- Cada módulo mapea sus errores con su propio `@RestControllerAdvice(basePackages = ...)` y arma el `422 DATOS_INVALIDOS` con la lista `errores` de `{campo, mensaje}` (ver `AgendaExceptionHandler`).
- En el frontend, `Pagina.roles` (C-44) limita una página a ciertos roles, pero `app.ts` muestra siempre el aviso "solo para odontólogos y administradores".

## Goals / Non-Goals

**Goals:**
- Calcular las cifras en código de dominio puro, que se pueda probar con unitarios sin base, y dejar en la base solo la lectura de filas.
- No escribir ni mapear con JPA tablas de otros módulos: solo lectura por JDBC.

**Non-Goals:**
- Cachear o precalcular los reportes. Un consultorio chico tiene pocos cientos de turnos en 92 días.
- Exportar, graficar la evolución en el tiempo o mostrar reportes al odontólogo (proposal, fuera de alcance).

## Decisions

### D1. Módulo `reportes/` en capas, como `coberturas/`
- `reportes/domain`: `Periodo` (valida desde y hasta y el tope de 92 días), `Intervalos` (unión y resta de intervalos `[inicio, fin)` en `Instant`) y `CalculoReportes`, que arma los cuatro bloques a partir de filas ya leídas.
- `reportes/application`: `ConsultaReportes` (caso de uso) y el puerto `LecturaReportes`, que devuelve los turnos, sus registros de auditoría, los horarios, las ausencias, los bloqueos, los profesionales y los sillones del período.
- `reportes/infrastructure`: `LecturaReportesJdbc` con `JdbcTemplate`, y `ReportesConfig`.
- `reportes/api`: `ReportesController`, los records de la respuesta y `ReportesExceptionHandler`.

*Alternativa descartada*: calcular todo en SQL con `generate_series`. Es más corto, pero la resta de ausencias y bloqueos y el redondeo quedan difíciles de leer y de probar sin base.

### D2. Lectura por período ampliado
`LecturaReportesJdbc` lee los turnos con `inicio` en `[desde 00:00, hasta + 1 día 00:00)` en la zona del consultorio, con `profesionalId` opcional. En la misma consulta trae:
- el actor del primer registro `CREAR` o `SOBRETURNO`;
- si existe un registro `REPROGRAMAR`;
- el actor del último `CANCELAR`.

Las ausencias y los bloqueos se leen si se superponen con el período. Los horarios, los profesionales y los sillones se leen completos, porque son pocos.

### D3. Minutos de atención
Para cada profesional, `CalculoReportes` recorre los días del período en la zona del consultorio y, por cada `horario_trabajo` de ese día de la semana, arma el intervalo de esa franja. A la unión de esas franjas le resta la unión de sus ausencias, licencias y bloqueos, recortados al período, y suma los minutos que quedan. Los minutos ocupados son la suma de `fin − inicio` de los turnos no cancelados, sin recortar al período. Un turno cuenta entero en el período de su inicio (RN-RE-03).

### D4. Redondeo y porcentaje
Un `porcentaje(parte, total)` con `BigDecimal` y `RoundingMode.HALF_UP` sirve para la ocupación y para la tasa de ausentismo. Con `total` 0 devuelve `null` (JSON `null`).

### D5. Origen, cancelaciones y "sin dato"
- **Origen**: un turno cancelado por una reprogramación no se cuenta, porque el turno nuevo lo reemplaza. Los demás se clasifican por el actor de su creación: si empieza con `paciente-online:` es `online`, otro actor es `consultorio`, y sin registro de creación es `sinDato`. El prefijo es una constante de `reportes/domain` con un comentario que apunta a `ActorOnline` y a DD-16.
- **Cancelaciones**: un turno `CANCELADO` se cuenta en `porReprogramacion` si tiene `REPROGRAMAR`. Si no, por el actor de `CANCELAR`. Sin ningún registro va a `sinDato`.
- `sinDato` en las cancelaciones no figura en RN-RE-07. Se suma por coherencia con el origen, para no inventar un actor en turnos cargados sin auditoría (por ejemplo, un seed). La pantalla lo oculta cuando vale 0.

### D6. "Sin registrar" con el reloj inyectado
`sinRegistrar` cuenta los turnos `RESERVADO` o `CONFIRMADO` con `fin` anterior a `Instant.now(reloj)`. Se usa el bean `Clock` existente, así los tests de integración lo fijan.

### D7. Sillones con filtro de profesional
Con `profesionalId`, la lista de sillones va vacía (spec `reportes`, "Ocupación por sillón"). Calcular la ocupación de un sillón compartido con los turnos de un solo profesional daría un porcentaje engañoso.

### D8. Permisos y errores
- `@PreAuthorize("hasAnyRole('ADMIN','RECEPCIONISTA')")` en el controlador. El `401` y el `403` los arman los handlers de `shared/security` que ya existen. No cambia `SecurityConfig`.
- `ReportesExceptionHandler`, limitado al paquete `reportes`, responde `422 DATOS_INVALIDOS` en estos casos:
  - falta un parámetro (`MissingServletRequestParameterException`);
  - una fecha no se puede leer (`MethodArgumentTypeMismatchException`);
  - el período es inválido (`PeriodoInvalidoException` de `Periodo`, con el campo `hasta`);
  - el profesional no existe (campo `profesionalId`).

### D9. Respuesta
```json
{
  "desde": "2026-12-07", "hasta": "2026-12-13", "profesionalId": null,
  "ocupacion": {
    "profesionales": [{ "profesionalId": 1, "nombre": "Ana Ejemplo", "minutosOcupados": 90, "minutosAtencion": 1200, "porcentaje": 8 }],
    "sillones": [{ "sillonId": 1, "nombre": "Sillón 1", "minutosOcupados": 120, "minutosAtencion": 1200, "porcentaje": 10 }]
  },
  "origen": { "online": 3, "consultorio": 3, "sinDato": 0 },
  "ausencias": {
    "pacientes": { "ausentes": 2, "atendidos": 6, "tasa": 25, "sinRegistrar": 1 },
    "profesionales": [{ "profesionalId": 2, "nombre": "Bruno Prueba", "tipo": "LICENCIA", "cantidad": 1, "minutos": 1440 }]
  },
  "cancelaciones": { "porPaciente": 2, "porConsultorio": 1, "porReprogramacion": 1, "sinDato": 0 }
}
```
Son records de `reportes/api`, nunca entidades. No hay ningún campo de paciente (RN-RE-02). Los profesionales se ordenan por apellido y nombre, y los sillones por nombre.

### D10. Frontend
- **Página**: `pages/reportes.ts` (`ruta: 'reportes'`, `acceso: 'personal'`, `roles: ['ADMIN', 'RECEPCIONISTA']`, `menu: 'personal'`, `fondoSuave`, ícono nuevo `'reportes'`). Se registra entre "Pacientes" y "Configuración".
- **Aviso por rol**: `Pagina` suma `avisoSinRol?: string`, y `app.ts` muestra ese texto, o el de C-44 si la página no lo define. Reportes usa "Esta sección es solo para administración y recepción.".
- **Módulo `frontend/src/reportes/`**:
  - `consulta.ts` lee la respuesta sin `any`, validando cada campo como `agenda/consulta.ts`.
  - `periodo.ts` propone la semana en curso (lunes a domingo, con `lunesDeLaSemana` y `sumarDias` de `shared/fecha/zona.ts`) y valida el período antes de pedir.
  - `vista.ts` dibuja los bloques.
- **Campos**: "Desde" y "Hasta" con `usarSelectorFecha`, como Reprogramar. "Profesional" se llena con el catálogo `GET /profesionales` (activos).
- **Barras**: elementos con `role="meter"`, `aria-valuemin` 0, `aria-valuemax` igual al mayor entre 100 y el valor, y `aria-valuenow`. El ancho visual se limita al 100 %. El porcentaje siempre se ve como texto.
- **Números**: `Intl.NumberFormat('es-AR')` para los miles ("1.200"). Los minutos de ausencias de profesionales se muestran en horas, redondeadas a un decimal si hace falta ("24 h", "1,5 h").
- **Errores**: `422` marca el campo informado ("Hasta" o "Profesional"). Otros errores van al aviso general con `mensajeDeError`, y la sesión vencida lleva a Mi cuenta.

### D11. Tests
- **Unitarios del dominio** (sin base): `Periodo`, `Intervalos` y `CalculoReportes`, con un caso por escenario de cálculo.
- **Integración** (`@Tag("integration")`, PostgreSQL real): el endpoint con los escenarios de período, permisos, privacidad y lectura de la auditoría, sobre datos ficticios cargados por el test.
- **Frontend**: Vitest con el `ServidorFalso` de `test/apoyo.ts`, un `it` por escenario de `frontend-reportes` y de `frontend-navegacion`.

## Risks / Trade-offs

- [El formato del actor online cambia en un change futuro] → El prefijo está en una sola constante, con un test de integración que crea un turno por la reserva online real y verifica que cuenta como `online`.
- [Un turno largo que cruza el fin del período suma minutos ocupados que caen fuera de él] → Se acepta por RN-RE-03 (el turno cuenta en el período de su inicio). Los turnos duran minutos, no días.
- [Los tests del menú que esperan solo "Agenda" y "Configuración" para la recepción fallan] → Cambian por el requisito MODIFIED de `frontend-navegacion`, no por debilitarlos: se actualizan al menú nuevo en el mismo PR.
- [Un consultorio con muchos turnos en 92 días] → Lectura en una consulta por tipo de dato y cálculo en memoria. Si alguna vez hace falta, se puede sumar un índice o agregar en SQL sin cambiar el contrato.

## Migration Plan

Sin migraciones. Se despliega con el backend y el build del frontend. Para volver atrás, se revierte el PR: el módulo no escribe datos.

## UI Mockups

- Design system revision: 5 (aprobada el 2026-11-24 por Sofia Indovino)
- Decision: yes (2026-11-24, política `system-and-mockups`)
- Mockups: `openspec/changes/reportes-y-metricas/mockups/` (ver su README)
- Approval: approved (2026-11-24, by user: Enzo aprobó los 5 mockups de la revisión 1)
- Preview: rendered (2026-11-24, 5 pantallas en `mockups/previews/` a 390x844 y 1440x900, sin desplazamiento horizontal)
- Visual verify: manual (2026-11-24, Enzo verificó la pantalla Reportes en la app real con el backend de desarrollo y confirmó que está bien; sin capturas automatizadas, porque el panel del navegador del agente no tenía sesión, así que `mockups/verify/` queda vacía)
