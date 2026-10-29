# Fixora Android

App móvil de Fixora (Kotlin + Jetpack Compose). La documentación del proyecto y del flujo de trabajo vive en el sitio de documentación del equipo.

## Entornos

Las variables de cada entorno están en `app/env/<flavor>.properties` (`dev` y `prod`) y se leen en código con `BuildConfig`. Para cambiar un valor solo en tu máquina, agrégalo a `local.properties` con el prefijo del flavor:

```properties
dev.API_URL=http://192.168.1.50:3001/api/
```

En el emulador la app usa `http://10.0.2.2:3001/api/`, que es la API corriendo en tu PC (`pnpm dev` en Api-Fixora). En un celular físico conectado a la misma red Wi-Fi, usa la IP de tu PC como en el ejemplo.

## Commits

Los commits siguen Conventional Commits con el ID de la tarea de Plane:

```txt
<tipo>(<ámbito>): <descripción> (ANDROID-XX)
```

El hook `.githooks/commit-msg` rechaza los mensajes que no cumplen el formato. Se activa solo al sincronizar Gradle en Android Studio; para activarlo a mano:

```bash
git config core.hooksPath .githooks
```

Cada pull request pasa además por el workflow `Conventional Commits`, que revisa el nombre de la rama, el título del PR y los commits.
