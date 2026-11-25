## ADDED Requirements

### Requirement: Notificación de Mercado Pago fuera de CSRF
`POST /api/v1/public/pagos/mercadopago/notificaciones` SHALL ser la **única** ruta exceptuada de la protección CSRF (PA-64, regla dura 8): MUST aceptarse sin sesión y sin token CSRF, y su autenticidad MUST decidirse por la firma de la notificación y la consulta del pago (spec `senas`). Toda otra ruta que modifica datos, pública o no, MUST seguir exigiendo el token CSRF.

#### Scenario: Notificación sin sesión ni token CSRF
- **WHEN** llega a la ruta de la notificación un `POST` sin cookie de sesión ni token CSRF y con la firma válida
- **THEN** la respuesta es `200`

#### Scenario: Notificación sin firma
- **WHEN** llega a la ruta de la notificación un `POST` sin cookie de sesión, sin token CSRF y sin firma
- **THEN** la respuesta es `401` con `FIRMA_INVALIDA`

#### Scenario: Otra ruta pública sigue con CSRF
- **WHEN** llega `POST /api/v1/public/confirmaciones/{token}` sin token CSRF
- **THEN** la respuesta es `403`

#### Scenario: Una ruta parecida no queda exceptuada
- **WHEN** llega `POST /api/v1/public/pagos/otra-cosa` sin token CSRF
- **THEN** la respuesta es `403`
