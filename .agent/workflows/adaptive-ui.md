---
description: Refactor an Android screen into an adaptive layout supporting Phone Portrait, Phone Landscape, Tablet Portrait, and Tablet Landscape viewports
---

1. Load `personas/android-adaptive-screen-agent.md`, `rules/common/adaptive-ui.md`, and `rules/platforms/android-adaptive-ui.md`.

2. Ask the user (or infer from context) which screen is the target. Inspect the Kotlin Activity/Fragment and current layout files for that screen.

3. **Pre-Check 1: ViewBinding ID Audit**
   - Open the Kotlin Activity/Fragment for the screen.
   - List every `binding.<view_id>` call.
   - Guardrail: All of these IDs must exist in all layout variants you create to prevent ViewBinding nullability compile errors.

4. **Pre-Check 2: Manifest Verification (`AndroidManifest.xml`)**
   - Confirm target Activity declares `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"`.
   - Confirm `android:windowSoftInputMode="adjustResize"` for screens with input fields.
   - Confirm `android:screenOrientation="portrait"` is NOT declared in the manifest (it blocks tablet landscape).

5. **Pre-Check 3: Dimension Tokens (`dimens.xml`)**
   - Ensure `res/values/dimens.xml` and `res/values-sw600dp/dimens.xml` exist with standard spacing tokens (`spacing_xs` through `spacing_xxl`, `screen_padding_horizontal`, `content_max_width`).

6. **Audit Base Phone Layout (`res/layout/<screen>.xml`)**
   - Root container must be scrollable (`NestedScrollView` with `android:fillViewport="true"`).
   - Verify all interactive controls have minimum 48×48dp touch targets.

7. **Create or Update Tablet Portrait Layout (`res/layout-sw600dp/<screen>.xml`)**
   - Single vertical column centered horizontally.
   - Constrain content width using `ConstraintLayout` with `app:layout_constraintWidth_max="600dp"` and `app:layout_constraintHorizontal_bias="0.5"`.
   - Apply tablet padding (`@dimen/screen_padding_horizontal` = 48dp).
   - Ensure all ViewBinding IDs from Pre-Check 1 are preserved.

8. **Create or Update Tablet Landscape Layout (`res/layout-sw600dp-land/<screen>.xml`)**
   - Two-column dual-pane layout (50/50 split via `ConstraintLayout` guideline or weighted `LinearLayout`).
   - Left pane: Summary, navigation, hero profile, or master list.
   - Right pane: Details, editor form, projects, or interactive canvas.
   - Ensure all ViewBinding IDs from Pre-Check 1 are preserved.

9. **Compose Adaptation (if applicable)**
   - Pass `windowWidthSizeClass: WindowWidthSizeClass` from host Activity.
   - Branch with `when (windowWidthSizeClass)`: Compact (single-column), Medium (bounded 600dp), Expanded (two-column Row).

10. **Verify via Native Gradle Tooling**
    - Run `./gradlew compileDebugKotlin` → Must exit with 0 errors.
    - Run `./gradlew lintDebug` → Confirm no hardcoded dimension or missing touch target warnings.
