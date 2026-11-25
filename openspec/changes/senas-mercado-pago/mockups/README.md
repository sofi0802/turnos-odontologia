# Mockups de C-23 `senas-mercado-pago`

- **Approval: approved (2026-11-25, by user: Enzo)**
- Design system: Revision 5 (`design-system/README.md`)
- Base: el DOM real de las pantallas actuales, volcado por los tests de `frontend/test/` (confirmación de la reserva, "Mis turnos", Configuración y el diálogo "Cancelar turno" de la agenda), con los estilos actuales de `frontend/src/styles/` copiados en `mockups.css`. Lo nuevo de C-23 está en `delta.css`, que en el apply pasa a `src/styles/23-senas.css` (sin la regla `.dialogo-mock`, que es solo de los mockups).
- Datos ficticios: "Paciente Dos" (DNI 99000002), "Ana Ejemplo", "Bruno Prueba", números de reserva inventados y la seña de `$ 4.500,00` (el 30 % de un copago de `$ 15.000,00`).

## Pantallas

| Archivo | Qué muestra | Escenarios |
|---|---|---|
| `config-senas.html` | Pestaña "Señas" de Configuración (admin): casilla, porcentaje, plazo, "Guardar" y el aviso de guardado | Ver la configuración de las señas, Guardar las señas |
| `config-senas-error.html` | El porcentaje 0 con el error del campo | Porcentaje inválido en la pantalla |
| `reserva-con-sena.html` | Confirmación de la reserva con el bloque de la seña y "Pagar seña". Sin link se ve igual: el botón pide uno nuevo | Reserva con seña, Seña sin link |
| `mis-turnos-senas.html` | "Mis turnos" con los cinco estados de la seña: pendiente con "Pagar seña", pagada, devuelta, retenida (ausente) y vencida | Seña pendiente en mis turnos, Seña pagada en mis turnos, Seña devuelta en mis turnos |
| `mis-turnos-vuelta-pago.html` | La vuelta de Mercado Pago con el aviso "Estamos confirmando tu pago…" y la seña todavía pendiente | Vuelta antes de la notificación |
| `agenda-cancelar-causa.html` | Diálogo "Cancelar turno" con "Causa" y su ayuda | Cancelar con la causa |
| `agenda-cancelar-causa-error.html` | El mismo diálogo con el error de la causa | Falta la causa de un turno con seña |

Los estados de la seña se distinguen por el texto ("Seña pendiente", "Seña pagada", …); el color del borde solo acompaña. "Reserva sin seña" y "La recepción no ve la sección" son las pantallas actuales, sin cambios.

## Ver los mockups

Desde la raíz del repo:

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

Después, abrir `http://127.0.0.1:8765/openspec/changes/senas-mercado-pago/mockups/mis-turnos-senas.html`.

## Capturas

`previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png` (390x844 y 1440x900, con Playwright y `prefers-reduced-motion: reduce`). Las pantallas van en página completa; los diálogos, al tamaño de la ventana. Ninguna pantalla tiene desplazamiento horizontal. En el celular, la barra de pestañas de Configuración se desplaza dentro de su franja, como hoy, así que "Señas" queda a la derecha de "Usuarios".
