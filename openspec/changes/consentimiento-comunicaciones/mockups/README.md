# Mockups de C-17 (`consentimiento-comunicaciones`)

- Design system: Revision 5 (`design-system/tokens.css`, `brand.js` y `assets/logo.svg`), más los estilos reales de `frontend/src/styles.css`.
- Estilos nuevos propuestos: `consentimiento.css` (lo marcado "Solo mockups" no pasa al módulo).
- Datos 100 % ficticios: "Paciente Uno" (DNI 99000001), "Paciente Dos" (DNI 99000002) y `recepcion`.
- Approval: pending

## Pantallas

| Archivo | Quién | Qué muestra |
|---|---|---|
| `mi-cuenta-sin-consentimiento.html` | Paciente | "Mi cuenta" con "Tus datos" y "Mensajes del consultorio" sin consentimiento |
| `mi-cuenta-con-consentimiento.html` | Paciente | Con consentimiento (aviso de éxito), la confirmación para dejar de recibir y el estado después de revocar |
| `registro.html` | Sin sesión | Registro con la casilla de mensajes |
| `recepcion-registrar.html` | Recepción | "Anotar paciente" con la línea de consentimiento, el diálogo del medio (con el error de falta de medio) y la línea después de registrar |
| `recepcion-sin-consentimiento.html` | Recepción | Rechazo `SIN_CONSENTIMIENTO` con la acción de registrar destacada |
| `paciente-lista-espera.html` | Paciente | Aviso de C-15 con el enlace "Aceptar mensajes en Mi cuenta" |

## Cómo verlos

Servir la raíz del repo (las rutas a `design-system/` y `frontend/src/` son relativas) y abrir cada archivo:

```
python -m http.server 8000
```

`http://localhost:8000/openspec/changes/consentimiento-comunicaciones/mockups/<archivo>.html`

Las capturas a 390 × 844 y 1440 × 900 están en `previews/`.
