# Proposal

## Why

El consultorio ya registra la obra social o prepaga de cada paciente (C-18), pero no puede decirle cuánto le cubre su plan y cuánto paga él por un tratamiento. La KB del dominio "Presupuestos" quedó aprobada por la líder el 2026-11-24 (PR #108: RN-PR-01 a RN-PR-07, US-047, DD-15 y las respuestas a PA-23 a PA-28), así que C-19 pasa a propuesta. Es además la base de las liquidaciones (C-20), que necesitan los importes de cada ítem.

## What Changes

- **Aranceles** (RN-PR-01, PA-28): el `ADMIN` carga y cambia el **precio particular** de cada prestación, en pesos, mayor que cero y con dos decimales; un solo precio vigente, sin historial y sin borrado. El resto del personal lo consulta.
- **Cobertura por plan** (RN-PR-02, PA-25): el `ADMIN` carga, para cada plan y cada código del nomenclador, el **porcentaje** que cubre el plan (entero de 0 a 100). Sin porcentaje cargado, el plan no cubre ese código. Sin borrado: dejar de cubrir es poner `0`.
- **Presupuestos** (RN-PR-03 a RN-PR-06, PA-23): el **odontólogo** de la sesión emite el presupuesto de un paciente con uno o más ítems (prestación, pieza y cara FDI opcionales, cantidad y, si hace falta, el código del nomenclador). El sistema calcula por ítem el importe, la cobertura (redondeo *half up* a centavos) y el copago según la afiliación vigente en la fecha del presupuesto, o todo como copago si el paciente es **particular** (RN-OS-04).
- **Foto del cálculo** (DD-15): cada presupuesto guarda el precio, el porcentaje, los importes, la afiliación usada y los nombres que se mostraron; no se recalcula, no se edita y no se borra.
- **Estados y vencimiento** (RN-PR-05, PA-26): `PENDIENTE` → `ACEPTADO` o `RECHAZADO`; vence a los 30 días; "vencido" se calcula (un pendiente vencido no se acepta, sí se rechaza).
- **Sugerencias desde el odontograma** (RN-PR-06): el odontólogo pide las piezas y caras cuyo último estado es `A_TRATAR` o `CARIES`, leídas con un puerto de solo lectura sin tocar `clinica/`.
- **Consulta por rol** (matriz de 03, PA-24, PA-27): `ADMIN` y `ODONTOLOGO` ven los presupuestos completos; la `RECEPCIONISTA` los ve **sin pieza ni cara**; el paciente ve **solo los suyos** en `/api/v1/paciente/presupuestos`.
- Errores de negocio con código estable en `ProblemDetail` (`409 ARANCEL_INEXISTENTE`, `PRESTACION_INACTIVA`, `PRESUPUESTO_VENCIDO`, `PRESUPUESTO_CERRADO`; `404 RECURSO_NO_ENCONTRADO`; `422 DATOS_INVALIDOS`, `PRESTACION_INEXISTENTE`, `CODIGO_NOMENCLADOR_REQUERIDO`, `CODIGO_NOMENCLADOR_INVALIDO`). Las piezas, las caras y los importes no van a logs, mensajes ni errores (RN-PR-07).
- **Migración V13** (`arancel`, `cobertura_plan`, `presupuesto`, `presupuesto_item`, con los `CHECK` de totales, vencimiento, cálculo y notación FDI) y seed ficticio de desarrollo `V13_1__seed_presupuestos_dev.sql`.
- **Solo backend** (decisión del usuario, 2026-11-24), como C-18: las pantallas (aranceles en Configuración, presupuesto en la ficha y "Mi cuenta") van en una ficha de frontend posterior.

**Fuera de alcance**: cobros, caja y cuenta corriente (C-22, Enzo); liquidaciones (C-20); topes, autorizaciones y validación online con la obra social (C-21); historial de aranceles (PA-28); cobertura por monto fijo (PA-25); descuentos; editar o borrar un presupuesto; avisos al paciente por WhatsApp o email.

## Capabilities

### New Capabilities
- `aranceles-y-cobertura-planes`: precio particular por prestación y porcentaje de cobertura por plan y código del nomenclador, sin borrado, con validación y permisos por rol.
- `presupuestos`: emisión del presupuesto por el odontólogo con cálculo de cobertura y copago, foto del cálculo, estados y vencimiento, sugerencias desde el odontograma, consulta por rol (recepción sin pieza ni cara, paciente solo los suyos) y privacidad.

### Modified Capabilities
<!-- Ninguna: catalogo-coberturas y afiliaciones-pacientes no cambian sus requisitos (C-19 solo lee el nomenclador y la afiliación vigente); ficha-clinica no cambia (el odontograma se lee sin modificarlo); seguridad-autorizacion ya cubre /api/v1/paciente/** y /api/v1/** por rol. -->

## Impact

- **Código**: se amplía el módulo `backend/.../coberturas/` (dueño: Juan Sebastián) con dominio, aplicación, infraestructura y API de aranceles, cobertura por plan y presupuestos. No modifica otros módulos: lee `prestacion` (configuración, Emanuel), `profesional` y `odontograma_registro` (clínica, Emanuel) por FK o por puertos JDBC de solo lectura.
- **Base de datos**: `V13__presupuestos.sql` (número reservado para C-19 en CHANGES.md; Flyway corre con out-of-order) y `db/seed/V13_1__seed_presupuestos_dev.sql` (solo perfil dev). V13 agrega una restricción única a `afiliacion_paciente` (tabla de C-18, mismo módulo y dueño) para una FK compuesta; no edita V12.
- **API**: endpoints nuevos bajo `/api/v1/aranceles`, `/api/v1/obras-sociales/{id}/planes/{planId}/coberturas`, `/api/v1/pacientes/{id}/presupuestos` y `/api/v1/paciente/presupuestos`. Ninguno existente cambia.
- **Seguridad**: `SecurityConfig` no cambia; `@PreAuthorize` por operación. El profesional del presupuesto y el paciente de "Mi cuenta" salen de la sesión, nunca del pedido.
- **Avisos**: a Emanuel (FK a `prestacion` y lectura de `odontograma_registro`) y a Enzo (el precio particular vive en `arancel` y C-22 puede leerlo; los presupuestos no son cobros).
- **Agenda, clínica y notificaciones**: sin cambios.
