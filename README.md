# KmpInspector lib (Beta)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.waqas028/kmp-inspector.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.waqas028/kmp-inspector)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS%20%7C%20Desktop-brightgreen.svg)

<p align="center">
  <a href="https://buymeacoffee.com/waqas028">
    <img src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=&slug=waqas028&button_colour=FFDD00&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=ffffff" alt="Buy me a coffee" height="45" />
  </a>
</p>

An in-app debugging overlay for [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) — inspect network traffic, database contents, background work, logs, and crashes from inside your running app.

KmpInspector is a floating bubble you wrap your UI with once. Tapping it opens a full-screen inspector with five panels, so you can debug a real device build without a laptop, a proxy, or a cable. It runs on **Android, iOS, and desktop** from a single Compose codebase, and it is deliberately client-agnostic: you feed it data from whatever HTTP client, database, and scheduler you already use, so it is not tied to Ktor, Room, or any specific stack. Drop it into any Compose Multiplatform app — or a Jetpack Compose Android app, or a Compose for Desktop app — enable it in debug builds, and switch it off for release.

## What it shows

| Panel               | What you see                                                             |
|---------------------|--------------------------------------------------------------------------|
| **Network**         | Requests with method, URL, status code, duration, and payload sizes      |
| **Database**        | Your database info and table contents                                    |
| **Background Work** | Scheduled/queued jobs (Android WorkManager; a quiet tab elsewhere)       |
| **Logs**            | A 2,000-line ring buffer, filterable by level                            |
| **Crashes**         | Fatal crashes kept across restarts, plus non-fatal exceptions you record |

## Supported platforms

| Platform      | Target                          |
|---------------|---------------------------------|
| Android       | `minSdk 24`+                    |
| iOS           | `iosArm64`, `iosSimulatorArm64` |
| Desktop (JVM) | Windows, macOS, Linux           |

> **Requirement:** KmpInspector renders on a Compose surface. On Android it injects that surface itself, so XML, Fragment and mixed apps work with no Compose code of your own. On iOS and desktop the UI you wrap must be Compose Multiplatform.

## Demo

# Android
https://github.com/user-attachments/assets/9df853f3-7bc6-4c90-88be-fce9e669fe3e

# IOS
https://github.com/user-attachments/assets/5f1a4c32-9a42-46b5-93bd-c2992d7e235d

# Desktop
https://github.com/user-attachments/assets/2402e153-4ebc-4777-b207-e82a02f13939



## Which setup do I need?

Pick the guide that matches your project. Each one is complete on its own.

| Your project                                                                                 | Guide                                                                                                                                       |
|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| **Native Android**: XML, Fragments, Jetpack Compose, or a mix                                | [Setup for a native Android project](#setup-for-a-native-android-project)                                                                   |
| **Compose Multiplatform (CMP)**: one Compose UI shared by Android, iOS and desktop           | [Setup for a Compose Multiplatform project](#setup-for-a-compose-multiplatform-project)                                                     |
| **Kotlin Multiplatform (KMP) with native screens**: shared logic, SwiftUI and XML/Compose UI | Your Android app follows the [native Android guide](#setup-for-a-native-android-project). iOS is not covered: the inspector draws with Compose, and a SwiftUI app has no Compose surface to draw on |

The short rule: on native Android the library collects on its own after one call. In a Compose Multiplatform app you wrap your UI once, and two companion artifacts fill the panels on every platform. KMP alone shares code; CMP also shares the UI, and the inspector is UI, so it needs CMP (or Android) to show up.

Both guides use the latest version from the badge above and need `mavenCentral()` in your repositories (in `settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

## Setup for a native Android project

For an app with XML layouts, Fragments, Jetpack Compose, or a mix. You do not wrap any screen, and you do not need Compose in your own code.

### Step 1: Add the dependency

Use the real artifact in debug builds and the `no-op` twin in release. The no-op has the same classes and signatures with empty bodies, so every call site compiles and your release APK carries no inspector code, fonts or collectors.

```kotlin
// app/build.gradle.kts
dependencies {
    debugImplementation("io.github.waqas028:kmp-inspector:1.0.1")
    releaseImplementation("io.github.waqas028:kmp-inspector-no-op:1.0.1")
}
```

Using a version catalog? Add both to `gradle/libs.versions.toml`:

```toml
[versions]
kmp-inspector = "1.0.1"

[libraries]
kmp-inspector = { module = "io.github.waqas028:kmp-inspector", version.ref = "kmp-inspector" }
kmp-inspector-noop = { module = "io.github.waqas028:kmp-inspector-no-op", version.ref = "kmp-inspector" }
```

```kotlin
// app/build.gradle.kts
debugImplementation(libs.kmp.inspector)
releaseImplementation(libs.kmp.inspector.noop)
```

### Step 2: Install it in your `Application` class

```kotlin
import android.app.Application
import com.waqas028.kmpinspector.KmpInspector

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KmpInspector.install(this)
    }
}
```

If your app had no `Application` class before, register the new one in `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApplication"
    ... >
```

That single call floats the bubble over your screens (XML, Fragments, Compose, or a mix), captures fatal crashes across restarts, streams the app's own logcat into the Logs panel, and mirrors WorkManager's job list into Background Work when WorkManager is on your classpath. It is a no-op unless the build is `debuggable`, so release builds get nothing.

### Step 3: Network — add the OkHttp interceptor

```kotlin
import com.waqas028.kmpinspector.okhttp.KmpInspectorInterceptor

val client = OkHttpClient.Builder()
    // your header interceptors first, so the recorded request is what was sent
    .addInterceptor(KmpInspectorInterceptor())
    .build()
```

Retrofit runs on OkHttp, so this covers Retrofit too: pass the client to `Retrofit.Builder().client(client)`.

### Step 4: Database — attach your Room database

```kotlin
// Room 2.7+. Snapshots now and again each time the inspector is opened
KmpInspector.attach(database, fileName = "app.db")
```

### Step 5: Run a debug build

A draggable bubble now floats over your app. Tap it to open the inspector.

Steps 3 and 4 are optional: skip one and only that panel stays empty. OkHttp, Room and WorkManager are `compileOnly` dependencies of the library, so you only need them on your classpath if you use that collector. For another HTTP client or database, see [Feeding it data](#feeding-it-data).

### Options

`install` takes a few options: `enabled` (defaults to the manifest's debuggable flag), `appPackagePrefix` for highlighting your stack frames, `captureLogcat`, `captureWorkManager`, and `excludeActivity` for screens that should not get the bubble, such as a splash. `appPackagePrefix` defaults to the application id; if your flavors use an `applicationIdSuffix`, pass your source package (for example `"com.example.shop"`) so the highlighted frames are really yours. Do not also wrap Compose content in `KmpInspector { }` on Android when using `install`, or you will see two bubbles.

### Which screens get the bubble

- **Shown** on every Activity built on AndroidX `ComponentActivity`, which includes `AppCompatActivity`, `FragmentActivity` and Compose activities.
- **Skipped, without a crash,** on screens that cannot host it: a plain `android.app.Activity`, and any Activity that never sets a content view. The usual example is a library's hand-off screen, such as Firebase Auth opening reCAPTCHA in the browser.
- **Skipped** on anything `excludeActivity` returns `true` for.

Screens from other libraries that do show content get the bubble too. To keep it on your own screens only, exclude everything outside your package:

```kotlin
KmpInspector.install(
    this,
    excludeActivity = { !it.javaClass.name.startsWith("com.example.shop") },
)
```

## Setup for a Compose Multiplatform project

For a Compose Multiplatform (CMP) app with a shared Compose module: `composeApp` if you started from the Kotlin Multiplatform wizard, `shared` in older templates. You wrap your UI once in that module, and Android, iOS and desktop all get the bubble.

### Step 1: Add the dependencies to your shared module

```kotlin
// composeApp/build.gradle.kts (or shared/build.gradle.kts)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.waqas028:kmp-inspector:1.0.1")

            // Optional companions, always the same version as the line above
            implementation("io.github.waqas028:kmp-inspector-ktor:1.0.0")   // Network panel from Ktor
            implementation("io.github.waqas028:kmp-inspector-room:1.0.0")   // Database panel from Room
        }
    }
}
```

With the wizard's `composeApp` layout, the Android `Application` and `MainActivity` sit in the same module (`androidMain`), so this is all the dependency setup. If your Android app is a separate module that calls the inspector itself (Step 4 does), declare the first line as `api(...)` so that module can see it.

A shared module has no debug/release split of its own, so the real artifact goes in with `implementation`. You switch it off at runtime and swap in the no-op at build time; both are covered in [Keeping it out of release builds](#keeping-it-out-of-release-builds). The artifact ships the R8 rules it needs.

### Step 2: Wrap your root composable

```kotlin
// commonMain
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

That is the whole visual integration. The bubble and the inspector come from the library; you build none of that UI yourself.

### Step 3: Feed the panels

The wrapper draws the UI. It does not collect anything, so every panel stays empty until your code reports into it. Do this once at startup, in shared code:

```kotlin
// commonMain
import com.waqas028.kmpinspector.Inspector
import com.waqas028.kmpinspector.ktor.KmpInspectorPlugin
import com.waqas028.kmpinspector.room.RoomInspector

// Header label, shown as "com.example.shop · debug"
Inspector.configure(appId = "com.example.shop", variant = "debug")

// Crashes: keep fatal crashes across restarts
Inspector.installCrashHandler(appPackagePrefix = "com.example.shop")

// Network: every Ktor call, on every platform
val client = HttpClient {
    install(KmpInspectorPlugin)
}

// Database: your Room database, on every platform
RoomInspector.attach(database, fileName = "app.db")
```

Logs come from your own `InspectorLog.i("Tag", "message")` calls. Not on Ktor or Room? Report through the plain `Inspector` calls in [Feeding it data](#feeding-it-data).

### Step 4: Host `App()` on each platform

#### Android

Show `App()` from an Activity, and give the inspector a `Context` in your `Application`, so crashes survive process death and the crash **Share** button works:

```kotlin
import com.waqas028.kmpinspector.data.initializeInspector

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeInspector(this)
    }
}
```

Register `MyApplication` in `AndroidManifest.xml` with `android:name=".MyApplication"`. Without `initializeInspector` the library still runs, but the crash buffer is in-memory only and sharing is disabled.

#### iOS

Host your Compose UI in a `ComposeUIViewController` on the Kotlin side:

```kotlin
// iosMain
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController { App() }
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

#### Desktop (JVM)

Compose for Desktop hosts `App()` in a `Window`:

```kotlin
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "My App") {
        App()
    }
}
```

### Optional: the Android collectors in a Compose Multiplatform app

The Android target can also use everything from the [native Android guide](#setup-for-a-native-android-project): logcat in the Logs panel, WorkManager in Background Work, and the OkHttp interceptor. Call `KmpInspector.install(this)` in your `Application` instead of `initializeInspector(this)`.

`install` draws its own bubble on every Activity, so switch the wrapper off on Android or you will see two bubbles:

```kotlin
// commonMain
@Composable
fun App(showInspector: Boolean = true) {
    MaterialTheme {
        KmpInspector(enabled = showInspector) {
            MyAppContent()
        }
    }
}

// Android Activity: install() already draws the bubble here
setContent { App(showInspector = false) }
```

## Using the inspector

### Panel actions

Every list has a sort toggle on its status line and a floating "Top" pill once you scroll. Network and Logs have **Clear**. Logs has **Share**, which sends the currently filtered lines as text. A table in Database has **CSV**, which shares the visible rows. The SQL editor runs real `SELECT` and `WITH` statements when a live database is attached, and falls back to showing the snapshot of the table named in the `FROM` clause otherwise.

### Sharing safely

Share as cURL or Text masks `Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization` and `X-Api-Key` values, since shared text tends to end up in chats. The Headers tab and the clipboard copy stay verbatim. Change the list with `Inspector.redactHeaders(setOf(...))`, or pass an empty set to share everything.

## Feeding it data

On Android, `install` plus the OkHttp interceptor and `attach` cover the common sources. On every platform, the two companion artifacts cover Ktor and Room:

```kotlin
// Network, any platform: com.waqas028.kmpinspector.ktor
HttpClient {
    install(KmpInspectorPlugin)
}

// Database, any platform: com.waqas028.kmpinspector.room
RoomInspector.attach(database, fileName = "app.db")
```

The Ktor plugin records method, URL, status, timing, headers and text bodies, and keeps binary responses untouched apart from their size. `RoomInspector.attach` reads tables now and on every open or Refresh, writes cell edits back, and runs the SQL editor's queries against the live database. On Android it is the same collector as `KmpInspector.attach`.

Everything else goes through the `Inspector` and `InspectorLog` entry points. Call `Inspector.configure(...)` once at startup, then record data wherever it happens in your app.

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

The inspector can read your database and run `SELECT` against it, so it should not ship enabled. There are two separate questions here, and they have different answers: whether the inspector *runs*, and whether its code is *in the binary at all*.

### Stopping it running: one switch, every platform

```kotlin
Inspector.enabled = isDebugBuild   // once, at startup, before anything else
```

With this off, every entry point returns immediately. Nothing is captured, no crash handler is installed, `attach` reads no tables and keeps no handle to your database, the Ktor plugin does not buffer response bodies, and the `KmpInspector { }` composable renders your content directly. `KmpInspector.install(application, enabled = false)` sets the same switch on Android.

This is a runtime guarantee, not a packaging one. The classes are still in the binary.

### Removing the code: depends on the target

| Target | Does unused code get removed? | What to do |
|---|---|---|
| Android | Yes, R8 | Use the `debugImplementation` / `releaseImplementation` split from [the native Android setup](#setup-for-a-native-android-project). Release builds link the no-op and contain nothing from the inspector. |
| iOS | Yes, Kotlin/Native dead-code elimination — **unless** the framework exports the library | Do not `export(...)` the inspector from your framework, and do not switch on `transitiveExport`. Exporting keeps its whole public API alive by definition. Then swap in the no-op for release builds as well. |
| Desktop (JVM) | No | Nothing is stripped, ever. The only way to keep the code out is to depend on the no-op artifact in the distribution you ship. |

So on iOS and desktop the `enabled` flag alone does **not** remove anything, and on iOS the export setting matters more than the flag does.

### Swapping the artifact in a shared module

A Kotlin Multiplatform module has no `debugImplementation`, but the choice can still be made at build time. Xcode passes its build configuration in the `CONFIGURATION` environment variable, and a Gradle property covers desktop:

```kotlin
// shared/build.gradle.kts
val inspectorEnabled = System.getenv("CONFIGURATION") != "Release" &&
    providers.gradleProperty("inspectorEnabled").getOrElse("true").toBoolean()

sourceSets {
    commonMain.dependencies {
        if (inspectorEnabled) {
            implementation("io.github.waqas028:kmp-inspector:1.0.1")
        } else {
            implementation("io.github.waqas028:kmp-inspector-no-op:1.0.1")
        }
    }
}
```

Release archives from Xcode then link the no-op, and a desktop release passes `-PinspectorEnabled=false`.

One catch: `kmp-inspector-ktor` and `kmp-inspector-room` depend on the real library, so they have no no-op twin and must sit behind the same condition. Guard those two dependencies with the same flag and keep their call sites in a source set that is only compiled when the inspector is on.

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

Contributions are welcome! Please follow these steps:

- Fork the repository.
- Create a new branch (git checkout -b feature-branch).
- Commit your changes (git commit -m 'Add some feature').
- Push to the branch (git push origin feature-branch).
- Open a pull request.

## 👤 Developed By

<table>
<tr>
<td width="130" valign="top">
<a href="https://x.com/waqas028?s=08" target="_blank">
  <img src="https://github.com/user-attachments/assets/99199ad1-a69d-4ceb-8599-e495a1ed937b" width="110" style="border-radius:12px;">
</a>
</td>
<td valign="middle">

**Muhammad Waqas**  

[![Email](https://img.shields.io/badge/Email-waqaswaseem679@gmail.com-D14836?style=flat&logo=gmail&logoColor=white)](mailto:waqaswaseem679@gmail.com)  
[![LinkedIn](https://img.shields.io/badge/LinkedIn-Profile-0A66C2?style=flat&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/muhammad-waqas-4399361a3)  
[![GitHub](https://img.shields.io/badge/GitHub-waqas028-181717?style=flat&logo=github&logoColor=white)](https://github.com/waqas028)  
[![Twitter](https://img.shields.io/badge/Twitter-@waqas028-000000?style=flat&logo=x&logoColor=white)](https://x.com/waqas028?s=08)  
[![Skype](https://img.shields.io/badge/Skype-live%3Awaqasyaqeen420-00AFF0?style=flat&logo=skype&logoColor=white)](https://join.skype.com/invite/p4ckdyAOrsCs)  
[![WhatsApp](https://img.shields.io/badge/WhatsApp-Chat-25D366?style=flat&logo=whatsapp&logoColor=white)](https://wa.me/+923045593294)  
[![Google Developer](https://img.shields.io/badge/Google%20Developer-Profile-4285F4?style=flat&logo=google&logoColor=white)](https://g.dev/MuhammadWaqasDev)  

</td>
</tr>
</table>

## License

[Apache License 2.0](LICENSE)
