# Spec Delta

## Purpose

Definir las pantallas con las que el administrador carga el precio particular de cada prestación y el porcentaje que cubre cada plan por código del nomenclador, sobre la API de C-19, sin borrar nada.

Datos ficticios de los escenarios (salvo que el escenario diga otra cosa):

- **Usuarios:** `admin` (`ADMIN`), `recepcion` (`RECEPCIONISTA`) y `ana.ejemplo` (`ODONTOLOGO`).
- **Aranceles:** "Consulta o control" 15000.00, "Limpieza (profilaxis)" 25000.00, "Tratamiento de conducto" 90000.00 y "Extracción simple" 30000.00.
- **Catálogo y nomenclador:** los de C-49. `N-0101` "Consulta" (de "Consulta o control"), `N-0201` "Limpieza" (de "Limpieza (profilaxis)") y `N-0301` "Extracción simple" (de "Extracción simple").
- **Cobertura:** "Plan Base" (id 11, de "Obra Social Ejemplo", id 1) cubre `N-0101` al 100 % y `N-0201` al 50 %, y no tiene cargado `N-0301`.

## ADDED Requirements

### Requirement: Precio particular en la pestaña "Prestaciones"
Con sesión `ADMIN`, la pestaña "Prestaciones" de `#/configuracion` SHALL pedir `GET /api/v1/aranceles` y mostrar en cada prestación su precio en pesos ("$ 15.000,00") o "Sin precio", junto al botón "Cambiar precio". Con sesión `RECEPCIONISTA` u `ODONTOLOGO` la pestaña MUST NOT pedir los aranceles ni mostrar precios.

#### Scenario: El administrador ve los precios
- **GIVEN** una sesión de `admin` y los aranceles de los datos ficticios, con "Consulta o control" a 15000.00
- **WHEN** abre la pestaña "Prestaciones"
- **THEN** se pide `GET /api/v1/aranceles`
- **AND** la fila "Consulta o control" muestra "$ 15.000,00" y el botón "Cambiar precio"

#### Scenario: Prestación sin precio
- **GIVEN** que `GET /api/v1/aranceles` responde "Tratamiento de conducto" sin precio
- **WHEN** el administrador abre la pestaña "Prestaciones"
- **THEN** la fila "Tratamiento de conducto" muestra "Sin precio"

#### Scenario: La recepción no ve precios
- **GIVEN** una sesión de `recepcion`
- **WHEN** abre la pestaña "Prestaciones"
- **THEN** no se envía ningún pedido a `/api/v1/aranceles`
- **AND** no hay botones "Cambiar precio"

#### Scenario: Falla al pedir los aranceles
- **GIVEN** una sesión de `admin` y que `GET /api/v1/aranceles` responde `500`
- **WHEN** abre la pestaña "Prestaciones"
- **THEN** la lista de prestaciones se sigue viendo
- **AND** se ve un aviso `role="alert"` sobre los precios y el botón "Reintentar"

### Requirement: Cambiar el precio particular
"Cambiar precio" SHALL abrir un diálogo con el precio actual cargado que envía `PUT /api/v1/aranceles/{prestacionId}` con `{"precio": <número>}` y, al terminar bien, cierra y vuelve a pedir los aranceles. Antes de enviar, el diálogo MUST rechazar un precio vacío, cero, negativo, con más de dos decimales o mayor que 9999999999.99, marcando el campo sin enviar nada. El texto MUST aceptar coma o punto como separador decimal.

#### Scenario: Cambiar un precio
- **GIVEN** una sesión de `admin` en la pestaña "Prestaciones"
- **WHEN** elige "Cambiar precio" en "Limpieza (profilaxis)", escribe "27000,50" y confirma
- **THEN** se envía `PUT /api/v1/aranceles/{id}` de "Limpieza (profilaxis)" con `{"precio":27000.5}`
- **AND** el diálogo se cierra y se vuelve a pedir `GET /api/v1/aranceles`

#### Scenario: Precio cero
- **GIVEN** el diálogo "Cambiar precio" de "Consulta o control"
- **WHEN** escribe "0" y confirma
- **THEN** el campo "Precio" muestra "Ingresá un precio mayor que cero." con `aria-invalid="true"`
- **AND** no se envía ningún pedido

#### Scenario: Precio con tres decimales
- **GIVEN** el diálogo "Cambiar precio" de "Consulta o control"
- **WHEN** escribe "15000,005" y confirma
- **THEN** el campo "Precio" muestra "Usá como máximo dos decimales."
- **AND** no se envía ningún pedido

#### Scenario: La API rechaza el precio
- **GIVEN** el diálogo "Cambiar precio" de "Consulta o control" con "15000"
- **WHEN** confirma y la API responde `422` con `DATOS_INVALIDOS` y el campo `precio`
- **THEN** el diálogo sigue abierto y el campo "Precio" queda con `aria-invalid="true"`

### Requirement: Cobertura de un plan por código
Con sesión `ADMIN`, cada plan de la pestaña "Obras sociales" SHALL ofrecer "Cobertura", que abre un diálogo con los códigos activos del nomenclador en el orden de la API, cada uno con su descripción y un campo "Porcentaje" con el valor cargado o vacío si no tiene. El diálogo pide `GET /api/v1/obras-sociales/{id}/planes/{planId}/coberturas` y `GET /api/v1/nomenclador` al abrirse. Un código sin porcentaje cargado MUST leerse como "No cubre (0 %)".

#### Scenario: Ver la cobertura de "Plan Base"
- **GIVEN** una sesión de `admin` en la pestaña "Obras sociales"
- **WHEN** elige "Cobertura" en "Plan Base"
- **THEN** se pide `GET /api/v1/obras-sociales/1/planes/11/coberturas`
- **AND** el diálogo muestra `N-0101` con 100, `N-0201` con 50 y `N-0301` vacío con "No cubre (0 %)"

#### Scenario: Código inactivo
- **GIVEN** que `N-0301` está inactivo
- **WHEN** se abre "Cobertura" de "Plan Base"
- **THEN** `N-0301` no se ofrece en el diálogo

### Requirement: Guardar la cobertura de un plan
"Guardar" SHALL enviar un `PUT .../coberturas/{nomencladorId}` con `{"porcentaje": <entero>}` solo por cada código cuyo porcentaje cambió, uno después del otro, y al terminar todos bien cerrar el diálogo y avisar "Cobertura guardada.". Si un pedido falla, el diálogo MUST quedar abierto, mostrar el error y decir qué códigos ya se guardaron.

#### Scenario: Cambiar un porcentaje
- **GIVEN** el diálogo "Cobertura" de "Plan Base"
- **WHEN** cambia `N-0201` a 60 y confirma
- **THEN** se envía solo `PUT /api/v1/obras-sociales/1/planes/11/coberturas/{id}` de `N-0201` con `{"porcentaje":60}`
- **AND** el diálogo se cierra y un `role="status"` dice "Cobertura guardada."

#### Scenario: Falla a mitad del guardado
- **GIVEN** el diálogo "Cobertura" de "Plan Base" con `N-0101` cambiado a 90 y `N-0201` a 60
- **WHEN** confirma, el primer `PUT` responde `200` y el segundo `500`
- **THEN** el diálogo sigue abierto con el error y el aviso "Se guardó N-0101; falta N-0201."

### Requirement: Porcentajes válidos
Cada porcentaje MUST ser un entero de 0 a 100; si no, MUST marcarse en su campo sin enviar nada. El diálogo MUST NOT ofrecer borrar una cobertura: vaciar un campo que tenía porcentaje MUST pedir que se cargue 0.

#### Scenario: Porcentaje fuera de rango
- **GIVEN** el diálogo "Cobertura" de "Plan Base"
- **WHEN** escribe 101 en `N-0101` y confirma
- **THEN** el campo de `N-0101` muestra "Usá un número entero de 0 a 100." con `aria-invalid="true"`
- **AND** no se envía ningún pedido

#### Scenario: Porcentaje con decimales
- **GIVEN** el diálogo "Cobertura" de "Plan Base"
- **WHEN** escribe "50,5" en `N-0201` y confirma
- **THEN** el campo de `N-0201` muestra "Usá un número entero de 0 a 100."
- **AND** no se envía ningún pedido

#### Scenario: Vaciar un porcentaje cargado
- **GIVEN** el diálogo "Cobertura" de "Plan Base"
- **WHEN** borra el 50 de `N-0201` y confirma
- **THEN** el campo de `N-0201` muestra "Para dejar de cubrirlo, cargá 0."
- **AND** no se envía ningún pedido

### Requirement: Aranceles solo para el administrador
El precio y la cobertura SHALL editarse solo con sesión `ADMIN`. Con otra sesión, Configuración MUST NOT mostrar "Cambiar precio" ni "Cobertura" y MUST NOT pedir `/api/v1/aranceles` ni `.../coberturas`. Ningún precio ni porcentaje MUST quedar en la dirección, en el almacenamiento del navegador ni en la consola.

#### Scenario: El odontólogo no ve los aranceles
- **GIVEN** una sesión de `ana.ejemplo`
- **WHEN** abre `#/configuracion` y la pestaña "Prestaciones"
- **THEN** no hay botones "Cambiar precio"
- **AND** no se envía ningún pedido a `/api/v1/aranceles`

#### Scenario: Nada fuera de la pantalla al guardar
- **GIVEN** una sesión de `admin`
- **WHEN** cambia el precio de "Consulta o control" a 16000 y la cobertura de `N-0201` de "Plan Base" a 60
- **THEN** la dirección sigue siendo `#/configuracion`
- **AND** `localStorage` y `sessionStorage` no tienen datos nuevos y la consola no recibió mensajes con "16000" ni "60"
