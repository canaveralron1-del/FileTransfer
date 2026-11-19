# Android Theming Reference — Material Design 3

## Material3 Setup

```kotlin
// Gradle
implementation("androidx.compose.material3:material3:1.2.1")
implementation("com.google.android.material:material:1.12.0") // XML

// App entry point
@Composable
fun MyApp() {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes
    ) {
        // content
    }
}
```

---

## Color Scheme

### Define your scheme
```kotlin
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005E),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    outline = Color(0xFF79747E),
    error = Color(0xFFB3261E),
    // ... add remaining roles
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    // ... mirror for dark
)
```

### Dynamic Color (Material You, API 31+)
```kotlin
@Composable
fun MyAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        isSystemInDarkTheme() -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, ...) { content() }
}
```

### Using color tokens (never hardcode)
```kotlin
// Correct
Box(Modifier.background(MaterialTheme.colorScheme.primaryContainer))
Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer)

// Wrong
Box(Modifier.background(Color(0xFFEADDFF)))
```

### Surface tones (elevation in dark mode)
```kotlin
// Material3 uses surface tones instead of shadows in dark mode
// Use Surface composable — it handles this automatically
Surface(
    tonalElevation = 4.dp, // adds primary color tint in dark mode
    shadowElevation = 2.dp
) { content() }
```

---

## Typography

```kotlin
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// Custom font family
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_bold, FontWeight.Bold)
)
```

---

## Shapes

```kotlin
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // Chip, small buttons
    small = RoundedCornerShape(8.dp),         // Text fields
    medium = RoundedCornerShape(12.dp),       // Cards
    large = RoundedCornerShape(16.dp),        // Bottom sheets (partial)
    extraLarge = RoundedCornerShape(28.dp)    // FAB, dialogs
)

// Custom shape on a composable:
Card(shape = MaterialTheme.shapes.large) { ... }
Button(shape = MaterialTheme.shapes.extraLarge) { ... }
```

---

## Common Components with Material3 Theming

### Button variants
```kotlin
Button(onClick = {}) { Text("Filled") }           // Primary action
FilledTonalButton(onClick = {}) { Text("Tonal") } // Secondary action
OutlinedButton(onClick = {}) { Text("Outlined") } // Less emphasis
TextButton(onClick = {}) { Text("Text") }          // Lowest emphasis
ElevatedButton(onClick = {}) { Text("Elevated") }
```

### Cards
```kotlin
// Three variants: Filled (default), Elevated, Outlined
ElevatedCard(
    modifier = Modifier.padding(16.dp),
    shape = MaterialTheme.shapes.large
) {
    Column(Modifier.padding(16.dp)) {
        Text("Title", style = MaterialTheme.typography.titleMedium)
        Text("Body text", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

### Top App Bar with scroll behavior
```kotlin
val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
        TopAppBar(
            title = { Text("Screen Title") },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            )
        )
    }
) { paddingValues -> ... }
```

### Navigation
```kotlin
// Bottom Navigation Bar (<=5 destinations)
NavigationBar {
    items.forEach { item ->
        NavigationBarItem(
            selected = currentRoute == item.route,
            onClick = { navController.navigate(item.route) },
            icon = { Icon(item.icon, contentDescription = item.label) },
            label = { Text(item.label) }
        )
    }
}

// Navigation Drawer (for complex apps)
ModalNavigationDrawer(drawerContent = { ModalDrawerSheet { ... } }) {
    // screen content
}
```

---

## Dark Mode Best Practices

1. **Never hardcode** `Color.Black` or `Color.White` — use `onBackground`, `onSurface`.
2. **Elevation in dark mode**: Use `Surface(tonalElevation = X.dp)` not `shadowElevation` alone — Material3 applies a primary tint at elevation in dark theme.
3. **Images**: Use `ColorFilter` or a `Box` with a dark overlay for images in dark mode:
   ```kotlin
   val overlay = if (isDark) Color.Black.copy(alpha = 0.3f) else Color.Transparent
   ```
4. **Status bar**: Update via `WindowInsetsController` or `accompanist-systemuicontroller`.
5. **Test both themes** — Android Studio's split preview with `@PreviewLightDark`.

```kotlin
@PreviewLightDark
@Composable
fun MyComponentPreview() {
    MyAppTheme { MyComponent() }
}
```

---

## Status Bar / Edge-to-Edge

```kotlin
// In Activity.onCreate()
enableEdgeToEdge()

// Handle insets in Compose
Scaffold { paddingValues ->
    Column(Modifier.padding(paddingValues)) { ... }
}

// Or manually:
Modifier.windowInsetsPadding(WindowInsets.systemBars)
Modifier.safeDrawingPadding()
```
