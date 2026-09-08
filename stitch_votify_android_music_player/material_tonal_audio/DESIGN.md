---
name: Material Tonal Audio
colors:
  surface: '#141218'
  surface-dim: '#141218'
  surface-bright: '#3b383f'
  surface-container-lowest: '#0f0d13'
  surface-container-low: '#1d1b21'
  surface-container: '#211f25'
  surface-container-high: '#2b292f'
  surface-container-highest: '#36343a'
  on-surface: '#e7e0e9'
  on-surface-variant: '#cac4d0'
  inverse-surface: '#e7e0e9'
  inverse-on-surface: '#322f36'
  outline: '#948f9a'
  outline-variant: '#49454f'
  surface-tint: '#d0bcff'
  primary: '#e9ddff'
  on-primary: '#37265e'
  primary-container: '#d0bcff'
  on-primary-container: '#594983'
  inverse-primary: '#665590'
  secondary: '#ccc2dc'
  on-secondary: '#332d41'
  secondary-container: '#4a4359'
  on-secondary-container: '#bab1ca'
  tertiary: '#ffd9e3'
  on-tertiary: '#492532'
  tertiary-container: '#efb8c8'
  on-tertiary-container: '#704654'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e9ddff'
  primary-fixed-dim: '#d0bcff'
  on-primary-fixed: '#210f48'
  on-primary-fixed-variant: '#4d3d76'
  secondary-fixed: '#e9def9'
  secondary-fixed-dim: '#ccc2dc'
  on-secondary-fixed: '#1e182b'
  on-secondary-fixed-variant: '#4a4359'
  tertiary-fixed: '#ffd9e3'
  tertiary-fixed-dim: '#efb8c8'
  on-tertiary-fixed: '#31111d'
  on-tertiary-fixed-variant: '#633b48'
  background: '#141218'
  on-background: '#e7e0e9'
  surface-variant: '#36343a'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 44px
    fontWeight: '700'
    lineHeight: 52px
    letterSpacing: -0.02em
  display-md:
    fontFamily: Inter
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 44px
    letterSpacing: -0.015em
  headline-lg:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: 0em
  headline-sm:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0em
  title-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 24px
    letterSpacing: 0em
  title-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 22px
    letterSpacing: 0.01em
  title-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.015em
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.015em
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.04em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.05em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  space-2: 0.125rem
  space-4: 0.25rem
  space-8: 0.5rem
  space-12: 0.75rem
  space-16: 1rem
  space-20: 1.25rem
  space-24: 1.5rem
  space-32: 2rem
  space-40: 2.5rem
  space-48: 3rem
  space-64: 4rem
  margin-screen: 1rem
  margin-screen-lg: 1.25rem
  gutter-grid: 0.75rem
  bar-docked-height: 4rem
  bar-navigation-height: 4.5rem
  safe-area-bottom: 2rem
---

## Brand & Style

This design system delivers an expressive, immersive, and tactile mobile audio streaming experience built natively on Android Material 3 (Material You) foundations. Rooted in deep OLED dark modes and dynamic tonal luminance, the interface recedes to spotlight artwork, waveforms, and media content while offering effortless thumb navigation.

Targeted at discerning audiophiles and modern mobile digital natives, the atmosphere is fluid, focused, and acoustically responsive. The aesthetic combines **Corporate / Modern (Material 3)** with tactile surface tiers:
- **Tonal Depth over Elevation Shadows:** Layers are differentiated via step-ups in surface luminance rather than ambient blur drop-shadows.
- **Micro-interactions:** Pill chips, rounded control panels, and pill navigation indicators deliver clear feedback with haptic-aligned state changes.
- **OLED Native & Contrast Tuned:** Optimized for Android displays with full gesture bar compliance and edge-to-edge transparent system integration.

## Colors

The palette is engineered strictly around Material 3 dark tonal palettes with primary deep purple tinting (`#6750A4` baseline mapped to dark roles).

### Token Roles & Specs
- **Primary / Accent:**
  - `md.sys.color.primary`: `#D0BCFF` (High-contrast lilac for active iconography, highlighted pills, playback scrubbers)
  - `md.sys.color.on-primary`: `#381E72` (Deep contrast text/iconography over primary fills)
  - `md.sys.color.primary-container`: `#4F378B` (Subdued container for interactive selections and active pill chips)
  - `md.sys.color.on-primary-container`: `#EADDFF` (Legible text atop primary containers)
- **Surfaces & Tonal Stack:**
  - `md.sys.color.surface`: `#141218` (Base root background, deep dark canvas)
  - `md.sys.color.surface-dim`: `#110F14` (Bottom bar background underlays)
  - `md.sys.color.surface-container-low`: `#1D1B20` (Subtle groupings, background grouping panels)
  - `md.sys.color.surface-container`: `#211F26` (Standard cards, docked mini-player surface, search inputs)
  - `md.sys.color.surface-container-high`: `#2B2930` (Nested interactive items, list sections, active search bar)
  - `md.sys.color.surface-container-highest`: `#36343B` (Unselected pill chips, elevated playback control trays)
- **Content & Typography:**
  - `md.sys.color.on-surface`: `#E6E0E9` (High-emphasis text & prominent glyphs, 100% opacity)
  - `md.sys.color.on-surface-variant`: `#CAC4D0` (Medium-emphasis metadata, artist subtitles, inactive tabs)
  - `md.sys.color.outline`: `#938F99` (Subtle dividers, inactive borders)
  - `md.sys.color.outline-variant`: `#49454F` (Card borders, list item separators)

## Typography

The typography uses Inter across all scales, optimized for legibility in Cyrillic and Latin script environments. The Cyrillic characters inherit the neutral proportions and clear terminal apertures needed for crowded music metadata strings.

### Hierarchy Guidelines
- **Section Group Headers (e.g., "ОСНОВНЫЕ", "ВНЕШНИЙ ВИД"):** Set in `label-sm` or `label-md` with uppercase transformation, bold/semibold weight, letter-spacing of `0.08em`, and colored with `on-surface-variant` (`#CAC4D0`).
- **Player Track Title (Expanded & Mini):** `headline-md` (expanded) or `title-sm` (mini-player) in weight `600`, colored with `on-surface` (`#E6E0E9`).
- **Artist & Album Metadata:** `body-sm` or `body-md` in `on-surface-variant`, strict single-line truncation with ellipsis for constrained mobile rows.
- **Chip Labels & Tab Switchers (e.g., "Всё", "Треки", "Плейлисты"):** Set in `label-md` or `title-sm` with vertical optical alignment inside pill containers.

## Layout & Spacing

Layout adheres to an 8-point spatial grid system (with 4px increments for micro-alignments such as chip padding and icon badge offsets).

### Screen Boundaries & Mobile Frame
- **Horizontal Screen Margins:** Fixed `16px` on standard viewport widths (< 400px), expanding to `20px` on wider phone devices.
- **Top Inset / Android Status Bar:** Transparent `40px` to `48px` safe area padding. App header icons and search elements align beneath the hardware punch-hole.
- **Bottom Stacking Hierarchy:**
  1. **Content Scroll Layer:** Bottom scroll padding of `144px` (`mini-player 64px` + `navigation bar 72px` + `system gesture inset 8px`) ensuring the lowest content can clear the docked overlays.
  2. **Floating / Docked Mini-Player:** `64px` total height, floating with `8px` side margins or snapped dock-width directly resting atop the navigation bar.
  3. **Material 3 Navigation Bar:** `72px` height + Android system gesture bar inset (`16px-24px`), with full transparent navigation pill compatibility.
- **Grid Layouts:** Media grids (playlists, album covers) use a 2-column or 3-column fluid grid with `12px` gutters and square `1:1` aspect-ratio bounding boxes.

## Elevation & Depth

This design system rejects fuzzy light-drop shadows in dark mode. Depth is expressed purely through **tonal layering**, surface luminescence, and translucent backdrops.

### Surface Tiers & Depth Strategy
- **Layer 0 (Base Canvas):** `#141218` (Surface). All non-elevated scrollable content rests directly on this layer.
- **Layer 1 (Recessed/Sub-panels):** `#1D1B20` (Surface Container Low). Used for full-width grouped settings lists or inset filter backgrounds.
- **Layer 2 (Contained Cards & Media Blocks):** `#211F26` (Surface Container). Standard cards, recent playback tiles, and library collections.
- **Layer 3 (Floating Controls & Navigation Bar):** `#2B2930` (Surface Container High). Bottom navigation bar and search bar inputs.
- **Layer 4 (Mini-Player & Modal Trays):** `#36343B` (Surface Container Highest) or dynamic tint (`#4F378B` primary container variant). High tactile focus.
- **Floating Chrome & Blurs:**
  - Docked Mini-player uses an ultra-high opacity backdrop blur (`backdrop-blur-xl`, `rgba(33, 31, 38, 0.88)`) with a subtle `1px` low-contrast outline (`rgba(255, 255, 255, 0.08)`) on top borders.
  - Active pressed states rely on the standard Material ripple in `md.sys.color.on-surface` at `0.12` alpha.

## Shapes

The interface embraces organic, pill-forward Material 3 geometry.

### Radius Assignments
- **Pill Shapes (`rounded-full` / `9999px`):** Filter chips, tab switches, bottom navigation indicator pills, play/pause circular actions, and the full-screen player playback control tray.
- **Extra Large Shapes (`28px` - `32px` / `rounded-3xl`):** Mini-player floating container, primary modal bottom-sheets, full player sheet headers.
- **Large Shapes (`16px` - `24px` / `rounded-2xl`):** Album and playlist artwork cards, grouped settings panels, search input containers, featured hero blocks ("Любимые").
- **Small Shapes (`8px` - `12px` / `rounded-lg`):** Small track thumbnails inside table/list rows, song item context menus, floating badges.
- **Record Disc Visuals:** Pure circle `rounded-full` (`50%`) with an inset center cutout (`12px`) for vinyl and avatar elements.

## Components

### Filter & Category Chips
- **Container:** Height `36px`, shape `rounded-full`, padding `0 16px`.
- **Selected State:** Filled with `#FFFFFF` or `md.sys.color.primary` (`#D0BCFF`), text and icon colored with `#000000` or `md.sys.color.on-primary` (`#381E72`), font `label-lg`.
- **Unselected State:** Filled with `surface-container` (`#211F26`) or `surface-container-highest` (`#36343B`), text in `on-surface-variant` (`#CAC4D0`), border optional `1px solid rgba(255, 255, 255, 0.04)`.

### Material 3 Bottom Navigation Bar
- **Bar Container:** Fixed height `72px` + safe bottom inset, background `surface-container-low` (`#1D1B20`), zero top shadow, optional `1px` subtle top border in `outline-variant` at 20% opacity.
- **Destination Item:** Centered column layout with label and icon.
- **Active Indicator:** Oval pill (`64px` width × `32px` height), `rounded-full`, fill `primary-container` (`#4F378B`) or pure white highlight; active icon colored with `on-primary-container` (`#EADDFF`) or dark inverted.
- **Inactive Item:** Icon with color `on-surface-variant` (`#CAC4D0`), label in `label-sm` matching icon color.

### Docked Mini-Player
- **Geometry:** Height `64px`, floating with `8px` left/right margins and `8px` bottom offset above the navigation bar, radius `rounded-2xl` (20px).
- **Surface:** `surface-container-highest` (`#36343B`) or tinted container with `backdrop-filter: blur(16px)`.
- **Internal Layout:**
  - Left: Thumbnail `44px × 44px`, radius `rounded-xl` or circular record art.
  - Middle: Track title (`title-sm`, weight `600`, single line) stacked above artist subtitle (`body-sm`, `on-surface-variant`).
  - Right Actions: Favorite icon toggle (`24px`, heart icon outline / filled `#D0BCFF` when liked) + circular primary play/pause button (`36px × 36px`, filled `#FFFFFF` or `#D0BCFF`, icon `#141218`).
  - Edge: Built-in 2px bottom progress bar along the container floor colored with `primary` (`#D0BCFF`).

### List Items & Grouped Settings Containers
- **Group Wrapper:** Radii `rounded-3xl` (`24px`), fill `surface-container-low` (`#1D1B20`) or `surface-container` (`#211F26`), padding `8px 16px`.
- **Track Row:** Height `56px`, leading thumbnail (`44px` with `rounded-xl`), title & artist column, trailing options button (3-dot menu glyph).
- **Settings Row:** Height `52px`, leading icon with subtle circular background (`surface-container-high`), setting title (`title-md`), trailing chevron (`>` in `on-surface-variant`). Separated by `1px` border `outline-variant` (`rgba(255, 255, 255, 0.05)`).

### Search Bar & Inputs
- **Search Bar:** Height `48px`, background `surface-container` (`#211F26`), shape `rounded-full`, left-aligned search glyph (`#CAC4D0`), placeholder text ("Поиск песен, артистов, альбомов") in `body-md` `on-surface-variant`.
- **Trailing Action:** Clear cross or audio wave/voice search icon aligned right.

### Full Player Sheet Controls
- **Scrubber Slider:** 4px thickness active bar (`#FFFFFF` or `#D0BCFF`), 2px inactive bar (`rgba(255, 255, 255, 0.2)`), thumb knob `12px` circle with subtle halo on drag.
- **Action Dock (Play/Pause/Skip):** Large central Play/Pause circle `64px × 64px` filled with high-contrast white `#FFFFFF`, glyph `#141218`. Previous/Next buttons `40px` glyphs in `on-surface`. Shuffle and Repeat switches in `on-surface-variant` toggling to `primary` when activated.