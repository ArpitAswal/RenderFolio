---
description: Create or refactor an Android screen into a responsive Phone/Tablet layout using XML or Compose
---

1. Load `personas/android-adaptive-screen-agent.md` and `rules/platforms/android-adaptive-ui.md`.
2. Inspect the target screen and determine whether it uses pure XML (with `layout-sw600dp`) or pure Jetpack Compose (`WindowSizeClass`).
3. Refactor the layout to support Compact (phone), Medium (tablet portrait/foldable), and Expanded (tablet landscape) dimensions.
4. Verify all touch targets meet the 48x48dp minimum and that no business logic or ViewModel connections are broken.