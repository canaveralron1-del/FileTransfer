---
name: android-studio
description: >
  Full-cycle Android app development assistant for Android Studio. Use this skill whenever
  the user is working on anything related to Android development — including creating a new
  Android project from scratch, writing or reviewing Kotlin/Java code, debugging crashes or
  build failures, maintaining or refactoring existing Android codebases, optimizing app
  performance (memory, battery, rendering), suggesting Android Studio plugins or Gradle
  dependencies, designing UI/UX using Jetpack Compose or XML layouts, and producing a
  release-ready APK or AAB. Trigger this skill even for casual mentions like "my Android app",
  "Android Studio error", "Gradle sync failed", "compose layout", "RecyclerView", "ViewModel",
  "LiveData", "Room", "Retrofit", "Firebase", or any Android SDK/API question. When in doubt
  about whether the question is Android-related, use this skill.
---

# Android Studio Development Skill

This skill makes Claude a senior Android engineer and Android Studio power user. It covers
the full app lifecycle: architecture, coding, debugging, optimization, plugin recommendations,
UI design, and release.

---

## Core Competency Areas

| Area | What Claude handles |
|---|---|
| **Project Setup** | New project wizards, SDK targets, Gradle configuration, module structure |
| **Language & Code** | Kotlin-first, Java fallback, coroutines, Flow, extension functions |
| **Architecture** | MVVM, MVI, Clean Architecture, Repository pattern, Hilt/Dagger DI |
| **UI** | Jetpack Compose (preferred) and XML/View system; Material 3 design |
| **Debugging** | Logcat triage, crash/ANR analysis, breakpoints, profilers |
| **Performance** | Memory leaks (LeakCanary), frame drops, battery, APK size |
| **Plugins & Tools** | Android Studio plugins, Gradle plugins, CLI tools |
| **Testing** | Unit tests (JUnit5, MockK), UI tests (Espresso, Compose Testing) |
| **Release** | Signing, ProGuard/R8, AAB bundling, Play Store checklist |

---

## 1. Project Creation

When a user wants to start a new project, ask (or infer from context):
- **App type**: Phone & Tablet / Wear OS / TV / Auto
- **Language**: Kotlin (default) or Java
- **Min SDK**: API 24+ recommended for broad coverage
- **UI toolkit**: Jetpack Compose (recommended) or Views (XML)
- **Template**: Empty Activity, Navigation Drawer, Bottom Navigation, etc.

### Recommended Project Structure (Clean Architecture)
```
app/
├── data/
│   ├── local/          # Room DB, DataStore
│   ├── remote/         # Retrofit services, DTOs
│   └── repository/     # Repository implementations
├── domain/
│   ├── model/          # Domain entities
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Business logic use cases
├── presentation/
│   ├── ui/             # Composables or Fragments
│   └── viewmodel/      # ViewModels
└── di/                 # Hilt modules
```

---

## 2. Coding Standards

### Kotlin Best Practices
- Prefer `val` over `var`; use data classes for models
- Use coroutines + `Flow` for async; avoid RxJava in new projects
- Prefer extension functions for utilities
- Use `sealed class` / `sealed interface` for UI state
- Avoid `!!` null assertions; use `?.let`, `?: return`, or `requireNotNull`

### ViewModel Pattern (Compose)
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getUsersUseCase: GetUsersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { loadUsers() }

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            getUsersUseCase()
                .onSuccess { _uiState.value = HomeUiState.Success(it) }
                .onFailure { _uiState.value = HomeUiState.Error(it.message) }
        }
    }
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val users: List<User>) : HomeUiState
    data class Error(val message: String?) : HomeUiState
}
```

---

## 3. Debugging Workflows

### Logcat Triage
1. Filter by **package name** first, then by log level
2. Search for `FATAL`, `AndroidRuntime`, `E/` prefix, or exception class names
3. Read the **cause chain** bottom-up from the stack trace

### Common Crash Types & Fixes

| Crash | Likely Cause | Fix |
|---|---|---|
| `NullPointerException` | Null access on optional | Use `?.` safe call or null check |
| `NetworkOnMainThreadException` | Network call on UI thread | Move to coroutine + `Dispatchers.IO` |
| `ANR` (App Not Responding) | Long op on main thread | Offload to background dispatcher |
| `IllegalStateException: Fragment not attached` | Fragment lifecycle issue | Check `isAdded` before UI access |
| `OutOfMemoryError` | Bitmap/leak | Compress bitmaps, use Glide/Coil, run LeakCanary |
| `ClassCastException` | Wrong view binding or cast | Verify layout IDs and types |

### Build / Gradle Failures
- **Sync failed**: Check `build.gradle` dependency versions; run `./gradlew dependencies`
- **Duplicate class**: Add packaging exclusion or upgrade conflicting deps
- **Manifest merger failed**: Check `AndroidManifest.xml` for conflicting attributes
- **R8/ProGuard**: Add `@Keep` or `-keep` rules for reflection/serialization classes

For deeper debugging guidance, read: `references/debugging-deep-dive.md`

---

## 4. Performance Optimization

### Rendering & UI
- Profile with **Android Studio Profiler → CPU → System Trace**
- Target 60fps (16ms/frame); 90/120fps on capable devices
- Avoid nested `weight` in Compose; prefer `LazyColumn` over `Column` for lists
- Use `remember` and `derivedStateOf` to avoid unnecessary recompositions
- Enable **Compose compiler metrics** to spot unstable classes

### Memory
- Run **Memory Profiler** to capture heap dumps
- Use `LeakCanary` in debug builds: `debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.x'`
- Avoid holding `Context` in singletons; use `applicationContext`
- Recycle bitmaps; load images with Coil or Glide

### APK / AAB Size
- Enable **R8** (shrinking + obfuscation) in release builds
- Use `android:extractNativeLibs="false"` in manifest
- Leverage **App Bundle (AAB)** for Play Store — reduces download size
- Audit with **Android Size Analyzer** plugin

---

## 5. Recommended Plugins & Libraries

For detailed plugin descriptions, read: `references/plugins-and-libraries.md`

### Essential Android Studio Plugins
| Plugin | Purpose |
|---|---|
| **ADB Idea** | ADB commands (clear data, kill app) directly from IDE |
| **Kotlin Fill Class** | Auto-fills constructor params |
| **Rainbow Brackets** | Color-coded bracket matching |
| **JSON to Kotlin Class** | Generates data classes from JSON |
| **GitLive** | Real-time Git collaboration |
| **Kotest** | Kotest framework support |
| **Detekt** | Kotlin static analysis |
| **SonarLint** | Code quality & security linting |
| **Material Design Icon Generator** | Insert MD icons directly |

### Core Gradle Libraries (2024-2025)
```kotlin
// Jetpack Compose BOM
implementation(platform("androidx.compose:compose-bom:2024.09.00"))

// Navigation
implementation("androidx.navigation:navigation-compose:2.8.x")

// Hilt DI
implementation("com.google.dagger:hilt-android:2.52")
kapt("com.google.dagger:hilt-android-compiler:2.52")

// Networking
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// Image Loading
implementation("io.coil-kt:coil-compose:2.7.0")

// Room DB
implementation("androidx.room:room-runtime:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// Testing
testImplementation("io.mockk:mockk:1.13.12")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
```

---

## 6. UI / UX Design

### Jetpack Compose Principles
- Build **stateless** composables; hoist state to ViewModel or parent
- Use **Material 3** (`androidx.compose.material3`)
- Use `Scaffold` for standard screen structure (TopBar, FAB, BottomBar, Snackbar)
- Theme with `MaterialTheme.colorScheme`, `MaterialTheme.typography`

### Compose Layout Skeleton
```kotlin
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Home") })
        }
    ) { padding ->
        when (uiState) {
            is HomeUiState.Loading -> CircularProgressIndicator(
                modifier = Modifier.fillMaxSize().wrapContentSize()
            )
            is HomeUiState.Success -> LazyColumn(contentPadding = padding) {
                items(uiState.users) { user -> UserCard(user) }
            }
            is HomeUiState.Error -> ErrorView(uiState.message, onRefresh)
        }
    }
}
```

### Accessibility Checklist
- Add `contentDescription` to all `Image` and `Icon` composables
- Use `semantics { }` blocks for custom touch targets
- Minimum touch target: 48×48dp
- Test with **TalkBack** and **Accessibility Scanner**

---

## 7. Testing Strategy

```
Unit Tests (fast, no device)
  └── ViewModels, UseCases, Repositories, Utils
      Tools: JUnit5, MockK, Turbine (for Flow), kotlinx-coroutines-test

Integration Tests
  └── Room DAOs, DataStore
      Tools: AndroidX Test, in-memory DB

UI / Instrumentation Tests (device/emulator)
  └── Compose screens, Navigation flows
      Tools: Compose Testing, Espresso
```

### ViewModel Unit Test Template
```kotlin
@ExtendWith(InstantExecutorExtension::class)
class HomeViewModelTest {

    private val useCase = mockk<GetUsersUseCase>()
    private lateinit var viewModel: HomeViewModel

    @BeforeEach fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = HomeViewModel(useCase)
    }

    @Test fun `loading state emitted on init`() = runTest {
        coEvery { useCase() } returns Result.success(emptyList())
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## 8. Release Checklist

Before generating a release APK/AAB:

- [ ] `versionCode` incremented, `versionName` updated in `build.gradle`
- [ ] `minifyEnabled true` and `shrinkResources true` in release build type
- [ ] ProGuard rules verified (`proguard-rules.pro`)
- [ ] Signing config set (keystore file, alias, passwords via env vars or `local.properties`)
- [ ] All debug logs removed or gated behind `BuildConfig.DEBUG`
- [ ] Network security config reviewed (`res/xml/network_security_config.xml`)
- [ ] Permissions in Manifest are minimal and justified
- [ ] Target SDK is latest stable (API 35 as of 2025)
- [ ] App tested on API 24 (min) and latest emulator
- [ ] Build AAB: `./gradlew bundleRelease`

---

## Interaction Style

- **Be specific**: Provide actual Kotlin/Gradle code, not pseudocode
- **State your assumptions**: If min SDK, architecture, or library version matters, say so
- **Diagnose before prescribing**: For bugs/crashes, ask for the stack trace or Logcat output before guessing
- **Prefer modern APIs**: Recommend Compose over Views, Flow over LiveData, Hilt over manual DI, KSP over KAPT
- **Explain the why**: Don't just fix — help the user understand the root cause

---

## Reference Files

- `references/debugging-deep-dive.md` — Advanced Logcat, Profiler walkthroughs, ANR analysis
- `references/plugins-and-libraries.md` — Full plugin list with setup instructions and ratings
