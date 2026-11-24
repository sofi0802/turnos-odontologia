# Proposal: reportes-y-metricas (C-32)

## Why

El consultorio no tiene forma de ver cifras de su propia operación: cuánto se usa la agenda, cuántos pacientes reservan solos, cuántos turnos se pierden por ausencias o cancelaciones. Dos de las métricas de éxito de la KB (`01_vision_y_objetivos.md`: turnos online y ausentismo) piden "medir la línea base" y hoy no se pueden medir. La KB del dominio Reportes (RN-RE-01 a RN-RE-07, US-210, DD-16) ya está aprobada (PR #111).

## What Changes

- **API de reportes**: `GET /api/v1/reportes?desde=&hasta=&profesionalId=` devuelve, para un período de hasta 92 días (RN-RE-03), cuatro bloques de cifras agregadas:
  - **ocupación** por profesional y por sillón, en minutos ocupados, minutos de atención y porcentaje (RN-RE-04);
  - **origen** de los turnos: online, del consultorio y sin dato (RN-RE-05);
  - **ausencias** de pacientes con la tasa de ausentismo y los turnos sin registrar, y ausencias y licencias de profesionales (RN-RE-06);
  - **cancelaciones** por el paciente, por el consultorio y por reprogramación (RN-RE-07).
- Solo `ADMIN` y `RECEPCIONISTA` (RN-RE-01). El `ODONTOLOGO` y el `PACIENTE` reciben `403`. La respuesta no tiene nombres, DNI ni datos de salud de pacientes (RN-RE-02).
- **Pantalla "Reportes"** en `#/reportes`, con su opción en la barra lateral para el `ADMIN` y la `RECEPCIONISTA`: período (por defecto, la semana en curso), filtro por profesional y los cuatro bloques con barras de porcentaje que no dependen solo del color.
- El menú del personal suma "Reportes"; por eso cambia el requisito que dice que la recepción ve solo "Agenda" y "Configuración".
- Sin migraciones: el origen y quién canceló salen de `turno_auditoria` (DD-16). El módulo nuevo solo lee tablas de otros módulos.

## Capabilities

### New Capabilities

- `reportes`: la API de reportes del consultorio: período, permisos, privacidad y el cálculo de ocupación, origen, ausencias y cancelaciones.
- `frontend-reportes`: la pantalla "Reportes": período, filtro, bloques, estados vacíos y errores.

### Modified Capabilities

- `frontend-navegacion`: "Menú según la sesión" suma "Reportes" para el admin y la recepción, y se agrega la página "Reportes" con su acceso por rol.

## Impact

- **Backend**: módulo nuevo `backend/.../reportes/` (`api`, `application`, `domain`, `infrastructure`). Lectura por JDBC de `turno`, `turno_auditoria`, `horario_trabajo`, `ausencia_profesional`, `bloqueo_agenda`, `profesional` y `sillon`, sin escribir ni cambiar sus mapeos. Permisos con `@PreAuthorize`; no cambia `SecurityConfig`. El módulo `reportes/` es de Enzo (tabla de dueños de CHANGES.md).
- **Avisos en el PR**: Sofia (`agenda/`) y Emanuel (horarios, ausencias y bloqueos de C-07), porque se leen sus tablas y se depende del formato del actor online de la auditoría (DD-16); Juan Sebastián (`reserva/`), dueño de `ActorOnline`.
- **Frontend**: `frontend/src/pages/reportes.ts` y `frontend/src/reportes/` nuevos, un ícono nuevo en la barra lateral y estilos nuevos. Cambian los tests del menú que esperan solo "Agenda" y "Configuración" para la recepción.
- **Visual**: modo `system-and-mockups`; mockups de la pantalla a 390 y 1440 px aprobados antes del apply.
- **Fuera de alcance**: exportar a CSV o PDF, gráficos de evolución en el tiempo, reportes del odontólogo sobre sí mismo y métricas de cobros o coberturas (C-22, C-20).
