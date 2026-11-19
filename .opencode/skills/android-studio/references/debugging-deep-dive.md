# Android Studio Debugging Deep Dive

## Logcat Advanced Usage

### Filtering Syntax
```
# By tag
tag:MyTag

# By package
package:com.example.myapp

# Combined (AND)
tag:Retrofit level:ERROR

# Regex
tag~:My(Tag|Other)
```

### Reading a Stack Trace
```
E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: Process: com.example.app, PID: 12345
E AndroidRuntime: java.lang.NullPointerException: Attempt to invoke virtual method
                  'java.lang.String com.example.User.getName()' on a null object reference
    at com.example.HomeFragment.onViewCreated(HomeFragment.kt:42)   <-- YOUR CODE (start here)
    at androidx.fragment.app.Fragment.performCreateView(...)          <-- framework
```
- Read top-to-bottom but **start debugging at the first line showing YOUR package**
- The line number in your file is the exact crash location

---

## Android Studio Profilers

### CPU Profiler
1. Run app in **debug** or **profileable** mode
2. Open **Profiler** tab → CPU → Record → **System Trace**
3. Look for: long frames (>16ms), main-thread blocking, binder calls

### Memory Profiler
1. Trigger GC several times
2. Capture **heap dump**
3. Filter by your package name
4. Look for: retained objects, bitmap references, Leaks tab

### Network Profiler
- Shows all HTTP requests with timing, body, headers
- Requires `OkHttp` (auto-detected) or manual instrumentation

---

## ANR Analysis

ANR = Application Not Responding (5s on main thread, 10s on broadcast receiver)

### Finding ANR Traces
- Device: `/data/anr/traces.txt` (requires root or `adb bugreport`)
- Play Console: Android Vitals → ANRs & crashes

### Common ANR Causes
| Pattern in trace | Cause |
|---|---|
| `waiting to lock <...>` | Deadlock or lock contention |
| `nativePollOnce` sleeping normally (OK if idle) | Idle main thread — look elsewhere |
| `Binder` calls in stack | Cross-process blocking call |
| Your code in `doInBackground` on main | Wrong thread usage |

---

## Gradle Build Troubleshooting

### Full Clean Build
```bash
./gradlew clean assembleDebug --stacktrace --info
```

### Dependency Tree (find conflicts)
```bash
./gradlew app:dependencies --configuration releaseRuntimeClasspath | grep -i "conflict\|FAILED"
```

### Common Fixes

**Duplicate class error**
```kotlin
// In app/build.gradle
android {
    packagingOptions {
        exclude("META-INF/DEPENDENCIES")
        pickFirst("lib/x86/libc++_shared.so")
    }
}
```

**Manifest merger conflict**
```xml
<!-- Override conflicting attribute -->
<activity
    android:name=".MainActivity"
    tools:replace="android:theme"
    android:theme="@style/MyTheme"/>
```

**R8 keeps stripping needed classes**
```proguard
# proguard-rules.pro
-keep class com.example.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
```

---

## Emulator vs Physical Device

| Scenario | Recommendation |
|---|---|
| Quick UI iteration | Emulator (fast boot, resizable) |
| Camera / Bluetooth / NFC | Physical device |
| Performance profiling | Physical device (emulator skews results) |
| Low-end device testing | Physical budget device or emulator with limited RAM config |
| API level testing | Emulator (easy to swap system images) |

---

## Useful ADB Commands

```bash
# Install APK
adb install -r app-debug.apk

# Clear app data
adb shell pm clear com.example.myapp

# Pull a database file
adb shell run-as com.example.myapp cp /data/data/com.example.myapp/databases/app.db /sdcard/
adb pull /sdcard/app.db

# Screen record
adb shell screenrecord /sdcard/demo.mp4

# Simulate low memory
adb shell am send-trim-memory com.example.myapp CRITICAL

# Show current activity
adb shell dumpsys activity activities | grep mResumedActivity
```
