# Proposal

## Why

El consultorio no registra con qué obra social o prepaga se atiende cada paciente, y sin ese dato no se pueden armar los presupuestos con copago (C-19), las liquidaciones (C-20) ni la validación online (C-21). La KB del dominio "Coberturas" ya está aprobada por la líder el 2026-11-24 (PR #103 y #104: RN-OS-01 a RN-OS-07, US-046, DD-14 y las respuestas a PA-20 a PA-22), así que C-18 puede pasar a propuesta.

## What Changes

- **Catálogo de coberturas** (solo `ADMIN` escribe; el resto del personal consulta): obras sociales y prepagas (`OBRA_SOCIAL` o `PREPAGA`), sus planes y el nomenclador de prácticas cargado a mano (PA-22), con alta, edición y activación o desactivación. Nada se borra (RN-OS-01).
- **Afiliación del paciente** (`ADMIN` y `RECEPCIONISTA` escriben; `ODONTOLOGO` consulta): el paciente se asocia a un plan activo con su número de afiliado y una vigencia. Como máximo una afiliación vigente por fecha (RN-OS-02, DD-14); cambiar de cobertura es cerrar la actual y crear otra, con historial (PA-20). Número de afiliado único por obra social entre pacientes distintos (RN-OS-03). No se afilia a un plan ni a una obra social inactivos (RN-OS-06).
- **Cobertura vigente**: el personal consulta la cobertura de un paciente en una fecha (por defecto, hoy en `America/Argentina/Buenos_Aires`); sin afiliación vigente el paciente figura como **particular** (RN-OS-04).
- **Cobertura propia del paciente**: `GET /api/v1/paciente/cobertura` devuelve solo la del paciente de la sesión, de solo lectura (PA-21, RN-OS-07).
- Errores de negocio con código estable en `ProblemDetail` (`409 VIGENCIA_SUPERPUESTA`, `AFILIADO_DUPLICADO`, `PLAN_INACTIVO`, `OBRA_SOCIAL_INACTIVA`, `OBRA_SOCIAL_EXISTENTE`, `PLAN_EXISTENTE`, `CODIGO_NOMENCLADOR_EXISTENTE`, `AFILIACION_CERRADA`; `404 RECURSO_NO_ENCONTRADO`; `422 DATOS_INVALIDOS`, `VIGENCIA_INVALIDA`, `PRESTACION_INEXISTENTE`). Las dos garantías contra superposición de vigencias: validación en el dominio y `EXCLUDE USING gist` en PostgreSQL.
- **Migración V12** (`obra_social`, `plan`, `nomenclador`, `afiliacion_paciente`) y seed ficticio de desarrollo `V12_1__seed_coberturas_dev.sql`.
- **Solo backend** (decisión del usuario, 2026-11-24). Las pantallas (pestaña de Configuración, cobertura en el registro del paciente y "Mi cuenta") van en una ficha de frontend posterior, aparte, como C-25 con C-46.

**Fuera de alcance**: porcentajes de cobertura, topes y copago (C-19); liquidaciones (C-20); validación online con la obra social (C-21); varias coberturas vigentes a la vez (DD-14); importar el nomenclador.

## Capabilities

### New Capabilities
- `catalogo-coberturas`: obras sociales y prepagas, sus planes y el nomenclador de prácticas, con unicidad, activación o desactivación sin borrado y permisos por rol.
- `afiliaciones-pacientes`: afiliación del paciente a un plan con número de afiliado y vigencia, historial, cierre, cobertura vigente o particular, consulta del propio paciente y privacidad de la cobertura.

### Modified Capabilities
<!-- Ninguna: seguridad-autorizacion ya cubre /api/v1/paciente/** y /api/v1/** por rol; catalogo-consultorio y cuentas-pacientes no cambian. -->

## Impact

- **Código**: módulo nuevo `backend/.../coberturas/` (dominio, aplicación, infraestructura y API), de Juan Sebastián. No modifica otros módulos: solo referencia por FK las tablas `paciente` (V1) y `prestacion` (V1/V2, configuración de Emanuel).
- **Base de datos**: `V12__coberturas.sql` (número reservado en CHANGES.md; Flyway corre con out-of-order, así que se aplica aunque V16, V21 y V22 ya existan) y `db/seed/V12_1__seed_coberturas_dev.sql` (solo perfil dev).
- **API**: endpoints nuevos bajo `/api/v1/obras-sociales`, `/api/v1/nomenclador`, `/api/v1/pacientes/{id}/afiliaciones`, `/api/v1/pacientes/{id}/cobertura` y `/api/v1/paciente/cobertura`. Ninguno existente cambia.
- **Seguridad**: `SecurityConfig` no cambia; permisos por operación con `@PreAuthorize`. El paciente se identifica solo por la sesión.
- **Privacidad**: la cobertura no va a logs, mensajes ni notificaciones (RN-OS-07, regla dura 6); los errores no repiten el número de afiliado ni datos de otro paciente.
- **Agenda y notificaciones**: sin cambios (RN-OS-04).
