# C-19: verificación del seed de desarrollo de presupuestos (tarea 7.2)

Fecha: 2026-11-24. Backend con el perfil `dev` (`./gradlew bootRun`, puerto 8081) contra bases PostgreSQL 16 descartables
(`c19_dev` y `c19_dev_b`, en un contenedor aparte del que usa el equipo). Datos ficticios; la cuenta ADMIN y la cuenta de
odontólogo son ficticias y sus contraseñas se generaron para la prueba y no se guardaron (la temporal del odontólogo se
cambió en el momento). Las salidas son las respuestas reales de la API.

Qué se comprueba: Flyway aplica V13 y V13.1 sin errores en una base nueva y en una base con V22 ya aplicada (V13 y V13.1
entran fuera de orden); `GET /api/v1/aranceles` muestra los cuatro precios; la cobertura de los tres planes es la de la KB;
`N-0301` queda vinculado a "Extracción simple"; y el presupuesto de "Paciente Uno" con "Consulta o control" y "Limpieza
(profilaxis)" da 40000.00 / 27500.00 / 12500.00.

## A. Base nueva

## A-base-nueva

### Migraciones aplicadas (flyway_schema_history, por orden de instalación)
11 | 12 | coberturas | t
12 | 12.1 | seed coberturas dev | t
13 | 13 | presupuestos | t
14 | 13.1 | seed presupuestos dev | t
16 | 21 | cuentas del personal | t
17 | 22 | llegada turno | t

### Inicio de sesión del ADMIN ficticio: HTTP 200 (credenciales omitidas)

### GET /api/v1/aranceles
[{"prestacion":{"id":1,"nombre":"Consulta o control"},"activa":true,"precio":15000.00,"actualizadoEn":"2026-11-24T20:27:58.198883Z"},{"prestacion":{"id":4,"nombre":"Extracción simple"},"activa":true,"precio":30000.00,"actualizadoEn":"2026-11-24T20:27:58.198883Z"},{"prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"activa":true,"precio":25000.00,"actualizadoEn":"2026-11-24T20:27:58.198883Z"},{"prestacion":{"id":3,"nombre":"Tratamiento de conducto"},"activa":true,"precio":90000.00,"actualizadoEn":"2026-11-24T20:27:58.198883Z"}]
HTTP 200
### Pacientes ficticios (id por DNI): {"99000001": 1, "99000002": 2, "99000003": 3}

### GET cobertura de Plan Base
[{"nomencladorId":1,"codigo":"N-0101","descripcion":"Consulta","activo":true,"porcentaje":100},{"nomencladorId":2,"codigo":"N-0201","descripcion":"Limpieza","activo":true,"porcentaje":50}]
HTTP 200

### GET cobertura de Plan Integral
[{"nomencladorId":1,"codigo":"N-0101","descripcion":"Consulta","activo":true,"porcentaje":100},{"nomencladorId":2,"codigo":"N-0201","descripcion":"Limpieza","activo":true,"porcentaje":100},{"nomencladorId":3,"codigo":"N-0301","descripcion":"Extracción simple","activo":true,"porcentaje":70}]
HTTP 200

### GET cobertura de Plan Oro
[{"nomencladorId":1,"codigo":"N-0101","descripcion":"Consulta","activo":true,"porcentaje":80},{"nomencladorId":2,"codigo":"N-0201","descripcion":"Limpieza","activo":true,"porcentaje":80},{"nomencladorId":3,"codigo":"N-0301","descripcion":"Extracción simple","activo":true,"porcentaje":80}]
HTTP 200

### GET /api/v1/nomenclador (N-0301 vinculado)
[{"id":1,"codigo":"N-0101","descripcion":"Consulta","prestacion":{"id":1,"nombre":"Consulta o control"},"activo":true},{"id":2,"codigo":"N-0201","descripcion":"Limpieza","prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"activo":true},{"id":3,"codigo":"N-0301","descripcion":"Extracción simple","prestacion":{"id":4,"nombre":"Extracción simple"},"activo":true}]
HTTP 200

### GET cobertura vigente de Paciente Tres
{"particular":false,"afiliacion":{"id":2,"obraSocial":{"id":2,"nombre":"Prepaga Prueba","sigla":null,"tipo":"PREPAGA"},"plan":{"id":3,"nombre":"Plan Oro"},"numeroAfiliado":"PP-000003","vigenteDesde":"2026-01-01","vigenteHasta":null,"vigente":true}}
HTTP 200

### Cuenta ficticia de odontólogo creada y contraseña cambiada: HTTP 204 (credenciales omitidas)
### Inicio de sesión del odontólogo ficticio: HTTP 200

### POST presupuesto de Paciente Uno (Consulta o control + Limpieza)
{"id":1,"fecha":"2026-11-24","venceEl":"2026-12-24","estado":"PENDIENTE","vencido":false,"profesional":{"id":1,"nombre":"Ana Ejemplo"},"particular":false,"cobertura":{"afiliacionId":1,"obraSocial":"Obra Social Ejemplo","plan":"Plan Base","numeroAfiliado":"OSE-000001"},"items":[{"id":1,"prestacion":{"id":1,"nombre":"Consulta o control"},"codigoNomenclador":"N-0101","pieza":null,"cara":null,"cantidad":1,"precioUnitario":15000.00,"porcentajeCobertura":100,"importe":15000.00,"cobertura":15000.00,"copago":0.00},{"id":2,"prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"codigoNomenclador":"N-0201","pieza":null,"cara":null,"cantidad":1,"precioUnitario":25000.00,"porcentajeCobertura":50,"importe":25000.00,"cobertura":12500.00,"copago":12500.00}],"totales":{"precio":40000.00,"cobertura":27500.00,"copago":12500.00},"creadoEn":"2026-11-24T20:28:06.583119Z"}
HTTP 201

### POST presupuesto de Paciente Dos, particular (Consulta o control + Limpieza)
{"id":2,"fecha":"2026-11-24","venceEl":"2026-12-24","estado":"PENDIENTE","vencido":false,"profesional":{"id":1,"nombre":"Ana Ejemplo"},"particular":true,"cobertura":null,"items":[{"id":3,"prestacion":{"id":1,"nombre":"Consulta o control"},"codigoNomenclador":"N-0101","pieza":null,"cara":null,"cantidad":1,"precioUnitario":15000.00,"porcentajeCobertura":0,"importe":15000.00,"cobertura":0.00,"copago":15000.00},{"id":4,"prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"codigoNomenclador":"N-0201","pieza":null,"cara":null,"cantidad":1,"precioUnitario":25000.00,"porcentajeCobertura":0,"importe":25000.00,"cobertura":0.00,"copago":25000.00}],"totales":{"precio":40000.00,"cobertura":0.00,"copago":40000.00},"creadoEn":"2026-11-24T20:28:06.652307Z"}
HTTP 201

### POST presupuesto de Paciente Tres, Plan Oro (Consulta o control + Limpieza)
{"id":3,"fecha":"2026-11-24","venceEl":"2026-12-24","estado":"PENDIENTE","vencido":false,"profesional":{"id":1,"nombre":"Ana Ejemplo"},"particular":false,"cobertura":{"afiliacionId":2,"obraSocial":"Prepaga Prueba","plan":"Plan Oro","numeroAfiliado":"PP-000003"},"items":[{"id":5,"prestacion":{"id":1,"nombre":"Consulta o control"},"codigoNomenclador":"N-0101","pieza":null,"cara":null,"cantidad":1,"precioUnitario":15000.00,"porcentajeCobertura":80,"importe":15000.00,"cobertura":12000.00,"copago":3000.00},{"id":6,"prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"codigoNomenclador":"N-0201","pieza":null,"cara":null,"cantidad":1,"precioUnitario":25000.00,"porcentajeCobertura":80,"importe":25000.00,"cobertura":20000.00,"copago":5000.00}],"totales":{"precio":40000.00,"cobertura":32000.00,"copago":8000.00},"creadoEn":"2026-11-24T20:28:06.672883Z"}
HTTP 201

### POST presupuesto de Paciente Tres con Extracción simple x2 en la pieza 36 (N-0301 vinculado)
{"id":4,"fecha":"2026-11-24","venceEl":"2026-12-24","estado":"PENDIENTE","vencido":false,"profesional":{"id":1,"nombre":"Ana Ejemplo"},"particular":false,"cobertura":{"afiliacionId":2,"obraSocial":"Prepaga Prueba","plan":"Plan Oro","numeroAfiliado":"PP-000003"},"items":[{"id":7,"prestacion":{"id":4,"nombre":"Extracción simple"},"codigoNomenclador":"N-0301","pieza":36,"cara":null,"cantidad":2,"precioUnitario":30000.00,"porcentajeCobertura":80,"importe":60000.00,"cobertura":48000.00,"copago":12000.00}],"totales":{"precio":60000.00,"cobertura":48000.00,"copago":12000.00},"creadoEn":"2026-11-24T20:28:06.697593Z"}
HTTP 201

### GET presupuestos de Paciente Uno como ADMIN
[{"id":1,"fecha":"2026-11-24","venceEl":"2026-12-24","estado":"PENDIENTE","vencido":false,"profesional":{"id":1,"nombre":"Ana Ejemplo"},"particular":false,"cobertura":{"afiliacionId":1,"obraSocial":"Obra Social Ejemplo","plan":"Plan Base","numeroAfiliado":"OSE-000001"},"items":[{"id":1,"prestacion":{"id":1,"nombre":"Consulta o control"},"codigoNomenclador":"N-0101","pieza":null,"cara":null,"cantidad":1,"precioUnitario":15000.00,"porcentajeCobertura":100,"importe":15000.00,"cobertura":15000.00,"copago":0.00},{"id":2,"prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"codigoNomenclador":"N-0201","pieza":null,"cara":null,"cantidad":1,"precioUnitario":25000.00,"porcentajeCobertura":50,"importe":25000.00,"cobertura":12500.00,"copago":12500.00}],"totales":{"precio":40000.00,"cobertura":27500.00,"copago":12500.00},"creadoEn":"2026-11-24T20:28:06.583119Z"}]
HTTP 200

## B. Base con V22 ya aplicada (V13 y V13.1 entran fuera de orden)

Primero se arrancó el backend sin los archivos de V13 y V13.1 para dejar la base en el estado de un desarrollador que ya
tenía V22 (resultado del primer arranque):

## B-1-estado-previo-sin-V13

### Migraciones aplicadas (flyway_schema_history, por orden de instalación)
11 | 12 | coberturas | t
12 | 12.1 | seed coberturas dev | t
14 | 21 | cuentas del personal | t
15 | 22 | llegada turno | t


## B-2-con-V22-y-V13-entra-fuera-de-orden

### Migraciones aplicadas (flyway_schema_history, por orden de instalación)
11 | 12 | coberturas | t
12 | 12.1 | seed coberturas dev | t
14 | 21 | cuentas del personal | t
15 | 22 | llegada turno | t
16 | 13 | presupuestos | t
17 | 13.1 | seed presupuestos dev | t

### Inicio de sesión del ADMIN ficticio: HTTP 200 (credenciales omitidas)

### GET /api/v1/aranceles
[{"prestacion":{"id":1,"nombre":"Consulta o control"},"activa":true,"precio":15000.00,"actualizadoEn":"2026-11-24T20:28:30.325834Z"},{"prestacion":{"id":4,"nombre":"Extracción simple"},"activa":true,"precio":30000.00,"actualizadoEn":"2026-11-24T20:28:30.325834Z"},{"prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"activa":true,"precio":25000.00,"actualizadoEn":"2026-11-24T20:28:30.325834Z"},{"prestacion":{"id":3,"nombre":"Tratamiento de conducto"},"activa":true,"precio":90000.00,"actualizadoEn":"2026-11-24T20:28:30.325834Z"}]
HTTP 200
### Pacientes ficticios (id por DNI): {"99000001": 1, "99000002": 2, "99000003": 3}

### GET cobertura de Plan Base
[{"nomencladorId":1,"codigo":"N-0101","descripcion":"Consulta","activo":true,"porcentaje":100},{"nomencladorId":2,"codigo":"N-0201","descripcion":"Limpieza","activo":true,"porcentaje":50}]
HTTP 200

### GET cobertura de Plan Integral
[{"nomencladorId":1,"codigo":"N-0101","descripcion":"Consulta","activo":true,"porcentaje":100},{"nomencladorId":2,"codigo":"N-0201","descripcion":"Limpieza","activo":true,"porcentaje":100},{"nomencladorId":3,"codigo":"N-0301","descripcion":"Extracción simple","activo":true,"porcentaje":70}]
HTTP 200

### GET cobertura de Plan Oro
[{"nomencladorId":1,"codigo":"N-0101","descripcion":"Consulta","activo":true,"porcentaje":80},{"nomencladorId":2,"codigo":"N-0201","descripcion":"Limpieza","activo":true,"porcentaje":80},{"nomencladorId":3,"codigo":"N-0301","descripcion":"Extracción simple","activo":true,"porcentaje":80}]
HTTP 200

### GET /api/v1/nomenclador (N-0301 vinculado)
[{"id":1,"codigo":"N-0101","descripcion":"Consulta","prestacion":{"id":1,"nombre":"Consulta o control"},"activo":true},{"id":2,"codigo":"N-0201","descripcion":"Limpieza","prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"activo":true},{"id":3,"codigo":"N-0301","descripcion":"Extracción simple","prestacion":{"id":4,"nombre":"Extracción simple"},"activo":true}]
HTTP 200

### GET cobertura vigente de Paciente Tres
{"particular":false,"afiliacion":{"id":2,"obraSocial":{"id":2,"nombre":"Prepaga Prueba","sigla":null,"tipo":"PREPAGA"},"plan":{"id":3,"nombre":"Plan Oro"},"numeroAfiliado":"PP-000003","vigenteDesde":"2026-01-01","vigenteHasta":null,"vigente":true}}
HTTP 200

### Cuenta ficticia de odontólogo creada y contraseña cambiada: HTTP 204 (credenciales omitidas)
### Inicio de sesión del odontólogo ficticio: HTTP 200

### POST presupuesto de Paciente Uno (Consulta o control + Limpieza)
{"id":1,"fecha":"2026-11-24","venceEl":"2026-12-24","estado":"PENDIENTE","vencido":false,"profesional":{"id":1,"nombre":"Ana Ejemplo"},"particular":false,"cobertura":{"afiliacionId":1,"obraSocial":"Obra Social Ejemplo","plan":"Plan Base","numeroAfiliado":"OSE-000001"},"items":[{"id":1,"prestacion":{"id":1,"nombre":"Consulta o control"},"codigoNomenclador":"N-0101","pieza":null,"cara":null,"cantidad":1,"precioUnitario":15000.00,"porcentajeCobertura":100,"importe":15000.00,"cobertura":15000.00,"copago":0.00},{"id":2,"prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"codigoNomenclador":"N-0201","pieza":null,"cara":null,"cantidad":1,"precioUnitario":25000.00,"porcentajeCobertura":50,"importe":25000.00,"cobertura":12500.00,"copago":12500.00}],"totales":{"precio":40000.00,"cobertura":27500.00,"copago":12500.00},"creadoEn":"2026-11-24T20:28:38.272108Z"}
HTTP 201

### POST presupuesto de Paciente Dos, particular (Consulta o control + Limpieza)
{"id":2,"fecha":"2026-11-24","venceEl":"2026-12-24","estado":"PENDIENTE","vencido":false,"profesional":{"id":1,"nombre":"Ana Ejemplo"},"particular":true,"cobertura":null,"items":[{"id":3,"prestacion":{"id":1,"nombre":"Consulta o control"},"codigoNomenclador":"N-0101","pieza":null,"cara":null,"cantidad":1,"precioUnitario":15000.00,"porcentajeCobertura":0,"importe":15000.00,"cobertura":0.00,"copago":15000.00},{"id":4,"prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"codigoNomenclador":"N-0201","pieza":null,"cara":null,"cantidad":1,"precioUnitario":25000.00,"porcentajeCobertura":0,"importe":25000.00,"cobertura":0.00,"copago":25000.00}],"totales":{"precio":40000.00,"cobertura":0.00,"copago":40000.00},"creadoEn":"2026-11-24T20:28:38.332926Z"}
HTTP 201

### POST presupuesto de Paciente Tres, Plan Oro (Consulta o control + Limpieza)
{"id":3,"fecha":"2026-11-24","venceEl":"2026-12-24","estado":"PENDIENTE","vencido":false,"profesional":{"id":1,"nombre":"Ana Ejemplo"},"particular":false,"cobertura":{"afiliacionId":2,"obraSocial":"Prepaga Prueba","plan":"Plan Oro","numeroAfiliado":"PP-000003"},"items":[{"id":5,"prestacion":{"id":1,"nombre":"Consulta o control"},"codigoNomenclador":"N-0101","pieza":null,"cara":null,"cantidad":1,"precioUnitario":15000.00,"porcentajeCobertura":80,"importe":15000.00,"cobertura":12000.00,"copago":3000.00},{"id":6,"prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"codigoNomenclador":"N-0201","pieza":null,"cara":null,"cantidad":1,"precioUnitario":25000.00,"porcentajeCobertura":80,"importe":25000.00,"cobertura":20000.00,"copago":5000.00}],"totales":{"precio":40000.00,"cobertura":32000.00,"copago":8000.00},"creadoEn":"2026-11-24T20:28:38.355063Z"}
HTTP 201

### POST presupuesto de Paciente Tres con Extracción simple x2 en la pieza 36 (N-0301 vinculado)
{"id":4,"fecha":"2026-11-24","venceEl":"2026-12-24","estado":"PENDIENTE","vencido":false,"profesional":{"id":1,"nombre":"Ana Ejemplo"},"particular":false,"cobertura":{"afiliacionId":2,"obraSocial":"Prepaga Prueba","plan":"Plan Oro","numeroAfiliado":"PP-000003"},"items":[{"id":7,"prestacion":{"id":4,"nombre":"Extracción simple"},"codigoNomenclador":"N-0301","pieza":36,"cara":null,"cantidad":2,"precioUnitario":30000.00,"porcentajeCobertura":80,"importe":60000.00,"cobertura":48000.00,"copago":12000.00}],"totales":{"precio":60000.00,"cobertura":48000.00,"copago":12000.00},"creadoEn":"2026-11-24T20:28:38.378842Z"}
HTTP 201

### GET presupuestos de Paciente Uno como ADMIN
[{"id":1,"fecha":"2026-11-24","venceEl":"2026-12-24","estado":"PENDIENTE","vencido":false,"profesional":{"id":1,"nombre":"Ana Ejemplo"},"particular":false,"cobertura":{"afiliacionId":1,"obraSocial":"Obra Social Ejemplo","plan":"Plan Base","numeroAfiliado":"OSE-000001"},"items":[{"id":1,"prestacion":{"id":1,"nombre":"Consulta o control"},"codigoNomenclador":"N-0101","pieza":null,"cara":null,"cantidad":1,"precioUnitario":15000.00,"porcentajeCobertura":100,"importe":15000.00,"cobertura":15000.00,"copago":0.00},{"id":2,"prestacion":{"id":2,"nombre":"Limpieza (profilaxis)"},"codigoNomenclador":"N-0201","pieza":null,"cara":null,"cantidad":1,"precioUnitario":25000.00,"porcentajeCobertura":50,"importe":25000.00,"cobertura":12500.00,"copago":12500.00}],"totales":{"precio":40000.00,"cobertura":27500.00,"copago":12500.00},"creadoEn":"2026-11-24T20:28:38.272108Z"}]
HTTP 200
