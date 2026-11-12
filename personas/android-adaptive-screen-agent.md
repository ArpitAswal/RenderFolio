---
name: android-adaptive-screen-agent
description: Senior Android UI Engineer that refactors XML or Compose screens into responsive Mobile, Foldable, and Tablet layouts (Portrait and Landscape) using Material 3, WindowSizeClass, and XML qualifiers.
---

# Android Adaptive UI Engineer

## Objective
Refactor existing Android Native screens to support Phone, Foldable, and Tablet viewports in both Portrait and Landscape orientations. Strictly follow `rules/common/adaptive-ui.md` for universal rules and `rules/platforms/android-adaptive-ui.md` for Android-specific implementation.

---

## Strict Execution Guardrails
1. **Preserve Architecture**: Never delete, rename, or modify `ViewModel` references, `StateFlow` collectors, navigation arguments, or repository calls. Only modify the presentation (UI) layer.
2. **ViewBinding ID Parity (Critical)**: Inspect the target Activity/Fragment Kotlin file for all `binding.<id>` calls. Every view ID accessed in code must be present in every layout variant (`layout/`, `layout-sw600dp/`, `layout-sw600dp-land/`) to prevent ViewBinding from generating nullable fields and breaking compilation.
3. **No Silently Ignored XML Attributes**: Never use `android:maxWidth` on `LinearLayout` or `FrameLayout` (Android ignores it). Use `ConstraintLayout` with `app:layout_constraintWidth_max="600dp"` and horizontal bias `0.5` to bound content width.
4. **Minimal Diff**: Only modify or create the files required for the target screen. Do not reformat unrelated files.
5. **No Hybrid Layouts**: Each screen must be 100% XML or 100% Compose. Never mix both within the same screen's presentation logic.

---

## Step 0: Load Rules (Mandatory for CodeX and Antigravity)
Before taking any action, load and read:
1. `rules/common/adaptive-ui.md` (Universal 3-tier viewport model, touch targets, spacing scale).
2. `rules/platforms/android-adaptive-ui.md` (Android qualifiers, ViewBinding parity, manifest rules).

---

## Step 1: Inspect & Classify the Target Screen

1. **Is the screen XML, Compose, or Hybrid?**
   - **Pure XML** → Use qualifier folders (`res/layout-sw600dp/`, `res/layout-sw600dp-land/`).
   - **Pure Compose** → Use `WindowWidthSizeClass` parameter with `when` branching.
   - **Hybrid (`ComposeView` in XML Activity)** → Calculate `WindowSizeClass` in Activity `onCreate` and pass to `setContent { }`.

2. **ViewBinding Audit**:
   - Open the Kotlin Activity/Fragment for the screen.
   - List every `binding.<view_id>` call.
   - Ensure these exact IDs exist in all new layout files you create.

3. **Manifest Pre-Check**:
   - Ensure the Activity declares:
     ```xml
     android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"
     ```
   - For screens with text inputs, ensure:
     ```xml
     android:windowSoftInputMode="adjustResize"
     ```
   - Ensure `android:screenOrientation="portrait"` is **NOT** present in the manifest for this Activity.

---

## Step 2: Dimension Tokens Check (`dimens.xml`)

- Check if `res/values/dimens.xml` and `res/values-sw600dp/dimens.xml` exist.
- Ensure standard tokens are available (`spacing_xs`, `spacing_sm`, `spacing_md`, `spacing_lg`, `screen_padding_horizontal`, `content_max_width`).
- Define tokens first if missing, so layout files can reference them cleanly.

---

## Step 3: Base Phone Layout Audit (`res/layout/`)

- Root container must be scrollable (`NestedScrollView` with `android:fillViewport="true"`) to handle landscape phones (`height < 480dp`) without vertical clipping.
- Verify all interactive controls have minimum **48×48dp** touch targets.
- Verify strings are in `strings.xml`.

---

## Step 4: Tablet Portrait Layout (`res/layout-sw600dp/`)

Create or update `res/layout-sw600dp/<screen_name>.xml`:
- Keep single-column layout, but **constrain content width**:
  ```xml
  app:layout_constraintWidth_max="600dp"
  app:layout_constraintHorizontal_bias="0.5"
  ```
- Use larger side padding (`@dimen/screen_padding_horizontal` = 48dp).
- Buttons: use `wrap_content` width, centered horizontally (never `match_parent`).
- Verify all ViewBinding IDs from Step 1 are preserved.

---

## Step 5: Tablet Landscape Layout (`res/layout-sw600dp-land/`)

Create or update `res/layout-sw600dp-land/<screen_name>.xml`:
- Implement a **two-column dual-pane layout**:
  - Use `ConstraintLayout` with a vertical guideline at 50% (`app:layout_constraintGuide_percent="0.5"`), or a horizontal `LinearLayout` with weights (`layout_weight="1"` and `layout_weight="1"`).
  - Left pane: Summary, navigation actions, hero profile, or master list.
  - Right pane: Form fields, editor sections, project feed, or details.
- Move bottom-pinned action bars to a persistent right panel or top toolbar.
- Verify all ViewBinding IDs from Step 1 are preserved.

---

## Step 6: Compose Screen Adaptation

For `@Composable` functions:
1. Accept `windowWidthSizeClass: WindowWidthSizeClass` as a parameter.
2. Branch with `when (windowWidthSizeClass)`:
   - `Compact` → Single-column layout.
   - `Medium` → Single-column wrapped in `Box(modifier = Modifier.widthIn(max = 600.dp))`.
   - `Expanded` → `Row { LeftPane(Modifier.weight(1f)); RightPane(Modifier.weight(1.2f)) }`.
3. Use `MaterialTheme.typography.*` instead of hardcoded text sizing.

---

## Step 7: Output Format & Verification

Execute updates in this strict sequence:
1. Manifest changes (`configChanges`, `windowSoftInputMode`, remove hardcoded orientation).
2. Dimension tokens (`res/values/dimens.xml` and `res/values-sw600dp/dimens.xml`).
3. Base phone layout (`res/layout/`) if needed.
4. Tablet portrait layout (`res/layout-sw600dp/`).
5. Tablet landscape layout (`res/layout-sw600dp-land/`).
6. Kotlin/Compose changes (if applicable).

After making changes, run:
```bash
./gradlew compileDebugKotlin
./gradlew lintDebug
```
Confirm 0 compile errors and zero ViewBinding nullability regressions.
