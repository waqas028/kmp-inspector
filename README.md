# KmpInspector

A [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) library published to
[Maven Central](https://central.sonatype.com/) as `com.waqas028:kmp-inspector`.

## Supported targets

| Target  | Source set                      |
|---------|---------------------------------|
| Android | `androidMain`                   |
| iOS     | `iosArm64`, `iosSimulatorArm64` |
| Desktop | `jvmMain`                       |

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.waqas028:kmp-inspector:1.0.0")
}
```

## Usage

Wrap your root composable once. The floating button and the inspector screen come from the library —
you do not build any of that UI yourself:

```kotlin
import com.waqas028.kmpinspector.KmpInspector

setContent {
    KmpInspector {
        MyApp()
    }
}
```

That single wrap gives you a draggable magnifier button floating over your app, which opens the
inspector screen when tapped.

### Keeping it out of release builds

The overlay is opt-in by design — it never appears unless you wrap something — but you also want it
gone in production. Pass your own debug flag:

```kotlin
KmpInspector(enabled = BuildConfig.DEBUG) {
    MyApp()
}
```

When `enabled` is false the composable renders `content()` directly, so nothing of the overlay
enters the composition.

## Sample app

`sample/` holds a Compose Multiplatform app that consumes the library, so you can run the real
thing on each platform before publishing anything.

| Module        | What it is                                                        |
|---------------|-------------------------------------------------------------------|
| `:library`    | The library itself — what gets published                          |
| `:sample:shared` | Shared Compose UI (`App.kt`) + desktop entry point, a KMP library |
| `:sample:androidApp` | Thin Android application module hosting `MainActivity`      |
| `sample/iosApp/` | Xcode project hosting the shared UI via `MainViewController()`  |

The split exists because **AGP 9 no longer allows `com.android.application` and the Kotlin
Multiplatform plugin in the same module**. Shared code lives in a KMP library (`:sample:shared`),
and `:sample:androidApp` is a plain Android app that depends on it. This is the layout Google now recommends.

Run it:

```bash
./gradlew :sample:shared:run          # desktop — fastest loop, no emulator
./gradlew :sample:androidApp:installDebug  # Android, onto a running emulator/device
open sample/iosApp/iosApp.xcodeproj        # iOS — then Cmd+R
```

Two iOS settings in `sample/iosApp/iosApp.xcodeproj` are load-bearing, and both cause runtime or build failures
if dropped:

- `EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64` — the Gradle build has no `iosX64` target, so
  Xcode must not ask for an Intel simulator slice. Remove this only if you add `iosX64()` to both
  `:library` and `:sample:shared` (needed for anyone building on an Intel Mac).
- `CADisableMinimumFrameDurationOnPhone` in `Info.plist` — Compose for iOS **throws on startup**
  without it, to avoid being silently capped at 60Hz on ProMotion devices.

The **Debug KMP Inspector** button calls `generateFibi()`, `firstElement` and `secondElement` from
the library. Because those two are `expect`/`actual` values, each platform prints different numbers —
which is the point: it proves the per-platform `actual` implementations are really being linked in.

## Testing the library in a project before publishing

Three ways, from fastest loop to most faithful. Use the first while building the API, the second
before you publish for real.

### 1. Project dependency — what this repo's sample uses

Both modules are in one Gradle build, so `:sample:shared` just declares:

```kotlin
// sample/shared/build.gradle.kts
commonMain.dependencies {
    api(project(":library"))
}
```

Edit library code, hit run, see the change. No publishing, no version numbers. The sample also acts
as a compile check on your public API — if something is awkward to call, you find out immediately.

**What it does not catch:** anything about packaging. Your POM, your coordinates, your published
metadata, and whether consumers can actually resolve the artifact are all invisible here, because
Gradle wires the modules together directly and never builds a real artifact.

### 2. Maven Local — verify the real artifact

This is the step to do **before** your first Maven Central release.

```bash
./gradlew :library:publishToMavenLocal
```

That writes a real artifact tree to `~/.m2/repository/com/waqas028/kmp-inspector/1.0.0/`. Inspect
what a consumer will actually download:

```bash
ls ~/.m2/repository/com/waqas028/kmp-inspector/1.0.0/
cat ~/.m2/repository/com/waqas028/kmp-inspector/1.0.0/*.pom
```

Then consume it from **any** project — including a throwaway one — by adding `mavenLocal()` first
in the repository list:

```kotlin
// settings.gradle.kts of the consuming project
dependencyResolutionManagement {
    repositories {
        mavenLocal()      // must come first, or Maven Central wins
        google()
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts of the consuming module
implementation("com.waqas028:kmp-inspector:1.0.0")
```

Two gotchas worth knowing:

- **Signing is skipped locally only because the build makes it conditional.** `signAllPublications()`
  runs only when a `signingInMemoryKey` property is present (CI supplies it). Called
  unconditionally, `publishToMavenLocal` fails with *"no configured signatory"* and cannot be
  worked around by excluding the sign tasks — the publications still reference the `.asc` files.
  Either way, a successful local publish does *not* prove the signed Central publish will succeed.
- Because the version is a fixed `1.0.0`, Gradle caches it. After republishing, the consumer may
  keep the stale copy — use a `1.0.0-SNAPSHOT` version while iterating, or run the consumer build
  with `--refresh-dependencies`.

### 3. Composite build — a separate project, still building from source

When you want to test against a real app that lives in its own repo, without publishing at all:

```kotlin
// settings.gradle.kts of the consuming project
includeBuild("../KmpInspector")
```

```kotlin
// build.gradle.kts of the consuming module — normal coordinates, no version
implementation("com.waqas028:kmp-inspector")
```

Gradle substitutes the dependency with the local build automatically, matching on the `group` and
artifact name. You get the separate-repo layout of a real consumer with the instant feedback of a
project dependency. This is the best option for testing against an existing app of yours.

## Building

```bash
./gradlew build
```

Per-target tests: `jvmTest`, `iosSimulatorArm64Test`, `testAndroidHostTest`. Note that
`iosSimulatorArm64Test` only runs on macOS — the GitHub Actions workflow in
[.github/workflows/gradle.yml](.github/workflows/gradle.yml) fans these out across runners.

## Publishing

Releases publish to Maven Central via [.github/workflows/publish.yml](.github/workflows/publish.yml),
which triggers on a published GitHub release. It requires these repository secrets:

| Secret                   | Purpose                              |
|--------------------------|--------------------------------------|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token name       |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password   |
| `SIGNING_KEY_ID`         | GPG key ID                           |
| `SIGNING_PASSWORD`       | GPG key passphrase                   |
| `GPG_KEY_CONTENTS`       | ASCII-armored GPG private key        |

Publish locally with `./gradlew publishToMavenLocal`.

## License

[Apache License 2.0](LICENSE)

## Other resources

* [Publishing KMP libraries](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html)
* [Publishing via the Central Portal](https://central.sonatype.org/publish-ea/publish-ea-guide/)
* [Gradle Maven Publish Plugin — Publishing to Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
