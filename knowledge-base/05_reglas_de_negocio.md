# Reglas de Negocio

Cada regla tiene un código `RN-{DOMINIO}-{NN}`. Entre paréntesis va el código original del Discovery (`docs/discovery/discovery.md`, punto 7) para mantener la trazabilidad. Las reglas marcadas **[2026-11-20]** salieron de las respuestas de la líder a las preguntas abiertas (Ajustar sobre la KB v1).

## Definiciones

- **Intervalo de un turno**: `[inicio, fin)`, semiabierto, con `fin = inicio + duración de la prestación`.
- **Margen**: tiempo libre obligatorio entre turnos que comparten recurso. Es **configurable, con un mínimo de 10 minutos** (`MARGEN ≥ 10 min`; valor por defecto: 10).
- **Recurso**: el profesional o el sillón de un turno.
- **Turno activo**: un turno en estado `RESERVADO` o `CONFIRMADO`. Los `CANCELADO` no ocupan agenda.
- **Varios profesionales**: el consultorio puede tener **varios profesionales trabajando al mismo tiempo**, cada uno con su agenda, siempre que usen **sillones distintos**. El sillón es el recurso compartido. Cada profesional tiene sus **horarios de trabajo** semanales (por ejemplo, la Dra. A de lunes a viernes de 8 a 13 y el Dr. B de 14 a 20), un **sillón habitual** y las **prestaciones que realiza** (especialidades).
- **Conflicto**: un turno nuevo `N` está en conflicto con un turno activo existente `E` que comparte **profesional o sillón** si
  `N.inicio < E.fin + MARGEN` **y** `E.inicio < N.fin + MARGEN`.

## Dominio: Agenda (RN-AG)

- **RN-AG-01** (RN-01): cada prestación tiene una **duración propia** en minutos, **la misma para todos los profesionales** [2026-11-20]. El fin del turno se calcula como `inicio + duración`; no se ingresa a mano.
- **RN-AG-02** (RN-02): un **profesional** no puede tener dos turnos activos en conflicto. En particular, **no puede atender en dos sillones a la vez**.
- **RN-AG-03** (RN-03): un **sillón o box** no puede tener dos turnos activos en conflicto.
- **RN-AG-04** (RN-04): entre dos turnos consecutivos que comparten **sillón o profesional** debe haber al menos el **margen** libre (mínimo 10 min), para limpieza y preparación del sillón y para que el profesional cambie de sillón. El administrador puede **aumentar** el margen, pero **nunca bajarlo de 10 minutos**; un valor menor se rechaza [2026-11-20].
- **RN-AG-05** (RN-05): **casos de borde del margen** (con margen = 10). Con un turno existente de 10:00 a 10:30 en el Sillón 1 con la Dra. A:
  - un turno nuevo a las **10:30** que comparte sillón o profesional → **se rechaza** (`MARGEN_INSUFICIENTE`);
  - un turno nuevo a las **10:40** que comparte sillón o profesional → **se acepta**;
  - un turno nuevo a las **10:30** con **otro profesional y otro sillón** → **se acepta** (no comparte recursos).
- **RN-AG-06** (RN-06): **sobreturno** [2026-11-20].
  - Solo el **personal** (recepcionista, odontólogo o administrador) puede cargarlo, y **exige un motivo obligatorio**.
  - El sobreturno puede **superponerse con la agenda del profesional**: es una excepción a RN-AG-02 y al margen del profesional.
  - **Nunca se exceptúa del sillón**: necesita un **sillón libre**, que respete RN-AG-03 y el **margen de limpieza** de RN-AG-04. Si hay otro sillón disponible, se usa ese y solo hay solapamiento del profesional. **Si no hay ningún sillón libre con margen, el sobreturno se rechaza** (`SIN_SILLON_DISPONIBLE`): la limpieza es obligatoria sí o sí.
  - Queda marcado (`es_sobreturno = true`), con su motivo, y se audita.
- **RN-AG-07** (RN-07): **cancelar o reprogramar** [2026-11-20].
  - **El paciente, online**: desde **su cuenta** (sesión iniciada), ingresando el **número de reserva**, hasta **24 h antes** del inicio. Después de ese plazo, el error es `CANCELACION_FUERA_DE_PLAZO` y tiene que comunicarse con el consultorio.
  - **Por el consultorio** (el paciente llama o se acerca): lo hace el personal, después de validar el **DNI** del paciente y el **número de reserva (token)**. El personal puede hacerlo en cualquier momento.
- **RN-AG-08** (RN-08): al **cancelar** un turno, pasa a `CANCELADO` y el horario queda **libre** de inmediato para otra reserva. Reprogramar equivale a crear un turno nuevo válido y cancelar el anterior, en una única transacción.
- **RN-AG-09**: no se puede crear ni reprogramar un turno **en el pasado**.
- **RN-AG-10**: no se puede crear un turno que se superponga con un **bloqueo de agenda** o una **ausencia o licencia** del profesional (RN-AG-16).
- **RN-AG-11**: no se puede asignar un turno a un profesional, sillón o prestación **inactivos**.
- **RN-AG-12**: la verificación de conflicto y la creación del turno son **atómicas**. Si dos reservas compiten por el mismo hueco, solo una se confirma (DD-03).
- **RN-AG-13**: un turno debe quedar **completo dentro de un horario de trabajo** del profesional: `inicio` y `fin` dentro de la misma franja de ese día de la semana. Un turno fuera de horario se rechaza con `FUERA_DE_HORARIO`. Solo un **sobreturno** cargado por el personal puede quedar fuera de horario (RN-AG-06).
- **RN-AG-14**: **extensión por complicaciones** [2026-11-20]. Si durante la atención surge una complicación, el **personal** puede **extender** el fin de un turno ya iniciado. La extensión se valida como cualquier turno (sillón libre con margen y profesional). Si choca con el turno siguiente, el sistema lo avisa y el personal decide: reprogramar el siguiente o registrar la extensión como sobreturno. Se audita con su motivo.
- **RN-AG-15**: **especialidades** [2026-11-20]. Un turno solo puede asignarse a un profesional **habilitado para esa prestación**. Si no lo está, el error es `PROFESIONAL_NO_HABILITADO`.
- **RN-AG-16**: **ausencias y licencias del profesional** [2026-11-20]. El **administrador** o la **recepcionista** registran las ausencias (por ejemplo, enfermedad) y las licencias (por ejemplo, vacaciones) de cada profesional, con fecha y hora de inicio y fin. Mientras dura, el profesional **no recibe turnos** (RN-AG-10). Si ya tenía **turnos dados en ese período**, el sistema los lista y la recepción los **reasigna** a otro profesional habilitado para la prestación (RN-AG-15) en el mismo horario. Si no hay ninguno, los **reprograma**. En los dos casos se **avisa al paciente** por su canal (RN-NO-02) [2026-11-21].
- **RN-AG-17**: **ausencia del paciente** [2026-11-21; cambiada el 2026-11-24]. Si el paciente no se presenta, lo marcan como **ausente** la **recepción desde la agenda** o el **odontólogo del turno desde la ficha clínica**. Lo marca siempre una persona: el sistema **nunca** pasa un turno a `AUSENTE` por su cuenta. El administrador no la marca: lo hacen solo la recepción y el odontólogo del turno. Se puede marcar recién **15 minutos después del inicio** (tolerancia fija) y solo si el turno no tiene la llegada registrada (RN-AG-20). El turno pasa a `AUSENTE`, deja de estar activo y **el tiempo que resta queda liberado** para otro turno o un sobreturno, siempre desde el momento actual (RN-AG-09). La ausencia queda registrada en la ficha del paciente.
  - **Deshacer la ausencia**: si el paciente llega después de que lo marcaron ausente, **solo la recepción** puede deshacerla, mientras el turno no haya terminado y **solo si su horario sigue libre**: el turno vuelve a estar activo y se validan otra vez las reglas de superposición (RN-AG-02 a RN-AG-05). Si el horario ya se dio a otro turno o a un sobreturno, no se deshace y la recepción le da al paciente **otro turno**. En la ficha clínica, el odontólogo ve que la ausencia **se deshizo**, cuándo y quién lo hizo.
- **RN-AG-20**: **llegada del paciente** [2026-11-24]. Cuando el paciente llega al consultorio, **solo la recepción** registra la **llegada** desde la agenda, aunque llegue tarde, mientras el turno no haya terminado. El turno **sigue activo** y sigue ocupando el profesional y el sillón: registrar la llegada no lo pasa a `ATENDIDO`, para no liberar el horario mientras el paciente se atiende. Cuando termina el horario de un turno con la llegada registrada, el sistema lo pasa a **`ATENDIDO`** automáticamente. Un turno que terminó sin llegada ni ausencia queda **sin registrar**: la recepción lo puede corregir después, registrando la llegada (pasa a `ATENDIDO`) o la ausencia, y el paciente lo ve como "Sin registrar" en Mis turnos mientras tanto (C-42).
- **RN-AG-18**: **agenda propia del odontólogo** [2026-11-23]. El odontólogo **solo ve los turnos de su profesional**, en la vista del día y de la semana. La API aplica ese filtro **según la sesión**, sin importar el profesional o el sillón que se pidan: si pide otro profesional, recibe igual solo los suyos. La recepción y el administrador ven la agenda de todos. No cambia lo que el odontólogo puede crear, cancelar o reprogramar (ver la matriz de [03_actores_y_roles.md](03_actores_y_roles.md)).
- **RN-AG-19**: **turnos afectados por un bloqueo** [2026-11-23]. Si el odontólogo o el administrador **bloquean** un período en el que ya hay turnos dados, el sistema los lista igual que en una ausencia (RN-AG-16). La **recepción** o el **administrador** los **reasignan** a otro profesional habilitado o los **reprograman**, con el mismo aviso al paciente. El odontólogo no reasigna: avisa a la recepción.

## Dominio: Reserva online (RN-RO)

- **RN-RO-01**: el paciente solo ve **horarios libres** para una prestación. Nunca ve nombres ni datos de otros pacientes, ni estadísticas del consultorio.
- **RN-RO-02**: la disponibilidad que ve el paciente aplica las mismas reglas que la reserva del personal: RN-AG-01 a RN-AG-05, RN-AG-09, RN-AG-10, RN-AG-13 y RN-AG-15.
- **RN-RO-03**: el paciente **no puede** generar sobreturnos.
- **RN-RO-04**: al reservar, el paciente **elige un profesional en particular** o marca **"sin preferencia"**.
  - **Con profesional elegido**: ve solo los horarios libres de ese profesional, dentro de su horario de trabajo.
  - **Sin preferencia**: ve la **unión** de los horarios libres de los profesionales activos **habilitados para la prestación** (RN-AG-15) que trabajan ese día. Cada horario aparece una sola vez.
- **RN-RO-05**: en una reserva **sin preferencia**, el sistema **asigna el profesional** al confirmar [2026-11-20]:
  1. solo entre los profesionales **libres** en ese horario y **habilitados para la prestación**;
  2. el que tenga **menos turnos ese día**;
  3. si hay empate, uno **al azar**.
- **RN-RO-06**: el sistema **asigna el sillón** en toda reserva online [2026-11-20]: primero el **sillón habitual** del profesional, si está libre con margen; si no, cualquier otro sillón activo libre con margen. El paciente nunca elige sillón.
- **RN-RO-07**: un horario se muestra como disponible solo si existe **al menos una combinación de profesional habilitado y sillón** libre que cumpla las reglas de RN-RO-02.
- **RN-RO-08**: las acciones online del paciente (reservar, cancelar, reprogramar, ver sus turnos) requieren **cuenta de paciente** con sesión iniciada. El paciente **se registra una sola vez** (la primera reserva) y después solo inicia sesión [2026-11-21]. La cuenta de paciente **no es una cuenta del personal**: solo accede a sus propios datos y turnos.
- **RN-RO-09**: **sillón habitual exclusivo** [2026-11-23]. Un sillón es el **sillón habitual de un solo profesional** (activo o inactivo). Asignar uno que ya tiene otro profesional se rechaza con `SILLON_HABITUAL_OCUPADO`; para pasarlo, primero hay que quitárselo al anterior. Lo valida el dominio, sin restricción en la base (C-34).

## Dominio: Notificaciones (RN-NO)

- **RN-NO-01** (RN-09): los recordatorios **no incluyen datos de salud**. Solo llevan el nombre del paciente, la fecha, la hora, el profesional y la dirección del consultorio, más los links de confirmar, cancelar o reprogramar.
- **RN-NO-02** (RN-09): el canal es **WhatsApp** si `tiene_whatsapp = true`; si no, **email**.
- **RN-NO-03**: solo se envían comunicaciones a pacientes con `consentimiento_comunicaciones = true` (Ley 25.326; ver DD-07).
- **RN-NO-04**: se envía **un recordatorio 24 h antes** del inicio de cada turno activo (`RESERVADO` o `CONFIRMADO`). Si el turno se reserva con menos de 24 h de anticipación, no se envía recordatorio; alcanza con la confirmación de la reserva.

## Dominio: Clínica (RN-CL)

- **RN-CL-01**: la ficha clínica y el odontograma solo los ve y los edita el rol `ODONTOLOGO` (y `ADMIN` en modo lectura). La recepción no accede (Ley 26.529, datos sensibles).
- **RN-CL-02**: los cambios en el odontograma se registran como **historial** (quién, cuándo, qué pieza y cara). No se sobrescribe un registro anterior.
- **RN-CL-03**: el odontograma usa la **notación FDI** e incluye dientes temporales (estándar de mercado según el informe, C.1).
- **RN-CL-04**: **periodontograma por visita** [2026-11-24]. Cada vez que el odontólogo guarda un periodontograma se crea una **versión nueva** con la fecha, la hora y el profesional; las versiones anteriores quedan de solo lectura y no se sobrescriben (igual que RN-CL-02). Para corregir, se guarda otra versión. Se pueden ver todas las versiones del paciente, de la más nueva a la más vieja. El acceso es el de RN-CL-01: el odontólogo lo carga y lo ve, el administrador solo lo ve y la recepción no accede.
- **RN-CL-05**: **datos del periodontograma** [2026-11-24]. Solo piezas **permanentes** (FDI 11–48). Por pieza se registra si está **ausente**, la **movilidad** (grado 0 a 3) y, en las piezas multirradiculares (molares y primeros premolares superiores 14 y 24), la **furcación** (grado 0 a 3). Por cada uno de los **6 sitios** de la pieza (mesiovestibular `MV`, vestibular `V`, distovestibular `DV`, mesiolingual o mesiopalatino `ML`, lingual o palatino `L`, distolingual o distopalatino `DL`) se registran la **profundidad de sondaje** (0 a 15 mm, entero), la **recesión** (−5 a 15 mm, entero; negativa si el margen de la encía está por encima del límite amelocementario), el **sangrado** al sondaje y la **placa** (sí o no). Una pieza ausente no lleva sitios, movilidad ni furcación. Una versión puede tener solo algunas piezas cargadas; cada pieza cargada lleva sus 6 sitios completos.
- **RN-CL-06**: **estado periodontal de cada pieza** [2026-11-24]. El sistema lo calcula; nadie lo carga a mano. Por sitio, la **pérdida de inserción** es profundidad de sondaje + recesión. Se toma el peor sitio de la pieza:
  - **Sano**: pérdida de inserción 0 o menos, profundidad de sondaje de 3 mm o menos en todos los sitios y sin sangrado.
  - **Leve**: pérdida de inserción máxima de 1 a 2 mm, o pérdida 0 con sangrado o con alguna profundidad mayor de 3 mm.
  - **Moderado**: pérdida de inserción máxima de 3 a 4 mm.
  - **Severo**: pérdida de inserción máxima de 5 mm o más.
  - Una pieza ausente no tiene estado. El estado es **orientativo** y no reemplaza el diagnóstico del profesional (DD-13).

## Dominio: Coberturas (RN-OS) [2026-11-24, C-18]

- **RN-OS-01**: **catálogo de obras sociales, planes y nomenclador**.
  - Solo el `ADMIN` los da de alta, los edita y los activa o desactiva. No se borran, para no perder el historial de las afiliaciones.
  - Una obra social puede ser `OBRA_SOCIAL` o `PREPAGA`, y tiene uno o más planes.
  - Desactivar una obra social no desactiva sus planes, pero ninguno de ellos se puede usar en una afiliación nueva.
- **RN-OS-02**: **afiliación del paciente**.
  - El paciente se asocia a un **plan** con su **número de afiliado** y una **vigencia**: una fecha desde y, opcionalmente, una fecha hasta.
  - Una afiliación está **vigente** en una fecha si la fecha cae dentro de la vigencia.
  - Un paciente tiene **como máximo una afiliación vigente** en cada fecha: dos vigencias del mismo paciente no se superponen.
  - Para cambiar de plan o de obra social se cierra la afiliación actual con `vigente_hasta` y se crea otra.
  - Las afiliaciones no se borran.
- **RN-OS-03**: **número de afiliado**.
  - Es obligatorio y tiene de 1 a 30 caracteres: letras, números, `-`, `/` y `.`.
  - Se guarda sin espacios en los extremos.
  - Es **único dentro de la misma obra social**: dos pacientes no pueden tener el mismo número en la misma obra social.
- **RN-OS-04**: **paciente particular**. Un paciente sin afiliación vigente en una fecha se atiende como **particular**, sin cobertura. Dar un turno no exige cobertura, así que la agenda no cambia con C-18.
- **RN-OS-05**: **nomenclador**.
  - Cada código de práctica es único y puede vincularse a una prestación del consultorio. Ese vínculo es el que usan C-19 para calcular la cobertura y C-20 para la liquidación.
  - Una prestación puede no tener código; en ese caso se cobra como particular.
  - La cobertura por plan, los topes y el copago **no** son parte de C-18.
- **RN-OS-06**: **no se afilia a lo inactivo**. No se puede crear una afiliación con un plan inactivo ni con un plan de una obra social inactiva. Las afiliaciones que ya existen no cambian si después se desactiva el plan o la obra social.
- **RN-OS-07**: **privacidad de la cobertura**. La obra social y el número de afiliado son datos personales del paciente (Ley 25.326).
  - No se incluyen en recordatorios, mensajes ni logs (RN-NO-01).
  - La ve el personal, según la matriz de `03_actores_y_roles.md`, y el propio paciente en "Mi cuenta" (PA-21). Un paciente nunca ve la cobertura de otro.

## Dominio: Presupuestos (RN-PR) [2026-11-24, C-19]

- **RN-PR-01**: **aranceles**. Cada prestación puede tener un **precio particular** en pesos, mayor que cero y con dos decimales.
  - Solo el `ADMIN` lo carga y lo cambia.
  - Una prestación sin arancel no se puede presupuestar: se rechaza con un error claro.
- **RN-PR-02**: **cobertura por plan**. Para cada plan y cada código del nomenclador, el `ADMIN` carga el **porcentaje** del precio particular que cubre el plan, un entero de 0 a 100.
  - Si no hay porcentaje cargado, el plan no cubre ese código (0 %).
  - Los topes, las autorizaciones y la validación con la obra social no son parte de C-19 (C-21).
- **RN-PR-03**: **cálculo de cada ítem**.
  - Importe = precio particular × cantidad.
  - Cobertura = importe × porcentaje / 100, redondeado a centavos hacia arriba desde 0,5 (*half up*). El porcentaje sale de la afiliación vigente del paciente en la fecha del presupuesto y del código del nomenclador del ítem.
  - Copago = importe − cobertura. Es lo que paga el paciente.
  - Si la prestación tiene un solo código activo, se usa ese. Si tiene varios (RN-OS-05), el ítem indica cuál. Si no tiene ninguno, el ítem es particular (0 %).
  - Un paciente **particular** (sin afiliación vigente) paga todo: la cobertura es 0 y el copago es el importe.
- **RN-PR-04**: **el presupuesto es una foto**. Al emitirlo se guardan en cada ítem el precio, el porcentaje y los importes calculados, y la afiliación usada. Si después cambian los aranceles, la cobertura del plan o la afiliación del paciente, el presupuesto emitido no cambia. Para tener los valores nuevos se emite otro presupuesto.
- **RN-PR-05**: **estados y vencimiento**.
  - Un presupuesto nace `PENDIENTE` y pasa a `ACEPTADO` o `RECHAZADO`. Un presupuesto aceptado o rechazado no cambia de estado.
  - **Vence a los 30 días** de su fecha (PA-26). Un presupuesto `PENDIENTE` cuya fecha de vencimiento ya pasó se muestra como **vencido** y no se puede aceptar; sí se puede rechazar.
  - Los presupuestos no se borran ni se editan. Para corregir uno, o para renovar uno vencido, se emite otro.
- **RN-PR-06**: **a partir del odontograma**. Lo arma el **odontólogo** (PA-23).
  - Cada ítem es una prestación con una pieza y una cara opcionales, en notación FDI (RN-CL-03).
  - Al armarlo, el sistema **sugiere** las piezas y caras cuyo último estado en el odontograma es `A_TRATAR` o `CARIES`. Es solo una sugerencia: el odontólogo elige la prestación de cada una y puede sumar o quitar ítems.
  - Un presupuesto tiene al menos un ítem.
- **RN-PR-07**: **privacidad del presupuesto**. Las piezas y caras son datos de salud (Ley 26.529) y los importes y la cobertura, datos personales (Ley 25.326).
  - No se incluyen en recordatorios, mensajes ni logs (RN-NO-01).
  - Solo los ven los roles de la matriz de `03_actores_y_roles.md`.

## Dominio: Excepciones globales

- **RN-GL-01**: **solo datos ficticios** en el repositorio, los seeds, los tests, los ejemplos y las capturas.
- **RN-GL-02**: todas las fechas se guardan como `timestamptz` y se interpretan en la zona `America/Argentina/Buenos_Aires` (confirmada el 2026-11-20).
- **RN-GL-03**: toda creación, cancelación, reprogramación, confirmación, extensión o sobreturno queda en `TurnoAuditoria`.
