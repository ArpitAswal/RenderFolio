# Universal Adaptive UI Rules

> **Activation**: Apply to any platform (Android, iOS, Flutter, Web) whenever designing, refactoring, or reviewing screens for multiple screen sizes and form factors.

---

## 1. The 3-Tier Viewport Progression Model

Every responsive screen must support three core viewport states:

| Viewport Tier | Typical Device & Orientation | Layout Structure | Max Content Width |
| :--- | :--- | :--- | :--- |
| **Tier 1: Compact (Phone)** | Phone portrait & phone landscape | Single-column vertical stream | Full width (minus 16–24dp side padding) |
| **Tier 2: Medium (Tablet Portrait / Foldable Inner)** | Tablet held vertically (600–839dp), unfolded foldable | Centered single column with expanded margins | Bounded to **600dp** (forms) or **720dp** (reading feeds) |
| **Tier 3: Expanded (Tablet Landscape / Desktop)** | Tablet held horizontally (≥840dp), desktop window | Multi-column / Dual-pane (50/50 split, sidebar + content) | Two panes side-by-side filling the canvas |

### Landscape Phone Guard (Critical Rule)
When a device is held in landscape but its **available height is under 480dp / 500dp** (typical for phones rotated sideways), it must **never** be treated as a tablet—even if its width exceeds 600dp:
- Keep the **single-column layout**.
- Collapse or shrink tall hero headers and banners.
- Ensure the root container scrolls vertically so no buttons or input fields are clipped.

---

## 2. Touch Target Safety
- Every interactive element (button, icon, chip, list row, menu item) must have a minimum interactive touch target area of **48×48dp** (Android/Web) or **44×44pt** (iOS), regardless of its visual size.
- Do not shrink the touch target to match a small visual icon (e.g., a 20dp icon). Use padding, insets, or platform-native minimum size modifiers to keep the 48dp tappable area without altering the visual artwork.

---

## 3. Spacing Tokens — Never Hardcode Pixel Dimensions
- Define all spacing and dimension values in a single centralized resource file (`res/values/dimens.xml` for Android, design tokens for Web/Flutter).
- Reference tokens by semantic names, not raw numerical values. This enables automatic platform overrides per screen size (e.g., `res/values-sw600dp/dimens.xml`) without modifying layout files.
- Universal Spacing Scale:
  - `spacing_xs` = `4dp`
  - `spacing_sm` = `8dp`
  - `spacing_md` = `16dp`
  - `spacing_lg` = `24dp`
  - `spacing_xl` = `32dp`
  - `spacing_xxl` = `48dp`

---

## 4. Scroll Safety — Prevent Content Truncation
- Any screen whose content can exceed the visible viewport **must** be wrapped in a scroll container (`NestedScrollView` for Android XML, `Modifier.verticalScroll` for Compose, `SingleChildScrollView` for Flutter).
- Always account for keyboard insets, system navigation bars, and top status bars reducing visible vertical height.
- Set the scroll container to fill the viewport (`android:fillViewport="true"` in Android) so that short content centers naturally while long content scrolls seamlessly.

---

## 5. Content Width Capping & Typography Line Length
- On Medium and Expanded screens, **never let form fields, cards, or body text stretch edge-to-edge across the screen**.
- **The 45–75 Character Rule**: Optimal reading line length is 45 to 75 characters per line. Text wider than 75 characters causes reader fatigue and eye tracking loss.
- Cap content widths:
  - Forms, login dialogs, card editors: **Max 600dp**, centered horizontally.
  - Detail panels, visualizers, article feeds: **Max 720dp**, centered horizontally.
- Center capped content using horizontal gravity or bias; never leave a narrow form left-aligned against a vast empty screen.

---

## 6. Device Orientation Freedom
- Never hardcode a single fixed orientation (e.g., portrait-only) globally across an entire application if tablet viewports are supported.
- Tablets are frequently used in keyboard docks and landscape stands. Locking orientation breaks the user experience.
- If phone portrait locking is required by product design, lock it **conditionally in code for small screens only** (`< 600dp`), keeping tablets free to rotate.
