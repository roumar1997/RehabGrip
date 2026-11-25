# Rehagrip: subir a TestFlight desde su propio repo

Ahora la compilación de iPhone se lanza desde el repo de **Cumbre**
(`.github/workflows/rehabgrip-testflight.yml`), porque es el que tiene guardadas las claves de Apple.
Esta guía sirve para que Rehagrip tenga **sus propias claves** y no dependa de Cumbre.

Las claves las metes **tú** en GitHub. GitHub no deja leerlas una vez guardadas, así que nadie puede copiarlas de un repo a otro.

---

## 1. Qué ficheros hay en tu equipo

| Fichero | Dónde está | Para qué |
|---|---|---|
| `distribution.p12` | `C:\Users\rouma\distribution.p12` | Certificado para firmar la app |
| `distribution_new.p12` | `C:\Users\rouma\distribution_new.p12` | Otra copia del certificado (puede ser la vigente) |
| `AuthKey_39CT43AKCN.p8` | `C:\Users\rouma\Downloads\` | Clave de App Store Connect (id `39CT43AKCN`) |
| `AuthKey_8RXH43AWKR.p8` | `C:\Users\rouma\Downloads\` | Otra clave de App Store Connect (id `8RXH43AWKR`) |

Los cuatro son del 30/06/2026. Antes de empezar hay que saber **cuál está en uso**:

- **Clave .p8:** entra en App Store Connect → **Usuarios y acceso** → **Integraciones** → **App Store Connect API**.
  Mira qué **ID de clave** sale como activo (`39CT43AKCN` u `8RXH43AWKR`) y usa ese fichero.
  En esa misma página, arriba, está el **ID del emisor (Issuer ID)**, un texto largo con guiones. Cópialo, lo necesitarás.
- **Certificado .p12:** usa el que tenga la contraseña que recuerdes. Si los dos la tienen, usa `distribution_new.p12`.
  Si al compilar falla la firma, prueba con el otro.
  También tienes `C:\Users\rouma\Documents\Certificado digital\Certificado-FNMT-...p12`.
  **Ese no:** es tu certificado digital personal de la FNMT, no tiene nada que ver con Apple. No lo subas nunca a ningún sitio.

---

## 2. Pasar los ficheros a texto (base64)

GitHub guarda texto, así que los ficheros hay que convertirlos.
Abre **PowerShell** y pega esto. Copia el certificado en el portapapeles:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\rouma\distribution_new.p12")) | Set-Clipboard
```

Ahora el portapapeles tiene el certificado en texto. Pégalo en el paso 3 (secreto 1).
Después, para la clave (cambia el nombre si la activa es la otra):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\rouma\Downloads\AuthKey_39CT43AKCN.p8")) | Set-Clipboard
```

---

## 3. Meter las 5 claves en GitHub

1. Ve a **https://github.com/roumar1997/RehabGrip**.
2. Pulsa **Settings**, arriba a la derecha.
3. En el menú de la izquierda: **Secrets and variables** → **Actions**.
4. Pulsa **New repository secret** para cada una de estas 5. El nombre tiene que ser **exacto**:

| Nombre (Name) | Valor (Secret) |
|---|---|
| `DISTRIBUTION_CERTIFICATE_P12` | Lo que copiaste del .p12 en el paso 2 |
| `DISTRIBUTION_CERTIFICATE_PASSWORD` | La contraseña del .p12 |
| `APP_STORE_CONNECT_API_KEY` | Lo que copiaste del .p8 en el paso 2 |
| `APP_STORE_CONNECT_KEY_ID` | El ID de la clave, por ejemplo `39CT43AKCN` |
| `APP_STORE_CONNECT_ISSUER_ID` | El ID del emisor de App Store Connect |

5. Pulsa **Add secret** en cada uno. Al terminar deben salir los 5 en la lista.

Cuando acabes, **vacía el portapapeles**: copia cualquier otra cosa.

---

## 4. Lo que hago yo después

1. Muevo el workflow a este repo (`.github/workflows/testflight.yml`) y quito los pasos que descargan Cumbre.
   El script que crea el perfil (`asc_make_profile.js`) se copia aquí.
2. Lo lanzo con `gh workflow run testflight.yml -f build_number=N`.
3. Si sube bien, borro `rehabgrip-testflight.yml` del repo de Cumbre. Esto necesita tu «sí, fusiona».

Para cada versión nueva hay que subir el número de build (`build_number`). Apple no acepta dos builds con el mismo número.
