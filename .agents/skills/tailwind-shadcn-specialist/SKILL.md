---
name: tailwind-shadcn-specialist
description: >-
  Enforces professional, sleek modern UI design systems (Tailwind CSS, Shadcn UI, Radix primitives,
  and native Jetpack Compose M3 design tokens) to prevent generic, cluttered "AI slop" styling.
  Use whenever creating or restyling UI components, screens, modals, or themes.
---

# Premium Design & Design System Specialist

This skill eliminates "AI slop" (cluttered gradients, artificial border outlines, misplaced glows, generic card templates) in favor of high-craft, professional design systems.

## Anti-"AI Slop" Directives:

1. **NO Artificial Borders or Outlines**:
   - Do NOT wrap every container, card, or modal in high-contrast border strokes (`border-white/20` or `1.dp border`).
   - Use subtle surface luminance steps (`#0E0E10` base -> `#141416` container -> `#1C1C1E` elevated) and soft inner padding instead of harsh lines.

2. **NO Cluttered Icon Badges**:
   - Do not wrap icons in artificial colored circles or badges unless representing an active selected state or notification chip.
   - Unselected items must display the icon directly with proper muted tint (`text-muted-foreground` or `#8E8E93`).

3. **Strict Design System Primitives**:
   - **For Web**:
     - Use **Tailwind CSS** utility classes aligned with **shadcn/ui** tokens (`bg-background`, `bg-card`, `text-card-foreground`, `rounded-xl`).
     - Rely on **Radix UI** primitives for accessibility, keyboard navigation, and popovers/sheets.
     - Icons from **lucide-react** at consistent 16px/20px sizes.
   - **For Android Jetpack Compose**:
     - Use standard Material 3 shape and tonal tokens (`RoundedCornerShape(16.dp)`, `Surface`).
     - Consistent typography hierarchy (Regular/Medium weights for body, SemiBold for headers; avoid random bolding).

4. **Authentic Platform Ergonomics**:
   - Respect mobile thumb zones, smooth spring animations, and native system insets.
