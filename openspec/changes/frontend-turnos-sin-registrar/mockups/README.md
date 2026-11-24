# Mockups de C-42 `frontend-turnos-sin-registrar`

- Design system: Revision 3 (`design-system/`). Sin tokens nuevos: `.e-sin-registrar` reusa los de "Cancelado".
- Base visual: Mis turnos de la etapa C de C-39 (`mockups.css` copiado de `frontend-uniformidad-visual/mockups/C/`). `c42.css` suma solo lo de este change. Revisión 2 de los mockups: la 1 (2026-11-23) decía "Vencido" y usaba el estilo de la etapa B.
- Datos ficticios: Paciente Uno, Ana Ejemplo. Momento actual: martes 2026-12-01 a las 08:00.
- Approval: approved (2026-11-24, by user; revisión 2)

| Pantalla | Archivo | Capturas |
|---|---|---|
| Mis turnos con C-42: dos turnos pasados sin registrar como "Sin registrar" | `mis-turnos.html` | `previews/mis-turnos-390.png`, `previews/mis-turnos-1440.png` |
| Referencia de hoy, antes de C-42 (no se implementa) | `mis-turnos-hoy.html` | `previews/mis-turnos-hoy-390.png` |

## Cómo verlos

Desde la raíz del repo, `python -m http.server 8765 --bind 127.0.0.1`, y abrir
`http://127.0.0.1:8765/openspec/changes/frontend-turnos-sin-registrar/mockups/mis-turnos.html`. Para el celular, achicar la ventana a 390 px.
