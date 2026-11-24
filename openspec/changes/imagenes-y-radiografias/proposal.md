# Proposal

## Why

La ficha clínica (C-13 y C-44) registra la ficha básica, el odontograma y, desde C-25, el periodontograma. Pero el odontólogo no tiene dónde guardar las radiografías, las fotos intraorales ni los estudios del paciente, y hoy los conserva fuera del sistema. C-26 es el siguiente change de clínica avanzada del post-MVP. Las reglas que faltaban en la KB (solo existía la línea US-203 del backlog) las fijó Emanuel el 2026-11-24 y van en este mismo PR para que la líder las revise: RN-CL-07, RN-CL-08, US-047 y DD-15.

## What Changes

- **Carga de imágenes clínicas**: el odontólogo sube un archivo del paciente con:
  - su categoría (radiografía, fotografía o estudio);
  - la fecha de la toma, que no puede ser futura;
  - opcionalmente la pieza (notación FDI, permanentes o temporales) y una descripción corta.
  
  El sistema guarda quién la cargó y cuándo (RN-CL-07).
- **Archivos permitidos**: JPEG, PNG y PDF de hasta **10 MB**. El tipo se reconoce por el contenido del archivo, no por la extensión ni por el tipo que declara el navegador. Un archivo vacío, de otro tipo o más grande se rechaza con un código estable y sin guardar nada.
- **Almacenamiento detrás de un puerto**: el contenido va a un almacén de archivos y la base guarda solo los metadatos, el tamaño y el hash SHA-256 (DD-15).
  - El único adaptador de este change escribe en una carpeta del disco, configurable por variable de entorno.
  - Un adaptador compatible con S3 queda como extensión futura.
- **Consulta**:
  - Listar las imágenes del paciente, de la toma más nueva a la más vieja, con un filtro opcional por pieza.
  - Descargar o ver el archivo con su tipo de contenido, sin caché y con un nombre de archivo genérico, que no lleva el nombre del paciente.
- **Anulación con motivo**: una imagen nunca se borra ni se modifica. El odontólogo la anula con un motivo, y queda registrado quién y cuándo (RN-CL-08).
  - Las anuladas no aparecen en el listado salvo que se pidan.
  - Siguen descargables para auditoría.
- **Acceso** igual que la ficha (RN-CL-01):
  - el odontólogo carga, consulta y anula;
  - el administrador solo consulta;
  - la recepción y el paciente reciben `403` sin datos.
- **Migración V17** (número reservado en CHANGES.md): una tabla de solo agregado. Lo único que se puede cambiar de una fila es su anulación, que se hace una sola vez.
- **Solo backend**. La pantalla (carga, listado, visualización y descarga dentro de la ficha clínica) se registra en CHANGES.md como un change nuevo, **C-47 `frontend-imagenes-y-radiografias`**, que depende de C-26 y C-44 y lleva mockups aprobados antes del apply.

## Capabilities

### New Capabilities
- `imagenes-clinicas`: cubre
  - la carga de imágenes y estudios del paciente, con validación del tipo real y del tamaño;
  - el almacenamiento del contenido detrás de un puerto, con integridad por hash;
  - el listado, la descarga y la anulación con motivo;
  - el control de acceso por rol y la privacidad de los datos de salud.

### Modified Capabilities
<!-- Ninguna: ficha-clinica no cambia. Las imágenes reutilizan sus reglas de acceso y de privacidad como requisitos propios, igual que el periodontograma de C-25. -->

## Impact

- **Código**: `backend/.../clinica/` (dominio, aplicación, API e infraestructura nuevos). El módulo es de Emanuel.
  - El manejo del error de tamaño de la subida (`413`) va en el manejador global `shared/web/ProblemDetailGlobalHandler`, porque Spring lo lanza antes de elegir el controller. Se avisa en el PR.
- **Base de datos**: `V17__imagenes_clinicas.sql`. Flyway corre con out-of-order, así que V17 se aplica aunque V21 y V22 ya estén en `master`.
- **API**: cuatro endpoints nuevos bajo `/api/v1/pacientes/{id}/imagenes`. Ninguno existente cambia.
- **Configuración**:
  - Límites de multipart de Spring (`max-file-size` 10 MB).
  - Variable nueva `IMAGENES_DIRECTORIO`, con un valor por defecto para desarrollo dentro de una carpeta ignorada por git.
  - Se documenta en `knowledge-base/08_arquitectura_propuesta.md` y en `.env.example`.
- **Seguridad**:
  - Mismos permisos que la ficha (`@PreAuthorize`, sesión con CSRF activo, también en la subida multipart).
  - Ni la descripción ni los metadatos de salud van a logs ni a mensajes de error (regla dura 6).
  - La clave del archivo en el almacén la genera el servidor y no contiene datos del paciente.
- **KB**: se actualizan
  - `03_actores_y_roles.md` (fila de imágenes en la matriz de permisos);
  - `05_reglas_de_negocio.md` (RN-CL-07 y RN-CL-08);
  - `06_funcionalidades.md` (US-047 reemplaza la línea US-203 del backlog);
  - `04_modelo_de_datos.md` (entidad `ImagenClinica`);
  - `09_decisiones_y_supuestos.md` (DD-15, almacén de archivos);
  - `08_arquitectura_propuesta.md` (variable de entorno).
- **Roadmap**: `CHANGES.md` registra C-47 y marca C-26 en curso.
