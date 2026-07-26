# KLog

KLog es una librería Android nativa y liviana para capturar logs, errores de red
y crashes no controlados en reportes Markdown o texto. Los archivos se guardan
en el almacenamiento privado de la aplicación:

```text
context.filesDir/klog_reports
```

## Módulos

- `klog-core`: captura, formato, persistencia, retención y auto-inicialización.
- `klog-ui-compose`: módulo reservado para el visor y el flujo de compartir con
  Jetpack Compose.

## Instalación local

Incluye los módulos en `settings.gradle.kts`:

```kotlin
include(":klog-core")
include(":klog-ui-compose")
```

Agrega el núcleo a tu aplicación:

```kotlin
dependencies {
    implementation(project(":klog-core"))
}
```

No es necesario modificar la clase `Application`. El manifest de la librería
registra un `ContentProvider` privado con `initOrder="100"` y KLog queda
inicializado antes de crear la primera `Activity`.

## Uso básico

```kotlin
import dev.rafaflow.klog.core.KLog

KLog.i("Checkout", "Checkout started")
KLog.w("Checkout", "Address is incomplete")

try {
    submitOrder()
} catch (error: Exception) {
    KLog.e("Checkout", "Could not submit order", error)
}
```

Los crashes no controlados se capturan por defecto. KLog escribe el reporte de
forma síncrona y después delega en el `UncaughtExceptionHandler` que Android o la
aplicación ya tenían instalado.

## Configuración en runtime

```kotlin
import dev.rafaflow.klog.core.KLog
import dev.rafaflow.klog.core.KLogConfig
import dev.rafaflow.klog.core.LogFormat

KLog.configure(
    KLogConfig(
        maxLogAgeDays = 14,
        maxFolderSizeMb = 25,
        logFormat = LogFormat.TEXT,
        captureUncaughtCrashes = true,
        customDeviceMetadata = mapOf(
            "Environment" to "staging",
            "Build channel" to "internal",
        ),
    ),
)
```

La nueva configuración se aplica a las entradas futuras. Al cambiar el formato
o los metadatos se abre un reporte nuevo; los reportes existentes conservan su
contenido original. Los límites inválidos de retención se normalizan a un mínimo
seguro de un día y un MiB.

## OkHttp opcional

`klog-core` declara OkHttp como `compileOnly`, por lo que no lo añade a una app
que no lo use. Si tu app ya depende de OkHttp, registra el interceptor:

```kotlin
import dev.rafaflow.klog.core.KLogNetworkInterceptor
import okhttp3.OkHttpClient

val client = OkHttpClient.Builder()
    .addInterceptor(KLogNetworkInterceptor())
    .build()
```

El interceptor registra respuestas 4xx/5xx y excepciones de red sin leer el
body. También elimina credenciales, query parameters y fragmentos de la URL
antes de persistirla.

## Administrar reportes

```kotlin
val newestFirst = KLog.getLogFiles()

// Elimina todos los .md y .txt administrados por KLog.
KLog.clearLogs()
```

No guardes secretos, tokens ni datos personales en mensajes o metadatos
personalizados. Aunque los reportes están en almacenamiento privado, pueden
compartirse explícitamente desde la aplicación.
