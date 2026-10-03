# Pixel specification — historical design reference for Compose

## Viewport & unit mapping

This is a measured design target, not a screenshot-validated native result.
Implementation commit `0466d274ce18f5851d332fcf6ae1a98b2d384ad5` passed Android/Rust CI tests and debug APK assembly (run [37152109273](https://github.com/Nazatric/Sundown/actions/runs/37152109273)); no device screenshot comparison has been performed.

The original design was mobile-first, authored against a 9:16 Android phone with
`<meta viewport width=device-width>`. On Android Chrome the layout viewport in
**CSS px equals the device width in dp**, so:

```
1 CSS px  ==  1 dp        (lengths, radii, borders, offsets)
1 CSS px  ==  1 dp        (font sizes — see "font scaling" below)
```

Reference device target: 1080 × 2400 px @ 2.75 ⇒ **393 × 873 dp**. The original design also specified 360 dp and 412 dp widths; native rendering at those sizes remains unverified.

Do **not** convert via the 96-dpi CSS reference; `device-width` already
normalises it.

### Font scaling

CSS `px` text does not grow with Android's system font scale. The current
`Typography.kt` maps CSS tokens directly to `sp`: this matches at the default
font scale, but honors Android accessibility scaling and therefore intentionally
diverges from CSS at non-default scales. There is no separate font-size switch
in native settings yet.

### Font family

Web stack is `Arial, Helvetica, sans-serif`. The current `SundownFontFamily`
uses Android's generic sans family (normally Roboto); glyph metrics differ
slightly from Arial. A bundled metric-compatible font and screenshot comparison
would be needed to improve that match.

## Breakpoints

| CSS | Native (`WindowWidth`) |
|---|---|
| base (phone) | `< 640.dp` |
| `@media (min-width: 640px)` | `>= 640.dp` |
| `@media (min-width: 860px)` | `>= 860.dp` |
| `@media (min-width: 1200px)` | `>= 1200.dp` |
| `@media (max-width: 360px)` | `<= 360.dp` |
| `@media (max-height: 560px) and (orientation: landscape)` | landscape && `height <= 560.dp` |

The Android shell enables edge-to-edge, hides the status bar, and adds navigation-bar insets to the mini-player. `preferredRefreshRate` is left at 0 (no app-level cap), so Android may use the display's available high-refresh mode. Cutout/safe-area behavior, frame pacing and the resulting device layout still need to be checked on hardware; a `WindowInsets.safeDrawing` mapping is a target, not a verified result.

## Core tokens

| Token | Value |
|---|---|
| library surface | `#5C6770` |
| app background | `#444F58` |
| player gradient | `#46525C → #444E57 @44% → #434D56` |
| silver | `#FBFDFF 0% → #D7E0E7 38% → #B7C3CC 62% → #8E9CA7 100%` |
| metal | `#89949D 0% → #72808B 48% → #586773 100%` |
| toolbar | `#89939B 0% → #7B8790 22% → #697681 63% → #596873 100%` |
| tab selected | `#30383F 0% → #3E4851 62% → #4C5863 100%` |
| accent | `#238FC2` |
| cover size | `min(41vw, 216dp)` phone · `clamp(150, 21vw, 224)` ≥860 |
| player height | `108.dp` phone · `126.dp` ≥860 · `84.dp` landscape |
| row height | `62.dp` |

## Component table (exhaustive values live in `ui/theme/Dimens.kt`)

| Component | Spec |
|---|---|
| Status strip | h 20 + safe-top, bg `#050505`, text 13/700 `#C6C6C6`, name left 8 bottom 1, no synthetic clock, decorative battery 22×10 right 7 bottom 4 |
| Toolbar | phone grid `auto / 1fr`, areas `sources search` / `nav nav`, gap 8, padding 8/8/10; ≥860 single row `auto / 1fr / 210` |
| Metal button | h 35, pad-x 11, r 6, border `#3F4B56`, inset-hi `rgba(231,237,242,.24)`, text 13/700, shadow `0 -1 1 #3B4650` |
| Segmented control | h 35, r 6, border `#3C4852`, divider `#404D58`, label 13/700, selected inset `0 2 5 rgba(0,0,0,.44)` |
| Search pill | h 34, r 22, pad-x 9, border `#46515A`, inset `0 1 2 rgba(31,43,53,.43)`, icon 16 `#9AA4AC`, text 14 `#424F5B` |
| Scan bar | pad 9/14, gap 12, bg `#4D6072 → #43586B`, spinner 18, title 13/17, sub 11/15 `#C4D6E4`, meter 90×7 |
| Grid | 2 cols (3 @640, 3 @860, 4 @1200), gap 30/10, pad `26 30 30 14` |
| Album stack | 5 sleeves, border 1 `#323A40`, r 2, origin 50%/55%; paper `+4° (1,-3)`, rear `-4° (-1,-2)` α.65 sat.45 bri1.2, left `-3.7° (-3,3)` bri.79 sat.6, right `+3° (3,3)` bri.9 sat.75, front shadow `0 2 4 rgba(10,18,25,.72)` + inner hairline `rgba(248,248,243,.42)`; ground ellipse `h12 blur4 rgba(12,21,29,.43)` |
| Tile caption | name mt 20, 14/17 700; count mt 1, 12.5/16 `#DFE4E8` (≥860: 24, 15/18 and 14/18) |
| Song row | h 62, grid `40 / 1fr / 44`, gap 10, pad `6 8 6 10`, art 40 r2, title 13.5/18 700, sub 11.5/16, time 12; ≥860 grid `44 / 1.2fr / .8fr / 1fr / 46` |
| Songs header | sticky, min-h 52, pad `8 30 8 14`, bg `rgba(96,108,118,.97) → rgba(84,95,105,.97)` |
| A–Z rail | right 1, w 24, h `min(80%,580)`, key 10/700 `#B5BEC7`, active bg `rgba(32,47,60,.32)` |
| Transport | 42 circle (play 54) phone; 48/64 ≥860; silver, border `#23313B`, glyph `#34424D` |
| Progress slider | track h 8 r 6, filled `#A3B0BA`, rest `#34414B`, thumb 19 concentric |
| Volume | pill h 34 r 20, knob 32, filled `#9FADB8→#8FA0AB`, rest `#313B44` |
| Mode pill | h 36 r 20, button 46, active `#238FC2`, repeat-one badge 9/700 |
| Sheet | r 14 top, bg `#5B6975`, border `#22333F`, shadow `0 -10 50 rgba(0,10,21,.6)`, scrim `rgba(12,24,35,.56)` + blur 2, max-h `min(88%,780)` |
| Sheet toolbar | min-h 50, bg `#8D9BA6 → #647888 65% → #566B7C`, title 16/700 |
| Metal switch | 52×30 r 16, knob 25, on `#2E7BA6→#3F9BCC`, travel 2→22 |
| Empty state | badge 84 circle radial `#6A7A87→#4C5C6A`, h2 20/25, body 13.5/21 max-w 330 |
| Toast | bottom = player + safe + 14, pad 11/14, r 7, `#5B7182 → #3A5164` |

## Motion

| Name | Spec |
|---|---|
| `sleeve-arrive` | 460 ms `cubic-bezier(.2,.65,.3,1)`, y 12→0, α 0→1, stagger `(i % 4) * 55 ms` |
| `sheet-up` | 260 ms `cubic-bezier(.2,.75,.25,1)`, y 28→0, α .4→1 |
| `spin` | 700 ms linear |
| `audio-bar` | 720 ms alternate, bars delayed 0 / −380 / −190 ms |
| `breathe` | 3.2 s, scale 1 → 1.012 |
| `np-pulse` | 1.8 s, α 0 → .8 → 0 |
| `notice-enter` | 200 ms, y 6→0 |
| press | metal/silver darken + inset; transport `scale .94` |

The web source disables/reduces motion under `prefers-reduced-motion`. Native
Compose currently has no verified animator-duration/reduced-motion integration;
all motion rows above are design references, not claims of parity. This remains
an implementation gap to address and test.
