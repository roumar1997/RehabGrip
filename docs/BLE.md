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
