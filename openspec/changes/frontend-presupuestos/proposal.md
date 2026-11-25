# Proposal

## Why

C-19 dejó en el backend los aranceles, la cobertura por plan y los presupuestos (RN-PR-01 a RN-PR-07, US-047), pero ninguna pantalla los usa. Sin frontend, el administrador no puede cargar precios ni coberturas, el odontólogo no puede presupuestar y el paciente no ve lo que tiene que pagar. Ahora que C-49 dejó las obras sociales, los planes y la cobertura del paciente en el frontend, C-50 completa el circuito.

## What Changes

- **Precios particulares** (solo `ADMIN`): en Configuración → "Prestaciones", cada prestación muestra su precio o "Sin precio" y suma "Cambiar precio" (`PUT /api/v1/aranceles/{prestacionId}`). No hay pestaña nueva. *(confirmada por la líder, 2026-11-25: ver design D2)*
- **Cobertura por plan** (solo `ADMIN`): en Configuración → "Obras sociales" (C-49), cada plan suma "Cobertura", un diálogo con los códigos activos del nomenclador y el porcentaje que cubre cada uno (`GET` y `PUT .../planes/{planId}/coberturas/{nomencladorId}`). Dejar de cubrir un código es cargar 0 %.
- **Sección "Presupuestos" en la ficha** (`#/pacientes`), debajo de "Cobertura":
  - el odontólogo arma un presupuesto con las piezas sugeridas del odontograma (`/sugerencias`), elige la prestación, el código si hace falta, la pieza, la cara y la cantidad, y ve un **estimado** de importe, cobertura y copago antes de emitir *(confirmada por la líder, 2026-11-25: ver design D4)*;
  - lista los presupuestos del paciente con estado ("Pendiente", "Vencido", "Aceptado", "Rechazado"), cobertura usada o "Particular", ítems y totales;
  - el odontólogo acepta o rechaza un pendiente, con confirmación; uno vencido solo se puede rechazar;
  - el `ADMIN` solo lee.
- **Presupuestos en la página "Coberturas"** (C-49): la recepción y el administrador ven los presupuestos del paciente elegido en solo lectura. La API no le manda pieza ni cara a la recepción (PA-24). *(confirmada por la líder, 2026-11-25: ver design D3)*
- **"Mis presupuestos" del paciente** en "Mi cuenta", debajo de "Mi cobertura", en solo lectura (PA-27), sobre `GET /api/v1/paciente/presupuestos`.
- Mensajes para `ARANCEL_INEXISTENTE`, `PRESUPUESTO_VENCIDO`, `PRESUPUESTO_CERRADO`, `CODIGO_NOMENCLADOR_REQUERIDO` y `CODIGO_NOMENCLADOR_INVALIDO`. `PRESTACION_INACTIVA` y `PRESTACION_INEXISTENTE` ya tienen el suyo.
- Ninguna pieza, cara, importe ni dato de cobertura en la dirección, en `localStorage`, en `sessionStorage` ni en la consola (reglas duras 1 y 6, RN-PR-07).
- Sin cambios de backend ni migraciones.

## Capabilities

### New Capabilities

- `frontend-aranceles`: pantallas del administrador para el precio particular de cada prestación y el porcentaje que cubre cada plan por código del nomenclador.
- `frontend-presupuestos`: armado, emisión, consulta, aceptación y rechazo de presupuestos en la ficha, su consulta en "Coberturas" y "Mis presupuestos" del paciente.

### Modified Capabilities

Ninguna. La ficha, "Coberturas" y "Mi cuenta" suman secciones nuevas sin cambiar sus requisitos vigentes; esas secciones se especifican en `frontend-presupuestos`. Los requisitos de C-49 (`frontend-coberturas`, y los deltas de `frontend-configuracion-consultorio`, `frontend-cuenta-paciente` y `frontend-navegacion`) todavía no están archivados, así que este change no los modifica (design D1).

## Impact

- **Frontend** (`frontend/`, dueño Enzo):
  - módulos nuevos `src/presupuestos/` (`api.ts`, `modelo.ts`, `seccion.ts`, `armado.ts`, `mis-presupuestos.ts`) y `src/aranceles/` (`api.ts`, `precios.ts`, `cobertura-plan.ts`);
  - cambios en `configuracion/catalogo.ts` (Prestaciones), `coberturas/catalogo.ts` (botón "Cobertura" de cada plan), `pages/pacientes.ts` (ficha, módulo de Emanuel), `pages/coberturas.ts`, `pages/mi-cuenta.ts`, `shared/ui/mensajes.ts` y un módulo de estilos nuevo.
- **API usada** (C-19, sin cambios): `/api/v1/aranceles`, `/api/v1/obras-sociales/{id}/planes/{planId}/coberturas`, `/api/v1/pacientes/{id}/presupuestos` (con `/sugerencias`, `/aceptar` y `/rechazar`), `/api/v1/paciente/presupuestos`, `/api/v1/nomenclador`, `/api/v1/prestaciones` y `/api/v1/pacientes/{id}/cobertura`.
- **Dependencias**: C-19 (backend, completo) y C-49 (mergeado en el PR #138).
- **Governance**: ALTO (datos de salud y dinero). La propuesta y los mockups los aprueba la líder antes del apply.
- **Riesgos**:
  - el estimado repite en el frontend la regla de cálculo de RN-PR-03 (D4);
  - C-51 (liquidaciones) toca los mismos archivos de Configuración y estilos, así que C-50 y C-51 van en un PR por vez.
