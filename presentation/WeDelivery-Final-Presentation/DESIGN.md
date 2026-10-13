# DESIGN.md — WeDelivery Final Presentation

## 0. Style routing result
Not academic, not consulting, not government → **universal design principle**, instantiated here for a *deep-tech product defense*. Visual culture: dark technical deck (like a modern infra / AI product keynote), high contrast, one accent colour reserved for proof points.

## 1. Canvas & master grid (hard px values)

- Canvas: **1280 × 720**
- Outer padding: `paddingTop 20 · paddingBottom 20 · paddingLeft 72 · paddingRight 72` on every content slide
- **Zone A · title block**: y 20 → 118 (height 98). Page title 34px bold, plus a 3px × 44px accent rule above it on hero pages only.
- **Zone B · content**: y 118 → 656 (height 538). Everything lives here.
- **Zone C · footer**: y 656 → 700 (height 44). Left: `WeDelivery · Flag Camp` 14px `#94A3B8`. Right: page number `NN / 14` 14px `#94A3B8`.
- Cover (01) and ending (14) replace Zone A/B with a custom full-bleed composition; **Zone C omitted on 01, kept on 14**.

## 2. Colour system

| Role | Hex | Use |
|:--|:--|:--|
| Background | `#0B1220` | every slide base |
| Surface | `#16243A` | cards, table body rows, panel fills |
| Primary | `#3B82F6` | title rule, diagram main nodes, table header fill |
| Accent | `#22D3EE` | **only** the single proof point per slide (giant number, one cell, one CTA) |
| Text strong | `#F1F5F9` | headings, table header text, body |
| Text muted | `#94A3B8` | footnotes, footer, secondary labels |

**Area budget per slide type**

| Type | Background | Surface | Primary | Accent |
|:--|:--|:--|:--|:--|
| Supporting | 55–65% | 25–30% | 8–12% | ≤ 5% |
| Hero (01 / 07 / 09 / 14) | 45–55% | 20–25% | 12–18% | 15–20% |

Gradients allowed: `linear-gradient(135deg, #3B82F6 0%, #22D3EE 100%)` — used **only** on the cover title rule, the hero accent number, and the trade-off "chosen" column header. Transparency: decorative glow `rgba(59,130,246,0.10)`; card shadow `0 4px 20px rgba(0,0,0,0.35)`.

Forbidden: two palettes on one slide; saturated fills over ≥ 1/3 of a slide; identical colour ratio on every page.

## 3. Type system

- Display / numbers: **Space Grotesk**, bold
- Body / UI: **Inter**

| Level | Size | Weight | Line-height | Use |
|:--|:--|:--|:--|:--|
| Cover title | 76 | bold | 1.15 | slide 01 only |
| Section-scale line | 44 | bold | 1.2 | slide 14 closing line |
| Giant number | 96–120 | bold | 1.0 | slides 11, 14 |
| Page title (Zone A) | 34 | bold | 1.3 | every content slide |
| Card / block subtitle | 24 | 600 | 1.35 | card heads |
| Body | 19 | regular | 1.55 | paragraphs |
| Table cell | 17–18 | regular / 600 | 1.4 | table body |
| Footer / footnote | 14 | regular | 1.4 | Zone C |

Rules: giant numbers always Space Grotesk bold, never the body face. Never two different type personalities in one heading block.

## 4. Density gates

- Content slide minimum: 140 words of copy **or** one table/diagram ≥ 45% of Zone B.
- Card fill ≥ 85%: every card ends with a pinned tail strip (`marginTop: auto`), all sibling cards' tail strips aligned on the same y.
- Default whitespace ceiling 35%; hero pages may reach 40–45% but only around the anchor element (≥ 40px breathing room).
- Max 3 images / charts per slide, total image area ≤ 60% of Zone B.

## 5. Image plan

| File | Level | Source | Prompt / scheme | Used on | Verified |
|:--|:--|:--|:--|:--|:--|
| `assets/cover_hero.png` | L1 | ImageGen | San Francisco skyline at dusk, a quadcopter delivery drone and a sidewalk delivery robot in the foreground on a clean plaza, cinematic wide shot, muted navy + cyan grade, photorealistic 3D render, no text, no logos, no watermark | 01 | pending |
| `assets/last_mile.png` | L1 | ImageGen | isometric illustration of a dense city block with three small depot hubs, one drone flying a dashed route and one ground robot on the sidewalk, dark navy background with cyan route lines, clean flat 3D icon style, no text, no watermark | 02 | pending |
| `assets/fleet_ops.png` | L1 | ImageGen | operations control visual: a dark map of San Francisco with three glowing hub markers, dashed drone arcs and robot paths, floating glass panels, cyan and blue glow, cinematic 3D render, no text, no logos, no watermark | 05 | pending |
| `assets/ui_register.png` | L1 | material | real screenshot of our running app at `localhost:3000` (register screen, shows the assistant launch button) | 06 | pending |
| `assets/demo_scene.png` | L1 | ImageGen | split view of a laptop running a delivery web app, left side a live map with a moving vehicle marker, right side a chat panel with an assistant reply, dark UI, dramatic side lighting, photorealistic, no readable text, no watermark | 13 | pending |
| architecture diagram | L2 | inline SVG | 4 stacked layers + AI side branch | 07 | — |
| lock / timeline diagram | L2 | inline SVG | two parallel request lanes converging on a row lock | 10 | — |
| polyline bar | L2 | inline SVG | three-segment route bar with milestone ticks | 11 | — |
| map + chat race diagram | L2 | inline SVG | polyline interpolation + generation counter | 12 | — |

Style rule: all generated art is **3D / photoreal render** — no illustration/photo mixing. SVG only for structure (architecture, timelines, mechanisms), never for city or people scenes.

## 6. Page map (contract with STORY.md)

| # | file | type | role | layout | L1 | words | whitespace | colour | key constraint |
|:--|:--|:--|:--|:--|:--|:--|:--|:--|:--|
| 01 | 01.slide | cover | hero | 全屏视觉+大标题 | cover_hero.png | 35 | 40% | bg45/surf20/prim18/acc18 | full-bleed + dark scrim 55% |
| 02 | 02.slide | content | supporting | 非对称双栏 60:40 | last_mile.png | 150 | 25% | acc ≤5% | image ≥ 55% of left column |
| 03 | 03.slide | content | supporting | 上下分栏 | — | 140 | 22% | acc ≤5% | two stacked bands, table full width |
| 04 | 04.slide | content | supporting | 左标题+右内容 | — | 160 | 25% | acc ≤5% | left rail 30% with big index number |
| 05 | 05.slide | content | supporting | 上大图+下方卡片 | fleet_ops.png | 170 | 20% | acc ≤5% | image band 300px tall |
| 06 | 06.slide | content | supporting | 左大图+右侧文字 | ui_register.png | 150 | 22% | acc ≤5% | screenshot left 56%, no crop of the assistant button |
| 07 | 07.slide | content | hero | 非对称双栏 65:35 | SVG | 120 | 25% | acc 15% | diagram ≥ 60% width |
| 08 | 08.slide | content | supporting | 左标题+右内容 | — | 170 | 25% | acc ≤5% | two stacked lists, no icon wall |
| 09 | 09.slide | content | hero | 图表+洞察 | Table | 200 | 18% | acc 18% | table ≥ 70% of Zone B, insight bar pinned |
| 10 | 10.slide | content | supporting | 非对称双栏 60:40 | SVG | 160 | 24% | acc ≤5% | lock glyph ≥ 44px |
| 11 | 11.slide | content | supporting | 巨型数字+洞察 | 1,862 | 140 | 30% | acc 18% | number ≥ 96px |
| 12 | 12.slide | content | supporting | 左大图+右侧文字 | SVG | 150 | 24% | acc ≤5% | different surface tone from 06 |
| 13 | 13.slide | content | supporting | 上大图+下方卡片 | demo_scene.png | 90 | 28% | acc ≤5% | 3 cards, timestamps on tail |
| 14 | 14.slide | ending | hero | 居中金句/巨型数字 | 144 | 60 | 38% | acc 20% | number ≥ 110px, closing line 44px |
