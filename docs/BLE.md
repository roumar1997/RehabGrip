# Báscula Crane Scale WH-C06 (BLE)

La WH-C06 (nombre BLE «IF_B7») **no se conecta**: emite el peso en cada anuncio BLE.
Código: `core/ble` (`CraneAdvertisementParser`, `AndroidCraneForceSource`,
`IosCraneForceSource`, `SimulatedForceSource`).

## Protocolo (contrastado en 3 proyectos)

Fuentes: [IsoForce](https://github.com/ryussx/IsoForce) (`src/scale.ts`),
[Crane](https://github.com/sebws/Crane) (`ScaleWatcher.java`),
[hang-ten PR 451](https://github.com/Asherlc/hang-ten/pull/451) (`WHC06Protocol.swift`).

| Campo | Valor |
|---|---|
| Company ID del manufacturer data | `0x0100` (256) |
| Peso | bytes **10-11** del payload, big-endian, en centésimas (raw / 100) |
| Byte 14 (opcional) | nibble alto = estable; nibble bajo = unidad (1 kg, 0/2 lb, 3 st, 4 jin) |
| Longitud mínima | 12 bytes (paquetes antiguos sin byte de unidad); también hay de 17 y 18 bytes que empiezan por `02 03` |
| Frecuencia | ~8-10 paquetes/s |

**Ojo con los offsets:** se cuentan sobre el payload **sin** los 2 bytes del company ID.
- Android `ScanRecord.getManufacturerSpecificData(0x0100)` ya los quita.
- iOS `CBAdvertisementDataManufacturerDataKey` los **incluye** (`00 01`, little-endian):
  `CraneAdvertisementParser.stripCompanyId` los quita antes de parsear.

Se descartan lecturas > 300 kg (hang-ten: los radares de presencia LD2410B usan el
mismo company ID y dan falsos positivos). Se filtra solo por company ID (como IsoForce).

## Escaneo

- **Android:** `SCAN_MODE_LOW_LATENCY`, `CALLBACK_TYPE_ALL_MATCHES`, filtro por
  manufacturer data 0x0100, `reportDelay 0`. Android corta escaneos largos sin avisar,
  así que un vigilante cada 2 s: «señal perdida» a los 4 s sin paquetes y reescaneo a
  los 8 s, nunca más de una vez cada 30 s (límite de 5 arranques / 30 s).
  Permiso: `BLUETOOTH_SCAN` con `neverForLocation` (API 31+), ubicación en API < 31.
- **iOS:** `scanForPeripheralsWithServices(nil, [CBCentralManagerScanOptionAllowDuplicatesKey: true])`.
  Sin «duplicates» iOS agrupa los anuncios y la gráfica se congela. Vigilante de 10 s
  que para y relanza el escaneo (como hang-ten). Ni Crane ni el diff de hang-ten
  muestran la opción, la añadimos nosotros. Crane avisa de que en iOS la frecuencia es
  menor que en Android: **pendiente de medir con la báscula real**.
  Requiere `NSBluetoothAlwaysUsageDescription` (está en `iosApp/project.yml`).

## Báscula simulada

`SimulatedForceSource`: 10 Hz, ciclos descanso → subida → sostenido con temblor y
fatiga → suelta; cada tirón un poco más flojo. Se activa en Perfil (solo debug) y es
la opción por defecto en debug, para poder probar en el emulador.

## Pendiente de verificar con la báscula real
- Que el byte de unidad sea `1` con la báscula en kg.
- Frecuencia real en iPhone con «allow duplicates».
- Si conviene usar el flag «estable» para algo (ahora solo se guarda el pico).

## Ronda 4: señal y diagnóstico
- **Estado de la señal** (`SignalMonitor`/`SignalInfo`): «Conectando…» los primeros 3 s, «Buscando báscula…» solo tras
  3 s sin ningún anuncio válido, «Báscula conectada · N lecturas/s» con señal y «Señal perdida» si pasan 3 s sin
  lecturas.
- **Formatos aceptados** (`CraneAdvertisementParser.parseAny`): con los 2 bytes del company ID delante (`00 01`, iOS)
  y sin ellos (Android). `IosCraneForceSource` no filtra por nombre ni servicio.
- **Perfil → Báscula · diagnóstico Bluetooth**: escanea 15 s sin filtros (Android `AndroidAdvertisementScanner`,
  iOS `IosAdvertisementScanner`, duplicados permitidos) y lista nombre, RSSI, company ID, bytes en hex, anuncios/s;
  marca la WH-C06 (company 0x0100 o nombre WH-C06/Crane/IF_B7) y el peso interpretado. «Copiar informe» lo deja en
  el portapapeles. Probado en emulador solo la pantalla (sin Bluetooth: 0 dispositivos).

## Ronda 5: una sola báscula
- Captura real (iPhone): `IF_B7`, company 0x0100, payload (sin los 2 bytes) `02 03 11 2a c0 19 11 22 7f 01 04 92 01 f4 01 4e db`;
  bytes 10-11 = 0x0492 = 11,70 kg, byte 14 = 0x01 (kg). El decodificador era correcto.
- Causa de los picos de 87/97,9 kg: se aceptaba cualquier anuncio con company 0x0100 (otro aparato cercano).
- `DeviceLock`: solo se escucha UN dispositivo (iOS `peripheral.identifier` / Android MAC). Sin id guardado, tras 1,5 s
  elige el de mayor RSSI que parezca la báscula (nombre IF_B7/WH-C06/Crane o firma 02 03 con payload >= 17 bytes;
  si no hay, el más fuerte con company 0x0100 que decodifique) y lo guarda en ajustes. Diagnóstico: «Usar esta
  báscula»; Perfil: «Olvidar báscula».
- `JumpFilter`: una lectura que salta más de 40 kg en menos de 300 ms se retiene; solo vale si la siguiente la confirma.
- `ScalePipeline` (bloqueo + decodificación + filtro) lo comparten Android, iOS y el simulador. Perfil (debug) →
  «Simulada contaminada» mezcla otro dispositivo (87 kg) y picos de 97,9 kg: no llegan a la gráfica.
- Diagnóstico: «Copiar» por dispositivo, «Copiar todo», «Grabar» 30 s (payloads distintos, veces, peso, saltos).
- La señal («Báscula IF_B7 · -55 dBm · N lecturas/s») sale de `SignalMonitor` en todas las pantallas con báscula.

## Ronda 6: fluidez de las lecturas (causa de nuestro lado)
FREZ lee la misma WH-C06 con fluidez en el mismo iPhone, así que la cadencia es un problema nuestro, no del aparato.
Dato de partida: Medir mostraba 3 lecturas/s y el diagnóstico 6,3 anuncios/s de la misma IF_B7.
Revisado y corregido:
- **Cola del delegado** (iOS): `CBCentralManager` usaba la cola principal; ahora corre en una cola serie propia
  (`com.rehabgrip.ble.scan`) y el callback hace lo mínimo (decodificar y publicar).
- **Vigilante del escaneo** (iOS): `lastPacketAt` empezaba en 0, así que el vigilante de 2 s relanzaba el escaneo hasta
  el primer paquete. Ahora se inicializa al arrancar y no reinicia mientras llegan anuncios.
- **Pérdida por desbordamiento**: el flujo de lecturas tenía 64 huecos y descartaba lo nuevo si el colector iba lento;
  ahora son 256 y se descarta lo más antiguo.
- **Cuenta del diagnóstico**: los «6,3 anuncios/s» incluían anuncios sin manufacturer data (respuestas de escaneo).
  Ahora cada dispositivo muestra «con datos N/s» aparte. Hipótesis a confirmar con la báscula real: iOS entrega 2
  callbacks por ciclo y solo uno lleva el peso.
- **Contadores por etapa** (`ScalePipeline.stats`, visibles en Diagnóstico → Grabar): recibidas, del dispositivo
  bloqueado, decodificadas, retenidas por el filtro de saltos y aceptadas; más «Tipos de trama» (primeros 10 bytes + longitud)
  por si la báscula alterna tipos y el decodificador descarta alguno.
- `SignalMonitor` cuenta TODAS las lecturas recibidas, también las de valor repetido.
- **Latencia visual**: retardo adaptativo = 1× el intervalo mediano entre muestras (60–180 ms) con `EdgeSampler`; la línea
  llega hasta el borde derecho y, si el flujo se corta, mantiene el último valor sin inventar picos. El número grande se
  dibuja dentro de la gráfica con el valor de ese mismo borde y el mismo instante.

Pendiente de medir en el iPhone: con la cola propia y el vigilante corregido, cuántas lecturas/s dice Medir frente a los
contadores del diagnóstico.

## Varias básculas
Perfil → «Mis básculas»: lista de básculas conocidas (se guardan al elegirlas o al autoseleccionarse) con nombre propio
editable, «Usar esta» y «Olvidar». Solo una está activa (la que bloquea `DeviceLock`); su nombre sale en las cabeceras.
