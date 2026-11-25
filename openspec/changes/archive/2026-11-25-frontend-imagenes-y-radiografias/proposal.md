# Proposal

## Why

C-26 hizo el backend de las imágenes clínicas: carga de JPEG, PNG y PDF de hasta 10 MB, listado, descarga y anulación con motivo. Pero ninguna pantalla lo usa, y el odontólogo sigue sin poder guardar las radiografías del paciente desde la aplicación. C-47 suma la interfaz en la ficha clínica de C-44, con las decisiones que tomó Emanuel el 2026-11-24.

## What Changes

- **Sección "Imágenes y estudios"** en la ficha (`#/pacientes`), debajo del periodontograma de C-46, para `ODONTOLOGO` y `ADMIN`.
  - No suma una entrada en la barra lateral.
  - Se carga aparte del resto de la ficha, con su propio error y "Reintentar".
- **Listado sin miniaturas**, de la toma más nueva a la más vieja.
  - Cada fila muestra la categoría, la fecha de la toma, la pieza, la descripción y quién la cargó.
  - Se puede filtrar por pieza y elegir "Mostrar anuladas"; las anuladas muestran quién las anuló, cuándo y el motivo.
  - El archivo se pide recién cuando se abre o se descarga.
- **Ver y descargar**:
  - Las imágenes (JPEG y PNG) se ven en un visor dentro de la página.
  - Los PDF no se muestran: solo se descargan.
  - Las imágenes también se pueden descargar.
- **Cargar imagen** (solo `ODONTOLOGO`): un botón abre un diálogo con archivo, categoría, fecha de la toma, pieza opcional y descripción opcional.
  - La fecha arranca en hoy y no puede ser futura.
  - Antes de enviar, se valida el tipo y el tamaño del archivo.
  - Se muestran los errores de la API.
  - Si se cierra el diálogo con datos cargados, se pide confirmación.
- **Anular con motivo** (solo `ODONTOLOGO`): un diálogo pide el motivo y confirma; la imagen queda anulada y sale del listado.
- **Mensajes nuevos** para `TIPO_ARCHIVO_NO_PERMITIDO`, `ARCHIVO_DEMASIADO_GRANDE` e `IMAGEN_YA_ANULADA`.
- **El cliente de la API** suma el envío de formularios multipart y la descarga de archivos, con el mismo manejo de CSRF y de errores, y traduce el `413` como un error de validación.
- **Design system**: se reusan los tokens de la Revision 5, sin tokens nuevos.
- Sin cambios de backend ni migraciones.

## Capabilities

### New Capabilities
- `frontend-imagenes-clinicas`: la sección de imágenes y estudios de la ficha clínica del frontend. Cubre:
  - la carga independiente;
  - el listado con sus filtros;
  - el visor de imágenes y la descarga;
  - la carga con validación previa;
  - la anulación con motivo;
  - los errores;
  - la accesibilidad;
  - el acceso por rol;
  - la privacidad de los datos de salud.

### Modified Capabilities
- `frontend-cliente-api`: el cliente suma el envío multipart y la descarga de archivos, y el `413` pasa a ser un error de validación con su código.

<!-- frontend-ficha-clinica no se modifica: C-46 todavía no se archivó y tiene su propio delta sobre "Solo lectura para el administrador" y "Datos de salud solo en la pantalla". Las imágenes llevan esas reglas como requisitos propios (design D1). -->

## Impact

- **Código**:
  - `frontend/src/ficha/`: módulos nuevos de las imágenes y sus pedidos.
  - `frontend/src/pages/pacientes.ts`: monta la sección y suma la confirmación al cambiar de paciente.
  - `frontend/src/shared/api/cliente.ts`: multipart, descarga y `413`.
  - `frontend/src/shared/ui/mensajes.ts`: tres códigos nuevos.
  - `frontend/src/shared/ui/iconos.ts`: íconos de imagen, documento y descarga.
  - Un módulo de estilos nuevo, `frontend/src/styles/21-imagenes.css`.
- **Avisos**:
  - `frontend/` es de Enzo: se le avisa y revisa el PR.
  - `pacientes.ts` también lo tocó C-46 (Sofia): el apply parte de `master` con C-46 ya mergeado.
- **API**: usa los cuatro endpoints de C-26 bajo `/api/v1/pacientes/{id}/imagenes`, sin cambiarlos.
- **Tests**: Vitest con jsdom, con un test por escenario. Los tests de C-44 y C-46 siguen pasando.
