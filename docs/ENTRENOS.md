# Entrenamientos guiados (tipo FREZ)

Código: `core/domain/.../workout/` (modelo + `WorkoutEngine`, puro y con tests) y
`feature/workouts` (editor, configuración de serie, ejecución y resumen).

## Sesión
Tabla de series: `#`, `REPS`, `WORK` (s tirando), `REST` (s de descanso), `GRIP`.
- **Edición masiva**: marca filas y cambia reps/work/rest a la vez (vacío = no tocar).
- **Reordenar**: arrastra por el asa.
- **Duración estimada**: se calcula simulando el motor (cuenta atrás de 10 s incluida).
  La plantilla de ejemplo (6 series 6-6-2-2-2-2, 10 s / 20 s) da 9m 50s.
- Modo de manos: **una mano**, **ambas seguidas** (todas las series con una y luego
  con la otra) o **alternando**.

## Configuración por serie (icono GRIP)
- Agarre: Semiarqueo 4 dedos, Extensión 3 dedos, Semiestirado 4 dedos o uno propio
  (regleta mm + dedos + tipo, p. ej. «20 mm / 2 dedos centrales / semiarqueo»).
- Mano de la serie: como la sesión, derecha o izquierda.
- Objetivo: Máximo (Peak) o Zona (kg mín/máx). Con «Umbral basado en registro» la
  zona es un % del último registro de esa mano y agarre (solo agarres estándar).
- Hasta el fallo: Desactivado / Repetición (suma reps hasta que una no aguanta) /
  Tiempo (cada rep dura hasta bajar del umbral).
- Inicio: por tiempo o por peso (la rep empieza al superar umbral o el mínimo de la
  zona; después el temporizador sigue aunque la fuerza baje).
- Umbral inicio/parada (2,00 kg por defecto).

## Regla del modo alternando
Cada mano tiene su propio descanso, pero el **turno es estricto** (D, I, D, I…).
Si las dos han descansado, solo cuenta la mano a la que le toca; la otra espera
(«Izquierda lista, después»). Un tirón nunca se apunta a las dos manos ni a la que
no tocaba. «Saltar» cede el turno (se apunta como saltado). Tests:
`WorkoutEngineTest` (descansos vencidos a la vez, retrasos, saltar turno, fallo por
reps y por tiempo, inicio por peso, % de registro, orden de manos).

## Durante la ejecución
Pitido + vibración al empezar y acabar cada tirón, cuenta atrás de 3 s, alarma al
pasar del máximo (cada 0,7 s) y aviso suave por debajo del mínimo (cada 2 s).
Al terminar: máximo, media tirando y % de tiempo en zona por mano. Se guarda en
Historial → Entrenamientos y, si el agarre es estándar, el máximo de cada mano
también como registro (alimenta Evolución).

## Ronda 3: biblioteca de agarres, evaluación y pantalla partida

### Biblioteca de agarres («Mis agarres»: Perfil, Mis agarres)
Cada agarre tiene nombre libre (opcional), ancho de regleta en mm (4-60, cualquiera:
15, 20, 25, 30…), nº de dedos (1-4) y cuáles («centrales», «delanteros»…), y tipo
(arqueado, semiarqueado, extendido/abierto, drag, otro). Sin nombre se muestra como
«20 mm / 2 dedos centrales / semiarqueado». Los 3 de siempre (Semiarqueo 4 dedos,
Extensión 3 dedos, Semiestirado 4 dedos, 20 mm) se crean solos como agarres
predefinidos: se pueden editar pero no borrar, y siguen enlazados a sus registros
antiguos. Las series eligen agarre de la biblioteca (pestañas en la configuración de
serie; la columna GRIP del editor muestra el nombre).

### Evaluación por agarre y mano («Evaluar agarre»)
Desde Perfil, Mis agarres, Entrenos o la configuración de una serie. Por mano: «Medir
con báscula» (tira y mantén; al parar se guarda el máximo y, de la parte sostenida del
tirón —lecturas por encima del 60 % del pico—, el mínimo y la media) o «A mano» (máximo
obligatorio, mínimo y media opcionales). Se guarda en `grip_evaluation` y el máximo
también como registro (alimenta Evolución e Historial). Perfil muestra por agarre el
máximo de cada mano y su fecha.

El objetivo «Umbral basado en registro» usa el último máximo evaluado de ESE agarre y
ESA mano. Para los predefinidos, si nunca se evaluaron, vale el último registro antiguo
(se indica como «último registro»). Si falta, la serie avisa y ofrece evaluar, y al
pulsar Empezar un aviso lista los agarres/manos sin evaluar («Evaluar ahora» o
«Entrenar igualmente», sin zona en esas series).

### Evolución por agarre
El selector de Evolución lista cada agarre de la biblioteca (+ dinamómetro); cada uno
tiene su gráfica y su comparación lesionada vs sana. Inicio muestra una fila por agarre
con el % y los kg de cada mano («Derecha 37,4 kg · Izquierda 41,1 kg»); el % es la
fuerza de la mano lesionada respecto a la sana (100 % = igual de fuertes).

### Ejecución en dos columnas
En «Ambas seguidas» y «Alternando» la pantalla se parte en IZQUIERDA y DERECHA, cada
una con su estado (TIRA / DESCANSA / ESPERA) y su temporizador a la vez; la mano a la
que le toca va resaltada en grande y con color. El turno estricto no cambia
(`WorkoutEngine.snapshot().panels`; tests en `GripLibraryTest`, y los de
`WorkoutEngineTest` siguen pasando).

### Báscula simulada
Durante un entreno o una evaluación sigue el ritmo: sube al empezar cada «Tira» y baja
a 0 en el descanso (la derecha simulada es algo más floja). `ForceSource.setEffort`
es una pista que solo usa el simulador; las básculas reales la ignoran.

### Datos
Esquema v3 (`migrations/2.sqm`): `custom_grip` gana `name`, `finger_count`, `built_in`;
tabla `grip_evaluation`; `measurement.grip_id`. Los agarres propios de la ronda 2 se
conservan con la misma etiqueta que tenían. Los registros de agarres propios usan
`Exercise.CUSTOM` (oculto en selectores).

## Ronda 4

### Gráfica en directo
`LiveForceTrace` (designsystem) guarda las muestras en `ForceRingBuffer` (core:domain, `LongArray`/`FloatArray`,
sin objetos por muestra). El `Long` de versión (`tick`) y el reloj de fotogramas (`withFrameNanos`, 60 fps) se leen
SOLO dentro de `drawWithCache`/`onDrawBehind`: cada muestra invalida el dibujo, no recompone la pantalla. La ventana
de 10 s se desliza con el tiempo real (150 ms de retardo de render) interpolando entre muestras; Path/Brush/etiquetas
se reutilizan y se decima a ~0,5 puntos por píxel. Ejes con `niceScale` (0, 10, 20, 30…). Las usan la ejecución, la
evaluación de agarre y «Medir». El valor grande se publica a 10 Hz como mucho y redondeado a 0,1 kg.

### Flujo de inicio
Pantalla previa (resumen + estado de la báscula) con **EMPEZAR** → «Prepárate…» 5 s → empieza solo. Con la báscula
real EMPEZAR queda desactivado hasta recibir lecturas (la simulada siempre puede). Pausa siempre disponible.
Durante el trabajo se ve el nombre completo del agarre («Semiarqueo 4 dedos · 20 mm») y la mano que toca.
Botones inferiores: tres `RgStackedButton` iguales (icono arriba, etiqueta de una línea).

### Regla de «Tira»
El tiempo de trabajo solo corre con fuerza ≥ umbral (2 kg por defecto) y nunca antes de alcanzarlo. Si ya empezó y
baja del umbral más de 1,5 s: «Parada: fallo», la repetición termina y pasa a descanso (en «Repetición» además se
cortan las reps restantes de esa serie y mano; en «Tiempo» es el final normal). Tests en `WorkoutEngineTest`.

### Agarres y sesión por defecto
Predefinidos (editables, no borrables): Semiarqueo 4 dedos, Extensión 3 dedos, Semiestirado 4 dedos (de siempre) y
Bidedo estirado / arqueado / pistola / pistola arqueado (`presetKey`, guardado como `PRESET:...` en `built_in`).
Sesión «Entreno de agarre» (se crea una vez): 6 series 6-6-2-2-2-2, 10 s / 20 s, alternando, orden Semiarqueo,
Extensión, Bidedo estirado, pistola, arqueado, pistola arqueado.
Cada serie tiene dos bloques «Mano izquierda» / «Mano derecha» con su objetivo (Máximo / zona kg / % de mi registro,
sobre el máximo de ESE agarre y ESA mano) y su «Hasta el fallo» (`WorkoutSet.targetFor/toFailureFor`; la izquierda sin
valor propio copia la derecha). El selector de mano por serie desapareció; «Ajustes de la sesión» (plegado) permite
«Solo derecha / Solo izquierda» y el orden. Esquema v4 (`migrations/3.sqm`).

### Medir
Inicio y Entrenos → «Medir»: valor actual, MÁXIMO que se mantiene, intentos, «Reiniciar máximo», «¿Quién?» (recuerda
los últimos). «Guardar»: agarre (biblioteca + dinamómetro), mano y nombre. Perfil → «Mis máximos» (mejor valor por
agarre y mano, evaluaciones y mediciones propias) y «Registros de amigos» aparte (`MaxRecords`;
`Measurement.person`, vacío = propio). Los amigos no entran en Evolución, Historial ni Inicio.

## Ronda 6
- **Cambio de mano**: ajustes de la sesión → «Cambio de mano» (0–10 s, 3 por defecto). Al acabar el tirón de una mano
  sale «Cambia a IZQUIERDA · 3…2…1» (no corre tiempo de tirar). Después, la mano a la que le toca espera tanto como
  haga falta: esperar NUNCA cuenta como fallo y no se fuerzan turnos; en cuanto tira, es la mano en turno. El descanso
  de cada mano cuenta desde el final de SU tirón. Tests: `HandoverTest`.
- **Una sola lectura de los registros**: Inicio, Evolución, Mis máximos e Historial usan `GripKey` (agarre de la
  biblioteca por `gripId`; si ese agarre ya no existe, el predefinido de su ejercicio; si no, el ejercicio suelto).
  `CoherenceTest` mezcla registros manuales, de evaluación, de entreno y huérfanos y exige que todas las pantallas
  coincidan.
- **Historial**: una fila por día y agarre con las dos manos («Dcha 13,0 · Izda 31,5 kg»); tocar abre «Editar» (fecha
  para las dos manos, kilos de cada mano, «Avanzado… Mover a la otra mano», borrar por mano). Varios registros el mismo
  día: fila expandible.
- **Evolución**: «Por mano» (varios agarres a la vez con casillas, derecha sólida y izquierda suave y discontinua,
  leyenda y tabla por agarre) y «Comparar» (% lesionada/sana en el tiempo con la línea del 100 % y barras).
- **Inicio**: saludo → Medir ahora → Entrenar (Entreno de agarre) → ejercicios → «Lesionada respecto a sana» plegable.
- **CSV** (UTF-8 con BOM, «;», decimal con coma), ordenado por fecha, `rehagrip-AAAA-MM-DD.csv`. Ejemplo:

```
Fecha;Hora;Ejercicio;Agarre;Regleta (mm);Mano;Mano lesionada;Kilos;Origen;Persona;Molestias;Nota
1970-01-03;00:00;Extensión;Extensión 3 dedos;20;Derecha;Sí;13,0;Entreno;Pepe;Polea A2 2/10;Entreno «Repeticiones»
1970-01-06;01:00;Extensión;Extensión 3 dedos;20;Izquierda;No;31,5;Evaluación;;;Evaluación · Extensión 3 dedos
```

## Ronda 7: regla del tirón (sustituye a «parada: fallo tras 1,5 s»)
- El tiempo de trabajo de una repetición es **acumulativo** y solo corre con fuerza >= umbral. Por debajo, el contador se
  PARA y espera: no hay fallo, ni descanso, ni cambio de mano o serie, y tampoco avanzan los descansos de la otra mano.
- La repetición termina cuando el tiempo acumulado por encima del umbral llega al tiempo de trabajo, o con **Saltar**
  (disponible en TIRA/ESPERA, con confirmación corta; cuenta como saltada y empieza el descanso).
- El **fallo** solo existe con «Hasta el fallo» en Repetición o Tiempo (fuerza < umbral más de 1,5 s tras empezar). Con
  «Desactivado» nunca hay fallo automático. Tests: `WorkoutEngineTest` (tirar 3 s y soltar 20 s no avanza; soltar y volver
  suma hasta completar; Saltar avanza; fallo solo con Hasta el fallo; descansos congelados en pausa).
- Debug: la báscula «Simulada contaminada» también suelta entre los segundos 3 y 7 de cada tirón y vuelve a tirar.

## Mis básculas
Al abrirse lanza un escaneo de ~8 s y lista en vivo las básculas detectadas (nombre, RSSI) junto a las guardadas; cada una
detectada se guarda sola (nombre por defecto «Báscula IF_B7»). Una báscula elegida antes de existir la lista se añade al
arrancar. Nombre editable, «Usar esta», «Olvidar» y marca de la activa.

## Ronda 9: Evolución «Por mano»
Cada agarre tiene color (paleta de 8, guardado en `custom_grip.color_index`) y siglas
(`initials`, máx. 3; SA, E, SE, BE, BA, BP, PA, DM por defecto; propios = iniciales del
nombre), editables en el formulario de agarre. Lógica en `core/domain/grip/GripStyle.kt` y
`progress/ChartRules.kt` (eje desde el primer registro hasta hoy, dodge, modo
Superpuestas/Separadas, máx. 8 superpuestas). Gráfica: `designsystem/chart/GripChart.kt`.
Derecha = círculo lleno y línea continua; izquierda = círculo claro, borde y línea discontinua al 55 %.
Capturas en `docs/capturas/ronda9/`.
