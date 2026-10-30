# Adaptive Android UI Engineering Rules (XML & Jetpack Compose)

> **Activation**: Apply whenever designing, refactoring, or reviewing responsive layouts across Phones, Foldables, Tablets, and ChromeOS in XML or Compose.

## 1. Window Size Class Envelopes (Compact, Medium, Expanded)
Follow the official Android Window Size Class breakpoints based on available window width and height:
- **Compact (`width < 600dp` OR `height < 480dp`)**:
  - Standard phone portrait or compact window.
  - Single vertical column; navigation via Bottom Navigation Bar or standard Toolbar.
  - **Landscape Phone Guard**: When `height < 480dp` (even if width > 600dp), treat the layout as Compact. Collapse tall headers, use `NestedScrollView` / `verticalScroll`, and prevent vertical truncation.
- **Medium (`600dp <= width < 840dp` AND `height >= 480dp`)**:
  - Small tablets in portrait, unfolded foldables, and large unfolded inner screens.
  - Center forms and cards within a maximum content width (e.g., `maxWidth = 600dp`) so fields and text do not stretch edge-to-edge.
  - Navigation via Navigation Rail or Drawer.
- **Expanded (`width >= 840dp` AND `height >= 480dp`)**:
  - Tablets in landscape, desktop, and ChromeOS windowed environments.
  - Multi-pane composition: list-detail layouts, side-by-side hero and form panels, or permanent navigation drawers.

---

## 2. Implementation Guide: Pure XML Layouts
When the screen is built in XML:
1. **Resource Directory Qualifiers**:
   - Store base phone layouts in `res/layout/`.
   - Store tablet and wide-screen variants in `res/layout-sw600dp/` (Smallest Width ≥ 600dp).
   - Store landscape-specific tweaks in `res/layout-land/` or `res/layout-sw600dp-land/`.
2. **Constraint-Based Elasticity**:
   - Use `ConstraintLayout` as the root layout.
   - Use percent-based guidelines (`app:layout_constraintGuide_percent`), dimension constraints (`0dp` / `MATCH_CONSTRAINT`), and constraint chains rather than hardcoded pixel/dp widths.
   - Prevent horizontal stretching on tablets by setting `app:layout_constraintMaxWidth="600dp"` on centered containers.
3. **Scroll & Keyboard Safety**:
   - Wrap vertical content in `NestedScrollView` with `android:fillViewport="true"`.
   - Set `android:windowSoftInputMode="adjustResize"` in `AndroidManifest.xml` so layouts resize cleanly when the software keyboard opens.
4. **Touch Targets**:
   - Keep all interactive controls (`Button`, `ImageButton`, list items) at a minimum of `48x48dp` touch area (`android:minWidth="48dp"`, `android:minHeight="48dp"`).

---

## 3. Implementation Guide: Pure Jetpack Compose
When the screen is built in Jetpack Compose:
1. **Window Size Classes**:
   - Read size classes using `calculateWindowSizeClass(activity)` from `androidx.compose.material3.windowsizeclass:window-size-class`.
   - Pass `WindowWidthSizeClass` and `WindowHeightSizeClass` down to screen-level composables.
2. **Adaptive Layout Switching**:
   - `WindowWidthSizeClass.Compact`: Standard single-column `Column` + `LazyColumn`.
   - `WindowWidthSizeClass.Medium`: Centered content using `Modifier.widthIn(max = 600.dp)`.
   - `WindowWidthSizeClass.Expanded`: Two-pane `Row` (`SupportingPaneScaffold`, `ListDetailPaneScaffold`, or custom two-pane `Row` with weighted modifiers).
3. **Avoid Fixed Dimensions**:
   - Do not use hardcoded pixel math. Calculate spacing using Material 3 spacing tokens or `Modifier.weight(1f)`.
   - Wrap dynamic content flows in `Modifier.verticalScroll(rememberScrollState())` to survive landscape orientation and keyboard insets (`WindowInsets.ime`).