# Mockups de C-43 `registro-asistencia`

- Design system: Revision 3 (`design-system/`). Sin tokens nuevos: las etiquetas usan los colores de turno, `--ds-ok` (llegó) y `--ds-alerta` (sin registrar).
- Base visual: la agenda del día de la etapa D de C-39 (`mockups.css` y `delta.css` copiados de `frontend-uniformidad-visual/mockups/D/`). `c43.css` suma solo lo de este change.
- Escena: martes 1/12 a las 10:20, con datos ficticios.
- Approval: approved (2026-11-24, by user)

| Pantalla | Archivo | Capturas |
|---|---|---|
| Agenda de la recepción con un turno en cada estado: atendido, sin registrar ("Llegó" y "Ausente"), llegó, ausente ("Deshacer ausencia") y futuros ("Ausente" deshabilitado hasta los 15 minutos) | `agenda-recepcion.html` | `previews/agenda-recepcion-1440.png`, `previews/agenda-recepcion-390.png` |
| La recepción quiso deshacer una ausencia y el horario ya estaba ocupado | `agenda-recepcion-deshacer-ocupado.html` | `previews/agenda-recepcion-deshacer-ocupado-1440.png`, `previews/agenda-recepcion-deshacer-ocupado-390.png` |
| La misma agenda vista por la odontóloga: solo sus turnos, con etiquetas y sin botones de asistencia | `agenda-odontologo.html` | `previews/agenda-odontologo-1440.png`, `previews/agenda-odontologo-390.png` |

## Cómo verlos

Desde la raíz del repo, `python -m http.server 8765 --bind 127.0.0.1`, y abrir
`http://127.0.0.1:8765/openspec/changes/registro-asistencia/mockups/agenda-recepcion.html`.
