# KmpInspector

A [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) library published to
[Maven Central](https://central.sonatype.com/) as `com.waqas028:kmp-inspector`.

## Supported targets

| Target      | Source set                     |
|-------------|--------------------------------|
| Android     | `androidMain`                  |
| iOS         | `iosArm64`, `iosSimulatorArm64` |
| JVM         | `jvmMain`                      |
| Linux x64   | `linuxX64Main`                 |

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.waqas028:kmp-inspector:1.0.0")
}
```

## Usage

The library currently ships the template's sample API — a Fibonacci sequence seeded with
platform-provided numbers — under the `com.waqas028.kmpinspector` package. Replace it with the real
API.

```kotlin
import com.waqas028.kmpinspector.generateFibi

val firstThree = generateFibi().take(3).toList()
```

## Building

```bash
./gradlew build
```

Per-target tests: `jvmTest`, `iosSimulatorArm64Test`, `linuxX64Test`, `testAndroidHostTest`.
Note that `linuxX64Test` only runs on a Linux host, and `iosSimulatorArm64Test` only on macOS —
the GitHub Actions workflow in [.github/workflows/gradle.yml](.github/workflows/gradle.yml) fans
these out across runners.

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
