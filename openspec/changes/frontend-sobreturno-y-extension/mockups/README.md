# Mockups de C-45 `frontend-sobreturno-y-extension`

- **Approval: approved (2026-11-24, by user: Enzo)**
- Design system: Revision 4 (`design-system/README.md`)
- Base: el HTML de la agenda de los mockups de la etapa D de C-39, con los estilos actuales de `frontend/src/styles/` (después de la etapa E), copiados en `mockups.css`. Lo nuevo de C-45 está en `delta.css`.
- Los elementos nuevos llevan un **borde punteado** (clase `.nuevo`) solo para señalarlos en la revisión. Ese borde no pasa a la aplicación.
- Datos ficticios: los pacientes, profesionales y sillones del seed de `knowledge-base/04_modelo_de_datos.md`.

## Pantallas

| Archivo | Qué muestra | Escenarios |
|---|---|---|
| `agenda-dia.html` | Agenda del día de la recepción con "Sobreturno" junto a "Nuevo turno" y "Extender" en los turnos activos. El sobreturno de las 11:00 no ofrece "Extender" | Botón de sobreturno, Acción Extender |
| `agenda-exito.html` | Mensaje después de cargar un sobreturno | Recepción carga un sobreturno |
| `sobreturno-vacio.html` | Formulario de sobreturno recién abierto: sin sillón y con la ayuda del motivo | El sobreturno no pide sillón, El motivo pide no escribir datos de salud |
| `sobreturno-sin-sillon.html` | Rechazo `SIN_SILLON_DISPONIBLE` con los datos cargados | Sin sillón libre para el sobreturno |
| `sobreturno-odontologo.html` | "Mi agenda" del odontólogo, con el profesional fijo | El odontólogo carga un sobreturno con su profesional |
| `extender.html` | Formulario de extender, con el fin actual más 15 minutos | Recepción extiende un turno |
| `extender-choque.html` | Choque al extender, con "Reprogramar el turno de las 10:40" y "Cargar como sobreturno" | La extensión choca con el turno siguiente |
| `extender-a-sobreturno.html` | Sobreturno precargado desde el choque: paciente fijo, hora del fin actual y motivo | Cargar la extensión como sobreturno |

## Ver los mockups

Los navegadores bloquean `file://` con Playwright. Desde la raíz del repo:

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

Después, abrir `http://127.0.0.1:8765/openspec/changes/frontend-sobreturno-y-extension/mockups/agenda-dia.html`.

## Capturas

`previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png` (390x844 y 1440x900, con Playwright y `prefers-reduced-motion: reduce`). La agenda va en página completa. Los diálogos van al tamaño de la ventana, como se ven en la pantalla. En 390 px el diálogo de sobreturno se desplaza para llegar a "Cargar sobreturno", como los otros diálogos largos de la agenda.
