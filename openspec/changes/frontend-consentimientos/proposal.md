# Proposal

## Why

C-27 dejó en el backend los consentimientos informados con **firma electrónica** (Ley 25.506, art. 5): plantillas con versiones, firma en el consultorio, copia inmutable del texto, hash SHA-256, verificación y revocación. Pero ninguna pantalla lo usa: el `ADMIN` no puede cargar las plantillas y el odontólogo no puede hacer firmar al paciente. C-48 suma la interfaz, sin cambiar el contrato de la API, con las decisiones que tomó Emanuel el 2026-11-24.

## What Changes

- **Pestaña "Consentimientos" en Configuración** (`#/configuracion`), solo para `ADMIN`, última de la tira. No suma una entrada en la barra lateral.
  - Lista de plantillas activas e inactivas, con el título, la versión actual, la fecha de la última versión y el estado escrito.
  - **Nueva plantilla** y **Editar**: un diálogo con el título (hasta 120 caracteres) y un área de texto grande con un contador de **20.000 caracteres**. El texto es **texto plano**: se muestra con sus saltos de línea y nunca se interpreta como HTML.
  - Cada edición con cambios crea una **versión nueva**: se manda `versionBase` y se manejan `409 VERSION_DESACTUALIZADA` y `PLANTILLA_DUPLICADA`. Una edición sin cambios avisa que la versión no cambió.
  - **Versiones**: un diálogo con todas las versiones, de la más nueva a la más vieja, con quién y cuándo, y el texto de cada una.
  - **Activar y desactivar**, sin borrar.
- **Sección "Consentimientos" en la ficha clínica** (`#/pacientes`, C-44), después de "Imágenes y estudios" (C-47), para `ODONTOLOGO` y `ADMIN`. Se carga aparte, con su propio error y "Reintentar".
  - Lista de los consentimientos firmados, del más nuevo al más viejo, con el estado **"Vigente"** o **"Revocado"** escrito en una etiqueta (no solo por color).
  - **Ver**: abre el documento firmado y **pide la verificación**. Al lado del documento se lee **"Íntegro"** o **"Alterado"**, con el firmante, la fecha y hora, el profesional y, en un bloque de evidencia que se despliega, la IP, el dispositivo y el hash.
  - **Firmar consentimiento** (solo `ODONTOLOGO`): el odontólogo elige una plantilla activa y se abre una **vista de firma a pantalla completa que tapa la ficha**: el título y el texto de la plantilla, el **nombre y apellido del paciente** para que confirme que es suyo, "Nombre completo" y "DNI" **vacíos, que escribe el paciente** (el DNI de la ficha nunca se muestra), y la casilla "Leí y acepto", con "Cancelar" y "Firmar". Dice "firma electrónica" y nunca "firma digital".
  - **Aviso de doble firma**: si el paciente ya tiene vigente la misma plantilla y versión firmada hoy, se pregunta antes de abrir la vista. Mientras se envía, un solo pedido y "Firmar" deshabilitado.
  - **"Devolvé el equipo al profesional"**: al firmar, cancelar o fallar, la vista se cierra, la ficha se quita de la página, se cierra la sesión (`POST /api/v1/logout`) y queda una pantalla de bloqueo. Se desbloquea con la contraseña del odontólogo (`POST /api/v1/public/login` con su usuario fijo); tres fallos llevan al ingreso. Una marca sin datos en `localStorage` hace que recargar o abrir otra pestaña cierre la sesión.
  - Errores de la firma: `422 FIRMANTE_NO_COINCIDE` (sin repetir el DNI escrito), `409 VERSION_DESACTUALIZADA` (se muestra el texto nuevo para volver a leerlo) y `409 PLANTILLA_INACTIVA`. Ante un envío dudoso (red, 30 s sin respuesta, `5xx`), después de desbloquear se **comprueba en el listado si la firma quedó guardada** antes de decirlo; un `401` es "no se guardó".
  - **Revocar** (solo `ODONTOLOGO`, consentimiento vigente): un diálogo con el motivo obligatorio de hasta 200 caracteres; maneja `409 CONSENTIMIENTO_YA_REVOCADO`.
  - El `ADMIN` ve y verifica, sin "Firmar consentimiento" ni "Revocar". La recepción y el paciente no acceden (RN-CL-01): no ven la ficha ni la pestaña, y no se pide nada.
- **Privacidad** (regla dura 6): ningún dato de un consentimiento en la dirección, `localStorage`, `sessionStorage` ni la consola (solo la marca `turnos.firmaEnCurso = "1"`, sin datos); el nombre y el DNI del firmante no quedan en el autocompletado del navegador.
- **Mensajes nuevos** para `PLANTILLA_DUPLICADA`, `VERSION_DESACTUALIZADA`, `PLANTILLA_INACTIVA`, `CONSENTIMIENTO_YA_REVOCADO` y `FIRMANTE_NO_COINCIDE`.
- **Design system**: se reusan los tokens de la Revision 5, sin tokens nuevos. Un módulo de estilos nuevo.
- Sin cambios de backend, del cliente de la API ni migraciones. El bloqueo usa el login y el logout que ya existen (design D12).

## Capabilities

### New Capabilities
- `frontend-plantillas-consentimiento`: la pestaña "Consentimientos" de Configuración para el `ADMIN`: lista, alta, edición con versiones, conflicto de versión, versiones anteriores, activar y desactivar, editor de texto plano con contador y errores.
- `frontend-consentimientos`: la sección "Consentimientos" de la ficha clínica: listado con su estado, elección de la plantilla, aviso de doble firma, vista de firma a pantalla completa, errores de la firma, bloqueo "Devolvé el equipo al profesional" con desbloqueo, comprobación de un envío dudoso, documento firmado con su verificación, revocación con motivo, solo lectura del `ADMIN`, accesibilidad y privacidad.

### Modified Capabilities
- `frontend-configuracion-consultorio`: "Secciones según el rol" suma la pestaña "Consentimientos" para `ADMIN`.

<!-- frontend-ficha-clinica no se modifica: igual que C-47 (design D1), las reglas de solo lectura y de datos de salud van como requisitos propios de frontend-consentimientos, para no abrir otro delta sobre ese spec. frontend-cliente-api tampoco cambia: los pedidos son JSON y el 422 y el 409 ya llegan con su código. -->

## Impact

- **Código** (en el apply, no en este propose):
  - `frontend/src/consentimientos/`: módulos nuevos de pedidos, modelo puro, pestaña de plantillas, sección de la ficha, vista de firma, bloqueo y documento firmado.
  - `frontend/src/app.ts`: al arrancar con la marca `turnos.firmaEnCurso`, cierra la sesión antes de dibujar (design D12).
  - `frontend/src/pages/configuracion.ts` y `frontend/src/configuracion/permisos.ts`: la pestaña y el permiso `gestionaConsentimientos`.
  - `frontend/src/pages/pacientes.ts`: monta la sección después de las imágenes, llama a `destruir()` al cambiar de paciente y vacía la ficha al bloquear.
  - `frontend/src/shared/ui/mensajes.ts` (cinco códigos) e `iconos.ts` (íconos de firma, verificado, alterado y candado).
  - Un módulo de estilos nuevo, `frontend/src/styles/28-consentimientos.css` (del 22 al 27 ya están en `master`: caja, lista de espera, coberturas, presupuestos, señas y consentimiento de comunicaciones).
- **Avisos**:
  - `frontend/` es de Enzo: se le avisa y revisa el PR.
  - C-49 (`frontend-obras-sociales`, Juan Sebastián) también toca `configuracion.ts`, `permisos.ts`, `pacientes.ts`, `styles.css` y el requisito "Secciones según el rol". **No es una dependencia**: C-48 no usa nada de C-49. El delta de C-48 va contra el spec principal actual; el que se archive segundo incluye la pestaña del otro y el que se aplique segundo rebasa (design D2).
- **API**: usa los endpoints de C-27 bajo `/api/v1/consentimientos/plantillas` y `/api/v1/pacientes/{id}/consentimientos`, sin cambiarlos. El apply necesita C-27 mergeado (PR #128).
- **Tests**: Vitest con jsdom, un test por escenario. Los tests de C-44, C-46, C-47 y C-49 siguen pasando.
- **Governance**: CRITICO (validez legal de la firma). Emanuel respondió las preguntas 1 a 10 el 2026-11-24 (`design.md`, "Open Questions"); quedan las nuevas 11 a 13.
