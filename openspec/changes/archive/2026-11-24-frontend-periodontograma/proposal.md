# Proposal

## Why

C-25 hizo el backend del periodontograma (versiones inmutables por visita y estado calculado de cada pieza), pero ninguna pantalla lo usa: hoy el odontólogo no puede cargar ni consultar un periodontograma desde la aplicación. C-46 suma la interfaz en la ficha clínica de C-44, con las decisiones que tomó la líder el 2026-11-24.

## What Changes

- **Sección "Periodontograma" en la ficha** (`#/pacientes`), debajo del odontograma, para `ODONTOLOGO` y `ADMIN`. Sin entrada nueva en la barra lateral. Se carga aparte del resto de la ficha, con su propio error y "Reintentar".
- **Consulta**: la última versión, con la fecha, la hora y el profesional. Un mapa de las 32 piezas permanentes por cuadrante muestra el estado calculado de cada pieza cargada (sano, leve, moderado o severo) con el nombre y una marca, no solo con el color. Al elegir una pieza se ven sus 6 sitios, la movilidad y la furcación.
- **Versiones anteriores**: un selector abre cualquier versión, en solo lectura, de la más nueva a la más vieja. Sin comparación entre versiones.
- **Nueva versión** (solo `ODONTOLOGO`): el formulario arranca con los datos de la última versión, o sin piezas si no hay ninguna. Se elige una pieza para cargarla, editarla, marcarla ausente o quitarla de la carga. La pantalla explica la convención de la recesión negativa. Se valida en el frontend lo mismo que el backend (rangos, 6 sitios, movilidad, furcación solo en multirradiculares) y se muestran los errores de la API. Antes de cambiar de paciente o cancelar con datos sin guardar, se pide confirmación.
- **Mensajes nuevos** para `SITIOS_INCOMPLETOS` y `FURCACION_NO_APLICA`.
- **Design system Revision 5**: cuatro tokens `--ds-perio-*` para el estado periodontal, con un único color nuevo (moderado). La sección se muestra en `components.html`. La aprobación vuelve a `pending` hasta que la líder apruebe la revisión junto con los mockups.
- Sin cambios de backend ni migraciones.

## Capabilities

### New Capabilities
<!-- Ninguna: el periodontograma se suma a la ficha clínica del frontend. -->

### Modified Capabilities
- `frontend-ficha-clinica`: se suman los requisitos de la sección del periodontograma (consulta, versiones, nueva versión, validación, errores, accesibilidad), y cambian "Solo lectura para el administrador" y "Datos de salud solo en la pantalla" para incluir el periodontograma.

## Impact

- **Código**: `frontend/src/ficha/` (módulos nuevos del periodontograma y sus pedidos), `frontend/src/pages/pacientes.ts` (monta la sección y la confirmación al cambiar de paciente), `frontend/src/shared/ui/mensajes.ts` (dos códigos) y un módulo de estilos nuevo `frontend/src/styles/19-periodontograma.css`. `frontend/` es de Enzo: se le avisa y revisa el PR. La ficha es de C-44 (Emanuel): se le avisa.
- **API**: usa `POST /api/v1/pacientes/{id}/periodontograma`, `GET /api/v1/pacientes/{id}/periodontograma` y `GET .../{versionId}` de C-25 sin cambiarlos. El PR se mergea después del de C-25.
- **Design system**: `tokens.css`, `components.html`, `README.md` (Revision 5) y `.active-orchestrator-state.json`.
- **Tests**: Vitest con jsdom, un test por escenario; los de C-44 siguen pasando.
