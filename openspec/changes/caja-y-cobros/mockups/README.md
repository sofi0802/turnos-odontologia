# Mockups de C-22 `caja-y-cobros`

- **Approval: approved (2026-11-24, by user: Enzo)**
- Design system: Revision 5 (`design-system/README.md`)
- Base: el encabezado y la barra lateral de los mockups de la etapa D de C-39, con los estilos actuales de `frontend/src/styles/` copiados en `mockups.css` (incluye los paneles y cifras de Reportes). Lo nuevo de C-22 está en `delta.css`.
- Datos ficticios: "Paciente Uno" (DNI 99000001), "Paciente Dos" y "Paciente Tres"; aranceles del seed de C-19.

## Pantallas

| Archivo | Qué muestra | Escenarios |
|---|---|---|
| `caja-sin-abrir.html` | Recepción sin caja abierta: "Abrir caja" con el monto inicial; pendientes sin "Cobrar" | Sin caja abierta para la recepción, Sin caja no se cobra |
| `caja-sin-abrir-admin.html` | Admin sin caja abierta: sin "Abrir caja" | Sin caja abierta para el admin |
| `caja-abierta.html` | Recepción con caja abierta: totales por medio, efectivo esperado, pendientes con "Cobrar", "Sin precio", cobros (uno anulado), "Cerrar caja", cuenta corriente vacía e historial con una diferencia | Caja abierta, Pendientes con caja abierta, Turno sin precio, La recepción no ve "Anular", Caja descuadrada en el historial |
| `caja-abierta-admin.html` | Admin con caja abierta: "Anular" en los cobros, sin "Cobrar" ni "Cerrar caja"; cuenta corriente de "Paciente Uno" | El admin anula desde la pantalla, Ver la cuenta corriente |
| `caja-cobrar.html` | Formulario de cobro parcial con débito | Cobro parcial desde la pantalla |
| `caja-cerrar.html` | Cierre con diferencia de -$ 500,00 y el motivo obligatorio | Cierre con diferencia sin motivo |

## Ver los mockups

Desde la raíz del repo:

```powershell
python -m http.server 8765 --bind 127.0.0.1
```

Después, abrir `http://127.0.0.1:8765/openspec/changes/caja-y-cobros/mockups/caja-abierta.html`.

## Capturas

`previews/<pantalla>-390.png` y `previews/<pantalla>-1440.png` (390x844 y 1440x900, con Playwright y `prefers-reduced-motion: reduce`). Las pantallas van en página completa; los formularios, al tamaño de la ventana. Ninguna pantalla tiene desplazamiento horizontal: en el celular, la tabla de la cuenta corriente se desplaza dentro de su panel.
