# Proposal

## Why

La ficha clínica (C-13) tiene ficha básica y odontograma, pero el odontólogo no puede registrar el sondaje periodontal ni seguir la evolución de la enfermedad periodontal entre visitas. Es la primera funcionalidad de clínica avanzada del post-MVP (C-25, gate 7). La KB ya está ampliada y aprobada por la líder el 2026-11-24 (RN-CL-04 a RN-CL-06, US-045, DD-13).

## What Changes

- Periodontograma por visita: cada carga crea una **versión nueva e inmutable** con el profesional de la sesión y el momento; las anteriores no se modifican ni se borran (RN-CL-04).
- Datos por pieza permanente (FDI 11–48): ausente, movilidad (0–3) y furcación (0–3, solo multirradiculares: molares, 14 y 24). Por cada uno de los 6 sitios (`MV`, `V`, `DV`, `ML`, `L`, `DL`): profundidad de sondaje (0–15 mm), recesión (−5–15 mm), sangrado y placa (RN-CL-05).
- **Estado periodontal calculado** de cada pieza (sano, leve, moderado, severo) por la pérdida de inserción del peor sitio; no se carga a mano y no se guarda (RN-CL-06).
- API REST nueva bajo `/api/v1/pacientes/{id}/periodontograma`: registrar una versión, listar las versiones (de la más nueva a la más vieja) y consultar una versión con sus piezas y estados.
- Acceso igual que la ficha: el odontólogo registra y consulta, el administrador solo consulta, la recepción recibe `403` sin datos (RN-CL-01).
- Migración **V16** con tres tablas de solo agregado.
- Solo backend. La pantalla del periodontograma queda para un change de frontend posterior (módulo de Enzo).

## Capabilities

### New Capabilities
- `periodontograma`: registro versionado del sondaje periodontal por pieza y sitio, validación de los datos, cálculo del estado periodontal de cada pieza, consulta de las versiones y control de acceso por rol.

### Modified Capabilities
<!-- Ninguna: ficha-clinica no cambia; el periodontograma reutiliza sus reglas de acceso y de privacidad como requisitos propios. -->

## Impact

- **Código**: `backend/.../clinica/` (dominio, aplicación, API e infraestructura nuevos para el periodontograma). El módulo es de Emanuel: se le avisa y revisa el PR.
- **Base de datos**: migración `V16__periodontograma.sql` (número reservado en CHANGES.md). Flyway corre con out-of-order, así que V16 se aplica aunque V21 y V22 ya estén en `master`.
- **API**: tres endpoints nuevos; ninguno existente cambia.
- **Seguridad**: los mismos permisos que la ficha (`@PreAuthorize`, sesión con CSRF); datos de salud fuera de los logs y de los mensajes de error (regla dura 6).
- **KB**: `04_modelo_de_datos.md` se corrige para que las versiones cuelguen del paciente y no de la ficha, igual que el odontograma de C-13 (design D2).
