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
