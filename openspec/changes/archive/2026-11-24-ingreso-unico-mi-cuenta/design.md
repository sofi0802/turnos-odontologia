# Design

## Context

Ver `proposal.md` (Why). Estado actual en `frontend/src/`:

- `app.ts` registra `paginaIngreso` (`pages/ingreso.ts`, ruta `ingreso`) y `paginaMiCuenta` (`pages/mi-cuenta.ts`, ruta `mi-cuenta`).
- Tres caminos llevan a `#/ingreso`: "Salir" con sesión del personal (`alSalir`), una página `acceso: 'personal'` abierta sin sesión (`mostrar`, design D2 de C-12) y la sesión vencida de la Agenda (`contexto.irAlIngreso(MENSAJE_SESION_TERMINADA)` en `pages/agenda.ts`).
- `ContextoPagina` tiene dos funciones parecidas: `irAlIngreso(aviso?)`, que además vacía el usuario del encabezado, e `irAMiCuenta(aviso?)`, que usan Reservar turno y Mis turnos. Las dos guardan el aviso en `avisoPendiente`, y la página siguiente lo recibe en `contexto.aviso`. Mi cuenta ya muestra `contexto.aviso` arriba de las pestañas.
- `Mi cuenta`, después de un login con un rol del personal, ya lleva a la Agenda (`seguirConSesion`).
- `shared/ui/router.ts` lleva cualquier dirección desconocida a la página inicial (Agenda) con `history.replaceState`.

## Goals / Non-Goals

**Goals:**
- Un solo destino de ingreso (`#/mi-cuenta`) para todos los caminos.
- Que los marcadores viejos a `#/ingreso` sigan sirviendo.

**Non-Goals:**
- Cambiar la pantalla Mi cuenta (etiqueta "Email", textos, estilos, pestañas).
- Que Mi cuenta con una sesión del personal ya iniciada lleve sola a la Agenda (hoy solo lo hace con sesión de paciente; queda como está).
- Tocar el backend o la API.

## Decisions

### D1. Se elimina `irAlIngreso` y todo usa `irAMiCuenta`
La Agenda llama a `contexto.irAMiCuenta(MENSAJE_SESION_TERMINADA)`, igual que Mis turnos y Reservar turno. `ContextoPagina` queda con una sola función para ir al ingreso.

La única diferencia de `irAlIngreso` era que vaciaba el usuario del encabezado antes de navegar. No hace falta: Mi cuenta es una página pública, y al mostrarla la aplicación consulta `GET /api/v1/public/sesion` y actualiza el encabezado con la respuesta, que después de un `401` es "sin sesión".

**Alternativa descartada:** mantener `irAlIngreso` apuntando a Mi cuenta. Quedarían dos funciones con el mismo destino y un nombre que remite a una página que ya no existe.

### D2. Redirección de `#/ingreso` en el router
`crearRouter` recibe un mapa opcional de direcciones viejas (`{ ingreso: 'mi-cuenta' }`). Si la dirección está en el mapa, reemplaza la dirección con `history.replaceState`, sin sumar una entrada al historial, y muestra la página de destino. Es el mismo mecanismo que ya usa para las direcciones desconocidas.

**Alternativa descartada:** una página `ingreso` que solo llama a `irA('mi-cuenta')`. Dejaría un paso extra en el historial (Atrás volvería a `#/ingreso` y otra vez a Mi cuenta) y una "página" sin contenido en `PAGINAS`.

### D3. Sin sesión, toda página privada va a Mi cuenta
En `mostrar`, la elección `acceso === 'paciente' ? paginaMiCuenta : paginaIngreso` pasa a ser siempre `paginaMiCuenta`. Reemplaza la parte de "cada grupo va a su propio ingreso" del design D2 de C-12; el resto de D2 (`acceso` por página y los avisos de grupo equivocado) no cambia.

### D4. "Salir" del personal
`alSalir` lleva a `paginaMiCuenta.ruta` con una sesión del personal y a `paginaReserva.ruta` con una de paciente, borrando la reserva pendiente como hasta ahora.

### D5. Mensaje de credenciales del personal
Con la página `#/ingreso` se va `MENSAJE_CREDENCIALES` ("Usuario o contraseña incorrectos."). El personal ve el mensaje de Mi cuenta, "Email o contraseña incorrectos.", porque la pantalla no cambia (decisión del usuario). Queda registrado en el spec `frontend-sesion-personal`, requisito "Credenciales rechazadas".

## UI Mockups

- Design system revision: 1
- Decision: no (2026-11-23): sin cambios visuales; reutiliza la pantalla Mi cuenta aprobada en C-12 y solo cambia a dónde llevan las redirecciones; decisión del usuario

## Risks / Trade-offs

- **[Changes sin archivar que tocan los mismos requisitos]** `frontend-configuracion-consultorio` (C-34) también modifica "Páginas disponibles" de `frontend-navegacion`. → El bloque MODIFIED de este change parte del texto de C-34 (sin "Configuración sigue mostrando solo su título."), así que el resultado es el mismo en cualquier orden de archive, siempre que C-34 se archive antes. Si se archiva después, su bloque volvería a poner `#/ingreso`: hay que archivar C-34 primero o actualizar su delta.
- **[Escenario de C-36]** `buscador-pacientes` (C-36, sin archivar) dice "se muestra la página de ingreso con el aviso…" en "Sesión vencida durante la búsqueda". El texto sigue siendo cierto (Mi cuenta es la página de ingreso); su test se actualiza de `#/ingreso` a `#/mi-cuenta` en este change.
- **[Mensaje de credenciales]** El personal lee "Email o contraseña incorrectos." aunque escribe un usuario. → Aceptado por el usuario (alcance: solo redirecciones). Si molesta, es un change visual aparte con mockup.
- **[Encabezado tras un `401`]** Al llegar a Mi cuenta, el usuario del encabezado se actualiza cuando responde `GET /public/sesion`, no antes (D1). → La consulta es inmediata y es el mismo comportamiento que ya tenían los pacientes.

## Migration Plan

Sin migración de datos. Deploy del frontend solo. Rollback: revertir el PR.
