# Android Studio Plugins & Libraries Reference

## Android Studio Plugins (Full List)

### Productivity
| Plugin | Rating | What it does |
|---|---|---|
| **ADB Idea** | ⭐⭐⭐⭐⭐ | Clear data, restart, uninstall, kill — from IDE menu |
| **Kotlin Fill Class** | ⭐⭐⭐⭐ | Auto-fills all constructor/function parameters |
| **Rainbow Brackets** | ⭐⭐⭐⭐ | Color-codes bracket pairs for readability |
| **String Manipulation** | ⭐⭐⭐⭐ | Case conversion, encoding, sorting of strings |
| **Key Promoter X** | ⭐⭐⭐ | Teaches keyboard shortcuts as you work |
| **Presentation Assistant** | ⭐⭐⭐ | Shows keystrokes on screen (great for demos) |

### Code Generation & Quality
| Plugin | Rating | What it does |
|---|---|---|
| **JSON to Kotlin Class** | ⭐⭐⭐⭐⭐ | Paste JSON → get data class (supports Gson, Moshi, kotlinx) |
| **Detekt** | ⭐⭐⭐⭐⭐ | Kotlin static analysis, enforces code style |
| **SonarLint** | ⭐⭐⭐⭐ | Security & quality linting, connects to SonarQube |
| **Ktlint** | ⭐⭐⭐⭐ | Kotlin formatter (integrates with pre-commit hooks) |
| **Android Parcelable Code Generator** | ⭐⭐⭐ | Generates Parcelable boilerplate (less needed with Kotlin Parcelize) |

### UI & Design
| Plugin | Rating | What it does |
|---|---|---|
| **Material Design Icon Generator** | ⭐⭐⭐⭐ | Browse and insert MD icons as vector drawables |
| **Android Drawable Preview** | ⭐⭐⭐⭐ | Previews drawable XML files in the gutter |
| **LayoutInspector** (built-in) | ⭐⭐⭐⭐⭐ | Live view of Compose/View hierarchy at runtime |
| **Compose Preview** (built-in) | ⭐⭐⭐⭐⭐ | Interactive Compose previews in IDE |

### Version Control & Collaboration
| Plugin | Rating | What it does |
|---|---|---|
| **GitLive** | ⭐⭐⭐⭐ | See teammates' changes in real-time, merge conflict hints |
| **Git Toolbox** | ⭐⭐⭐⭐ | Inline blame, branch status in status bar |
| **GitHub Copilot** | ⭐⭐⭐⭐ | AI code completion (subscription required) |

### Testing & Debugging
| Plugin | Rating | What it does |
|---|---|---|
| **Kotest** | ⭐⭐⭐⭐ | Kotest framework run configuration & gutter icons |
| **Hyperion** (library) | ⭐⭐⭐ | In-app debug menu (attributes inspector, network log) |
| **Chuck** (library) | ⭐⭐⭐⭐ | OkHttp interceptor showing requests in notification |

### Database
| Plugin | Rating | What it does |
|---|---|---|
| **Database Inspector** (built-in) | ⭐⭐⭐⭐⭐ | Browse/query Room/SQLite databases live on device |
| **DB Browser for SQLite** (external) | ⭐⭐⭐⭐ | Desktop tool for inspecting pulled .db files |

---

## Gradle Plugins

```kotlin
// build.gradle.kts (project level)
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.22" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.6" apply false
}
```

### Detekt Setup
```kotlin
// app/build.gradle.kts
detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/detekt-config.yml")
}
```

### Ktlint Setup
```kotlin
plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}
ktlint {
    android.set(true)
    outputToConsole.set(true)
}
```

---

## Library Ecosystem Map

### Networking
```
Retrofit 2 → HTTP client + REST
  + OkHttp 4 → underlying HTTP engine + interceptors
  + kotlinx.serialization / Gson / Moshi → JSON parsing
```

### Dependency Injection
```
Hilt (recommended) → built on Dagger, Jetpack-integrated
  Alternatives: Koin (simpler, runtime), manual DI (small apps)
```

### Async
```
Kotlin Coroutines + Flow (recommended)
  viewModelScope, lifecycleScope, Dispatchers.IO/Main/Default
  Alternatives: RxJava 3 (legacy), AsyncTask (deprecated)
```

### Image Loading
```
Coil 2 (recommended for Compose) → Kotlin-first, coroutine-based
Glide 4 → mature, View-based, large community
Picasso → simple but older
```

### Local Storage
```
Room (SQLite ORM) -> structured data, complex queries
DataStore (Preferences) -> simple key-value (replaces SharedPreferences)
  Proto DataStore -> typed, schema-enforced
```

### Navigation
```
Navigation Component -> single Activity, Fragment or Compose
  navigation-compose (recommended) -> type-safe routes with Kotlin DSL
```

### Firebase (Google)
```
Analytics, Crashlytics, Remote Config, Cloud Messaging (FCM)
  -> add google-services.json + classpath plugin
```

---

## Version Catalog (libs.versions.toml)

Recommended for multi-module projects:

```toml
[versions]
compose-bom = "2024.09.00"
hilt = "2.52"
room = "2.6.1"
retrofit = "2.11.0"
coil = "2.7.0"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

[plugins]
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.0-1.0.22" }
```
