# Tasks

> Rama `change/C-48-frontend-consentimientos`. No se empieza hasta que Emanuel apruebe los mockups (`design.md`, "UI Mockups") y responda las preguntas 11 a 13, y hasta que C-27 (PR #128) esté mergeado. Las preguntas 1 a 10 se respondieron el 2026-11-24.
>
> En cada grupo, primero los tests (Vitest con jsdom) y después el código. Cada escenario de `specs/frontend-plantillas-consentimiento/spec.md`, `specs/frontend-consentimientos/spec.md` y `specs/frontend-configuracion-consultorio/spec.md` tiene su test con el mismo nombre (regla dura 7).
>
> Criterio de aceptación visual: los mockups aprobados en `mockups/`.

## 1. Preparación

- [ ] 1.1 Avisar a Enzo (dueño de `frontend/`) y a Juan Sebastián (C-49 toca `configuracion.ts`, `permisos.ts`, `pacientes.ts`, `styles.css` y "Secciones según el rol"), y verificar que los avisos queden en la descripción del PR
- [ ] 1.2 Coordinar con C-49 según design D2 (no es una dependencia):
  - antes del apply, ver si C-49 ya está mergeado en `master` y, si lo está, rebasar y resolver `configuracion.ts` ("Obras sociales" antes de "Consentimientos"), `permisos.ts` (los dos permisos), `pacientes.ts` (cabecera, Cobertura, odontograma, periodontograma, imágenes, Consentimientos), `styles.css` y `MODULOS`/`REGLAS_POR_MODULO` (22 y 23);
  - antes del archive, si C-49 ya se archivó, sumar "Obras sociales" al `MODIFIED` de "Secciones según el rol" (la lista del `ADMIN`, "sin las tres últimas" y el escenario "El odontólogo no ve Obras sociales"); si C-48 se archiva primero, dejarlo como está y avisarle a Juan Sebastián que su delta tiene que sumar "Consentimientos";
  - verificar con `openspec validate frontend-consentimientos --strict` y leyendo `openspec/specs/frontend-configuracion-consultorio/spec.md` después del archive: tienen que estar las dos pestañas

## 2. Mensajes, modelo y pedidos

- [ ] 2.1 Sumar `PLANTILLA_DUPLICADA`, `VERSION_DESACTUALIZADA`, `PLANTILLA_INACTIVA`, `CONSENTIMIENTO_YA_REVOCADO` y `FIRMANTE_NO_COINCIDE` a `MENSAJES_POR_CODIGO` con sus tests en `test/mensajes.test.ts`, incluido que ningún mensaje contiene un DNI (design D9); verificar con `npm test`
- [ ] 2.2 Escribir los tests de `consentimientos/modelo.ts` y después el módulo (design D3); verificar con `npm test`. Los tests cubren:
  - los bordes del título (0, 1, 120, 121), el texto (20.000 y 20.001), el nombre (2, 3, 200, 201), el motivo (0, 200, 201), con espacios y NFC;
  - el DNI con puntos, espacios, letras, 6, 7, 8 y 9 dígitos;
  - el contador "1.234/20.000";
  - los nombres accesibles y las fechas "24/11/2026" y "10:30";
  - `firmaNueva` (id nuevo de la misma plantilla, versión y profesional; id que ya estaba; otra versión; otro profesional) y `firmadoHoy` (vigente de hoy, revocada, otra versión, otro día, medianoche de Buenos Aires) (design D13 y D14).
- [ ] 2.3 Escribir los tests de `consentimientos/api.ts` y después el módulo; verificar con `npm test`. Los tests cubren las rutas y cuerpos de C-27 (`incluirInactivas=true`, `versionBase`, `versionNumero`, `aceptado`, `{ activa }`, `{ motivo }`), el `201` y el `200` de la edición, `hash.formato` como número, la lectura defensiva y la firma con `AbortSignal` de 30 segundos.
- [ ] 2.4 Sumar los íconos `firma`, `verificado`, `alterado` y `candado` a `shared/ui/iconos.ts` (design D10); verificar con `npm test`

## 3. Pestaña de plantillas

- [ ] 3.1 Escribir los tests de "Secciones según el rol" y "Pestaña "Consentimientos" solo para el administrador", y después el permiso `gestionaConsentimientos` y la pestaña en `pages/configuracion.ts` (design D2); verificar con `npm test` y que los tests de C-34 y C-41 (y los de C-49, si ya está mergeado) sigan pasando
- [ ] 3.2 Escribir los tests de "Lista de plantillas", "Activar y desactivar una plantilla" y "Texto de la plantilla como texto plano", y después la lista en `consentimientos/plantillas.ts`; verificar con `npm test`
- [ ] 3.3 Escribir los tests de "Crear una plantilla", "Editor de texto plano con contador", "Editar una plantilla crea una versión nueva", "Conflicto de versión al editar" y "Descartar una edición sin guardar", y después el diálogo del editor (design D4); verificar con `npm test`
- [ ] 3.4 Escribir los tests de "Ver las versiones de una plantilla" y "Pestaña de plantillas accesible", y después el diálogo de versiones; verificar con `npm test`

## 4. Sección de la ficha y documento firmado

- [ ] 4.1 Escribir los tests de "Sección de consentimientos en la ficha" y "Listado de consentimientos firmados", y después `consentimientos/seccion.ts` y su montaje en `pages/pacientes.ts` después de las imágenes, con `destruir()`; verificar con `npm test` y que los tests de C-44, C-46 y C-47 sigan pasando
- [ ] 4.2 Escribir los tests de "Ver un consentimiento firmado" y "Verificar al abrir" (incluida la verificación lenta, que no muestra "Íntegro" antes de tiempo), y después `consentimientos/documento.ts` (design D7); verificar con `npm test`
- [ ] 4.3 Escribir los tests de "Revocar un consentimiento", y después el diálogo de revocación (design D8); verificar con `npm test`
- [ ] 4.4 Crear `styles/25-consentimientos.css` a partir de los mockups aprobados, importarlo en `styles.css` y registrarlo en `MODULOS` de `test/estilos.ts` y en `REGLAS_POR_MODULO` de `test/estilos-modulos.test.ts`; verificar con `npm test` y `npm run build` (design D10)

## 5. Firma

- [ ] 5.1 Escribir los tests de "Elegir la plantilla para firmar" y "Aviso antes de firmar dos veces", y después el diálogo de elección con el listado pedido en paralelo y la pregunta de doble firma (design D6 y D14); verificar con `npm test`
- [ ] 5.2 Escribir los tests de "Vista de firma a pantalla completa", "Nombre y DNI escritos por el paciente", "Firma electrónica, no firma digital" y "Validar la firma antes de enviarla", y después `consentimientos/firma.ts` (design D5 y D15); verificar con `npm test`, incluido un test que compara el texto mostrado con el de la versión cargada y otro que busca el DNI de la ficha en el texto y los atributos de la vista
- [ ] 5.3 Escribir los tests de "Firmar el consentimiento" (incluido "Un solo envío"), "DNI que no coincide", "El texto cambió mientras se leía", "Plantilla desactivada durante la firma", "Cancelar la firma" y "Cierre de la vista por el navegador", y después el envío y sus errores; verificar con `npm test`

## 6. Bloqueo, desbloqueo y comprobación

- [ ] 6.1 Escribir los tests de "Devolver el equipo al profesional", "Desbloquear con la contraseña del profesional" y "Contraseña incorrecta en el bloqueo", y después `consentimientos/bloqueo.ts` (design D12): ficha vaciada, bloqueo en `document.body` con la raíz `inert`, `POST /api/v1/logout` con reintento, `iniciarSesion` con el usuario fijo, comprobación del usuario y el rol, tres intentos y errores de red; verificar con `npm test`
- [ ] 6.2 Escribir los tests de "Marca de firma en curso" y "Recargar durante la firma o el bloqueo", y después la marca en `firma.ts` y `bloqueo.ts` y el logout al arrancar en `app.ts` (design D12); verificar con `npm test` y que los tests de navegación y sesión sigan pasando
- [ ] 6.3 Escribir los tests de "Sesión vencida durante la firma", "Envío dudoso de la firma" y "Comprobar si la firma quedó guardada" (incluido que nunca dice "no se guardó" sin una respuesta del listado), y después la comprobación en `seccion.ts` (design D13); verificar con `npm test`
- [ ] 6.4 Probar a mano en el navegador, con la sesión de desarrollo, que Escape (dos veces), atrás, recargar y abrir otra pestaña durante la firma y el bloqueo no muestran la ficha ni el buscador de pacientes; anotar el resultado en `design.md`

## 7. Permisos, accesibilidad y privacidad

- [ ] 7.1 Escribir los tests de "Solo lectura para el administrador" y "Sin acceso para la recepción y el paciente"; verificar con `npm test`
- [ ] 7.2 Escribir los tests de "Consentimientos accesibles" (nombres, foco al abrir la vista y el bloqueo, foco al cerrar, avisos anunciados); verificar con `npm test`
- [ ] 7.3 Escribir los tests de "Consentimientos solo en la pantalla": nada en la dirección, `localStorage` (salvo la marca `"1"`), `sessionStorage` ni la consola, `autocomplete="off"` y la vista, el bloqueo y el documento fuera del DOM al cerrar o cambiar de paciente; verificar con `npm test`
- [ ] 7.4 Verificar que ningún archivo de `frontend/src/` contiene "firma digital" (`grep -ri "firma digital" frontend/src` sin resultados)

## 8. Verificación

- [ ] 8.1 Correr `npm test`, `npx tsc --noEmit` y `npm run build` y verificar que todo pase
- [ ] 8.2 Hacer la verificación visual contra el backend de desarrollo con C-27:
  - datos ficticios, entrando como `admin` y como `ana.ejemplo`, a 390 × 844 y 1440 × 900;
  - crear y editar una plantilla, ver sus versiones, desactivarla; firmar, desbloquear, ver con "Íntegro", revocar; cancelar una firma; una contraseña mala en el bloqueo; el aviso de doble firma;
  - comparar cada pantalla con su mockup, revisar la consola y los pedidos fallidos, y que la dirección, `localStorage` y `sessionStorage` no tengan datos;
  - guardar las capturas en `mockups/verify/` y anotar las diferencias en `design.md`.
- [ ] 8.3 Marcar C-48 en `CHANGES.md` y abrir el PR a `master` con Enzo y Sofia como revisores

## Workflow follow-up

- Archivar el change después del merge y después del archive de C-27. Con C-49, aplicar la regla del segundo (design D2, tarea 1.2): no hace falta esperar a que C-49 se archive.
