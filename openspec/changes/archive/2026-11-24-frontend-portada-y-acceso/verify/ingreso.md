# Verificación de `#/ingreso` (primera tarea de C-37)

Fecha: 2026-11-23. Código: `origin/master` en `e60edb1`, con `ingreso-unico-mi-cuenta` ya mergeado (PR #59). Emanuel dudaba de que ese fix funcionara.

Método: la aplicación real (`vite`, `frontend/`) abierta en un navegador automatizado (Playwright), con la API **simulada** (solo `GET /api/v1/public/sesion` y los listados vacíos), para probar el router y las redirecciones con cada tipo de sesión. No se usó el backend ni credenciales reales.

## Resultado

| Caso | Dirección final | Página | Observación |
|---|---|---|---|
| Sin sesión, `#/ingreso` | `#/mi-cuenta` | "Mi cuenta" | **Funciona**: la dirección vieja redirige |
| Sin sesión, dirección vacía | `#/mi-cuenta` | "Mi cuenta" | La página inicial es la Agenda, que sin sesión lleva a Mi cuenta |
| Sin sesión, `#/desconocida` | `#/mi-cuenta` | "Mi cuenta" | Igual que la anterior |
| Sin sesión, `#/reserva` | `#/reserva` | "Reservar turno" | Menú visible: "Reservar turno" e "Ingresar" |
| Personal, `#/ingreso` | `#/mi-cuenta` | "Mi cuenta" | Redirige, pero **el personal se queda en Mi cuenta**: no sigue a la Agenda |
| Personal, `#/mi-cuenta` | `#/mi-cuenta` | "Mi cuenta" | Igual: sin redirección a la Agenda |
| Personal, dirección vacía | `#/agenda` | "Agenda" | Correcto |
| Paciente, `#/ingreso` | `#/mis-turnos` | "Mis turnos" | Redirige por Mi cuenta y sigue a Mis turnos |
| Paciente, `#/mi-cuenta` | `#/mis-turnos` | "Mis turnos" | Correcto |

Menú por rol, medido en pantalla: sin sesión, "Reservar turno" e "Ingresar"; personal, "Agenda", "Configuración" y "Salir"; paciente, "Reservar turno", "Mis turnos" y "Salir". En el celular (390 px), sin sesión: "Menú" e "Ingresar". Sin errores de consola.

## Conclusión

- **`#/ingreso` no está roto**: redirige a `#/mi-cuenta`, como pide su spec. No hace falta avisar a Emanuel de un defecto de redirección.
- **Sí hay una laguna**: el personal con sesión que abre `#/mi-cuenta` (por ejemplo, por la dirección vieja `#/ingreso`) se queda en Mi cuenta en lugar de ir a la Agenda, a diferencia del paciente. C-37 la cierra (escenario "Personal con sesión abre su cuenta").
- **La página inicial hoy es la Agenda**: un visitante que abre la aplicación sin dirección termina en la pantalla de ingreso, sin ninguna explicación del consultorio. Es lo que C-37 reemplaza con la portada.
- Límite: la verificación usó la API simulada. El comportamiento de sesión depende solo de `GET /public/sesion`, que es lo que se simuló.
