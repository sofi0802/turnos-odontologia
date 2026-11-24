# Reproducción de la falla al reprogramar (tarea 0.2)

Fecha: 2026-11-23. Método: un test de integración temporal (ya borrado) sobre PostgreSQL real, con las fixtures del proyecto
(`IntegracionAgendaBase`: reloj fijo 2026-12-01 08:00, sesión y CSRF como el frontend, usuario `recepcion`) y datos ficticios
(Dra. Ana Ejemplo, Dr. Bruno Prueba, Sillón 1 y Sillón 2, "Consulta" de 30 min, "Paciente Uno"/"Paciente Dos"). No se usó el navegador;
los pedidos son los mismos que arma el frontend.

Lo de abajo es la respuesta observada de la API real (estado HTTP y `codigo`).

## Candidato 1 — Sillón (CONFIRMADO)

Escenario: T1 = Dra. Ana, Sillón 1, 10:00. T2 = Dr. Bruno, Sillón 1, 11:00 (Sillón 1 ocupado a las 11:00; Sillón 2 libre).

| Paso | Pedido | Respuesta |
|---|---|---|
| 1 | `GET /api/v1/public/disponibilidad?prestacionId=<Consulta>&fecha=2026-12-01&profesionalId=<Dra. Ana>` | `200`, **ofrece** `2026-12-01T11:00:00-03:00` (y 10:45, 11:15, etc.) |
| 2 | `POST /api/v1/turnos/1/reprogramar` `{dni, numeroReserva, inicio: 11:00}` sin `sillonId` | `409` `SOLAPAMIENTO_SILLON` ("El sillón ya está ocupado en ese horario") |
| 3 | Igual, con `sillonId` = Sillón 2 | `200`, turno nuevo en Sillón 2 a las 11:00, número de reserva nuevo |

Causa confirmada: la disponibilidad pública dice "hay algún sillón libre", pero `ReprogramarTurno` conserva el sillón del turno original
cuando el pedido no trae `sillonId`. Un horario ofrecido puede rechazarse con `SOLAPAMIENTO_SILLON`. Es el comportamiento que
asume D1 y D2: el diseño (selector de sillón "Sin cambios" + mensaje "Elegí otro sillón") ya lo cubre; no cambia el diseño.
Mejora de fondo del backend (que elija un sillón libre, como `CrearSobreturno`): módulo `agenda/application` y `agenda/api`, de la líder
(Sofia Indovino); se le avisa en el informe de este change, no se toca acá.

## Candidato 2 — Número de reserva sin guion (CONFIRMADO)

T3 con número `FRS5-9ENB`, reprogramación a las 15:00:

| Valor enviado en `numeroReserva` | Respuesta |
|---|---|
| `FRS59ENB` (sin guion) | `409` `DATOS_DE_RESERVA_INVALIDOS` |
| `FRS5 9ENB` (espacio en lugar del guion) | `409` `DATOS_DE_RESERVA_INVALIDOS` |
| `frs5-9enb` (minúsculas con guion) | `200` (la comparación ignora mayúsculas) |

Causa confirmada: `NumeroReserva.coincideCon` exige el guion; solo ignora mayúsculas y espacios en los extremos. El cliente debe normalizar
(D5). El mensaje del backend ("El DNI o el número de reserva no corresponden al turno") no tiene texto propio en `MENSAJES_POR_CODIGO`,
así que hoy se muestra el detalle del servidor (D8).

## Candidato 3 — Profesional no habilitado (CONFIRMADO)

Se agregó la Dra. Carla Muestra (con horario, sin habilitación para "Consulta").

| Paso | Pedido | Respuesta |
|---|---|---|
| 1 | `GET /api/v1/public/profesionales?prestacionId=<Consulta>` | `200` `[{id:1,"Ana Ejemplo"},{id:2,"Bruno Prueba"}]` (no incluye a Carla) |
| 2 | `POST /api/v1/turnos/6/reprogramar` con `profesionalId` = Carla | `409` `PROFESIONAL_NO_HABILITADO` |

Causa confirmada: el formulario actual ofrece todos los profesionales activos; `GET /public/profesionales?prestacionId=` ya devuelve solo
los habilitados (D3). `PROFESIONAL_NO_HABILITADO` ya tiene texto en `MENSAJES_POR_CODIGO`.

## Candidato 4 — Códigos que puede devolver la reprogramación y su texto

Observados (todos `409` salvo indicación):

| Código | Observado | ¿Entrada en `MENSAJES_POR_CODIGO`? |
|---|---|---|
| `SOLAPAMIENTO_SILLON` | sí (candidato 1) | sí, pero sin la indicación de elegir otro sillón |
| `SOLAPAMIENTO_PROFESIONAL` | sí (Dr. Bruno ocupado a las 11:00; también a las 11:15) | sí |
| `PROFESIONAL_NO_HABILITADO` | sí | sí |
| `INICIO_EN_EL_PASADO` | sí | sí |
| `DATOS_DE_RESERVA_INVALIDOS` | sí | **no** |
| `TURNO_INEXISTENTE` (404) | sí | sí |
| `TURNO_NO_ACTIVO`, `RECURSO_INACTIVO`, `MARGEN_INSUFICIENTE`, `FUERA_DE_HORARIO`, `PROFESIONAL_AUSENTE`, `AGENDA_BLOQUEADA` | por lectura de `CodigoRechazo` y `CrearTurno` | sí |

Sin entrada y alcanzables por la reprogramación: `DATOS_DE_RESERVA_INVALIDOS` (se agrega, D8). El resto de los códigos del enum
(`SIN_SILLON_DISPONIBLE`, `TURNO_NO_EXTENSIBLE`, `TURNO_NO_AFECTADO`, `TURNO_NO_INICIADO`, `MARGEN_INVALIDO`, `DATOS_INVALIDOS`) no los
devuelve este endpoint; igual el mensaje genérico cubre cualquier código desconocido.

## Conclusión

Las causas confirmadas son las de D1 (1, 2 y 3; la 4 es el efecto visible de 2). El diseño no cambia y no hace falta la tarea 0.3.
La causa 1 es una limitación del backend (el sillón no se reasigna solo): se informa a Sofia Indovino como mejora posible; no bloquea
este change.

## Con el formulario nuevo (tarea 5.4)

Fecha: 2026-11-23. Método: la aplicación real en el navegador con la API simulada, que repite las respuestas observadas arriba (409
`SOLAPAMIENTO_SILLON` si el pedido no trae `sillonId`, 200 con `sillonId`; `DATOS_DE_RESERVA_INVALIDOS` si el número no coincide). Detalle y
capturas en `mockups/verify/README.md`.

| Candidato | Con el formulario viejo (0.2) | Con el formulario nuevo |
|---|---|---|
| 1. Sillón | `409` `SOLAPAMIENTO_SILLON` sin explicación | El rechazo dice "El sillón ya está ocupado en ese horario. Elegí otro sillón." y marca el campo "Sillón"; con el "Sillón 2" y el mismo horario ofrecido, el pedido lleva `sillonId` y responde 200 |
| 2. Número sin guion | `409` `DATOS_DE_RESERVA_INVALIDOS` | `" r4md2hzc "` se envía como `R4MD-2HZC` y no falla por el formato |
| 3. Profesional no habilitado | Se podía elegir cualquier profesional activo | El selector solo ofrece los de `GET /public/profesionales?prestacionId=` |
| 4. Código sin texto | Se mostraba el detalle del servidor | `DATOS_DE_RESERVA_INVALIDOS` tiene su texto: "El DNI o el número de reserva no son correctos." |

Límite de esta verificación: se hizo con la API simulada, no con el backend real y una sesión de personal. La parte del backend (los
códigos y cuándo se devuelven) es la que se reprodujo en 0.2 contra PostgreSQL real; la parte del formulario es la de esta tabla.
Para repetirlo de punta a punta, ver "Cómo repetirlo con el backend real" en `mockups/verify/README.md`.

Sigue pendiente, y fuera de este change, la mejora de fondo del backend (que la reprogramación elija un sillón libre si no se indica
uno): `agenda/application` y `agenda/api`, de Sofia Indovino.
