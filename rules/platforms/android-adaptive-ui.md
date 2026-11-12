# Platform Extension: Android Adaptive UI Engineering Rules

> **Activation**: Apply when designing, refactoring, or reviewing any Android screen (XML or Jetpack Compose) for Phone, Foldable, and Tablet viewports.
> Always read `rules/common/adaptive-ui.md` first for universal spacing, touch target, and scroll safety rules.

---

## 1. Window Size Class Breakpoints (The Three Envelopes)

| Envelope | Width Condition | Height Condition | Typical Device | Layout Archetype |
| :--- | :--- | :--- | :--- | :--- |
| **Compact** | `< 600dp` | Any | Phone portrait, phone landscape, foldable cover screen | Single-column vertical stream |
| **Medium** | `600dp – 839dp` | `>= 480dp` | Tablet portrait, unfolded foldable inner screen | Centered single column (max width 600dp) |
| **Expanded** | `>= 840dp` | `>= 480dp` | Tablet landscape, desktop, ChromeOS | Multi-pane / Two-column side-by-side |

### Landscape Phone Qualifier Reality
A phone held in landscape has a width of 700–900dp, but its **smallest width (`sw`) remains its short side (~360–412dp)**:
- A landscape phone will **NEVER load `layout-sw600dp/`**. It loads `res/layout/` (or `res/layout-land/`).
- Therefore, your base `res/layout/` must always be safe for short vertical heights (`height < 480dp`) by wrapping content in a `NestedScrollView` with `android:fillViewport="true"`.

---

## 2. Choosing the Right XML Resource Qualifier

| Qualifier | Folder Path | Trigger Condition | Best Used For |
| :--- | :--- | :--- | :--- |
| **Default** | `res/layout/` | Base fallback (all phones) | Phone portrait and landscape single-column layouts |
| **Smallest Width** | `res/layout-sw600dp/` | `sw >= 600dp` (any orientation) | **Tablet Portrait** centered single-column layouts |
| **Tablet Landscape** | `res/layout-sw600dp-land/` | `sw >= 600dp` AND landscape | **Tablet Landscape** two-column / multi-pane layouts |

> [!IMPORTANT]
> **Avoid `layout-w600dp/`**: The `w600dp` qualifier activates on both landscape phones and tablets, which often leads to squished multi-column layouts on phones. Prefer `sw600dp` for tablet-specific designs. Never mix `sw` and `w` qualifiers for the same screen.

---

## 3. Mandatory Manifest & Configuration Steps

Before creating or editing any adaptive layouts, verify the following in `AndroidManifest.xml`:

### 3a. `configChanges` Declaration (Prevents Activity Recreation)
Every Activity that supports adaptive layout transitions must declare:
```xml
android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"
```
*Why*: Without this, rotating a tablet or unfolding a foldable forces Android to **destroy and recreate** the Activity from scratch, causing flickering and loss of unsaved user form input.

### 3b. Keyboard Inset Safety (`windowSoftInputMode`)
All Activities containing text input fields or forms must declare:
```xml
android:windowSoftInputMode="adjustResize"
```
*Why*: Ensures the layout resizes cleanly when the software keyboard opens, allowing form fields to scroll into view instead of being covered.

### 3c. Screen Orientation Architecture (Do Not Hardcode in Manifest)
- **NEVER** put `android:screenOrientation="portrait"` in `AndroidManifest.xml` if tablets are supported. It locks tablet users in portrait and makes `layout-sw600dp-land` completely unreachable.
- If phone portrait locking is required by design, lock it **conditionally in code** in the Activity's `onCreate`:
  ```kotlin
  if (resources.configuration.smallestScreenWidthDp < 600) {
      try {
          requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      } catch (_: Exception) {}
  }
  ```

---

## 4. The ViewBinding ID Parity Rule (Critical Compile Safety)

When multiple layout variants exist for the same screen (`res/layout/`, `res/layout-sw600dp/`, `res/layout-sw600dp-land/`), Android ViewBinding generates a **single** binding class:

1. **If a view ID exists in all variants with the same view type**:
   - ViewBinding generates a non-null property: `val myButton: MaterialButton`.
2. **If a view ID is missing in ANY variant**:
   - ViewBinding marks the property as **nullable**: `val myButton: MaterialButton?`.
   - Any existing Kotlin code accessing `binding.myButton` without safe call `?.` will **fail compilation** or cause runtime crashes.

**Rules for Agents**:
- When creating a tablet layout (`layout-sw600dp` or `layout-sw600dp-land`), **preserve all view IDs** referenced by the Activity/Fragment.
- If a tablet layout intentionally introduces dual-pane views (e.g., `rvSectionsLeft` and `rvSectionsRight` instead of `rvSections`), the Kotlin Activity must explicitly check for dual-pane presence with safe calls:
  ```kotlin
  val isDualPane = binding.rvSectionsLeft != null && binding.rvSectionsRight != null
  if (isDualPane) {
      // Setup dual-pane
  } else {
      binding.rvSections?.let { setupSinglePane(it) }
  }
  ```

---

## 5. ConstraintLayout Max-Width vs LinearLayout/FrameLayout Pitfall

> [!CAUTION]
> In Android native XML, `android:maxWidth` is **ONLY supported by `TextView` and `SearchView`**.
> Putting `android:maxWidth` on `LinearLayout`, `FrameLayout`, or `ScrollView` is **silently ignored** by Android at runtime! The view will stretch 100% edge-to-edge on tablets.

### Correct Way to Cap Width in XML:
Use `ConstraintLayout` with `app:layout_constraintWidth_max`:
```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:id="@+id/container_content"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintWidth_max="600dp"
        app:layout_constraintHorizontal_bias="0.5">
        <!-- Content elements -->
    </LinearLayout>
</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## 6. Dimension Resource Token Strategy (`dimens.xml`)

Instead of duplicating entire XML layout files just to increase padding on tablets, leverage Android's native dimension qualifier overriding:

1. **Phone Baseline**: `res/values/dimens.xml`
   ```xml
   <dimen name="screen_padding_horizontal">24dp</dimen>
   <dimen name="screen_padding_vertical">24dp</dimen>
   <dimen name="content_max_width">380dp</dimen>
   <dimen name="spacing_md">16dp</dimen>
   ```

2. **Tablet Override**: `res/values-sw600dp/dimens.xml`
   ```xml
   <dimen name="screen_padding_horizontal">48dp</dimen>
   <dimen name="screen_padding_vertical">36dp</dimen>
   <dimen name="content_max_width">600dp</dimen>
   <dimen name="spacing_md">20dp</dimen>
   ```

Layouts that reference `@dimen/screen_padding_horizontal` will automatically adapt on tablets without duplicate XML layout files unless the layout hierarchy changes.

---

## 7. XML Layout Implementation Patterns

### Phone (Compact) — `res/layout/`
- Single vertical column.
- Root: `androidx.core.widget.NestedScrollView` with `android:fillViewport="true"`.
- Buttons and cards: full width or natural wrap_content.

### Tablet Portrait (Medium) — `res/layout-sw600dp/`
- Single vertical column centered horizontally.
- Constrained maximum content width (`600dp`).
- Increased side padding (`@dimen/screen_padding_horizontal` = 48dp).
- Buttons: `wrap_content` width, centered.

### Tablet Landscape (Expanded) — `res/layout-sw600dp-land/`
- Two-column / dual-pane layout.
- Use `ConstraintLayout` with a 50% guideline or horizontal `LinearLayout` with weights (`0.5` / `0.5` or `1` / `1.2`).
- Left pane: Summary, navigation, profile header, or master list.
- Right pane: Details, editor form, projects, or interactive canvas.
- Remove bottom-pinned action bars; place primary actions in top toolbar or dedicated pane panel.

---

## 8. Jetpack Compose & Hybrid App Strategy

### Pure Compose Screens
Use `WindowSizeClass` from Material 3:
```kotlin
@Composable
fun MyAdaptiveScreen(windowWidthSizeClass: WindowWidthSizeClass) {
    when (windowWidthSizeClass) {
        WindowWidthSizeClass.Compact -> PhoneLayout()
        WindowWidthSizeClass.Medium  -> TabletPortraitLayout(maxWidth = 600.dp)
        WindowWidthSizeClass.Expanded -> TabletLandscapeDualPane()
    }
}
```

### Hybrid App (ComposeView inside XML Activity)
1. Calculate `WindowSizeClass` in the **Activity's `onCreate`**:
   ```kotlin
   val windowSizeClass = calculateWindowSizeClass(this)
   ```
2. Pass `windowSizeClass.widthSizeClass` into `composeView.setContent { }`.
3. Never calculate `WindowSizeClass` inside a child composable—it requires the host Activity context.

---

## 9. Verification Matrix

Before completing any adaptive UI task, verify all 4 viewports:

| Viewport | Test Dimensions | Expected Behavior |
| :--- | :--- | :--- |
| **Phone Portrait** | 360 × 800dp | Single column, edge-to-edge cards with 16–24dp padding |
| **Phone Landscape** | 800 × 360dp (height < 480dp) | Scrollable single column, collapsed headers, zero content clipping |
| **Tablet Portrait** | 768 × 1024dp (`sw600dp`) | Single column centered, max-width 600dp, generous side gutters |
| **Tablet Landscape** | 1024 × 768dp (`sw600dp-land`) | Balanced two-column split, persistent action controls |

Run CLI checks:
```bash
./gradlew compileDebugKotlin
./gradlew lintDebug
```
