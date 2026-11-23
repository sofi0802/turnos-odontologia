# Proposal: página de Configuración del consultorio (C-34)

## Why

En la prueba del 2026-11-23, la página "Configuración" del personal mostró solo su título: para cargar profesionales, sillones, prestaciones, horarios, ausencias o bloqueos hay que llamar a la API a mano. Los endpoints ya existen (C-04 y C-07), así que falta la pantalla que los use respetando la matriz RBAC.

## What Changes

- La página `#/configuracion` deja de ser un título y pasa a tener siete secciones en pestañas: **Profesionales**, **Sillones**, **Prestaciones**, **Margen**, **Horarios**, **Ausencias** y **Bloqueos**.
- **Catálogos** (profesionales, sillones y prestaciones): lista con su estado y, para `ADMIN`, alta, edición, activar y desactivar. Los errores de la API (`MATRICULA_DUPLICADA`, `NOMBRE_DUPLICADO`, `DURACION_INVALIDA` y los campos inválidos) se muestran en el formulario.
- **Especialidades y sillón habitual**: en el detalle de cada profesional, `ADMIN` elige qué prestaciones hace y cuál es su sillón habitual. El resto del personal los ve.
- **Margen entre turnos**: todos lo ven y `ADMIN` lo cambia (mínimo 10 minutos, `MARGEN_INVALIDO`).
- **Horarios de trabajo**: franjas semanales por profesional. `ADMIN` las edita y guarda el conjunto completo (`FRANJAS_SUPERPUESTAS`, `HORARIO_INVALIDO`); recepción las lee y el odontólogo ve solo las suyas.
- **Ausencias y licencias**: `ADMIN` y `RECEPCIONISTA` las registran y borran. Al registrar una, o al pedirlo después, la página lista los turnos que caen en el período y permite reasignar cada uno a otro profesional (RN-AG-16). El odontólogo ve solo las suyas.
- **Bloqueos de agenda**: `ADMIN` los registra y borra para cualquier profesional; el odontólogo, solo los propios. Recepción no ve la pestaña.
- **Profesional del odontólogo**: las pestañas de agenda usan el `profesionalId` que C-35 ya agregó a la sesión (login y `GET /api/v1/public/sesion`). El backend solo deja que un odontólogo opere sobre su propio profesional, así que la página no le ofrece otros.
- Mensajes en castellano para los códigos de configuración y de disponibilidad que el frontend todavía no traduce.

## Capabilities

### New Capabilities
- `frontend-configuracion-consultorio`: la página Configuración del personal, con sus secciones, los permisos de cada rol y los mensajes de error.

### Modified Capabilities
- `frontend-navegacion`: "Páginas disponibles" deja de decir que Configuración muestra solo su título.

## Impact

- **Frontend** (`frontend/`, dueño Enzo; se le avisa en el PR):
  - `src/pages/configuracion.ts` se reemplaza;
  - nuevo módulo `src/configuracion/` con un archivo por sección;
  - `src/shared/ui/mensajes.ts` suma códigos;
  - `styles.css` agrega las clases que falten del design system.
  - Tests nuevos en `frontend/test/configuracion-*.test.ts`.
- **Backend**: sin cambios. Usa los endpoints de C-04 y C-07, y el `profesionalId` de la sesión que agregó C-35.
- **Fuera de alcance**: el menú no cambia (Configuración ya está en el menú del personal).
- **Mockups**: visual, con el modo `system-and-mockups`. El apply espera la aprobación de la líder.
- **Actualización del 2026-11-23**: la primera versión de este proposal sumaba `profesionalId` a la sesión en el backend. C-35 lo implementó antes (PR #57), así que C-34 vuelve a no tener cambios de backend.
