# KmpInspector

[![Maven Central](https://img.shields.io/maven-central/v/io.github.waqas028/kmp-inspector.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.waqas028/kmp-inspector)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20Desktop-brightgreen.svg)

An in-app debugging overlay for [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) — inspect network traffic, database contents, background work, logs, and crashes from inside your running app.

KmpInspector is a floating bubble you wrap your UI with once. Tapping it opens a full-screen inspector with five panels, so you can debug a real device build without a laptop, a proxy, or a cable. It runs on **Android, iOS, and desktop** from a single Compose codebase, and it is deliberately client-agnostic: you feed it data from whatever HTTP client, database, and scheduler you already use, so it is not tied to Ktor, Room, or any specific stack. Drop it into any Compose Multiplatform app — or a Jetpack Compose Android app, or a Compose for Desktop app — enable it in debug builds, and switch it off for release.

## What it shows

| Panel | What you see |
|---|---|
| **Network** | Requests with method, URL, status code, duration, and payload sizes |
| **Database** | Your database info and table contents |
| **Background Work** | Scheduled/queued jobs (Android WorkManager; a quiet tab elsewhere) |
| **Logs** | A 2,000-line ring buffer, filterable by level |
| **Crashes** | Fatal crashes kept across restarts, plus non-fatal exceptions you record |

## Supported platforms

| Platform | Target |
|---|---|
| Android | `minSdk 24`+ |
| iOS | `iosArm64`, `iosSimulatorArm64` |
| Desktop (JVM) | Windows, macOS, Linux |

> **Requirement:** KmpInspector renders on a Compose surface. On Android it injects that surface itself, so XML, Fragment and mixed apps work with no Compose code of your own. On iOS and desktop the UI you wrap must be Compose Multiplatform.

## Installation

Add the dependency to the module that holds your Compose UI. Use the latest version from the badge above.

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.waqas028:kmp-inspector:1.0.0-beta01")
}
```

Using a version catalog? Add it to `gradle/libs.versions.toml`:

```toml
[versions]
kmp-inspector = "1.0.0-beta01"

[libraries]
kmp-inspector = { module = "io.github.waqas028:kmp-inspector", version.ref = "kmp-inspector" }
```

```kotlin
// build.gradle.kts
implementation(libs.kmp.inspector)
```

Make sure `mavenCentral()` is in your repositories (in `settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

## Quick start

### Which setup do I need?

| Your app | Setup | What fills itself | What you feed by hand |
|---|---|---|---|
| **Android** — XML, Fragments, Jetpack Compose, or a mix | `KmpInspector.install(this)` in `Application`, plus one line for OkHttp and one for Room | Bubble on every screen, crashes, logs (logcat), background work (WorkManager), network (OkHttp), database (Room) | Nothing, unless you use another HTTP client or database |
| **Compose Multiplatform** — Android target | Same as Android above. `install` in the Android `Application`; do not also wrap on Android | Same as Android | Nothing |
| **Compose Multiplatform** — iOS and desktop targets | Wrap the root composable in `KmpInspector { }` | The bubble and the inspector UI only | Everything: `Inspector.recordRequest`, `Inspector.setDatabase`, `Inspector.setWork`, `InspectorLog`, `Inspector.installCrashHandler` |

The short rule: on Android the library collects on its own; off Android it draws the UI and you feed it. A Ktor plugin that would give the Network panel to iOS and desktop automatically is the next planned collector.

### Android: one call, nothing to wrap

```kotlin
import com.waqas028.kmpinspector.KmpInspector

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KmpInspector.install(this)
    }
}
```

That single call floats the bubble over **every** Activity (XML, Fragments, Compose, or a mix), captures fatal crashes across restarts, streams the app's own logcat into the Logs panel, and mirrors WorkManager's job list into Background Work when WorkManager is on your classpath. It is a no-op unless the build is `debuggable`, so release builds get nothing.

Two sources cannot be discovered from outside your code and stay one line each:

```kotlin
// Network — add after your header interceptors so the recorded request is what was sent
OkHttpClient.Builder()
    .addInterceptor(KmpInspectorInterceptor())   // com.waqas028.kmpinspector.okhttp

// Database — Room 2.7+. Snapshots now and again each time the inspector is opened
KmpInspector.attach(database, fileName = "app.db")
```

`install` takes a few options: `enabled` (defaults to the manifest's debuggable flag), `appPackagePrefix` for highlighting your stack frames, `captureLogcat`, `captureWorkManager`, and `excludeActivity` for screens that should not get the bubble, such as a splash. `appPackagePrefix` defaults to the application id; if your flavors use an `applicationIdSuffix`, pass your source package (for example `"com.example.shop"`) so the highlighted frames are really yours. Do not also wrap Compose content in `KmpInspector { }` on Android when using `install`, or you will see two bubbles.

OkHttp, Room and WorkManager are `compileOnly` dependencies of the library. You only need them on your classpath if you use that collector.

### Compose Multiplatform: wrap once

Wrap your root composable once. The bubble and the whole inspector come from the library — you build none of that UI yourself.

```kotlin
import com.waqas028.kmpinspector.KmpInspector

@Composable
fun App() {
    MaterialTheme {
        KmpInspector {
            MyAppContent()   // your existing UI
        }
    }
}
```

That's the whole visual integration. A draggable bubble now floats over your app; tapping it opens the inspector.

If you have a **shared Compose Multiplatform module**, wrap your UI here once and every platform gets the overlay for free — then each platform just hosts this `App()` in its native entry point, as shown below.

The wrapper draws the UI; it does not collect anything. On iOS and desktop every panel stays empty until your code reports into it — see [Feeding it data](#feeding-it-data). On the Android target, prefer `KmpInspector.install(this)` in your `Application` instead of wrapping, and you get the collectors listed above for free. If you wrap in shared code *and* call `install` on Android, pass `enabled = !isAndroid` (or similar) to the wrapper so Android does not show two bubbles.

## Platform setup

The examples below host the same `App()` composable on each platform. If your app is single-platform, use the section that applies.

### Android

If you used `KmpInspector.install(this)` above, there is nothing else to do on Android and you can skip this section. The manual route below is for apps that prefer to place the overlay themselves.

Jetpack Compose apps host `App()` in an `Activity`:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KmpInspector {
                MyAppContent()
            }
        }
    }
}
```

**One extra Android step** — give the inspector a `Context` in your `Application`, so crashes survive process death and the crash **Share** button works:

```kotlin
import com.waqas028.kmpinspector.data.initializeInspector

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeInspector(this)
    }
}
```

Without it the library still runs, but the crash buffer is in-memory only and sharing is disabled.

### iOS

Host your Compose UI in a `ComposeUIViewController` on the Kotlin side:

```kotlin
// iosMain
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    KmpInspector {
        MyAppContent()
    }
}
```

Then present it from Swift:

```swift
import SwiftUI
import Shared   // your Kotlin framework

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View { ComposeView() }
}
```

### Desktop (JVM)

Compose for Desktop hosts `App()` in a `Window`:

```kotlin
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "My App") {
        KmpInspector {
            MyAppContent()
        }
    }
}
```

## Feeding it data

On Android, `install` plus the OkHttp interceptor and `attach` cover the common sources. Everything else, and every source on iOS and desktop, goes through the `Inspector` and `InspectorLog` entry points. Call `Inspector.configure(...)` once at startup, then record data wherever it happens in your app.

```kotlin
import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.InspectorLog

// Identify the session in the inspector header (e.g. "com.example.shop · debug")
Inspector.configure(appId = "com.example.shop", variant = "debug")

// Network — call from your HTTP client's interceptor/logging hook
Inspector.recordRequest(
    method = "GET",
    url = "https://api.example.com/products",
    statusCode = 200,
    durationMillis = 142,
)

// Logs — land in the Logs panel (v / d / i / w / e)
InspectorLog.i("CartStore", "Cart restored from disk")
InspectorLog.e("Checkout", "Failed to parse price \"12,900\"")

// A handled exception you want a record of
Inspector.recordNonFatal(
    exceptionType = "JsonDecodingException",
    message = "Unexpected token at index 41",
    origin = "ProductMapper.kt:41",
)

// Database and background work snapshots
Inspector.setDatabase(info, tables)
Inspector.setWork(jobs, engineLabel = "WorkManager 2.11")
```

Because these are plain calls, the inspector works with any HTTP client (Ktor, OkHttp, …), any database (Room, SQLDelight, …), and any scheduler — you decide what to report.

### Capturing crashes

Opt in to catching uncaught exceptions so a fatal crash is still there after the app restarts:

```kotlin
// Frames starting with your package prefix are highlighted as "your" code
Inspector.installCrashHandler(appPackagePrefix = "com.example")
```

The handler delegates to whatever was installed before it, so an existing crash reporter keeps working and the app still crashes normally. On iOS this covers unhandled **Kotlin** exceptions only — Objective-C/Swift exceptions and hard signals (SIGSEGV, SIGABRT) don't reach it.

## Keeping it out of release builds

The overlay is opt-in — it never appears unless you wrap something — but you'll also want it gone in production. Pass your own debug flag:

```kotlin
KmpInspector(enabled = BuildConfig.DEBUG) {
    MyAppContent()
}
```

With `enabled = false` the composable renders your content directly, so nothing from the inspector enters the composition.

## Sample app

The [`sample/`](sample/) directory is a Compose Multiplatform app that consumes KmpInspector and runs it on all three platforms — a working reference for wiring it into a real app.

```bash
./gradlew :sample:shared:run              # desktop
./gradlew :sample:androidApp:installDebug # Android (emulator/device)
open sample/iosApp/iosApp.xcodeproj       # iOS — then Cmd+R
```

## Issues

Found a bug, or something not working as described? Please [open an issue](https://github.com/waqas028/kmp-inspector/issues) with your platform, versions, and steps to reproduce.

## Contributing

Contributions are welcome — anyone can help. Fork the repository, make your change, and open a pull request against [`waqas028/kmp-inspector`](https://github.com/waqas028/kmp-inspector). For anything larger, opening an issue first to discuss the approach is appreciated.

## License

[Apache License 2.0](LICENSE)
