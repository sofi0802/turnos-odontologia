# Mockups de C-35 `agenda-propia-odontologo`

- Design system revision: 1 (`design-system/README.md`, aprobado el 2026-11-22)
- Approval: approved (2026-11-23, by user: Sofia Indovino aprobó la revisión 3 de los mockups)
- Preview: rendered (2026-11-23). Las capturas están en `previews/` a 390 × 844 y 1440 × 900, en página completa. Ninguna pantalla tiene desplazamiento horizontal ni errores en la consola.

La líder los aprueba antes del apply. Una vez aprobados, son el criterio de aceptación visual de la tarea 3.4 de `tasks.md`.

## Brief

- **Para quién**: el odontólogo, que mira su agenda entre paciente y paciente, desde el celular o la computadora del consultorio.
- **Qué cambia (RN-AG-18)**:
  - El título pasa a ser "Mi agenda", con el nombre de su profesional debajo.
  - No hay filtro "Profesional".
  - Se ven solo sus turnos.
  - El filtro "Sillón", el período y las acciones quedan como en C-09.
  - Revisión 3 (2026-11-23, pedido de la líder al probar la app): la fecha del período va pegada a las flechas y más grande (24 px), para que se lea qué día se está viendo. En el celular queda debajo de los botones.
  - Revisión 2 (2026-11-23, pedido de la líder): "Anterior" y "Siguiente" pasan a ser flechas ‹ ›. Siguen teniendo su nombre accesible y su texto de ayuda, y el cambio vale para todos los roles del personal.
- **Base**: el armado actual de la agenda del personal (capturas de `archive/2026-11-23-frontend-agenda-personal/verify/`), con variables de `design-system/tokens.css`, el logo provisorio y el nombre de `brand.js`.
- **Restricciones**:
  - Sin datos de salud.
  - Datos 100% ficticios (seed de dev).
  - Sin recursos remotos.
  - El menú principal no cambia en este change.

## Pantallas

| Archivo | Pantalla y estado | Escenarios |
|---|---|---|
| `odontologo-dia.html` | Agenda del día de `ana.ejemplo`, sin filtro de sillón | El odontólogo ve solo su agenda |
| `odontologo-semana.html` | Agenda de la semana de `ana.ejemplo` | El odontólogo ve solo su agenda (vista semanal) |
| `estados.html` | (1) Día sin turnos filtrado por "Sillón 2". (2) Catálogo de profesionales no disponible: solo "Mi agenda". (3) Referencia: recepción conserva los dos filtros | El odontólogo filtra por sillón; Catálogos no disponibles; Recepción conserva el filtro por profesional |

La franja naranja de arriba es una nota del mockup y no forma parte de la pantalla.

## Cómo verlos

Desde la raíz del repo:

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

Abrir `http://127.0.0.1:8765/openspec/changes/agenda-propia-odontologo/mockups/odontologo-dia.html`. Los archivos también se pueden abrir con doble clic.
