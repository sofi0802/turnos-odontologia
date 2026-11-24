# Tasks

> Rama `change/C-47-frontend-imagenes-y-radiografias`. No se empieza hasta que la líder apruebe los mockups (`design.md`, "UI Mockups").
>
> En cada grupo, primero los tests (Vitest con jsdom) y después el código. Cada escenario de `specs/frontend-imagenes-clinicas/spec.md` y de `specs/frontend-cliente-api/spec.md` tiene su test con el mismo nombre (regla dura 7).
>
> Criterio de aceptación visual: los mockups aprobados en `mockups/`.

## 1. Preparación

- [ ] 1.1 Avisar a Enzo (dueño de `frontend/`) y verificar que el aviso quede en la descripción del PR, junto con el cambio en `pages/pacientes.ts`, que también tocó C-46

## 2. Cliente de la API

- [ ] 2.1 Escribir los tests de "Envío de un formulario con archivos", "Descarga de un archivo", "Error de validación ante un 413" y "Otras fallas sin detalles internos" en `test/cliente.test.ts`, y después `enviarFormulario`, `obtenerArchivo` y el 413 en `traducirError` (design D3); verificar con `npm test`
- [ ] 2.2 Sumar `TIPO_ARCHIVO_NO_PERMITIDO`, `ARCHIVO_DEMASIADO_GRANDE` e `IMAGEN_YA_ANULADA` a `MENSAJES_POR_CODIGO` con sus tests en `test/mensajes.test.ts` (design D8); verificar con `npm test`

## 3. Modelo y pedidos

- [ ] 3.1 Escribir los tests de `ficha/imagenes-modelo.ts` y después el módulo; verificar con `npm test`. Los tests cubren:
  - el tipo por MIME y extensión;
  - el borde de 10 MB;
  - las piezas FDI válidas e inválidas (11, 48, 51, 85, 19, 90);
  - la fecha de hoy y la de mañana en la zona del consultorio;
  - la descripción de 200 y 201 caracteres;
  - los nombres accesibles con y sin pieza;
  - el formato del peso.
- [ ] 3.2 Escribir los tests de `ficha/imagenes-api.ts` y después el módulo; verificar con `npm test`. Los tests cubren:
  - la lectura defensiva del listado y de la anulación;
  - las rutas y parámetros de C-26;
  - el `FormData` con las partes `archivo`, `categoria`, `tomadaEl`, `pieza` y `descripcion`.
- [ ] 3.3 Sumar los íconos `imagen`, `documento`, `descarga`, `ver` y `subir` a `shared/ui/iconos.ts` (design D10); verificar con `npm test`

## 4. Listado, visor y descarga

- [ ] 4.1 Escribir los tests de estos requisitos, y después `ficha/imagenes.ts` con el listado y los filtros; verificar con `npm test`:
  - "Sección de imágenes en la ficha";
  - "Listado de imágenes";
  - "Filtrar por pieza y mostrar anuladas";
  - "Sección de imágenes accesible".
- [ ] 4.2 Escribir los tests de "Ver una imagen" y "Descargar un archivo" (incluido que el PDF no tiene "Ver" y que se revoca el object URL), y después el visor y la descarga (design D5); verificar con `npm test`
- [ ] 4.3 Crear `styles/21-imagenes.css` (design D9) a partir de los mockups aprobados e importarlo en `styles.css`; registrarlo en `test/estilos.ts` (`MODULOS` y `REGLAS_POR_MODULO`) y verificar con `npm test` y `npm run build`

## 5. Carga y anulación

- [ ] 5.1 Escribir los tests de "Cargar una imagen", "Validar el archivo antes de enviarlo" y "Errores de la API al cargar", y después el diálogo de carga (design D6); verificar con `npm test`
- [ ] 5.2 Escribir los tests de "Descartar una carga sin guardar" (cancelar y cambiar de paciente), y después sumar `sinGuardar()` y `destruir()` a `pages/pacientes.ts`; verificar con `npm test` y que los tests de C-44 y C-46 sigan pasando
- [ ] 5.3 Escribir los tests de "Anular una imagen" (incluido el 409 `IMAGEN_YA_ANULADA`), y después el diálogo de anulación (design D7); verificar con `npm test`

## 6. Permisos y privacidad

- [ ] 6.1 Escribir los tests de "Solo lectura para el administrador" (sin "Cargar imagen" ni "Anular"), y verificar con `npm test`
- [ ] 6.2 Escribir los tests de "Imágenes solo en la pantalla": ningún dato ni archivo en la dirección, `localStorage`, `sessionStorage` ni la consola, y object URLs revocados al salir. Verificar con `npm test`

## 7. Verificación

- [ ] 7.1 Correr `npm test`, `npx tsc --noEmit` y `npm run build` y verificar que todo pase, incluidos los tests de C-44 y C-46
- [ ] 7.2 Hacer la verificación visual contra el backend de desarrollo con C-26:
  - usar datos ficticios e imágenes de prueba generadas, entrando como `ana.ejemplo` y como `admin`, a 390 × 844 y 1440 × 900;
  - comparar cada pantalla con su mockup;
  - revisar la consola y los pedidos fallidos;
  - guardar las capturas en `mockups/verify/`;
  - anotar las diferencias en `design.md`.
- [ ] 7.3 Marcar C-47 en `CHANGES.md` y abrir el PR a `master` con Enzo y Sofia como revisores

## Workflow follow-up

- Archivar el change después del merge, junto con el archive de C-26 o después de él.
