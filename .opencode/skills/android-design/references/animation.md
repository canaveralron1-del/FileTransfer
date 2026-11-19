# Android Animation Reference

## Jetpack Compose Animations

### Simple State Animations
```kotlin
// Float, Color, Dp, Size, Offset — all have animate*AsState variants
val alpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(durationMillis = 300, easing = EaseInOut),
    label = "alpha"
)
val color by animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    animationSpec = spring(stiffness = Spring.StiffnessMedium),
    label = "color"
)
```

### AnimatedVisibility
```kotlin
AnimatedVisibility(
    visible = showContent,
    enter = fadeIn(tween(300)) + slideInVertically(initialOffsetY = { it / 2 }),
    exit = fadeOut(tween(200)) + slideOutVertically()
) {
    Content()
}
```

### AnimatedContent (for state transitions)
```kotlin
AnimatedContent(
    targetState = uiState,
    transitionSpec = {
        fadeIn(tween(300)) togetherWith fadeOut(tween(200))
    },
    label = "content"
) { state ->
    when (state) {
        is Loading -> LoadingView()
        is Success -> SuccessView(state.data)
        is Error -> ErrorView(state.message)
    }
}
```

### Transition API (multi-property coordinated)
```kotlin
val transition = updateTransition(targetState = expanded, label = "card")
val size by transition.animateDp(
    transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy) },
    label = "size"
) { if (it) 200.dp else 80.dp }
val cornerRadius by transition.animateDp(label = "corner") { if (it) 8.dp else 40.dp }
```

### graphicsLayer (performant transforms — no re-layout)
```kotlin
Box(
    modifier = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = alphaValue
        rotationZ = rotation
        translationY = offsetY
    }
)
```

### Infinite Animations (loading, pulse, shimmer)
```kotlin
val infiniteTransition = rememberInfiniteTransition(label = "pulse")
val scale by infiniteTransition.animateFloat(
    initialValue = 0.9f,
    targetValue = 1.1f,
    animationSpec = infiniteRepeatable(
        animation = tween(800, easing = EaseInOut),
        repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
)
```

### Shared Element Transitions (Compose, API 21+)
```kotlin
// In your NavHost:
AnimatedNavHost(navController, startDestination = "list") {
    composable("list") {
        SharedTransitionLayout {
            ListScreen(
                onItemClick = { navController.navigate("detail/$it") },
                animatedVisibilityScope = this@composable,
                sharedTransitionScope = this@SharedTransitionLayout
            )
        }
    }
}

// In the composable:
Image(
    modifier = Modifier.sharedElement(
        state = rememberSharedContentState(key = "image_$id"),
        animatedVisibilityScope = animatedVisibilityScope
    )
)
```

### Reduce Motion — Always include this
```kotlin
val reduceMotion = LocalReduceMotion.current
val animSpec = if (reduceMotion) snap<Float>() else tween(300)
```

---

## Lottie Integration

### Gradle
```kotlin
implementation("com.airbnb.android:lottie-compose:6.4.0")
```

### Basic usage
```kotlin
val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.animation))
val progress by animateLottieCompositionAsState(
    composition,
    iterations = LottieConstants.IterateForever, // or 1 for one-shot
    isPlaying = isPlaying
)
LottieAnimation(composition = composition, progress = { progress })
```

---

## XML View Animations

### ObjectAnimator (property animation)
```kotlin
ObjectAnimator.ofFloat(view, "translationY", 0f, -100f).apply {
    duration = 300
    interpolator = DecelerateInterpolator()
    start()
}
// Combine with AnimatorSet for sequences/parallel
val set = AnimatorSet().apply {
    playTogether(fadeIn, slideUp)
    duration = 400
    start()
}
```

### ViewPropertyAnimator (fluent, simpler)
```kotlin
view.animate()
    .alpha(1f)
    .translationY(0f)
    .setDuration(300)
    .setInterpolator(FastOutSlowInInterpolator())
    .start()
```

### MotionLayout (XML, complex constraint-based animations)
```xml
<!-- res/xml/motion_scene.xml -->
<MotionScene>
    <ConstraintSet android:id="@+id/start">
        <Constraint android:id="@+id/button"
            app:layout_constraintTop_toTopOf="parent"
            android:alpha="0" />
    </ConstraintSet>
    <ConstraintSet android:id="@+id/end">
        <Constraint android:id="@+id/button"
            app:layout_constraintBottom_toBottomOf="parent"
            android:alpha="1" />
    </ConstraintSet>
    <Transition
        app:constraintSetStart="@id/start"
        app:constraintSetEnd="@id/end"
        app:duration="500">
        <OnSwipe app:touchAnchorId="@id/button" app:dragDirection="dragUp" />
    </Transition>
</MotionScene>
```

### AnimatedVectorDrawable
```kotlin
// In code:
val avd = AnimatedVectorDrawableCompat.create(context, R.drawable.avd_play_pause)
imageView.setImageDrawable(avd)
avd?.start()
```

---

## Navigation Transitions (Compose Navigation)

```kotlin
// Gradle: implementation("androidx.navigation:navigation-compose:2.7.7")
AnimatedNavHost(
    navController = navController,
    startDestination = "home",
    enterTransition = { slideInHorizontally { it } + fadeIn() },
    exitTransition = { slideOutHorizontally { -it / 3 } + fadeOut() },
    popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
    popExitTransition = { slideOutHorizontally { it } + fadeOut() }
) { ... }
```

---

## Common Animation Specs Cheat Sheet

| Feel | Spec |
|---|---|
| Snappy UI feedback | `spring(stiffness = Spring.StiffnessMediumLow)` |
| Bouncy | `spring(dampingRatio = Spring.DampingRatioMediumBouncy)` |
| Smooth enter | `tween(300, easing = EaseOut)` |
| Smooth exit | `tween(200, easing = EaseIn)` |
| Instant (no animation) | `snap()` |
| Material standard | `tween(300, easing = FastOutSlowInEasing)` |
