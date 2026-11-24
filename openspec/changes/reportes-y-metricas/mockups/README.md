# Mockups de C-32 `reportes-y-metricas`

- **Approval: approved (2026-11-24, by user: Enzo)**
- Design system: Revision 5 (`design-system/README.md`)
- Base: el encabezado y la barra lateral de los mockups de la etapa D de C-39, con los estilos actuales de `frontend/src/styles/` copiados en `mockups.css`. Lo nuevo de C-32 está en `delta.css`.
- Datos ficticios: Ana Ejemplo y Bruno Prueba, los sillones 1 a 3 y "Carla Muestra" como profesional sin horario. Ningún dato de paciente.

## Pantallas

| Archivo | Qué muestra | Escenarios |
|---|---|---|
| `reportes-admin.html` | Menú del administrador (Agenda, Pacientes, Reportes, Configuración) y los cuatro bloques con datos: porcentajes, más del 100 % rayado, "Sin horario" | Menú del administrador, Bloque de ocupación, Bloques de origen, ausencias y cancelaciones |
| `reportes-filtro.html` | Recepción con "Ana Ejemplo" elegida: sin sillones, con la nota | Menú del personal, Sillones con filtro de profesional |
| `reportes-vacio.html` | Período sin turnos | Período vacío |
| `reportes-error-periodo.html` | "Hasta" con error por pasar los 92 días; se conservan los datos anteriores | Período de más de 92 días |
| `reportes-odontologo.html` | El odontólogo abre `#/reportes`: aviso sin datos y sin la opción en el menú | El odontólogo abre la dirección |

## Ver los mockups

Desde la raíz del repo:

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

Después, abrir `http://127.0.0.1:8765/openspec/changes/reportes-y-metricas/mockups/reportes-admin.html`.

## Capturas

`previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png` (390x844 y 1440x900, página completa, con Playwright y `prefers-reduced-motion: reduce`). Ninguna pantalla tiene desplazamiento horizontal.
