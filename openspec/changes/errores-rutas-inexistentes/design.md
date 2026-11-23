## Context

C-05 (`auth-sesion-roles`) armó la cadena de filtros de `SecurityConfig` con estas reglas:

1. `/api/v1/public/**` → `permitAll`.
2. `/api/v1/paciente/**` → `PACIENTE`.
3. `/api/v1/**` → los roles del personal.
4. `anyRequest()` → `denyAll`.

En Spring Boot 3.5 (Spring Security 6), la autorización también se aplica al despacho interno de error (`DispatcherType.ERROR`). Esto pasa en dos casos:

- **Ruta inexistente:** Spring MVC lanza `NoResourceFoundException` y responde con `sendError(404)`, que hace un *forward* interno a `/error`.
- **Excepción no controlada:** pasa lo mismo con un `500`.

`/error` solo coincide con la regla 4, así que el *forward* termina en `ProblemDetailAccessDeniedHandler` → `403 ACCESO_DENEGADO`. Lo reproduje el 2026-11-23 con `GET /api/v1/no-existe` y la sesión `admin` → `403`.

Cada módulo tiene su `@RestControllerAdvice` (`AgendaExceptionHandler`, `UsuariosExceptionHandler`…), pero ninguno maneja `NoResourceFoundException` ni `Exception` genérica.

## Goals / Non-Goals

**Goals**
- Que el personal reciba `404 RECURSO_INEXISTENTE` en una ruta inexistente y `500 ERROR_INTERNO` en un error no controlado, con el mismo formato `ProblemDetail` + `codigo` del resto de la API.
- No aflojar ninguna regla de C-05.

**Non-Goals**
- Cambiar los códigos `401` y `403` o el orden de las reglas.
- Hacer públicas las rutas inexistentes.
- Tocar el frontend.
- Exponer o activar Actuator.

## Decisions

### D1. Manejador global de `ProblemDetail` en `shared/` *(a confirmar)*
Un `@RestControllerAdvice` con la prioridad más baja (`@Order(Ordered.LOWEST_PRECEDENCE)`), para que los manejadores de cada módulo sigan ganando. Maneja:
- `NoResourceFoundException` → `404 RECURSO_INEXISTENTE`, "El recurso pedido no existe".
- `Exception` → `500 ERROR_INTERNO`, "Ocurrió un error inesperado". Registra la excepción en el log con `log.error` y la traza, pero sin parámetros del pedido (rule 6 y RN-NO-01: la query puede traer un DNI).

Así el error se resuelve dentro del `DispatcherServlet`, sin *forward* a `/error`, y la respuesta lleva `codigo` como el resto de la API (hard rule 9).

**Alternativa descartada como única solución:** solo permitir el despacho `ERROR` (ver D2). Funciona, pero el cuerpo lo arma el `BasicErrorController` de Boot, sin `codigo`.

### D2. Permitir solo el despacho interno de error *(a confirmar)*
Se agrega `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` como primera regla, que es la recomendación de la documentación de Spring Security 6. Cubre los errores que no pasan por D1, como los que se producen en un filtro o en `sendError` de otra capa: el estado HTTP real llega al cliente en lugar de un `403`.

- Un `GET /error` pedido por el navegador es un despacho `REQUEST`, no `ERROR`, así que sigue denegado por `denyAll`.
- Boot no expone la traza ni el mensaje por defecto (`server.error.include-stacktrace=never` e `include-message=never`). La tarea 1.2 lo fija explícitamente en `application.yml` para que no dependa del valor por defecto.

**Recomendado: D1 + D2 juntos.** D1 da el formato correcto en los casos comunes y D2 evita que cualquier otro error quede disfrazado de `403`.

### D3. Sin sesión sigue siendo `401`
La autorización corre antes del `DispatcherServlet`. Un pedido sin sesión a `/api/v1/no-existe` coincide con la regla 3 y el `AuthenticationEntryPoint` responde `401` antes de saber si la ruta existe. Así nadie puede averiguar sin sesión qué rutas existen. Con una sesión `PACIENTE` pasa lo mismo: `403` antes de la resolución. No requiere código; se prueba.

## Risks / Trade-offs

- **Un `@RestControllerAdvice` global con `Exception` podría tapar las excepciones de seguridad de los métodos.** Con `@PreAuthorize`, Spring lanza `AccessDeniedException` dentro del controller y, si el advice la capturara, devolvería `500` en vez de `403`. **Mitigación:** el manejador relanza `AccessDeniedException` y `AuthenticationException`, o las excluye explícitamente. Lo prueba el escenario existente "Operación de ADMIN pedida por recepción".
- **Prioridad frente a los advices de los módulos.** Los advices existentes manejan excepciones específicas. Con la prioridad más baja, el global solo atiende lo que nadie más maneja. Se verifica corriendo la suite completa.
- **El log de un `500`** registra la traza, que puede incluir el mensaje de una excepción de SQL con valores. **Mitigación:** se registra solo el tipo de excepción y la traza, sin `request.getQueryString()`. Queda anotado para C-14.

## Migration Plan

Sin migración de datos. El deploy es el backend solo. El rollback es revertir el PR.

## Open Questions

- ¿La líder lo registra en `CHANGES.md` como fix de C-05 o como ficha propia del MVP (fase 5b)?
