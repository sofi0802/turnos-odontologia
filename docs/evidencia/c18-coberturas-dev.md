# C-18: verificación del seed de desarrollo de coberturas (tarea 6.2)

Fecha: 2026-11-24. Backend con el perfil `dev` (`./gradlew bootRun`, puerto 8081) contra una base PostgreSQL 16 descartable
(`c18_dev`, en un contenedor aparte del que usa el equipo). Datos ficticios; la cuenta ADMIN es ficticia y su contraseña
se generó para la prueba y no se guardó. Las salidas son las respuestas reales de la API.

### A. Base nueva
## A-base-nueva

### Migraciones aplicadas (flyway_schema_history, por orden de instalación)
11 | 12 | coberturas | t
12 | 12.1 | seed coberturas dev | t
14 | 21 | cuentas del personal | t
15 | 22 | llegada turno | t

### Inicio de sesión del ADMIN ficticio: HTTP 200 (credenciales omitidas)

### GET /api/v1/pacientes/1/cobertura  (Paciente Uno (99000001))
{"particular":false,"afiliacion":{"id":1,"obraSocial":{"id":1,"nombre":"Obra Social Ejemplo","sigla":"OSE","tipo":"OBRA_SOCIAL"},"plan":{"id":2,"nombre":"Plan Base"},"numeroAfiliado":"OSE-000001","vigenteDesde":"2026-01-01","vigenteHasta":null,"vigente":true}}
HTTP 200

### GET /api/v1/pacientes/2/cobertura  (Paciente Dos (99000002))
{"particular":true,"afiliacion":null}
HTTP 200

### GET /api/v1/pacientes/1/afiliaciones
[{"id":1,"obraSocial":{"id":1,"nombre":"Obra Social Ejemplo","sigla":"OSE","tipo":"OBRA_SOCIAL"},"plan":{"id":2,"nombre":"Plan Base"},"numeroAfiliado":"OSE-000001","vigenteDesde":"2026-01-01","vigenteHasta":null,"vigente":true}]
HTTP 200

### GET /api/v1/obras-sociales
[{"id":1,"nombre":"Obra Social Ejemplo","sigla":"OSE","tipo":"OBRA_SOCIAL","activa":true,"planes":[{"id":2,"obraSocialId":1,"nombre":"Plan Base","activo":true},{"id":1,"obraSocialId":1,"nombre":"Plan Integral","activo":true}]},{"id":2,"nombre":"Prepaga Prueba","sigla":null,"tipo":"PREPAGA","activa":true,"planes":[{"id":3,"obraSocialId":2,"nombre":"Plan Oro","activo":true}]}]
HTTP 200

### GET /api/v1/nomenclador
[{"id":1,"codigo":"N-0101","descripcion":"Consulta","prestacion":{"id":1,"nombre":"Consulta o control"},"activo":true},{"id":2,"codigo":"N-0201","descripcion":"Limpieza","prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"activo":true},{"id":3,"codigo":"N-0301","descripcion":"Extracción simple","prestacion":null,"activo":true}]
HTTP 200

### B. Base con V22 ya aplicada (V12 y V12.1 entran fuera de orden)
## B-con-v22

### Migraciones aplicadas (flyway_schema_history, por orden de instalación)
14 | 21 | cuentas del personal | t
15 | 22 | llegada turno | t
16 | 12 | coberturas | t
17 | 12.1 | seed coberturas dev | t

### Inicio de sesión del ADMIN ficticio: HTTP 200 (credenciales omitidas)

### GET /api/v1/pacientes/3/cobertura  (Paciente Uno (99000001))
{"particular":false,"afiliacion":{"id":1,"obraSocial":{"id":1,"nombre":"Obra Social Ejemplo","sigla":"OSE","tipo":"OBRA_SOCIAL"},"plan":{"id":2,"nombre":"Plan Base"},"numeroAfiliado":"OSE-000001","vigenteDesde":"2026-01-01","vigenteHasta":null,"vigente":true}}
HTTP 200

### GET /api/v1/pacientes/4/cobertura  (Paciente Dos (99000002))
{"particular":true,"afiliacion":null}
HTTP 200

### GET /api/v1/pacientes/3/afiliaciones
[{"id":1,"obraSocial":{"id":1,"nombre":"Obra Social Ejemplo","sigla":"OSE","tipo":"OBRA_SOCIAL"},"plan":{"id":2,"nombre":"Plan Base"},"numeroAfiliado":"OSE-000001","vigenteDesde":"2026-01-01","vigenteHasta":null,"vigente":true}]
HTTP 200

### GET /api/v1/obras-sociales
[{"id":1,"nombre":"Obra Social Ejemplo","sigla":"OSE","tipo":"OBRA_SOCIAL","activa":true,"planes":[{"id":2,"obraSocialId":1,"nombre":"Plan Base","activo":true},{"id":1,"obraSocialId":1,"nombre":"Plan Integral","activo":true}]},{"id":2,"nombre":"Prepaga Prueba","sigla":null,"tipo":"PREPAGA","activa":true,"planes":[{"id":3,"obraSocialId":2,"nombre":"Plan Oro","activo":true}]}]
HTTP 200

### GET /api/v1/nomenclador
[{"id":1,"codigo":"N-0101","descripcion":"Consulta","prestacion":{"id":1,"nombre":"Consulta o control"},"activo":true},{"id":2,"codigo":"N-0201","descripcion":"Limpieza","prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"activo":true},{"id":3,"codigo":"N-0301","descripcion":"Extracción simple","prestacion":null,"activo":true}]
HTTP 200

