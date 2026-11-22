# Informe de Discovery

## Sistema de gestión de turnos y agenda para consultorios odontológicos (mercado argentino)

| | |
|---|---|
| **Materia** | Desarrollo de Software (Trabajo Global de Integración) |
| **Integrantes** | Sofia Indovino (líder), Emanuel Mateo, Juan Sebastián Cabrera, Juan Cruz Grivel, Enzo Mellimaci |
| **Fecha** | Relevamiento: 2026-11-19. Verificación manual: 2026-11-20 y 2026-11-21 |
| **Estado** | Versión final (v2.1, 2026-11-22). Incluye las correcciones de la verificación manual de 5 fuentes por el equipo. La v2.1 solo cambia el formato: agrega la portada y separa proveedor, fuente y fecha en cada fila de la tabla A. |
| **Uso de IA** | Relevamiento y redacción asistidos por un agente de IA (Claude Code). Las afirmaciones se tomaron de las páginas oficiales citadas. El equipo verificó personalmente 5 fuentes y el informe se corrigió con sus hallazgos (ver "Verificación de fuentes"). |

---

## Resumen ejecutivo

**Qué se investigó.** Se relevaron **16 sistemas** de gestión odontológica con agenda de turnos: 9 argentinos, 4 latinoamericanos y 3 internacionales. Para cada uno se usó su sitio oficial, consultado el 2026-11-19. Cada dato se clasificó como *comprobado* (demo, video, captura real o centro de ayuda), *afirmación comercial* (texto o mockup del proveedor) o *No evidenciado*.

**Principales hallazgos.**

1. **Ficha clínica con odontograma, agenda multiprofesional y recordatorios son estándar de mercado.** El odontograma aparece en 15 de los 16 sistemas, así que no sirve como diferencial.
2. **La agenda por sillón casi no está evidenciada.** Solo 2 de 16 la mencionan (AgendaPro y Órbita), y **ninguno** describe públicamente cómo evita que se superpongan turnos en un mismo sillón. El único que menciona "detección de solapamiento" es DentalSoft, y no la aplica a sillones.
3. **La lista de espera no aparece en ningún sistema relevado.**
4. **Las integraciones locales están fragmentadas.** Solo DentalCore afirma tener juntas WhatsApp por API oficial, ARCA, Mercado Pago y obras sociales. Los sistemas con más clientes declarados (Bilog y Dentalink) no publican precios.
5. **Los recordatorios por WhatsApp tienen un costo concreto.** DentalSoft informa un costo de Meta de **USD 0,026 por mensaje**, que hay que considerar en el modelo de negocio.
6. **Las afirmaciones de impacto (por ejemplo, "−90 % de ausencias", "−40 % de ausentismo") no tienen metodología publicada** y se consideran marketing.

**MVP recomendado.** Una agenda para un odontólogo independiente o un consultorio chico, con:

- **turnos sin solapamiento por profesional y por sillón;**
- duración por prestación;
- reserva online por el paciente, con cancelación y reprogramación hasta 24 h antes;
- recordatorios y confirmación por WhatsApp sin datos de salud en el mensaje;
- ficha básica con odontograma.

Obras sociales, ARCA, Mercado Pago y las funciones de IA quedan para etapas posteriores. El detalle está en la sección D.

---

## A. Tabla comparativa (ordenada por relevancia para Argentina)

Criterio de orden: primero los productos argentinos con más presencia declarada y más funcionalidad local, después los regionales con oferta explícita para Argentina, y al final las referencias internacionales.

**Leyenda de celdas:**

- **Comprobado (…)**: se ve en una demo, un video, una captura real o la documentación oficial.
- **Afirmado**: declaración comercial en texto o mockup.
- **No evidenciado**: el sitio oficial consultado no lo menciona.

Todas las fuentes se consultaron el **2026-11-19**.

### A.1 Identificación

| # | Producto | Proveedor | País · Segmento | Modalidad | Fuente y fecha |
|---|---|---|---|---|---|
| 1 | Bilog | No evidenciado | Argentina · Consultorio, clínica, obras sociales | Web y app móvil (nube No evidenciado) | https://bilog.com.ar/ · consultada el 2026-11-19 |
| 2 | DentalTec | Tándem Digital | Argentina · Odontólogo, círculos y federaciones | Web, sin instalación | https://web.dentaltec.com.ar/software-odontologos-argentina · consultada el 2026-11-19 |
| 3 | DentalSoft | No evidenciado | Argentina · Consultorio, clínica | Nube | https://dentalsoft.com.ar/ · consultada el 2026-11-19 |
| 4 | DentalCore | No evidenciado | Argentina (opera en 8 países LatAm) · Consultorio, clínica | Nube y app de escritorio para Windows | https://dentalcore.app/ · consultada el 2026-11-19 |
| 5 | AgendaPro | AgendaPro Inc. | Chile, con página para Argentina · Consultorio, clínica | Nube (AWS) | https://agendapro.com/ar/dental/software-odontologico · consultada el 2026-11-19 |
| 6 | Dentalink | Engenis SpA (Healthatom) | Chile, con módulo para Argentina · Consultorio, clínica, cadena | Nube | https://www.softwaredentalink.com/ · consultada el 2026-11-19 |
| 7 | Odonthia | Genera Impacto | Argentina · Consultorio, clínica con varios sillones | SaaS web | https://odonthia.com/ · consultada el 2026-11-19 |
| 8 | Órbita | Órbita Global | Argentina · Consultorio, clínica | Nube (AWS) | https://hiorbita.com/ · consultada el 2026-11-19 |
| 9 | Odonto.ar | No evidenciado | Argentina (inferido) · Independiente hasta clínica de 10 personas | Web multidispositivo (nube No evidenciado) | https://odonto.ar/ · consultada el 2026-11-19 |
| 10 | VecDent | Vectora | Argentina (inferido) · Independiente, consultorio, clínica | No evidenciado | https://vectora.ar/pages/vecdent/ · consultada el 2026-11-19 |
| 11 | DentiClick | ThunderSkills | Argentina y Venezuela · Consultorio, clínica, centro | Nube (PWA) | https://www.denticlick.app/ · consultada el 2026-11-19 |
| 12 | Clinic Cloud | Doctoralia España (Docplanner) | España, grupo presente en Argentina · Clínicas en general | No evidenciado | https://clinic-cloud.com/ · consultada el 2026-11-19 |
| 13 | Doctocliq | No evidenciado | No evidenciado (LatAm, "+20 países") · Consultorio dental, clínica | Nube y apps móviles | https://doctocliq.com/ · consultada el 2026-11-19 |
| 14 | Medesk | No evidenciado | No evidenciado ("50 países") · Clínica dental, consultorio | No evidenciado | https://www.medesk.net/es/solutiones/odontologia · consultada el 2026-11-19 |
| 15 | Dentidesk | No evidenciado | Chile · Clínica, centro médico, academia | Web y app móvil | https://www.dentidesk.com/ · consultada el 2026-11-19 |
| 16 | Open Dental | Open Dental Software | EE. UU. · Consultorio, clínica | No evidenciado | https://www.opendental.com/ · consultada el 2026-11-19 |

**Proveedor "No evidenciado"**: en la página oficial consultada el 2026-11-19 no se registró una empresa proveedora distinta de la marca del producto. No se completó por deducción.

### A.2 Gestión de turnos y experiencia del paciente

Las tablas A.2 y A.3 usan el mismo número de fila que A.1: el proveedor, el país, la URL oficial y la fecha de consulta de cada sistema están en esa fila de A.1. Las correcciones de la verificación manual indican su propia fecha en la celda.

| # | Producto | Agenda multiprofesional | Sillón / box | Prevención de solapamientos | Reserva online del paciente | Recordatorios / confirmación | WhatsApp | Lista de espera |
|---|---|---|---|---|---|---|---|---|
| 1 | Bilog | Afirmado | No evidenciado | No evidenciado | Afirmado (reservar y cancelar) | Afirmado (WhatsApp, email, llamada) | Afirmado | No evidenciado |
| 2 | DentalTec | Afirmado | No evidenciado | No evidenciado | No evidenciado | Afirmado | Afirmado | No evidenciado |
| 3 | DentalSoft | Comprobado (captura de agenda) | No evidenciado | Afirmado (detección en sobreturnos) | Comprobado (demo en vivo, 4 pasos) | Afirmado (confirmar o cancelar respondiendo) | Afirmado (automático en plan Pro) | No evidenciado |
| 4 | DentalCore | Afirmado | No evidenciado | No evidenciado | No evidenciado (solo confirmar o cancelar por link) | Afirmado (48 y 24 h) | Afirmado (API Meta) | No evidenciado |
| 5 | AgendaPro | Afirmado | Afirmado (mockup) | No evidenciado | Afirmado (24/7 y Google) | Afirmado (1 a 3 días antes) | Afirmado (asistente IA) | No evidenciado |
| 6 | Dentalink | No evidenciado | No evidenciado | No evidenciado | Afirmado (24/7) | Afirmado | Afirmado (Contact Center IA) | No evidenciado |
| 7 | Odonthia | No evidenciado (solo enlace) | Afirmado (plan "varios sillones") | No evidenciado | Afirmado (solo enlace) | Afirmado (solo enlace) | Afirmado (planes pagos) | No evidenciado |
| 8 | Órbita | No evidenciado | Afirmado (captura) | No evidenciado | No evidenciado (vía WhatsApp sí) | Afirmado | Afirmado (API Meta) | No evidenciado |
| 9 | Odonto.ar | Afirmado | No evidenciado | No evidenciado | Afirmado (mockup, con reglas de cancelación) | No evidenciado | No evidenciado | No evidenciado |
| 10 | VecDent | Comprobado (demo) | No evidenciado | No evidenciado | No evidenciado (la demo solo permite crear turnos al personal; verificación manual del 2026-11-20) | No evidenciado | No evidenciado | No evidenciado |
| 11 | DentiClick | Afirmado | No evidenciado | No evidenciado | Afirmado (mini-web) | No evidenciado | No evidenciado | No evidenciado |
| 12 | Clinic Cloud | Afirmado | No evidenciado | No evidenciado | Afirmado (vía Doctoralia) | Afirmado | Afirmado | No evidenciado |
| 13 | Doctocliq | Afirmado | No evidenciado | No evidenciado | No evidenciado (vía WhatsApp sí) | Afirmado | Afirmado (IA) | No evidenciado |
| 14 | Medesk | No evidenciado | No evidenciado | No evidenciado | Afirmado | Afirmado | No evidenciado | No evidenciado |
| 15 | Dentidesk | Afirmado | No evidenciado | No evidenciado | Afirmado (integración) | Afirmado (sin canal) | No evidenciado ("próximamente") | No evidenciado |
| 16 | Open Dental | Afirmado | No evidenciado | No evidenciado | Afirmado (Web Sched) | Afirmado (SMS) | No evidenciado | No evidenciado |

### A.3 Clínica, administración y modelo comercial

| # | Producto | Odontograma | Periodontograma | Presupuestos | Obras sociales | ARCA | Mercado Pago | Precio publicado | Prueba / plan gratis |
|---|---|---|---|---|---|---|---|---|---|
| 1 | Bilog | Afirmado (mockup) | No evidenciado | Afirmado | Afirmado (liquidaciones) | No evidenciado | No evidenciado | No evidenciado | No evidenciado |
| 2 | DentalTec | Afirmado (CIE-10) | No evidenciado | No evidenciado | Afirmado (+15, validación online) | Afirmado | No evidenciado | No evidenciado | Demo a pedido |
| 3 | DentalSoft | Comprobado (captura) | No evidenciado | Afirmado | Afirmado | No evidenciado | Afirmado | Gestión $30.000 y Pro $60.000/mes con 1 profesional, +$10.000/mes por cada profesional extra (hasta 10; más, a cotizar). Moneda e IVA no explícitos (verificado el 2026-11-20) | Plan gratis de 50 turnos/mes **durante 3 meses**; el alta se pide por WhatsApp |
| 4 | DentalCore | Afirmado | Afirmado | Afirmado | Afirmado (65 cargadas) | Afirmado | Afirmado | ARS 75.000/mes **o** USD 50/mes (inconsistente) | Prueba de 7 días |
| 5 | AgendaPro | Afirmado | No evidenciado | Afirmado | No evidenciado | No evidenciado | Afirmado (señas) | Desde ARS 13.900/mes | Prueba de 14 días |
| 6 | Dentalink | Afirmado | Afirmado | Afirmado | Comprobado (centro de ayuda, módulo AR) | No evidenciado | No evidenciado | No evidenciado (cotización) | No evidenciado |
| 7 | Odonthia | Comprobado (captura y video) | Afirmado (solo enlace) | Afirmado (solo enlace) | Afirmado (solo enlace) | No evidenciado | No evidenciado | ARS 77.100 y 159.100/mes (verificado el 2026-11-20; el relevamiento del 19/11 leyó 76.600 y 158.100) | Plan gratis permanente (gestión del consultorio; IA y WhatsApp automático son pagos) |
| 8 | Órbita | Afirmado (voz) | No evidenciado | Afirmado | Afirmado | No evidenciado | No evidenciado | No evidenciado | No evidenciado |
| 9 | Odonto.ar | Afirmado (mockup) | No evidenciado | Afirmado | Afirmado | No evidenciado | No evidenciado (solo para pagar la suscripción) | USD 29, 49 y 99/mes | Prueba de 30 días |
| 10 | VecDent | Comprobado (demo) | Comprobado (demo, 6 puntos; desde el plan Profesional) | Afirmado | Comprobado (demo) | No evidenciado | No evidenciado | $30.000, 50.000 y 80.000/mes (moneda no explícita) | Demo sin registro |
| 11 | DentiClick | Comprobado (demo interactiva) | No evidenciado | Afirmado | Afirmado | No evidenciado | No evidenciado | USD 12, 29 y 35/mes | Plan gratis y prueba de 1 mes |
| 12 | Clinic Cloud | No evidenciado | No evidenciado | No evidenciado | No evidenciado (mutuas de España) | No evidenciado | No evidenciado | No evidenciado | Afirmado ("empezar gratis") |
| 13 | Doctocliq | Afirmado (captura) | No evidenciado | Afirmado | No evidenciado | No evidenciado | No evidenciado | No evidenciado | Plan gratis y prueba de 7 días |
| 14 | Medesk | Afirmado (listado) | No evidenciado | No evidenciado | No evidenciado | No evidenciado | No evidenciado | No evidenciado | Afirmado ("empieza gratis") |
| 15 | Dentidesk | Afirmado (captura) | Afirmado (ficha de periodoncia) | No evidenciado | No evidenciado | No evidenciado | No evidenciado | No evidenciado | Prueba de 15 días |
| 16 | Open Dental | Afirmado (3D) | No evidenciado | No evidenciado | No evidenciado (seguros de EE. UU.) | No evidenciado | No evidenciado | No evidenciado | No evidenciado |

El detalle por sistema (roles, auditoría, exportación, seguridad, cifras de adopción) está en `docs/discovery/sources/relevamiento-fuentes.md`.

---

## B. Matriz de puntuación (0 a 5)

**Pesos:**

| Dimensión | Peso |
|---|---|
| T. Gestión de turnos y automatización | 25 % |
| C. Funcionalidad odontológica clínica | 20 % |
| I. Integraciones locales y WhatsApp | 15 % |
| A. Administración, cobros y facturación | 15 % |
| P. Experiencia del paciente | 10 % |
| S. Seguridad, exportación y trazabilidad | 10 % |
| $. Precio y facilidad de adopción | 5 % |

| # | Producto | T | C | I | A | P | S | $ | **Total ponderado** |
|---|---|---|---|---|---|---|---|---|---|
| 1 | DentalCore | 4 | 5 | 5 | 5 | 3 | 5 | 3 | **4,45** |
| 2 | DentalSoft | 4 | 4 | 4 | 4 | 5 | 3 | 4 | **4,00** ✱ |
| 3 | AgendaPro | 4 | 4 | 3 | 4 | 5 | 3 | 4 | **3,85** |
| 4 | Bilog | 4 | 4 | 3 | 3 | 4 | 2 | 1 | **3,35** |
| 5 | Dentalink | 3 | 4 | 3 | 3 | 4 | 4 | 1 | **3,30** |
| 6 | Doctocliq | 3 | 4 | 2 | 3 | 3 | 2 | 3 | **2,95** |
| 7 | Órbita | 4 | 2 | 3 | 2 | 4 | 3 | 1 | **2,90** |
| 8 | DentalTec | 2 | 3 | 4 | 4 | 1 | 3 | 1 | **2,75** |
| 9 | DentiClick | 2 | 4 | 1 | 3 | 3 | 2 | 5 | **2,65** |
| 10 | Odonthia | 2 | 3 | 2 | 1 | 2 | 3 | 5 | **2,30** |
| 11 | Odonto.ar | 2 | 3 | 1 | 2 | 3 | 2 | 4 | **2,25** |
| 12 | Clinic Cloud | 3 | 1 | 1 | 2 | 4 | 3 | 2 | **2,20** |
| 13 | Medesk | 2 | 2 | 1 | 2 | 3 | 3 | 2 | **2,05** |
| 13 | VecDent | 1 | 4 | 1 | 3 | 1 | 1 | 4 | **2,05** ✱ |
| 15 | Dentidesk | 2 | 3 | 0 | 2 | 2 | 1 | 2 | **1,80** |
| 15 | Open Dental | 3 | 2 | 0 | 2 | 3 | 0 | 1 | **1,80** |

Fórmula: Total = (T·25 + C·20 + I·15 + A·15 + P·10 + S·10 + $·5) / 100.

✱ **Ajuste por verificación manual (2026-11-20)**: VecDent bajó en "Experiencia del paciente" de 3 a 1, y su total pasó de 2,25 a 2,05, porque la demo no tiene reserva por el paciente (ver "Verificación de fuentes", fila 5). DentalSoft bajó en "Precio" de 5 a 4, y su total pasó de 4,05 a 4,00, porque el plan gratis es una prueba de 3 meses y no un plan permanente (fila 2). Con el mismo criterio que AgendaPro (precio publicado y prueba), queda en 4. El orden de la matriz no cambia.

### Nota metodológica

- **Qué significa cada nota:**
  - **0**: ninguna funcionalidad de la dimensión está evidenciada en la fuente oficial.
  - **1**: un único elemento aislado.
  - **2**: cobertura parcial.
  - **3**: cobertura de los elementos centrales.
  - **4**: cobertura amplia, con algún hueco relevante.
  - **5**: cobertura completa de la dimensión según lo relevado.
- **Cómo se trató lo "No evidenciado":** suma **0** en esa dimensión. Es un criterio conservador: se puntúa lo que el proveedor muestra o declara públicamente, no lo que podría tener. Por eso un producto maduro con poca información pública, como Open Dental, puntúa bajo. **La matriz mide evidencia pública, no calidad real.**
- **Comprobado contra afirmado:** las afirmaciones comerciales se puntuaron igual que lo comprobado para no castigar a los sitios sin demo. Sí se usó como criterio de desempate y para elegir los sistemas de la sección D.
- **Precio:** suma más un precio publicado, un plan gratuito o una prueba sin tarjeta. Un precio inconsistente (DentalCore) resta.
- **Limitación:** las notas son un juicio del equipo sobre la evidencia relevada y pueden cambiar después de la verificación manual o de las demos.

---

## C. Análisis competitivo

### C.1 Funcionalidades estándar de mercado
Aparecen en la mayoría de los sistemas, así que **no diferencian a nadie**: son requisito para competir.

- Agenda con varios profesionales y vistas diaria y semanal (12 de 16 afirman algún tipo de agenda multiprofesional o por profesional).
- Ficha clínica digital con **odontograma** (15 de 16).
- Recordatorios automáticos de turnos (13 de 16), cada vez más por WhatsApp.
- Presupuestos y reportes básicos de ingresos y ocupación.
- Modalidad web o en la nube, accesible desde el celular.

### C.2 Diferenciadores reales (con evidencia)
- **Validación online contra obras sociales** (DentalTec afirma webservices con más de 15 obras sociales). Es un diferencial fuerte en Argentina, pero depende de convenios con las instituciones.
- **Asistente conversacional por WhatsApp con la API oficial de Meta** que agenda y reprograma (Órbita, AgendaPro, Doctocliq y Dentalink). Lo declaran varios, pero solo con mockups o métricas propias.
- **Paquete local completo** (ARCA, Mercado Pago, obras sociales y WhatsApp). Solo DentalCore lo afirma entero.
- **Transparencia comercial:** plan gratuito permanente o precio publicado (DentalSoft, Odonthia, DentiClick y VecDent). Facilita la adopción en profesionales independientes.
- **Interoperabilidad (HL7 FHIR) y exportación completa de la historia clínica** (DentalCore). Hoy es poco común y se vuelve relevante si se exige portabilidad de datos.

### C.3 Vacíos frecuentes del mercado argentino
1. **Agenda por sillón y prevención de solapamientos.** Solo 2 de 16 mencionan el sillón y **ninguno** documenta reglas para evitar choques de profesional y sillón al mismo tiempo. En un consultorio donde varios profesionales comparten sillones, es el problema central.
2. **Lista de espera:** 0 de 16. Cuando se cancela un turno, nadie documenta cómo se reasigna el horario liberado.
3. **Reglas de cancelación o reprogramación por anticipación:** solo Odonto.ar y DentalCore mencionan un plazo configurable.
4. **Privacidad de los recordatorios:** ningún sistema dice qué datos viajan en el mensaje de WhatsApp. Una buena práctica es no incluir información de salud.
5. **Precios opacos en los líderes:** Bilog, DentalTec, Dentalink y Órbita no publican precios.
6. **Normativa declarada:** solo 3 de 16 citan normativa argentina (DentalCore, AgendaPro y Bilog). Los demás no mencionan las leyes 25.326 ni 26.529.

### C.4 Oportunidades de innovación
- **Motor de agenda con doble restricción, por profesional y por sillón,** con duración por prestación y reglas explícitas y verificables (por ejemplo: "un turno que empieza cuando termina otro no se solapa").
- **Lista de espera automática:** al cancelarse un turno, ofrecer el horario al primer paciente en espera (por WhatsApp).
- **Política de cancelación configurable** (por ejemplo, 24 h) aplicada igual en la reserva online y en WhatsApp.
- **Recordatorios "privacy by design":** solo nombre, fecha, hora y profesional, con consentimiento del paciente registrado.
- **Plan gratuito o precio publicado** para el odontólogo independiente, el segmento con menos opciones transparentes.

---

## D. Recomendación final

### D.1 Cinco competidores prioritarios para analizar mediante demo
| # | Sistema | Motivo |
|---|---|---|
| 1 | **DentalCore** | Mejor puntaje (4,45). Paquete local completo **afirmado**: conviene confirmarlo en demo, porque además publica precios contradictorios. |
| 2 | **DentalSoft** | Único que menciona detección de solapamientos; tiene demo en vivo y reserva del paciente en 4 pasos. |
| 3 | **AgendaPro** | Mayor escala declarada en LatAm; agenda por sillón y duración por práctica (solo en mockup). |
| 4 | **Bilog** | Mayor base declarada en Argentina (+1.500 clínicas), sin precios ni demo pública. |
| 5 | **Dentalink** | Referente regional con módulo específico para Argentina documentado en su centro de ayuda. |

*Condición de la consigna: no se contacta a proveedores ni se crean cuentas con datos personales reales. Las "demos" se limitan a demos públicas, videos y sandboxes sin registro.*

### D.2 Tres productos de referencia de experiencia de usuario
1. **DentalSoft:** flujo de reserva del paciente en 4 pasos (datos, profesional, fecha, confirmación) con demo pública.
2. **AgendaPro:** agenda por sillón y por profesional, y turnos 24/7 desde Google.
3. **DentiClick:** odontograma interactivo con historial de versiones (demo pública).

### D.3 MVP sugerido

**Imprescindible (v1):**
1. Alta de profesionales, sillones o boxes y prestaciones con **duración propia**.
2. **Crear turno sin solapamiento por profesional y por sillón.** Entre turnos consecutivos del mismo sillón **o** del mismo profesional deben quedar **10 minutos** libres (decisión del equipo, ver `discovery.md`, RN-04 y RN-05).
3. **Cancelar y reprogramar** turnos. El paciente puede hacerlo online hasta **24 h antes**; el personal, siempre. Al cancelar, el horario queda libre.
4. **Sobreturno** que solo puede cargar el personal (recepcionista u odontólogo), registrado como excepción.
5. Agenda diaria y semanal por profesional y por sillón.
6. **Reserva online por el paciente** según la disponibilidad real.
7. **Recordatorio y confirmación por WhatsApp,** sin datos de salud en el mensaje. Si el paciente no tiene WhatsApp, se usa **email**.
8. **Ficha básica del paciente con odontograma,** por ser un estándar de mercado (C.1).

**Diferenciador (v1.x, después del núcleo):**
- Lista de espera automática al liberarse un horario.
- Política de cancelación configurable por consultorio.
- Plan gratuito o precio publicado para el odontólogo independiente.
- Registro de consentimiento para comunicaciones y auditoría de cambios en la agenda.

**Etapas posteriores:**
- Obras sociales y prepagas (nomencladores, coberturas, liquidaciones, validación online).
- Facturación electrónica ARCA.
- Cobro de señas con Mercado Pago.
- Periodontograma, imágenes y radiografías, consentimientos con firma electrónica.
- Asistentes con IA, multisucursal, exportación HL7 FHIR.

> **Contraste con la hipótesis inicial del equipo.** La v1 propuesta por el equipo (agenda con reserva online, recordatorios por WhatsApp y ficha con odontograma) **se mantiene**. La investigación agrega como núcleo la **prevención de solapamientos por profesional y sillón**, porque es el vacío más claro del mercado (C.3). Además, ubica el odontograma como requisito de mercado y no como diferencial. Por el plazo de entrega (6 días), lo que efectivamente se implementa en el trabajo práctico es el punto 2 (change OPSX). El resto queda especificado en el roadmap.

---

## Verificación de fuentes

> **Completada por el equipo (2026-11-20 y 2026-11-21).** La consigna exige que el equipo abra y compruebe personalmente como mínimo 5 fuentes de tipos distintos. Cada integrante verificó una fuente y dejó el detalle en `docs/discovery/verificacion/`. El agente solo consolidó los resultados en esta tabla y aplicó las correcciones.

| # | Tipo de fuente | URL | Qué afirma el informe | Responsable | Qué encontramos (fecha) | Corrección aplicada |
|---|---|---|---|---|---|---|
| 1 | Sitio oficial | https://bilog.com.ar/ | +1.500 clínicas; reserva y cancelación online; recordatorios por WhatsApp; sin precios publicados | Emanuel | **Coincide** (2026-11-21 11:20). Las 4 afirmaciones están respaldadas. La fuente agrega detalle: también reservan los pacientes nuevos, que completan sus datos desde el celular con un QR en la recepción; los recordatorios también salen por email; el precio se pide a los proveedores. Detalle en `verificacion/ema.md` (PR #5). | Ninguna. |
| 2 | Página de precios | https://dentalsoft.com.ar/ (sección planes) | Plan Gratis de 50 turnos/mes; Gestión $30.000; Pro $60.000 por profesional; costo de Meta de USD 0,026 por mensaje | Juan Sebastián | (2026-11-20 21:07) **Plan gratis: parcial**: el límite de 50 turnos/mes coincide, pero dura **3 meses** y después hay que pasar a Gestión o Pro; no incluye imágenes ni dominio propio, y el alta se pide por WhatsApp. **Precios: NO coinciden**: $30.000 y $60.000 son con 1 profesional, y cada profesional extra suma $10.000/mes (por ejemplo, Gestión con 10 profesionales cuesta $120.000), hasta 10; más, a cotizar. No aclara IVA. **Costo de Meta: coincide**, y aplica solo a los recordatorios automáticos del plan Pro. Detalle en `verificacion/juan-sebastian.md` (PR #4). | **Ajustar**: A.3 corrige el precio y la duración del plan gratis. Matriz B: el puntaje de precio baja de 5 a 4 y el total, de 4,05 a 4,00. |
| 3 | Centro de ayuda | https://ayuda.softwaredentalink.com/es/collections/19196578-modulo-exclusivo-para-argentina | Módulo para Argentina con obras sociales, conciliaciones y receta electrónica | Juan Cruz | **Coincide** (2026-11-20 14:36). Las 3 afirmaciones están respaldadas, y la fuente agrega detalle: convenios, planes y aranceles, simulación de presupuesto, recaudación de pago y conciliaciones con estado. Detalle en `verificacion/juan-cruz.md` (PR #1). | Ninguna en el informe. La ficha de Dentalink en A.3 pasa de mencionar solo los títulos a tener **contenido verificado**. |
| 4 | Video oficial | https://odonthia.com/ (video "Mirá Odonthia por dentro") | Odontograma FDI con dientes temporales; plan gratuito permanente | Enzo | **Coincide** (2026-11-20 16:45). Odontograma FDI (ISO 3950) en el video (min 2:44) con controles de dentición primaria, mixta y edéntulo (min 2:49). Los dientes temporales se confirman en el texto de la página, no en el video. El plan gratis no vence. **Los precios cambiaron**: IA ARS 77.100 y Max ARS 159.100 (el informe decía 76.600 y 158.100). Detalle en `verificacion/enzo.md` (PR #2). | **Ajustar**: se actualizan los precios de Odonthia en A.3 y se aclara que el plan gratis cubre la gestión del consultorio, mientras que la IA, el WhatsApp automático y los mensajes internos son pagos. |
| 5 | Demo pública | https://vectora.ar/pages/vecdent/ (demo sin registro) | Periodontograma de 6 puntos; portal del paciente; ARS 30.000, 50.000 y 80.000 | Sofia | (2026-11-20 19:29) **Periodontograma: coincide**, con 3 puntos vestibulares (MV, V, DV) y 3 palatinos/linguales (ML, L, DL), más recesión, sangrado, placa, movilidad y furcación; está incluido desde el plan Profesional. **Portal del paciente: NO coincide**: solo administrador, odontólogo y recepcionista crean turnos. Un rol personalizado "paciente" **no pudo crear turnos** y además ve el dashboard con estadísticas de la clínica y el menú Configuración, así que no es autogestión. **Precios: coinciden** ($30.000, $50.000 y $80.000 por mes), pero el sitio no aclara la moneda. Detalle en `verificacion/sofia.md` (PR #3). | **Ajustar**: A.2 VecDent / Reserva online pasa a "No evidenciado". A.3 aclara la moneda y que el periodontograma va desde el plan Profesional. Matriz B: P baja de 3 a 1 y el total, de 2,25 a 2,05. |

**Problemas ya detectados en el relevamiento automático** (para la reflexión):

- **DentalCore** publica dos precios incompatibles (ARS 75.000/mes en la home y USD 50/mes en el FAQ), y la prueba figura como "1 semana" y como "7 días".
- **AgendaPro** muestra una certificación **CENS/FONASA** en su página para Argentina, pero es chilena y no aplica al mercado argentino.
- Un resumen automático de buscador dijo que el odontograma de AgendaPro "no quedaba claro". La lectura directa de la página oficial lo muestra en texto y mockup. **Los resúmenes de buscador no se usaron como fuente.**
- **Reservo** se excluyó porque la página leída no permitió verificar funciones dentales. **Dently** se excluyó porque su dominio no resuelve. **Doctoralia Argentina** rechazó la conexión.
- **Open Dental**: que es open source y está en español es un dato conocido, pero **no figura** en la página consultada, así que se marcó "No evidenciado" en lugar de completarlo de memoria.

**Problemas detectados por la verificación manual del equipo** (2026-11-20):

- **VecDent: el agente marcó como "Comprobado" algo falso.** El relevamiento automático puso "Comprobado (portal en demo)" en la reserva del paciente porque la página de venta muestra una captura del "portal". Al probar la demo, solo el personal puede crear turnos, y un rol "paciente" armado a mano ve datos internos de la clínica. **Una captura de marketing no es una demostración de la función.** (Sofia)
- **Odonthia: los precios cambiaron de un día al otro**, o la lectura automática no fue exacta (ARS 76.600 a 77.100 y 158.100 a 159.100). Confirma por qué la consigna exige la fecha de consulta. (Enzo)
- **Moneda implícita**: VecDent, como DentalSoft, publica precios con "$" sin aclarar la moneda. El informe había escrito "ARS" deduciéndolo. (Sofia)
- **DentalSoft: el agente leyó mal una tabla de precios interactiva.** La página tiene un selector de cantidad de profesionales, y el relevamiento automático solo vio el valor inicial (1 profesional). De ahí sacó "por profesional", cuando en realidad cada profesional extra suma $10.000. Además, llamó "gratis" a lo que es una **prueba de 3 meses**. **Lo que cambia con la interacción del usuario no se ve en una lectura automática de la página.** (Juan Sebastián)
- **Bilog: la verificación confirmó todo** y sumó un dato que no estaba (el registro del paciente nuevo con QR en la recepción). No todo hallazgo es un error: también sirve para dar por buena la fuente. (Emanuel)

---

## Referencias

Todas consultadas el **2026-11-19**.

1. Bilog. https://bilog.com.ar/
2. DentalTec. https://web.dentaltec.com.ar/software-odontologos-argentina
3. DentalSoft. https://dentalsoft.com.ar/
4. DentalCore. https://dentalcore.app/
5. AgendaPro (Argentina, dental). https://agendapro.com/ar/dental/software-odontologico
6. Dentalink. https://www.softwaredentalink.com/
7. Dentalink, centro de ayuda, "Módulo exclusivo para Argentina". https://ayuda.softwaredentalink.com/es/collections/19196578-modulo-exclusivo-para-argentina
8. Odonthia. https://odonthia.com/
9. Órbita. https://hiorbita.com/
10. Odonto.ar. https://odonto.ar/
11. VecDent (Vectora). https://vectora.ar/pages/vecdent/
12. DentiClick. https://www.denticlick.app/
13. Clinic Cloud. https://clinic-cloud.com/
14. Doctocliq. https://doctocliq.com/
15. Medesk, odontología. https://www.medesk.net/es/solutiones/odontologia
16. Dentidesk. https://www.dentidesk.com/
17. Open Dental. https://www.opendental.com/

**Fuentes de contexto** (no usadas para completar celdas):

- Develop Argentina, "Software para clínicas y consultorios en Argentina 2026" (24/03/2026). https://developargentina.com/blog/software-clinicas-consultorios-argentina-2026
- Coderhouse, "IA para odontólogos y consultorios". https://www.coderhouse.com/coderlibrary/ia-para-odontologos-consultorios-automatizar-turnos

**Normativa mencionada** (a verificar en fuentes oficiales durante la KB):

- Ley 25.326 (Protección de Datos Personales).
- Ley 26.529 (Derechos del Paciente, Historia Clínica y Consentimiento Informado).
- Ley 25.506 (Firma Digital).
