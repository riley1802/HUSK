# HUSK UI Refinement & Customization Plan

## Current State

**Existing customization (all in `SettingsDialog.kt` as a modal):**
- AMOLED black toggle → `ThemeSettings.amoledMode`
- 8 accent color presets → `ThemeSettings.accentColorArgb`
- Font scale (S/M/L/XL) → `ThemeSettings.fontScale`
- Chat density (Compact/Comfortable/Spacious) → `ThemeSettings.chatDensity`

**Persistence:** Proto DataStore (`settings.proto` → `Settings` message, fields 11–14). Read/written via `DataStoreRepository` interface + `DataStoreRepositoryImpl`. Loaded at app start in `GalleryApplication.onCreate()` → `ThemeSettings.loadFrom(repo)`.

**Theme pipeline:** `ThemeSettings` (reactive `mutableStateOf`) → `GalleryTheme` composable reads values → builds `darkColorScheme` + `CustomColors` + `ChatDisplayConfig` → provided via `CompositionLocalProvider`.

---

## Planned Changes

### 1. Promote Settings → Full Appearance Screen

**Why:** The current `SettingsDialog` is a cramped modal that mixes appearance, HF token management, RAG toggles, licenses, and ToS. Appearance deserves its own full-screen destination with room for a live preview strip.

**Files touched:**
- `GalleryNavGraph.kt` — add `ROUTE_APPEARANCE` constant and `composable` block
- `HomeScreen.kt` — add `onAppearanceClicked` callback, wire drawer item or settings gear to navigate
- `SettingsDialog.kt` — remove the entire "Appearance" section, replace with a single "Appearance" row that navigates to the new screen
- **New file:** `ui/appearance/AppearanceScreen.kt`

**AppearanceScreen layout (top → bottom):**

```
┌─────────────────────────────────┐
│ ← Appearance            (topbar)│
├─────────────────────────────────┤
│ ┌─ Live Preview ──────────────┐ │
│ │  Mini chat mockup:          │ │
│ │  [user bubble]              │ │
│ │        [agent bubble]       │ │
│ │  Shows current accent,      │ │
│ │  bubble style, density,     │ │
│ │  font size in real-time     │ │
│ └─────────────────────────────┘ │
│                                 │
│ ── Colors ──────────────────── │
│ AMOLED black              [sw] │
│ Accent color     ● ● ● ● ● ●  │
│                  ● ●   [+]     │
│                                 │
│ ── Chat ────────────────────── │
│ Bubble style   [Rounded|Sharp| │
│                  Pill]          │
│ Chat density   [Compact|       │
│        Comfortable|Spacious]   │
│ Font size      [S|M|L|XL]     │
│                                 │
│ ── Home ────────────────────── │
│ Greeting style [Contextual|    │
│              Minimal|Off]      │
│ Entrance animations       [sw] │
│                                 │
└─────────────────────────────────┘
```

The live preview section is a non-interactive `Column` inside a bordered card that renders two fake chat bubbles (user + agent) using the current theme values. It recomposes instantly as the user changes any setting below it, giving visual feedback without navigating away.

---

### 2. Custom Accent Color Picker

**Why:** 8 presets is a good start but power users want to dial in their own hue. The existing `deriveAccentMuted()` and `deriveOnAccent()` functions already handle arbitrary colors cleanly.

**Implementation:**
- Add a `[+]` circle at the end of the accent preset row in `AppearanceScreen`
- Tapping it opens a bottom sheet with an HSL hue slider (single horizontal bar, 0–360°) and a saturation/lightness 2D pad
- The selected color runs through `deriveAccentMuted()` to confirm it fits the monochrome aesthetic before committing
- Persisted via the existing `accent_color_argb` proto field (no schema change needed — it already stores arbitrary ARGB)

**Files touched:**
- **New file:** `ui/appearance/HslColorPicker.kt` — the bottom sheet composable
- `AppearanceScreen.kt` — integrate the `[+]` trigger and bottom sheet

**Design constraints:**
- Enforce minimum lightness of ~0.35 and maximum of ~0.80 so the accent is always readable on both the dark background and as `onAccent` text
- Show a small preview chip next to the slider that updates live

---

### 3. Bubble Style Selector

**Why:** The current `MessageBubbleShape` has a hard-coded radius. Exposing this lets users pick between visual styles without changing the underlying chat logic.

**Options:**

| Style | Top corner radius | Bottom corner radius | Description |
|-------|------------------|---------------------|-------------|
| Rounded | 20.dp | 20.dp | Current default — soft, modern |
| Sharp | 6.dp | 6.dp | Tighter, more utilitarian feel |
| Pill | 24.dp | 24.dp | Fully rounded ends, messaging-app style |

**Implementation:**
- New proto enum `BubbleStyle` with values `BUBBLE_STYLE_UNSPECIFIED`, `BUBBLE_STYLE_ROUNDED`, `BUBBLE_STYLE_SHARP`, `BUBBLE_STYLE_PILL`
- New `Settings` field: `BubbleStyle bubble_style = 19;`
- `DataStoreRepository`: add `saveBubbleStyle(style)` / `readBubbleStyle()` interface + impl
- `ThemeSettings`: add `val bubbleStyle = mutableStateOf(BubbleStyle.BUBBLE_STYLE_ROUNDED)`
- `Theme.kt` → `ChatDisplayConfig`: add `val bubbleCornerRadius: Dp` field, computed from the style enum in `buildChatDisplayConfig()`
- `MessageBubbleShape` callers: replace hardcoded `radius` with `MaterialTheme.chatDisplayConfig.bubbleCornerRadius`
- `AppearanceScreen`: segmented button row for the three styles

**Files touched:**
- `settings.proto`
- `DataStoreRepository.kt` (interface + impl)
- `ThemeSettings.kt`
- `Theme.kt`
- `AppearanceScreen.kt`
- All callsites of `MessageBubbleShape` (grep for `MessageBubbleShape(` — likely `MessageBodyText.kt` and any other bubble renderers)

---

### 4. Greeting Style Selector

**Why:** The time-based greeting ("Good morning.", "Still here.") is charming but some users want less personality on the home screen.

**Options:**

| Style | Output |
|-------|--------|
| Contextual | Current behavior — time-based greeting |
| Minimal | Static "Husk." — just the brand |
| Off | No greeting text rendered at all |

**Implementation:**
- New proto enum `GreetingStyle` with values `GREETING_STYLE_UNSPECIFIED`, `GREETING_STYLE_CONTEXTUAL`, `GREETING_STYLE_MINIMAL`, `GREETING_STYLE_OFF`
- New `Settings` field: `GreetingStyle greeting_style = 20;`
- `DataStoreRepository`: `saveGreetingStyle()` / `readGreetingStyle()`
- `ThemeSettings`: `val greetingStyle = mutableStateOf(GreetingStyle.GREETING_STYLE_CONTEXTUAL)`
- `HomeScreen.kt` → `HuskGreeting()`: branch on `ThemeSettings.greetingStyle.value` to pick the text or skip rendering entirely

**Files touched:**
- `settings.proto`
- `DataStoreRepository.kt`
- `ThemeSettings.kt`
- `HomeScreen.kt`
- `AppearanceScreen.kt`

---

### 5. Entrance Animation Toggle

**Why:** The `enableAnimation` flag is already threaded through `HomeScreen`, `HuskGreeting`, `AppTitleGm4`, `HuskIntroText`, and `HuskHub`. But it's currently only controlled by a hardcoded value passed from the nav graph. Letting users disable it is trivial.

**Implementation:**
- New `Settings` field: `bool disable_entrance_animations = 21;`
- `DataStoreRepository`: `saveDisableAnimations(Boolean)` / `readDisableAnimations()`
- `ThemeSettings`: `val disableAnimations = mutableStateOf(false)`
- `GalleryNavGraph.kt` → where `HomeScreen` is invoked: set `enableAnimation = !ThemeSettings.disableAnimations.value`
- `AppearanceScreen`: simple switch toggle

**Files touched:**
- `settings.proto`
- `DataStoreRepository.kt`
- `ThemeSettings.kt`
- `GalleryNavGraph.kt`
- `AppearanceScreen.kt`

---

### 6. Live Preview Component

**Why:** Changing appearance settings blind (then navigating to chat to see the result) is a bad UX. A live preview strip at the top of the Appearance screen gives instant feedback.

**Implementation:**
- Self-contained `@Composable fun AppearanceLivePreview()` inside `AppearanceScreen.kt`
- Renders a small card (height ~160dp) with:
  - Background = current `MaterialTheme.colorScheme.background`
  - A fake user bubble ("How does this look?") using `userBubbleBgColor` + current `bubbleCornerRadius`
  - A fake agent bubble ("Looking good.") using `agentBubbleBgColor` + current `bubbleCornerRadius`
  - Text sized by current `fontSizeScale`
  - Bubble padding/spacing from current `ChatDisplayConfig`
- Because all values are reactive `mutableStateOf`, the preview recomposes instantly when any setting changes — no extra wiring needed

**Files touched:**
- `AppearanceScreen.kt` (internal composable, no separate file needed)

---

## File Change Summary

| File | Action | What changes |
|------|--------|-------------|
| `settings.proto` | Edit | Add `BubbleStyle` enum, `GreetingStyle` enum, fields 19–21 on `Settings` |
| `DataStoreRepository.kt` | Edit | Add 3 new interface methods + 3 implementations (bubble style, greeting style, disable animations) |
| `ThemeSettings.kt` | Edit | Add 3 new `mutableStateOf` fields + load them in `loadFrom()` |
| `Theme.kt` | Edit | Add `bubbleCornerRadius` to `ChatDisplayConfig`, compute in `buildChatDisplayConfig()` |
| `HomeScreen.kt` | Edit | Branch `HuskGreeting` on greeting style, add `onAppearanceClicked` param, wire drawer |
| `SettingsDialog.kt` | Edit | Rip out Appearance section, replace with single "Appearance →" row |
| `GalleryNavGraph.kt` | Edit | Add `ROUTE_APPEARANCE` constant + `composable` block, pass `!ThemeSettings.disableAnimations.value` to `enableAnimation` |
| `ui/appearance/AppearanceScreen.kt` | **New** | Full appearance screen with live preview, all controls |
| `ui/appearance/HslColorPicker.kt` | **New** | Bottom sheet HSL color picker composable |
| `MessageBubbleShape` callsites | Edit | Replace hardcoded radius with `chatDisplayConfig.bubbleCornerRadius` |

**Proto field numbers (next available after 18):**
- `19` → `BubbleStyle bubble_style`
- `20` → `GreetingStyle greeting_style`
- `21` → `bool disable_entrance_animations`

---

## Implementation Order

1. **Proto schema** — add enums + fields (triggers codegen, everything else depends on this)
2. **DataStoreRepository** — interface + impl for new fields
3. **ThemeSettings** — new state + `loadFrom()` update
4. **Theme.kt** — `ChatDisplayConfig` gets `bubbleCornerRadius`
5. **HslColorPicker.kt** — standalone composable, no dependencies on other new code
6. **AppearanceScreen.kt** — the main new screen with live preview + all controls
7. **GalleryNavGraph.kt** — add route + animation toggle wiring
8. **HomeScreen.kt** — greeting style branching + appearance navigation
9. **SettingsDialog.kt** — slim down, link to appearance screen
10. **Bubble shape callsites** — swap hardcoded radii for config value

Steps 1–4 are foundational. Steps 5–6 are the bulk of new UI. Steps 7–10 are integration/wiring.

---

## Design Notes

- **Keep the monochrome contract.** The custom color picker enforces lightness/saturation bounds so users can't pick neon green on the dark background. Every custom color still gets muted via `deriveAccentMuted()`.
- **No new dependencies.** The HSL picker is built from raw `Canvas` composables + sliders. No third-party color picker library.
- **Backwards compatible.** All new proto fields default to zero/unspecified, which maps to current behavior (rounded bubbles, contextual greeting, animations enabled). Existing installs won't see any change until they visit the new Appearance screen.
- **The settings dialog stays useful.** It keeps HF token management, RAG toggle, licenses, and ToS — things that don't need a live preview. Appearance just gets its own dedicated space.
