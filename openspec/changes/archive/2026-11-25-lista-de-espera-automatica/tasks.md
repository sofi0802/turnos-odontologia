# Tasks

> Tests primero: cada escenario GIVEN/WHEN/THEN de las specs tiene su test con el mismo nombre. Los tests de integración usan PostgreSQL real y llevan `@Tag("integration")`. Mockups aprobados (ver `design.md`, "UI Mockups") antes de empezar el grupo 7.

## 1. Avisos y base

- [x] 1.1 Avisar a Juan Cruz (`notificaciones/`), Emanuel (patrón de `configuracion/`), Juan Sebastián (`reserva/`) y Enzo (`frontend/`) del alcance y de las partes que revisan.
- [x] 1.2 Migración `V9__lista_espera.sql`: tablas `inscripcion_lista_espera` y `oferta_lista_espera` con sus `CHECK`, índices únicos parciales e índices de búsqueda; clave `lista_espera_plazo_horas = 2`; estado del aviso en la oferta (`aviso_enviado`, `aviso_intentos`), sin tocar `notificacion` (D6, D9).
- [x] 1.3 Test de integración de la migración: los índices parciales rechazan una segunda inscripción activa y una segunda oferta pendiente por horario.

## 2. Dominio (sin base)

- [x] 2.1 `Inscripcion` con su rango (hoy o después, hasta 60 días) y sus estados; tests de "Rango de más de 60 días" y "Rango que empieza en el pasado".
- [x] 2.2 `PlazoOferta` (1–24, `PLAZO_INVALIDO`) y el vencimiento `min(ahora + plazo, inicio)`; tests de "Plazo por defecto", "El inicio llega antes que el plazo" y "Plazo fuera de rango".
- [x] 2.3 Filtro de candidatas por rango, profesional y oferta pendiente; tests de "Otro profesional elegido", "Fuera del rango de fechas" y "El paciente con una oferta pendiente se saltea".
- [x] 2.4 `Oferta` con sus transiciones (aceptar, rechazar, vencer, no disponible) y los errores `OFERTA_VENCIDA` y `OFERTA_YA_RESPONDIDA`.

## 3. Inscripción, consulta y baja

- [x] 3.1 `Inscribir` con las validaciones de consentimiento, duplicado, profesional activo y habilitado; tests de "Una inscripción activa por prestación y con consentimiento".
- [x] 3.2 Endpoints `POST /api/v1/paciente/lista-espera` y `POST /api/v1/lista-espera`; tests de "El paciente se anota" y "La recepción anota a un paciente sin preferencia de profesional".
- [x] 3.3 `GET` del paciente y del personal, y las bajas; tests de "Consultar y dar de baja".
- [x] 3.4 Acceso por rol, `401`, `403` y CSRF; tests de "Acceso por rol".

## 4. Ofertas al liberarse un horario

- [x] 4.1 Puerto `HorarioLiberado` y su llamada desde `ServicioAgenda.cancelar` y `reprogramar`, aislada en un `try` (D2); test de que la cancelación se guarda aunque la oferta falle.
- [x] 4.2 `OfrecerHorario` con la compatibilidad de D3 (validador de la reserva online, `SKIP LOCKED`, límite de 50); tests de "Se ofrece por orden de llegada", "Una prestación distinta que entra en el horario", "La prestación no entra en el horario" y "La reprogramación libera el horario viejo".
- [x] 4.3 Tests de que la ausencia del paciente, la reasignación y un horario ya empezado no generan ofertas.
- [x] 4.4 Token con `GeneradorTokenSeguro` y hash con `HuellaToken`; tests de "Token y privacidad de la oferta".

## 5. Aviso de la oferta (`notificaciones/`, revisa Juan Cruz)

- [x] 5.1 Adaptador `AvisoOfertaNotificaciones` del puerto `AvisoOferta`, `MensajeRecordatorio.oferta` y el link `Enlaces.oferta` = `{base}/oferta/{token}`; reintento desde la tarea con token nuevo (D6).
- [x] 5.2 Tests de "Aviso de la oferta de la lista de espera": canal, respaldo por email, contenido sin prestación ni nombres, sin token guardado y reintento.

## 6. Respuesta, vencimientos y plazo

- [x] 6.1 `GET /api/v1/public/ofertas/{token}`; tests de "Ver la oferta desde el link".
- [x] 6.2 `ResponderOferta.aceptar` por el camino de la reserva online, con actor `lista-espera:<ofertaId>` y el aviso de reservado (D4); tests de "Aceptar la oferta", incluida la carrera con una reserva online.
- [x] 6.3 `ResponderOferta.rechazar`, que pasa al siguiente; tests de "Rechazar la oferta".
- [x] 6.4 `TareaListaEspera` con sus propiedades (D7); tests de "Vencimientos automáticos".
- [x] 6.5 `GET` y `PUT /api/v1/configuracion/lista-espera`; tests de "Plazo para aceptar configurable".
- [x] 6.6 `./gradlew build integrationTest` en verde.

## 7. Frontend: API, mensajes y estilos

- [x] 7.1 `lista-espera/api.ts` con los tipos y las funciones de la API, sin `any`.
- [x] 7.2 Mensajes de `YA_EN_LISTA_DE_ESPERA`, `SIN_CONSENTIMIENTO`, `INSCRIPCION_NO_ACTIVA`, `PLAZO_INVALIDO` y los de la oferta en `mensajes.ts`, con sus tests.
- [x] 7.3 Estilos `23-lista-espera.css` desde los mockups aprobados, registrados en `test/estilos.ts` y `estilos-modulos.test.ts`.

## 8. Frontend: pantallas del personal

- [x] 8.1 Página `#/lista-espera` y su opción en la barra lateral, inmediatamente después de "Agenda"; tests de "Página Lista de espera para administración y recepción". Si C-49 ya está en `master`, verificar el orden con "Coberturas" (Agenda, Lista de espera, Pacientes, Coberturas, Reportes, Configuración); si no, avisarle a Juan Sebastián para que lo respete al mergear.
- [x] 8.2 Diálogo "Anotar paciente"; tests de "Anotar a un paciente desde la recepción".
- [x] 8.3 "Dar de baja" con confirmación; tests de "Dar de baja desde la recepción".
- [x] 8.4 Plazo en la pestaña "Margen"; tests de "Plazo para aceptar en Configuración".

## 9. Frontend: pantallas del paciente

- [x] 9.1 "Anotarme en la lista de espera" en `#/reserva`; tests de "Anotarse desde la reserva".
- [x] 9.2 Sección "Lista de espera" en `#/mis-turnos`; tests de "Mi lista de espera en Mis turnos".
- [x] 9.3 `/oferta/{token}` en `entrada.ts` y la página `#/oferta`; tests de "Pantalla de la oferta" y "Responder la oferta".
- [x] 9.4 `npm test` y `npm run build` en verde.

## 10. Verificación

- [x] 10.1 Verificación visual contra los mockups a 390 × 844 y 1440 × 900, con el backend de desarrollo y datos ficticios; capturas en `mockups/verify/`.
- [x] 10.2 Recorrido completo: anotar, cancelar un turno, ver la oferta en el log del canal simulado, aceptar desde el link y ver el turno en la agenda.

## 11. Cierre

- [x] 11.1 Marcar C-15 en `CHANGES.md` y abrir el PR a `master`.
- [x] 11.2 `openspec validate lista-de-espera-automatica` sin errores (en modo estricto solo quedan avisos de longitud, como en las specs vigentes).
- [x] 11.3 Antes del archive, reconciliar "Menú según la sesión" de `frontend-navegacion` con un delta MODIFIED sobre el texto vigente (D10).
