# Proposal

## Why

C-18 dejó en el backend el catálogo de obras sociales, planes y nomenclador, y las afiliaciones de los pacientes, pero ninguna pantalla lo usa: el administrador no puede cargar el catálogo, la recepción no puede afiliar a un paciente y nadie ve con qué cobertura se atiende cada uno. C-49 suma esas pantallas, que además necesitan C-50 (presupuestos) y C-51 (liquidaciones).

## What Changes

- **Configuración → pestaña "Obras sociales"** (solo `ADMIN`):
  - las obras sociales y prepagas, ordenadas por nombre, cada una con su tipo, sigla, estado y sus planes;
  - el nomenclador, ordenado por código, con la descripción, la prestación vinculada y el estado;
  - alta, edición, activar y desactivar de obras sociales, planes y códigos, sin borrar nada;
  - lista vacía, carga, error con "Reintentar" y los errores de la API en cada diálogo.
- **Sección "Cobertura" del paciente**, un solo componente que se usa en dos lugares:
  - en la ficha de `#/pacientes` (C-44), arriba del odontograma: el `ADMIN` la edita y el odontólogo solo la lee;
  - en una página nueva **"Coberturas"** (`#/coberturas`) para `ADMIN` y `RECEPCIONISTA`, con el buscador de pacientes y solo la cobertura, sin ningún dato clínico (RN-CL-01). *(confirmada por la líder, 2026-11-24: ver design D2)*
  - Muestra la cobertura vigente hoy (obra social, plan, número y vigencia) o "Particular", y el historial de afiliaciones (vigente, próxima o finalizada).
  - Afiliar, corregir (plan, número y "desde") y cerrar una afiliación, con validación previa del número y de las fechas, y los errores `VIGENCIA_SUPERPUESTA`, `AFILIADO_DUPLICADO`, `PLAN_INACTIVO`, `OBRA_SOCIAL_INACTIVA`, `VIGENCIA_INVALIDA` y `AFILIACION_CERRADA`.
  - Se carga aparte del resto de la ficha, con su propio error y "Reintentar".
- **Mi cuenta del paciente con sesión** (`#/mi-cuenta`): deja de llevar a "Mis turnos" y muestra "Mi cobertura", solo lectura, sobre `GET /api/v1/paciente/cobertura` (PA-21). El menú del paciente suma "Mi cuenta". *(confirmada por la líder, 2026-11-24: ver design D3)*
- **Mensajes nuevos** en `MENSAJES_POR_CODIGO` para los códigos de coberturas.
- **Privacidad**: la obra social, el plan y el número de afiliado no van en la dirección, el almacenamiento del navegador ni la consola (RN-OS-07, reglas duras 1 y 6).
- **Design system**: se reusan los tokens de la Revision 5, sin tokens nuevos. Dos íconos nuevos: `credencial` ("Coberturas" y la tarjeta de la cobertura) y `cuenta` ("Mi cuenta" del paciente).
- Sin cambios de backend ni migraciones.

## Capabilities

### New Capabilities
- `frontend-coberturas`: las pantallas de coberturas del frontend. Cubre:
  - el catálogo de obras sociales, planes y nomenclador en Configuración;
  - la sección "Cobertura" del paciente (vigente o particular, historial, afiliar, corregir y cerrar) en la ficha y en la página "Coberturas";
  - "Mi cobertura" del paciente;
  - los permisos por rol, los errores, la accesibilidad y la privacidad.

### Modified Capabilities
- `frontend-configuracion-consultorio`: "Secciones según el rol" suma la pestaña "Obras sociales" para `ADMIN`.
- `frontend-navegacion`: dos requisitos nuevos, la página "Coberturas" para administración y recepción, y "Mi cuenta" en el menú del paciente.
- `frontend-cuenta-paciente`: "Cuenta del paciente con sesión iniciada" muestra "Mi cuenta" con "Mi cobertura" en lugar de llevar a "Mis turnos".

<!-- "Menú según la sesión" de frontend-navegacion no se modifica: ya tiene deltas abiertos en caja-y-cobros (C-22) y frontend-uniformidad-visual. Sus listas de enlaces se reconcilian al archivar (design D9). frontend-ficha-clinica tampoco: C-46 tiene su delta abierto; la cobertura en la ficha va como requisito propio de frontend-coberturas. -->

## Impact

- **Código** (en el apply, no en este propose):
  - `frontend/src/coberturas/`: módulos nuevos de pedidos, modelo puro, catálogo y sección de cobertura;
  - `frontend/src/pages/configuracion.ts` y `configuracion/permisos.ts`: la pestaña nueva;
  - `frontend/src/pages/pacientes.ts`: monta la sección de cobertura en la ficha;
  - `frontend/src/pages/coberturas.ts` (nueva) y `app.ts`: la página "Coberturas";
  - `frontend/src/pages/mi-cuenta.ts` y `shared/ui/layout.ts`: "Mi cuenta" con sesión de paciente;
  - `frontend/src/shared/ui/mensajes.ts` e `iconos.ts`;
  - un módulo de estilos nuevo, `frontend/src/styles/22-coberturas.css`.
- **Avisos**: `frontend/` es de Enzo; la ficha (`pacientes.ts`, C-44) y Configuración (C-34) los tocó Emanuel. Se les avisa antes del apply y revisan el PR.
- **API**: usa sin cambios `/api/v1/obras-sociales`, `/{id}/planes`, `/api/v1/nomenclador`, `/api/v1/prestaciones`, `/api/v1/pacientes?q=`, `/api/v1/pacientes/{id}/afiliaciones`, `/cobertura` y `/api/v1/paciente/cobertura`.
- **Tests**: Vitest con jsdom, un test por escenario. Los tests existentes de Configuración, la ficha, la navegación y la cuenta del paciente se ajustan solo donde los escenarios cambian.
