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
