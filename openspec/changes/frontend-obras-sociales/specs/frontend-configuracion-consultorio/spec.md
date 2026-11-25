# Spec Delta

## MODIFIED Requirements

### Requirement: Secciones según el rol
`#/configuracion` SHALL mostrar estas pestañas:

- **Para `ADMIN`:** "Profesionales", "Sillones", "Prestaciones", "Margen", "Horarios", "Ausencias", "Bloqueos", "Usuarios" y "Obras sociales".
- **Para `RECEPCIONISTA` y `ODONTOLOGO`:** las mismas, sin las dos últimas.

La pestaña elegida MUST quedar marcada con `aria-selected` y solo su contenido MUST estar visible. Como Agenda, la página MUST exigir una sesión del personal: sin sesión lleva a `#/ingreso` sin pedir datos de configuración.

#### Scenario: Abrir Configuración sin sesión
- **GIVEN** que `GET /api/v1/public/sesion` informa que no hay sesión
- **WHEN** se abre la aplicación en `#/configuracion`
- **THEN** se muestra la página de ingreso en la dirección `#/ingreso`
- **AND** no se envía ningún pedido a `/api/v1/profesionales`

#### Scenario: Pestañas del administrador
- **GIVEN** una sesión de `admin`
- **WHEN** se abre `#/configuracion`
- **THEN** se ven las pestañas "Profesionales", "Sillones", "Prestaciones", "Margen", "Horarios", "Ausencias", "Bloqueos", "Usuarios" y "Obras sociales"
- **AND** "Profesionales" está seleccionada y muestra la lista de profesionales

#### Scenario: Recepción no ve la pestaña Usuarios
- **GIVEN** una sesión de `recepcion`
- **WHEN** se abre `#/configuracion`
- **THEN** se ven siete pestañas, sin "Usuarios" ni "Obras sociales"
- **AND** no se envía ningún pedido a `/api/v1/usuarios`

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

#### Scenario: El odontólogo no ve Obras sociales
- **GIVEN** una sesión de `ana.ejemplo`
- **WHEN** se abre `#/configuracion`
- **THEN** no hay una pestaña "Obras sociales"
- **AND** no se envía ningún pedido a `/api/v1/obras-sociales`
