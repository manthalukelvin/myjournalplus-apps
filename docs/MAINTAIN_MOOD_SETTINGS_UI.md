# Maintaining Mood & Settings appearance (readable text)

## Goal
Every label, body line, and typed character must stay **clearly visible** on both screens.

## Single source of colors
File: `app/src/main/java/com/myjournalplus/app/ui/theme/AppColors.kt`

| Token | Use for |
|--------|---------|
| `TextPrimary` (`#1A1523`) | Titles, card titles, **typed input text** |
| `TextSecondary` (`#4B5563`) | Subtitles, descriptions |
| `TextMuted` (`#6B7280`) | Captions, timestamps only |
| `TextOnPrimary` (white) | Text on purple top bars and primary buttons |
| `FieldBg` (`#F8F7FC`) | Input field fill |
| `CardBg` (white) | Cards |
| `ScreenBg` (`#F5F3FF`) | Screen background |
| `Primary` (`#7B6CFF`) | Bars, buttons, accents |

**Do not** invent new greys for body text. Always import `AppColors`.

## Rules (follow these when editing)

1. **Inputs**
   - `focusedTextColor` / `unfocusedTextColor` = `AppColors.TextPrimary`
   - `focusedContainerColor` / `unfocusedContainerColor` = `AppColors.FieldBg`
   - Also set `textStyle = LocalTextStyle.current.copy(color = AppColors.TextPrimary)`

2. **Top app bars**
   - Background: `AppColors.Primary`
   - Title / icons: `AppColors.TextOnPrimary`
   - Optional subtitle under title: `AppColors.TextOnPrimaryMuted`

3. **Cards on ScreenBg**
   - Card background: `AppColors.CardBg`
   - Title: `TextPrimary`
   - Subtitle: `TextSecondary`

4. **Never** use light grey (`#9CA3AF` or lighter) for main labels on white/light purple.

5. **Buttons**
   - Primary: `containerColor = Primary`, `contentColor = TextOnPrimary`
   - Explicitly set `Text(..., color = TextOnPrimary)` if needed so theme does not override.

## Files to keep in sync

| File | Role |
|------|------|
| `ui/theme/AppColors.kt` | Color tokens only |
| `ui/mood/MoodScreen.kt` | Mood UI (free for all) |
| `ui/mood/MoodViewModel.kt` | Mood data |
| `ui/settings/SettingsScreen.kt` | Settings UI |

## Quick checklist after any UI change
- [ ] Open Mood → chip labels readable, note field text dark while typing
- [ ] Open Settings → every row title/subtitle readable, logout button white text
- [ ] Top bars: white title on purple
- [ ] No pure `#FFFFFF` text on `#F5F3FF` or white cards

## Optional: force light theme for these screens
If the device is in dark mode and Material3 flips colors, keep Scaffold `containerColor = AppColors.ScreenBg` and card/field colors from `AppColors` (as already done). That overrides system dark for these surfaces so text stays controlled.
