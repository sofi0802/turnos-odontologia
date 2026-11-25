# Mockups de C-15: lista de espera automática

- **Design system**: Revision 5 (`design-system/tokens.css`, `brand.js` y `assets/logo.svg`), sin tokens nuevos.
- **Estilos**: los reales de la aplicación (`frontend/src/styles.css`) más `lista-espera.css`, que en el apply pasa a `frontend/src/styles/22-lista-espera.css` sin las reglas marcadas "solo para los mockups".
- **Marco**: `mockup.js` arma el encabezado, la barra lateral y el pie según `data-rol` (incluye "Lista de espera" inmediatamente después de "Agenda" para `ADMIN` y `RECEPCIONISTA`, y "Coberturas" de C-49 en su lugar, para mostrar el orden final: Agenda, Lista de espera, Pacientes, Coberturas, Reportes y Configuración; y el rol `VISITANTE` sin sesión).
- **Datos**: 100 % ficticios ("Paciente Uno/Dos/Tres", DNI `9900000x`, "Ana Ejemplo", "Bruno Prueba").
- **Approval**: approved (2026-11-24, by Sofia Indovino).

## Pantallas

| Archivo | Rol | Estados |
|---|---|---|
| `lista-espera-recepcion.html` | `RECEPCIONISTA` | Lista en orden con una oferta pendiente y el aviso de alta, lista vacía, cargando, error |
| `lista-espera-dialogos.html` | `ADMIN` | Diálogo "Anotar paciente", rango de más de 60 días, `SIN_CONSENTIMIENTO`, confirmación de baja; plazo en la pestaña Margen (editable, fuera de rango y en solo lectura para la recepción) |
| `lista-espera-paciente.html` | `PACIENTE` | Reserva con un día sin horarios, diálogo para anotarse, anotado, `YA_EN_LISTA_DE_ESPERA`; Mis turnos con la sección "Lista de espera" y el error al cargarla |
| `oferta.html` | Sin sesión | Oferta pendiente, aceptada, "No me interesa", `OFERTA_NO_DISPONIBLE`, vencida, ya respondida y link inválido |

## Ver los mockups

Desde la raíz del repo:

```bash
python -m http.server 8765 --bind 127.0.0.1
```

y abrir `http://127.0.0.1:8765/openspec/changes/lista-de-espera-automatica/mockups/<archivo>.html`.

## Capturas

En `previews/`, a 390 × 844 y 1440 × 900 (página completa), sin desplazamiento horizontal ni errores de consola (2026-11-24).
