---
name: Monochrome Audio
colors:
  surface: '#131313'
  surface-dim: '#131313'
  surface-bright: '#393939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#1E1E1E'
  surface-container-high: '#2A2A2A'
  surface-container-highest: '#383838'
  on-surface: '#e5e2e1'
  on-surface-variant: '#c4c7c8'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#8e9192'
  outline-variant: '#444748'
  surface-tint: '#c6c6c7'
  primary: '#ffffff'
  on-primary: '#2f3131'
  primary-container: '#e2e2e2'
  on-primary-container: '#636565'
  inverse-primary: '#5d5f5f'
  secondary: '#c6c6cb'
  on-secondary: '#2f3034'
  secondary-container: '#46464b'
  on-secondary-container: '#b5b4ba'
  tertiary: '#ffffff'
  on-tertiary: '#2f3131'
  tertiary-container: '#e2e2e2'
  on-tertiary-container: '#636565'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e2e2e2'
  primary-fixed-dim: '#c6c6c7'
  on-primary-fixed: '#1a1c1c'
  on-primary-fixed-variant: '#454747'
  secondary-fixed: '#e3e2e7'
  secondary-fixed-dim: '#c6c6cb'
  on-secondary-fixed: '#1a1b1f'
  on-secondary-fixed-variant: '#46464b'
  tertiary-fixed: '#e2e2e2'
  tertiary-fixed-dim: '#c6c6c7'
  on-tertiary-fixed: '#1a1c1c'
  on-tertiary-fixed-variant: '#454747'
  background: '#131313'
  on-background: '#e5e2e1'
  surface-variant: '#353534'
  pitch-black: '#000000'
  surface-base: '#121212'
  border-subtle: '#2A2A2A'
  border-prominent: '#48484A'
  text-muted: '#8E8E93'
  text-secondary: '#E5E5EA'
  text-primary: '#FFFFFF'
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
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
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
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
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
  gutter-grid: 0.75rem
  margin-screen: 1rem
  margin-screen-lg: 1.25rem
  bar-docked-height: 4rem
  bar-navigation-height: 4.5rem
  safe-area-bottom: 2rem
---

## Brand & Style

This design system delivers a stark, high-contrast, tactile mobile audio streaming interface rooted in dark mode performance. Stripping away decorative chromatic tints and saturated accents, the interface relies on deep pitch blacks, slate and neutral greys, and crisp white highlights. 

The aesthetic synthesizes **Minimalism** with **Modern Material 3** card architecture:
- **Pure Achromatic Hierarchy:** Pure blacks (`#000000`) and low-reflection charcoal surfaces (`#121212`, `#1E1E1E`) frame the UI, allowing media artwork and waveforms to provide the sole dynamic coloration.
- **High-Contrast Precision:** Crisp white (`#FFFFFF`) serves as the primary focal driver for active states, key playback controls, and prominent typographic anchors.
- **Material 3 Geometry:** Expressive pill-shaped chips, nested rounded cards, and floating docks preserve friendly ergonomics while adopting a rigorous, editorial edge.
- **Tactile Balance:** Subtle low-contrast structural borders and tonal stair-stepping replace ambient drop shadows for effortless scanning in low-light environments.

## Colors

The palette is strictly achromatic, eliminating all purple, violet, and chromatic tints. High-contrast white anchors primary interactive actions, while carefully graduated slate and neutral greys define structural depth and typographic prominence.

### Core Color Assignments
- **Primary (`#FFFFFF`):** High-contrast action points, active toggle pills, play/pause controls, slider scrubbers, and primary headlines.
- **Secondary (`#8E8E93`):** Medium-emphasis metadata, inactive icons, passive scrubber tracks, and secondary labels.
- **Neutral (`#121212`):** Primary canvas surface, backing list sheets, and global screen backgrounds.
- **Surface Stack:**
  - `pitch-black` (`#000000`): Inset base backdrops, full-bleed media canvas, and inverted text glyphs.
  - `surface-base` (`#121212`): The global root background.
  - `surface-container` (`#1E1E1E`): Standard Material cards, grouped list sheets, docked mini-player containers, and text fields.
  - `surface-container-high` (`#2A2A2A`): Interactive chips, hovered states, card dividers, and active search bars.
  - `surface-container-highest` (`#383838`): Unselected pill chips, elevated control trays, and tactile borders.
- **Borders & Dividers:**
  - `border-subtle` (`#2A2A2A`): Structural borders around cards and list dividers at low opacity.
  - `border-prominent` (`#48484A`): Active input outlines and chip borders.
- **Text & Glyph Hierarchy:**
  - `text-primary` (`#FFFFFF`): High-emphasis text, track titles, and active icons.
  - `text-secondary` (`#E5E5EA`): Body copy, section headlines, and prominent list row titles.
  - `text-muted` (`#8E8E93`): Secondary subtitles, durations, timestamps, and placeholder labels.

## Typography

Inter provides modern, neutral structural legibility across both Latin and Cyrillic character sets. In a pure monochrome environment, typography assumes extra responsibility for establishing visual weight, contrast, and scan order.

### Application Rules
- **Display & Section Headers:** High-impact titles and featured views use tight letter-spacing (`-0.01em` to `-0.02em`) with weight `700` in pure white (`#FFFFFF`).
- **Group/Category Labels:** Upper-case micro-labels (e.g., settings categories, section markers) use `label-sm` or `label-md` with tracking stretched to `0.06em` in slate grey (`#8E8E93`).
- **Media Information:** Track titles use `title-md` or `headline-sm` in bright off-white (`#E5E5EA`), while artist names and record labels use `body-sm` in `#8E8E93` with single-line ellipsis clipping.
- **Numbers & Durations:** Timestamps, scrubbing counters, and track indices employ tabular figures (`tnum`) to prevent jitter during real-time playback.

## Layout & Spacing

The layout is built upon an 8-point spatial rhythm, with 4px sub-increments applied to compact component padding, pill chips, and icon alignment.

### Grid & Margins
- **Screen Margins:** Standard mobile screens employ `1rem` (16px) horizontal margins, expanding to `1.25rem` (20px) on wide-screen viewports (≥600px).
- **Grid Layouts:** Media grids (albums, artist carousels, playlists) use a 2-column or 3-column fluid grid with `0.75rem` (12px) gutters and 1:1 aspect-ratio bounding containers.

### Mobile Vertical Stacking
To support persistent navigation and playback overlays without obscuring content:
- **Top Safe Area:** 44px to 48px transparent padding clearing device cameras and notches.
- **Bottom Clearance Area:** Minimum scroll offset of `9rem` (144px) beneath content to comfortably clear both the docked mini-player (`4rem` / 64px) and the navigation bar (`4.5rem` / 72px) plus the system gesture bar (`0.5rem` to `1rem`).

## Elevation & Depth

This design system completely omits colored glows, blurred colored shadows, and ambient drop shadows. Visual hierarchy is established via **tonal luminance step-ups** and **crisp hairline containment borders**.

### Depth Layers
- **Level 0 (Root Ground):** `#000000` or `#121212`. The deep black bedrock beneath all views.
- **Level 1 (Recessed/Group Panels):** `#1E1E1E`. Background containment for grouped settings lists and inactive form fields.
- **Level 2 (Material 3 Cards & Tiles):** `#1E1E1E` bounded by a `1px` border in `#2A2A2A`. Used for media tiles, albums, and interactive cards.
- **Level 3 (Elevated Bars & Floating Inputs):** `#2A2A2A` with a `1px` border in `#383838`. Search inputs, floating controls, and dropdown surfaces.
- **Level 4 (Overlay Chrome & Trays):** `#1E1E1E` backed by an active backdrop filter (`backdrop-blur-md` at 85% opacity) and a crisp `1px` top border in `#383838`. Used for docked mini-players, bottom sheets, and navigation bars.

### Interaction States
- **Pressed / Active:** Elements shift one tier up in surface luminance (e.g., from `#1E1E1E` to `#2A2A2A`) with an instant 0.12 opacity white ripple overlay.

## Shapes

The design system uses a pronounced rounded geometry (Level 2), featuring fluid pill containers for controls and generous, soft corners on Material 3 content cards.

### Corner Radius System
- **Pill Shapes (`rounded-full` / 9999px):** Filter chips, segment switches, bottom navigation indicator pills, play/pause action buttons, and scrub thumbs.
- **Extra-Large Cards (`rounded-xl` / 1.5rem to 2rem):** Grouped list containers, modal bottom sheets, floating mini-players, and featured hero banners.
- **Standard Cards (`rounded-lg` / 1rem):** Album artwork containers, playlist items, search inputs, and modal dialogues.
- **Sub-elements (`rounded` / 0.5rem):** List track thumbnails, checkbox and radio hit areas, small badges, and tooltip elements.

## Components

### Buttons
- **Primary Buttons:** Pure white `#FFFFFF` fill with pitch black `#000000` text/glyph. Fully rounded (`rounded-full`), height `44px`, padding `0 24px`. Font `label-lg`.
- **Secondary Buttons:** Surface `#1E1E1E` with a `1px` border in `#383838`. Text in `#E5E5EA`. On hover or press, background illuminates to `#2A2A2A`.
- **Icon Action Buttons:** Circular (`rounded-full`), 40px × 40px. Resting state in `#1E1E1E` with `#FFFFFF` icon; primary playback actions (Play/Pause) invert to `#FFFFFF` fill with `#000000` icon.

### Chips & Segment Controls
- **Filter Chips:** Height `36px`, shape `rounded-full`, padding `0 16px`.
  - *Active / Selected:* Solid `#FFFFFF` fill, `#000000` text, bold `label-md`.
  - *Inactive / Unselected:* Surface `#1E1E1E` fill, `1px` border in `#2A2A2A`, text in `#8E8E93`.
- **Tab Indicators:** Contained in a `#121212` trough; active tab sits inside an animated `#FFFFFF` pill container with dark typography.

### Lists & Grouped Containers
- **Grouped Container:** Full-width or inset card with `rounded-xl` (1.5rem) corners, filled with `#1E1E1E`, framed with a `1px` border in `#2A2A2A`.
- **Track Row:** Height `56px`, leading square thumbnail (`44px`, `rounded-lg`), two-line metadata stack (Title `#E5E5EA`, Artist `#8E8E93`), trailing options button in `#8E8E93`. Rows separated by a `1px` border in `#2A2A2A`.
- **Settings Row:** Height `52px`, leading icon badge inside `#2A2A2A`, title in `#FFFFFF`, trailing chevron or toggle in `#8E8E93`.

### Input Fields & Search Bars
- **Search Bar:** Height `48px`, shape `rounded-full`, fill `#1E1E1E`, border `1px solid #2A2A2A`. Leading search icon in `#8E8E93`, placeholder text in `#8E8E93` (`body-md`), input text `#FFFFFF`.
- **Focused State:** Border brightens to `#FFFFFF` with no chromatic outline.

### Checkboxes, Radios & Switches
- **Checkboxes & Radios:** Size `20px`. Inactive outline in `#8E8E93` with `#121212` interior. Active state filled with `#FFFFFF` and checked/bulleted with `#000000`.
- **Switches:** Track `48px × 28px`, `rounded-full`. Inactive track `#2A2A2A` with `#8E8E93` thumb. Active track `#FFFFFF` with `#000000` thumb.

### Cards
- **Media Cards:** `rounded-lg` (1rem) or `rounded-xl` (1.5rem), surface `#1E1E1E`, border `1px solid #2A2A2A`. Square image frame on top with `rounded-lg` corners, followed by bold title in `#FFFFFF` and subtitle in `#8E8E93`.

### Docked Mini-Player
- **Geometry:** Height `64px`, floating with `8px` lateral margins and `8px` bottom spacing above the navigation bar. Shape `rounded-xl` (1.5rem).
- **Surface:** Backed by `#1E1E1E` (90% opacity) with `backdrop-blur-md` and a `1px` perimeter border in `#383838`.
- **Elements:**
  - Left: Thumbnail `44px × 44px` (`rounded-lg`).
  - Middle: Track title (`#FFFFFF`, `title-sm`, weight `600`) and artist (`#8E8E93`, `body-sm`).
  - Right: Favorite toggle heart (`#FFFFFF` outline or filled solid `#FFFFFF`), circular Play/Pause button (`36px × 36px`, filled `#FFFFFF`, icon `#000000`).
  - Base: 2px progress track along the bottom edge, inactive track `#2A2A2A`, filled progress bar `#FFFFFF`.

### Sliders & Scrubbers
- **Playback Scrubber:** Total track height `4px`, `rounded-full`. Background `#2A2A2A`, active elapsed portion `#FFFFFF`. Drag thumb `12px` solid white circle `#FFFFFF` with zero colored glow.