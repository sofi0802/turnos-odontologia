# Proposal: página de Configuración del consultorio (C-34)

## Why

En la prueba del 2026-11-23, la página "Configuración" del personal mostró solo su título: para cargar profesionales, sillones, prestaciones, horarios, ausencias o bloqueos hay que llamar a la API a mano. Los endpoints ya existen (C-04 y C-07), así que falta la pantalla que los use respetando la matriz RBAC.

En una segunda prueba del mismo día, ya con la página implementada (PR #60), Emanuel pidió seis ajustes:

1. Que un sillón no pueda ser habitual de dos profesionales.
2. Que el detalle del profesional tenga aire entre el selector de sillón y su botón.
3. Que el alta de un profesional permita elegir sus prestaciones y su sillón.
4. Que una franja horaria se pueda cargar para varios días de una vez, y que todos los minutos de la configuración se elijan de 5 en 5.
5. Que ausencias y bloqueos se puedan ver para todos los profesionales juntos.
6. Que los turnos afectados por un bloqueo se puedan reasignar, como los de una ausencia.

Los puntos 1 y 6 necesitan backend, así que C-34 deja de ser solo frontend.

## What Changes

- La página `#/configuracion` deja de ser un título y pasa a tener siete secciones en pestañas: **Profesionales**, **Sillones**, **Prestaciones**, **Margen**, **Horarios**, **Ausencias** y **Bloqueos**.
- **Catálogos** (profesionales, sillones y prestaciones): lista con su estado y, para `ADMIN`, alta, edición, activar y desactivar. Los errores de la API (`MATRICULA_DUPLICADA`, `NOMBRE_DUPLICADO`, `DURACION_INVALIDA` y los campos inválidos) se muestran en el formulario.
- **Especialidades y sillón habitual**: en el detalle de cada profesional, `ADMIN` elige qué prestaciones hace y cuál es su sillón habitual. El resto del personal los ve.
- **Margen entre turnos**: todos lo ven y `ADMIN` lo cambia (mínimo 10 minutos, `MARGEN_INVALIDO`).
- **Horarios de trabajo**: franjas semanales por profesional. `ADMIN` las edita y guarda el conjunto completo (`FRANJAS_SUPERPUESTAS`, `HORARIO_INVALIDO`); recepción las lee y el odontólogo ve solo las suyas.
- **Ausencias y licencias**: `ADMIN` y `RECEPCIONISTA` las registran y borran. Al registrar una, o al pedirlo después, la página lista los turnos que caen en el período y permite reasignar cada uno a otro profesional (RN-AG-16). El odontólogo ve solo las suyas.
- **Bloqueos de agenda**: `ADMIN` los registra y borra para cualquier profesional; el odontólogo, solo los propios. Recepción no ve la pestaña.
- **Profesional del odontólogo**: las pestañas de agenda usan el `profesionalId` que C-35 ya agregó a la sesión (login y `GET /api/v1/public/sesion`). El backend solo deja que un odontólogo opere sobre su propio profesional, así que la página no le ofrece otros.
- **Ajustes de la segunda prueba (2026-11-23):**
  - **Sillón habitual exclusivo (RN-RO-09):** el backend rechaza con `409 SILLON_HABITUAL_OCUPADO` un sillón que ya es habitual de otro profesional. Los selectores muestran esos sillones deshabilitados, con el nombre de quien lo tiene.
  - **Alta completa del profesional:** el formulario de alta suma las prestaciones que hace y el sillón habitual.
  - **Franjas para varios días:** "Agregar franja" tiene casillas de lunes a domingo y crea una franja por cada día marcado.
  - **Minutos de 5 en 5:** las horas de franjas, ausencias, bloqueos y reasignación se eligen con un selector de hora y otro de minutos (00, 05 … 55). El margen avanza de 5 en 5.
  - **"Todos los profesionales":** en Ausencias y Bloqueos, `ADMIN` y `RECEPCIONISTA` pueden ver los períodos de todos los profesionales activos, con una columna "Profesional".
  - **Reasignar turnos afectados por un bloqueo (RN-AG-19):** endpoint nuevo, para `ADMIN` y `RECEPCIONISTA`, con las mismas reglas que en las ausencias. La recepción pasa a **leer** los bloqueos (sigue sin poder registrarlos ni borrarlos) y ve la pestaña "Bloqueos".
  - **Espaciado:** el detalle del profesional separa cada campo de su botón.
- Mensajes en castellano para los códigos de configuración y de disponibilidad que el frontend todavía no traduce.

## Capabilities

### New Capabilities
- `frontend-configuracion-consultorio`: la página Configuración del personal, con sus secciones, los permisos de cada rol y los mensajes de error.

### Modified Capabilities
- `frontend-navegacion`: "Páginas disponibles" deja de decir que Configuración muestra solo su título.
- `catalogo-consultorio`: el sillón habitual pasa a ser exclusivo de un profesional.
- `disponibilidad-profesional`: la recepción puede consultar los bloqueos, y los turnos afectados por un bloqueo se pueden reasignar.

## Impact

- **Frontend** (`frontend/`, dueño Enzo; se le avisa en el PR):
  - `src/pages/configuracion.ts` se reemplaza;
  - nuevo módulo `src/configuracion/` con un archivo por sección;
  - `src/shared/ui/mensajes.ts` suma códigos;
  - `styles.css` agrega las clases que falten del design system.
  - Tests nuevos en `frontend/test/configuracion-*.test.ts`.
- **Backend** (módulos de Emanuel, de C-04 y C-07):
  - `configuracion/`: validación del sillón habitual exclusivo en `ProfesionalService`;
  - `agenda/disponibilidad/`: `GET` de bloqueos para `RECEPCIONISTA` y el endpoint `POST /profesionales/{id}/bloqueos/{bloqueoId}/turnos/{turnoId}/reasignar`, que reutiliza `ServicioReasignacion`.
  - **Sin migración:** la exclusividad la garantiza el dominio, no un índice (decisión de Emanuel, 2026-11-23).
  - El permiso de lectura de bloqueos para la recepción es un **cambio de autorización**, así que se le señala a la líder en el PR.
- **KB:**
  - `05_reglas_de_negocio.md`: RN-RO-09 y RN-AG-19;
  - `03_actores_y_roles.md`: la matriz RBAC de bloqueos;
  - `02_descripcion_general.md`: el endpoint nuevo.
- **Fuera de alcance**: el menú no cambia (Configuración ya está en el menú del personal).
- **Mockups**: visual, con el modo `system-and-mockups`. El apply espera la aprobación de la líder.
- **Actualización del 2026-11-23**: la primera versión de este proposal sumaba `profesionalId` a la sesión en el backend. C-35 lo implementó antes (PR #57), así que C-34 volvió a no tener cambios de backend, hasta los ajustes de la segunda prueba.
