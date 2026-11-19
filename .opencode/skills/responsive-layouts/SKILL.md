---
name: responsive-layouts
description: Reference for CSS Grid, Flexbox, Container Queries, and JS/Compose/SwiftUI responsive layout patterns across web, Android, and iOS.
---

# Responsive Layout Patterns — Implementation Reference

## CSS Grid — Responsive Card Grid

```css
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 24px;
}
/* No media queries needed — auto-fit + minmax handles reflow automatically.
   Cards stay at least 260px wide; wraps to fewer columns as space shrinks. */
```

## CSS Grid — Explicit Breakpoint Layout

```css
.layout {
  display: grid;
  grid-template-columns: 1fr;          /* mobile: single column */
  gap: 16px;
}

@media (min-width: 600px) {
  .layout {
    grid-template-columns: repeat(2, 1fr);   /* tablet: 2 columns */
    gap: 20px;
  }
}

@media (min-width: 1024px) {
  .layout {
    grid-template-columns: 280px 1fr;        /* desktop: sidebar + main */
    gap: 32px;
  }
}
```

## Flexbox — Responsive Nav

```css
.navbar {
  display: flex;
  flex-direction: column;     /* mobile: stacked */
  gap: 12px;
}

@media (min-width: 768px) {
  .navbar {
    flex-direction: row;       /* desktop: horizontal */
    justify-content: space-between;
    align-items: center;
  }
}
```

## Container Queries (modern — component-level responsiveness)

```css
.card-container {
  container-type: inline-size;
  container-name: card;
}

/* This card adapts based on ITS OWN container width,
   not the viewport — works inside sidebars, modals, etc. */
@container card (min-width: 400px) {
  .card {
    display: flex;
    flex-direction: row;
  }
}
@container card (max-width: 399px) {
  .card {
    display: flex;
    flex-direction: column;
  }
}
```
Container queries are the modern solution for components that appear in multiple
contexts (sidebar AND full-width) where viewport-based media queries can't adapt correctly.

---

## React — Responsive Hook Pattern

```jsx
function useBreakpoint() {
  const [breakpoint, setBreakpoint] = useState('desktop');

  useEffect(() => {
    const checkSize = () => {
      const width = window.innerWidth;
      if (width < 600) setBreakpoint('mobile');
      else if (width < 1024) setBreakpoint('tablet');
      else setBreakpoint('desktop');
    };
    checkSize();
    window.addEventListener('resize', checkSize);
    return () => window.removeEventListener('resize', checkSize);
  }, []);

  return breakpoint;
}

// Usage — swap component structure, not just CSS
function Layout() {
  const breakpoint = useBreakpoint();

  if (breakpoint === 'mobile') return <MobileLayout />;
  return <DesktopLayout />;   // structurally different component tree
}
```

Prefer **CSS-based responsiveness** for spacing/sizing changes, but use a **JS breakpoint
hook** when the component structure itself must change (e.g., table → card list,
sidebar → bottom sheet) since that's not achievable with CSS alone.

---

## Jetpack Compose — WindowSizeClass (Android)

```kotlin
@Composable
fun AdaptiveLayout(windowSizeClass: WindowSizeClass) {
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // Phone portrait — single column, bottom nav
            CompactLayout()
        }
        WindowWidthSizeClass.Medium -> {
            // Tablet portrait / foldable — 2 columns, rail nav
            MediumLayout()
        }
        WindowWidthSizeClass.Expanded -> {
            // Tablet landscape / desktop — persistent sidebar, multi-pane
            ExpandedLayout()
        }
    }
}

// In Activity
val windowSizeClass = calculateWindowSizeClass(this)
AdaptiveLayout(windowSizeClass)
```

### Compose Adaptive Navigation Pattern
```kotlin
@Composable
fun AdaptiveScaffold(widthSizeClass: WindowWidthSizeClass, content: @Composable () -> Unit) {
    when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            Scaffold(bottomBar = { NavigationBar { /* tabs */ } }) { content() }
        }
        WindowWidthSizeClass.Medium -> {
            Row {
                NavigationRail { /* icons only */ }
                content()
            }
        }
        WindowWidthSizeClass.Expanded -> {
            Row {
                PermanentNavigationDrawer { /* icons + labels */ }
                content()
            }
        }
    }
}
```

---

## SwiftUI — Adaptive Layout (iOS)

```swift
struct AdaptiveLayout: View {
    @Environment(\.horizontalSizeClass) var sizeClass

    var body: some View {
        if sizeClass == .compact {
            VStack { content }          // iPhone — stacked
        } else {
            HStack { content }          // iPad — side by side
        }
    }
}

// GeometryReader for fine-grained control
GeometryReader { geometry in
    if geometry.size.width > 700 {
        HStack { /* wide layout */ }
    } else {
        VStack { /* narrow layout */ }
    }
}
```

---

## Common Responsive Strategies by Layout Type

### Sidebar Navigation
```
Desktop (1024px+):   Persistent sidebar, always visible, ~240-280px wide
Tablet (600-1023px): Collapsible sidebar (icon rail) or temporary overlay drawer
Mobile (<600px):     Bottom tab bar (replaces sidebar entirely) or hamburger drawer
```

### Multi-Column Content
```
Desktop: 3 columns side-by-side
Tablet:  2 columns
Mobile:  1 column, vertically stacked (reorder if needed — most important first)
```

### Data Tables
```
Desktop: Full table, all columns visible
Tablet:  Hide lowest-priority columns, horizontal scroll for the rest
Mobile:  Convert to stacked cards (see component-patterns.md)
```

### Hero Sections
```
Desktop: Image/visual + text side by side
Mobile:  Image/visual on top, text below, reduced visual height (~40vh max)
```

---

## Touch Target Adjustments by Platform

```css
/* Base size for mouse/desktop */
.button { padding: 8px 16px; min-height: 32px; }

/* Increase for touch devices */
@media (pointer: coarse) {
  .button { padding: 12px 20px; min-height: 44px; }
}

/* Or combine with breakpoint for mobile-specific touch sizing */
@media (max-width: 600px) {
  .button { min-height: 48px; }   /* Android minimum */
}
```

```css
/* Detect touch vs mouse for hover-dependent UI */
@media (hover: hover) {
  .card:hover { transform: translateY(-2px); }   /* only apply hover on devices that support it */
}
```
