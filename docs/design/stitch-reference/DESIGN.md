---
name: Jean Calcul Lumina
colors:
  surface: '#101415'
  surface-dim: '#101415'
  surface-bright: '#363a3b'
  surface-container-lowest: '#0b0f10'
  surface-container-low: '#181c1d'
  surface-container: '#1c2021'
  surface-container-high: '#272b2c'
  surface-container-highest: '#313536'
  on-surface: '#e0e3e4'
  on-surface-variant: '#c6c6cb'
  inverse-surface: '#e0e3e4'
  inverse-on-surface: '#2d3132'
  outline: '#909095'
  outline-variant: '#46474b'
  surface-tint: '#c6c6cc'
  primary: '#e2e2e8'
  on-primary: '#2f3035'
  primary-container: '#c6c6cc'
  on-primary-container: '#515257'
  inverse-primary: '#5d5e63'
  secondary: '#a4c9ff'
  on-secondary: '#00315d'
  secondary-container: '#224a79'
  on-secondary-container: '#96bbf0'
  tertiary: '#e9ddff'
  on-tertiary: '#3c0091'
  tertiary-container: '#d0bcff'
  on-tertiary-container: '#612aca'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e2e2e8'
  primary-fixed-dim: '#c6c6cc'
  on-primary-fixed: '#1a1c20'
  on-primary-fixed-variant: '#45474c'
  secondary-fixed: '#d3e3ff'
  secondary-fixed-dim: '#a4c9ff'
  on-secondary-fixed: '#001c39'
  on-secondary-fixed-variant: '#1f4876'
  tertiary-fixed: '#e9ddff'
  tertiary-fixed-dim: '#d0bcff'
  on-tertiary-fixed: '#23005c'
  on-tertiary-fixed-variant: '#5516be'
  background: '#101415'
  on-background: '#e0e3e4'
  surface-variant: '#313536'
  surface-glass: rgba(255, 255, 255, 0.03)
  border-glass: rgba(255, 255, 255, 0.15)
  ambient-violet: rgba(138, 91, 245, 0.15)
  ambient-blue: rgba(1, 100, 180, 0.1)
  error-alert: '#ffb4ab'
typography:
  display-lg:
    fontFamily: Hanken Grotesk
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-sm:
    fontFamily: Geist
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 8px
  container-padding: 24px
  gutter: 16px
  glass-margin: 12px
  section-gap: 48px
---

## Brand & Style

The brand identity of "Jean Calcul" is rooted in the "Futuristic Executive" aesthetic—a sophisticated blend of high-tech artificial intelligence and premium, calm productivity. It aims to evoke a sense of advanced intelligence that is both powerful and approachable.

The design style is **Glassmorphism**, characterized by translucent surfaces, deep backdrop blurs, and vibrant ambient light leaks. The interface feels like a high-end command center floating in a digital void. It utilizes subtle animations (pulsing dots, shimmering text) to imply "life" within the AI system, while maintaining a strict, clean layout to ensure professional utility.

## Colors

The palette is anchored in a deep Obsidian background (`#101415`), creating a high-contrast foundation for "Light Leak" accents.

- **Primary & Secondary:** Cool silvers and soft cerulean blues provide a professional, tech-forward feel.
- **Tertiary:** A deep violet is used for "Active State" indicators and ambient glows, signaling the presence of AI.
- **Surface Strategy:** Instead of solid fills, the system relies on tiered transparency. Surfaces use `surface-glass` with varying levels of opacity and backdrop blurs (`24px`) to create depth without clutter.
- **Gradients:** Animated radial gradients in the background provide a sense of atmospheric depth.

## Typography

The typographic hierarchy uses three distinct typefaces to separate roles:
1. **Hanken Grotesk (Display/Headlines):** High-impact, geometric, and modern. Large display titles utilize a "Shimmer" gradient effect to draw the eye.
2. **Inter (Body):** Maximum legibility for status updates and descriptions.
3. **Geist (Labels/Technical):** A monospaced-adjacent feel used for "System Status" and "Meta-data" to reinforce the technical nature of the assistant.

Text should follow a "Shimmer" effect for primary greetings, using a linear gradient between `@on-surface` and `@secondary`.

## Layout & Spacing

The system uses a **Bento Grid** philosophy for dashboarding, where information is encapsulated in discrete modules of varying sizes.

- **Breakpoints:**
  - **Mobile (<768px):** Single column with 24px horizontal margins. Navigation moves to a fixed bottom pill-bar.
  - **Desktop (>768px):** 12-column fluid grid within a 1280px (`max-w-7xl`) container. Top-level navigation and floating chat bar at the bottom.
- **Rhythm:** An 8px base unit controls all internal padding and gaps. Sections are separated by a generous 48px gap to maintain a minimalist, airy feel despite the dark theme.

## Elevation & Depth

Hierarchy is established through **optical layering** rather than traditional shadows:
- **Level 0 (Background):** Solid `#101415` with pulse-animated radial gradients.
- **Level 1 (Cards):** `glass-card` with 3% white opacity, 24px backdrop-blur, and a 15% opacity white top-border. This simulates a light source hitting the "edge" of the glass.
- **Level 2 (Interaction):** On hover, glass cards increase to 5% opacity and gain a 20% opacity `ambient-violet` outer glow (shadow) to simulate physical lift.
- **Overlays:** Navigation bars use a much higher blur (`3xl`) and 10% surface opacity to distinguish them from content cards.

## Shapes

The shape language is "Soft-Modern":
- **Standard Cards/Buttons:** `0.75rem` (xl) corner radius.
- **Interactive Pills/Nav:** Full rounding (`rounded-full`) for navigation bars and status indicators to emphasize the "capsule" or "assistant" metaphor.
- **Icons:** Material Symbols (Outlined) with a weight of 400 for a crisp, thin-line aesthetic.

## Components

### Buttons & Interaction
- **Action Buttons:** Large cards with icon/title/description clusters. Hover states should trigger a 110% icon scale and a subtle border color shift toward the primary/secondary hue.
- **Quick Launch Bar:** A centered, floating, pill-shaped input field with high transparency (`40%`) and a white microphone button for focal contrast.

### Cards (The Bento Unit)
- **Standard Card:** Padded (24px), glass-textured, with a subtle internal glow in the top-right corner using the secondary or tertiary color at very low opacity.
- **Priority Card:** Includes a 2px solid left-border (e.g., `error-alert`) to break the glass texture and signal urgency.

### Status Indicators
- **Pulse Dot:** Small circles (10px) with a `pulseDot` keyframe animation (expanding shadow) used next to labels to indicate "live" system activity.

### Navigation
- **Top Bar:** Transparent, utilizing content-padding for alignment.
- **Bottom Mobile Nav:** A floating pill with 15% border opacity and extreme blur (`backdrop-blur-3xl`), detached from the screen edges by `glass-margin`.
