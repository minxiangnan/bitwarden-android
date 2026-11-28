# zh authenticator design system

One Material 3 theme for every screen. Tokens live in `BitwardenTheme` (`ui/.../theme`). Screens read `BitwardenTheme.colorScheme`, `typography`, and `shapes`. There is no second palette and no Material You override: a system dynamic scheme can be pure black, so `dynamicColor` is ignored and both dynamic composition locals point at these static schemes.

## Color

Light surfaces are cream and warm white. Text is warm brown. Dark mode is deep warm brown, never black.

| Token | Light | Dark | Role |
| --- | --- | --- | --- |
| background | `#FBF6EF` | `#241812` | Scaffold and page |
| surface | `#FFFDF9` | `#32241C` | Cards, sheets, bars |
| surface high / track | `#F3E6DC` | `#3D2C24` | Pressed and selected fills |
| primary | `#C2552D` | `#E08A6C` | Filled buttons, FAB |
| on primary | `#FFFFFF` | `#241812` | Label on a filled button |
| primary text | `#BA4E29` | `#E08A6C` | Links, codes, selected icons |
| on surface | `#3B2A22` | `#F6EDE4` | Titles and body |
| muted | `#7A675B` | `#C4A894` | Notes, hints, unselected icons |
| divider | `#EADFD3` | `#4E3B30` | Hairline, 0.5dp |
| outline | `#9E8676` | `#4E3B30` | Input borders that must clear 3:1 |
| error / urgent | `#C2412D` | `#F0A8A0` | Last 5 seconds, destructive confirm |
| scrim | `#3B2A22` at 40% | `#241812` at 55% | Dialogs and the QR mask |

### WCAG AA adjustments

Requested values that failed small-text contrast were shifted. Filled-button terracotta stays the requested brand color because white on it passes.

| Requested | Measured on cream / surface | Decision |
| --- | --- | --- |
| on surface `#3B2A22` | 12.68 / 13.42 | Kept |
| muted `#8A7568` | 4.05 / 4.28 | `#7A675B` (4.98 / 5.27) |
| primary `#C2552D` as small text | 4.22 / 4.46 | Buttons stay `#C2552D` with white (4.53). Text, icons, and codes use `#BA4E29` (4.64 / 4.91) |
| divider `#EADFD3` | 1.22 against the page | Kept. It is a hairline, not text. Input outlines use `#9E8676` (3.37 on surface) |
| urgent | `#C2412D` | 4.78 on cream, 5.06 on surface. Shared with the extension |

Dark primary `#E08A6C` on `#241812` is 6.61. Dark muted `#C4A894` on the page is 7.72. Button label `#241812` on `#E08A6C` is 6.61.

## Type

Inter, already loaded by `bitwardenTypography`.

| Style | Size / line | Use |
| --- | --- | --- |
| titleLarge | 19 / 28 semibold | Screen titles |
| titleSmall | 14 / 18 semibold | Row names. Authenticator names are 12 / 16 medium |
| bodyLarge | 16 / 24 | Fields |
| bodySmall | 12 / 16 | Notes. Authenticator notes are 11 / 15 regular, muted |
| labelLarge | 14 / 20 | Segment labels, buttons |
| code | 22 / 28 monospace, `tnum` | Authenticator code |

## Spacing

4, 8, 12, 16, 24. Page inset is 16. Grouped list inset is 16. Row padding is 12. Touch targets stay 48.

## Shape

| Token | Radius |
| --- | --- |
| coachmark | 8 |
| badge, segmented item, info callout, menu | 12 |
| content cards, snackbar, FAB item | 16 |
| dialog, bottom sheet top | 28 |
| button, FAB, progress | pill |

## Elevation

No hard drop shadows on resting cards. Grouped surfaces sit on the page by color alone, with a 0.5dp divider. A dragged authenticator row lifts for 220ms: scale 1.02, 8dp shadow tinted with the terracotta (not black), and a 16% terracotta wash. Pressed state uses the pressed background token.

## Icons

One set: the existing `BitwardenDrawable` vector icons. The authenticator add action is `ic_plus_small` on a filled FAB. Overflow is three dots. The countdown is a 3dp rounded bar under the code: divider track, terracotta fill, urgent color in the last 5 seconds, and a small muted seconds label.

## Motion

| Motion | Duration | Curve |
| --- | --- | --- |
| List placement (reorder, pin) | 220ms | FastOutSlowIn |
| Dragged row lift | 220ms | default tween |
| Countdown fill | 1000ms, linear, across each second | snaps when the window resets |
| Reduced motion | system animator scale, including 0 | Compose `MotionDurationScale` |

Haptic: gesture-start when a drag begins, context-click on drop and on copy.

## States

| State | Treatment |
| --- | --- |
| Default row | Surface group, hairline, terracotta code |
| Last 5 seconds | Code, bar, and seconds label turn urgent; next code appears |
| Copied | Terracotta pill under the status bar: 「已复制验证码」, slides in from the top, gone after 1.5s |
| Reorder | Long-press a row, then drag. The row lifts; the others slide. Drop writes `zh.totp.order` |
| Search or a filter | Dragging is off so order is not computed against a partial list. Tap still copies. Overflow keeps edit and delete |
| Empty | Title, muted body, one filled button |
| Disabled | Divider fill, muted label |
| Error dialog | Confirm uses the error color |

## Authenticator

One rounded group (16dp), not a stack of outlined cards. Each row is at least 64dp: name in titleSmall and notes in bodySmall, both wrapping to as many lines as they need, code in a fixed-width tabular column. The countdown bar is exactly as wide as that column, the seconds sit on its right edge, and 下一段 shares that seconds line so the row stays the same height. The name is 12sp medium and the notes are 11sp regular. The list stays in the saved custom order. Header is search. Add is a FAB with 扫码 and 添加. 更多 stays in the top bar for import, export, trash, and the rest. Long-press drags a row; the overflow menu keeps 上移, 下移, and 置顶. Favorite flags stay on the stored items and are not shown: no star, no favorites filter, and no favorite action in the menu. Copying a code shows a top pill, 「已复制验证码」, instead of a row caption or a bottom snackbar. It slides and fades in, follows the system animator scale, and is announced to TalkBack.
