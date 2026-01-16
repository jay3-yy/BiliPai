# iOS-First UI Style Audit and Improvement Plan

## Goal
Maximize iOS-native feel across the app by consolidating design tokens, replacing
Material-styled components with iOS-aligned equivalents, and standardizing
interaction patterns to match iOS behavior.

## Current State Summary
- Token split: `core/theme/*` defines iOS-like colors/typography/shapes, while
  `core/ui/DesignSystem.kt` defines a parallel Bili-branded palette and spacing.
- Component mix: iOS-styled components exist (lists, dialogs, sheets, large
  title bars), but many screens still use Material defaults or hybrids.
- Interaction mismatch: residual Material ripple/elevation and inconsistent
  navigation/transition behavior dilute the iOS feel.

## Observations (by area)
### Tokens
- Colors are mostly iOS-like in `core/theme/Color.kt`, but DesignSystem colors
  reintroduce Bili-centric tones that conflict with iOS gray semantics.
- Typography in `core/theme/Type.kt` approximates iOS sizes, but usage is not
  enforced consistently across components.
- Shapes in `core/theme/Shape.kt` provide iOS corner radii, but usage is uneven,
  and continuous corners are not consistently simulated.

### Components
- Lists and settings components (`core/ui/components/iOSListComponents.kt`) are
  close to iOS grouped list patterns but need stricter spacing and separator
  rules to be consistent across screens.
- Dialogs and sheets (`core/ui/iOSDialogComponents.kt`,
  `core/ui/iOSSheetComponents.kt`) are iOS-inspired but still rely on Material
  primitives with default behavior.
- Video detail and player controls (`feature/video/screen/VideoDetailScreen.kt`,
  `feature/video/ui/section/VideoPlayerSection.kt`) show a mix of iOS visuals
  and Material interaction density.

## iOS-First Direction (Selected)
Focus on strict iOS visual + interaction parity:
- Single source of truth for tokens (colors, spacing, typography, shapes).
- Replace Material components with iOS analogs rather than skinning them.
- Match iOS interaction physics (press scale, subtle shadows, hairline dividers).

## Component Replacement Map
- Grouped lists: `iOSListComponents.kt` becomes default for settings/profile.
- Dialogs: `iOSDialogComponents.kt` replaces generic Material dialogs.
- Sheets: `iOSSheetComponents.kt` evolves toward Action Sheet patterns.
- Navigation: `iOSLargeTitleBar.kt` standardizes large-title behavior and blur.
- Cards/Buttons: `IOSModifiers.kt` standardizes press + haptic + shadow.

## Phased Plan
### Phase 0: Token Unification (1-2 weeks)
- Merge or deprecate DesignSystem token values in favor of `core/theme/*`.
- Enforce iOS gray scale as background and separator system.
- Standardize typography usage with explicit token mapping.

### Phase 1: Core Components (2-3 weeks)
- Replace list items, dialogs, sheets, and navigation bars with iOS versions.
- Remove Material ripple/elevation where iOS patterns apply.
- Align hairline dividers and grouped list spacing rules.

### Phase 2: Key Screens (2-4 weeks)
- Migrate video detail and player settings to iOS patterns.
- Reduce visual density in player controls to iOS-like spacing.
- Verify bottom sheets and modals behave like iOS Action Sheets.

## Risks and Mitigations
- Mixed behavior in legacy screens: add a lint/checklist for iOS tokens.
- Typography drift: enforce usage via composable wrappers.
- Transition inconsistency: define standard motion specs (push, fade, sheet).

## Verification
- Spot-check Home, Video Detail, and Settings for token consistency.
- Ensure dialog/sheet sizes, typography, and separators match iOS reference.
- Validate interaction feedback (press scale, haptics, no ripple).
