# Spec Delta

<!-- Escrito contra el spec principal actual, que ya incluye "Señas" de C-23 (archivado el 2026-11-25). C-49 (frontend-obras-sociales) también modifica este requisito y todavía no se archivó: el que se archive segundo incluye la pestaña del otro en su MODIFIED (design D2). -->

## MODIFIED Requirements

### Requirement: Secciones según el rol
`#/configuracion` SHALL mostrar estas pestañas:

- **Para `ADMIN`:** "Profesionales", "Sillones", "Prestaciones", "Margen", "Horarios", "Ausencias", "Bloqueos", "Usuarios", "Señas" y "Consentimientos".
- **Para `RECEPCIONISTA` y `ODONTOLOGO`:** las mismas sin las tres últimas.

La pestaña elegida MUST quedar marcada con `aria-selected` y solo su contenido MUST estar visible. La página MUST exigir una sesión del personal: sin sesión lleva a `#/ingreso` sin pedir datos de configuración.

#### Scenario: Abrir Configuración sin sesión
- **GIVEN** que `GET /api/v1/public/sesion` informa que no hay sesión
- **WHEN** se abre la aplicación en `#/configuracion`
- **THEN** se muestra la página de ingreso en la dirección `#/ingreso`
- **AND** no se envía ningún pedido a `/api/v1/profesionales`

#### Scenario: Pestañas del administrador
- **GIVEN** una sesión de `admin`
- **WHEN** se abre `#/configuracion`
- **THEN** se ven las pestañas "Profesionales", "Sillones", "Prestaciones", "Margen", "Horarios", "Ausencias", "Bloqueos", "Usuarios", "Señas" y "Consentimientos"
- **AND** "Profesionales" está seleccionada y muestra la lista de profesionales

#### Scenario: Recepción no ve la pestaña Usuarios
- **GIVEN** una sesión de `recepcion`
- **WHEN** se abre `#/configuracion`
- **THEN** se ven siete pestañas, sin "Usuarios", "Señas" ni "Consentimientos"
- **AND** no se envía ningún pedido a `/api/v1/usuarios` ni a `/api/v1/senas/configuracion`

#### Scenario: Recepción ve los bloqueos sin poder registrarlos
- **GIVEN** una sesión de `recepcion`
- **WHEN** abre la pestaña "Bloqueos" y elige a la Dra. Ana Ejemplo
- **THEN** se pide `GET /api/v1/profesionales/1/bloqueos`
- **AND** no hay botones para registrar ni borrar bloqueos

#### Scenario: Cambiar de pestaña
- **GIVEN** una sesión de `admin` en `#/configuracion`
- **WHEN** elige la pestaña "Margen"
- **THEN** la pestaña "Margen" tiene `aria-selected="true"` y se muestra el margen
- **AND** la lista de profesionales ya no está visible

#### Scenario: El odontólogo no ve Consentimientos
- **GIVEN** una sesión de `ana.ejemplo`
- **WHEN** se abre `#/configuracion`
- **THEN** no hay una pestaña "Consentimientos"
- **AND** no se envía ningún pedido a `/api/v1/consentimientos/plantillas`
