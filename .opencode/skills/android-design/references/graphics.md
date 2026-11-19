# Android Graphics & Drawing Reference

## Compose Canvas

### Basic drawing
```kotlin
Canvas(modifier = Modifier.fillMaxSize()) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    // Circle
    drawCircle(
        color = Color(0xFF6200EE),
        radius = 100f,
        center = Offset(canvasWidth / 2, canvasHeight / 2)
    )

    // Rounded rectangle
    drawRoundRect(
        color = Color.Blue,
        topLeft = Offset(50f, 50f),
        size = Size(200f, 100f),
        cornerRadius = CornerRadius(16f)
    )

    // Path (custom shape)
    val path = Path().apply {
        moveTo(0f, canvasHeight)
        cubicTo(canvasWidth * 0.3f, 0f, canvasWidth * 0.7f, 0f, canvasWidth, canvasHeight)
        close()
    }
    drawPath(path, Brush.linearGradient(listOf(Color.Blue, Color.Cyan)))
}
```

### Brushes (gradients)
```kotlin
val linearGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF6200EE), Color(0xFF03DAC5)),
    start = Offset.Zero,
    end = Offset(size.width, size.height)
)
val radialGradient = Brush.radialGradient(
    colors = listOf(Color.White, Color.Transparent),
    center = Offset(size.width / 2, size.height / 2),
    radius = size.minDimension / 2
)
val sweepGradient = Brush.sweepGradient(
    colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Red)
)
```

### Text on Canvas
```kotlin
val textMeasurer = rememberTextMeasurer()
Canvas(Modifier.fillMaxSize()) {
    val textLayoutResult = textMeasurer.measure(
        text = "Hello",
        style = TextStyle(fontSize = 24.sp, color = Color.Black)
    )
    drawText(textLayoutResult, topLeft = Offset(50f, 50f))
}
```

### drawWithCache (expensive pre-computation)
```kotlin
Modifier.drawWithCache {
    // This block runs once or when size changes
    val path = buildComplexPath(size)
    val brush = Brush.linearGradient(colors, size = size)
    onDrawBehind {
        drawPath(path, brush)
    }
}
```

---

## Custom Compose Modifiers

### Draw behind content
```kotlin
fun Modifier.coloredShadow(color: Color, radius: Dp) = this.drawBehind {
    drawCircle(color = color.copy(alpha = 0.3f), radius = size.minDimension / 2 + radius.toPx())
}

fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200)),
        label = "offset"
    )
    drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.4f), Color.Transparent),
                start = Offset(offset * size.width, 0f),
                end = Offset((offset + 1f) * size.width, size.height)
            )
        )
    }
}
```

---

## XML Custom Views

### onDraw basics
```kotlin
class CustomView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(width / 2f, height / 2f, minOf(width, height) / 3f, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = resolveSize(200, widthMeasureSpec)
        setMeasuredDimension(size, size)
    }
}
```

### Hardware acceleration tips
- Always use `Paint.ANTI_ALIAS_FLAG` for smooth edges.
- Avoid allocating objects in `onDraw` — pre-allocate `Paint`, `Path`, `RectF`.
- For animated custom views, call `invalidate()` in an `ObjectAnimator` value callback.
- Use `setLayerType(LAYER_TYPE_HARDWARE, null)` for complex views with frequent redraws.

---

## Vector Drawables

### Animated Vector Drawable (AVD)
```xml
<!-- res/drawable/avd_check.xml -->
<animated-vector xmlns:android="..."
    android:drawable="@drawable/ic_check">
    <target android:name="checkmark">
        <aapt:attr name="android:animation">
            <objectAnimator
                android:propertyName="pathData"
                android:duration="300"
                android:valueFrom="M5,12 L5,12 L5,12"
                android:valueTo="M5,12 L10,17 L19,6"
                android:valueType="pathType"
                android:interpolator="@android:interpolator/fast_out_slow_in"/>
        </aapt:attr>
    </target>
</animated-vector>
```

### Compose with ImageVector
```kotlin
// Use Material Icons
Icon(
    imageVector = Icons.Rounded.Favorite,
    contentDescription = "Favorite",
    tint = MaterialTheme.colorScheme.primary,
    modifier = Modifier.size(24.dp)
)

// Load from resource
Image(
    painter = painterResource(R.drawable.ic_custom),
    contentDescription = null
)
```

---

## RenderEffect & Shaders (API 31+)

```kotlin
// Blur effect (requires API 31+)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    Box(
        modifier = Modifier.graphicsLayer {
            renderEffect = BlurEffect(
                radiusX = 20f,
                radiusY = 20f,
                edgeTreatment = TileMode.Clamp
            ).asComposeRenderEffect()
        }
    ) {
        Content()
    }
}

// For API < 31, use Accompanist Blur or custom Canvas blur
```

---

## Image Loading (Coil)

```kotlin
// Gradle: implementation("io.coil-kt:coil-compose:2.6.0")
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(imageUrl)
        .crossfade(true) // fade-in on load
        .placeholder(R.drawable.placeholder)
        .transformations(CircleCropTransformation(), BlurTransformation(context, radius = 8f))
        .build(),
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier.clip(RoundedCornerShape(12.dp))
)
```

---

## Splash Screen (API 31+ / SplashScreen compat)

```kotlin
// Gradle: implementation("androidx.core:core-splashscreen:1.0.1")

// In Activity:
installSplashScreen().apply {
    setKeepOnScreenCondition { !viewModel.isReady }
    setOnExitAnimationListener { splashScreenView ->
        ObjectAnimator.ofFloat(splashScreenView.view, View.ALPHA, 1f, 0f).apply {
            duration = 300
            doOnEnd { splashScreenView.remove() }
            start()
        }
    }
}
```

```xml
<!-- res/values/themes.xml -->
<style name="Theme.App.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/md_theme_primary</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/avd_splash_logo</item>
    <item name="windowSplashScreenAnimationDuration">500</item>
    <item name="postSplashScreenTheme">@style/Theme.App</item>
</style>
```

---

## Adaptive Icons

```xml
<!-- res/mipmap-anydpi-v26/ic_launcher.xml -->
<adaptive-icon xmlns:android="...">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_foreground"/> <!-- API 33+ -->
</adaptive-icon>
```
