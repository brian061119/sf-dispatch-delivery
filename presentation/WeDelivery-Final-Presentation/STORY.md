# STORY.md — WeDelivery Final Presentation

## ① Intent Alignment

- **Audience & occasion**: Flag Camp final project defense. Judges are industry engineers / camp mentors, watching a 20-minute recorded team presentation. Slides are also archived and sent to `admin@laioffer.com`.
- **Core goal**: After 20 minutes the judges should believe three things — (1) the team understood an existing legacy codebase before writing anything, (2) the team made explicit, defensible engineering trade-offs rather than picking whatever was easiest, (3) the system actually runs and is covered by tests, not slides.
- **Length**: 14 slides. 4 hero pages (29%).
- **Tone (5 words)**: engineering-credible, modern, restrained, evidence-driven, confident.
- **Boundaries**:
  - MUST cover: Problem, Solution, Frontend UI, Backend architecture, Tech Stack + Trade-offs, 1–2 frontend & 1–2 backend technical challenges, Demo.
  - MUST NOT: name individual teammates on slides (speaker names are collected separately), claim real payment or real GPS hardware, invent market statistics.
  - Language: **all slide copy in English**.

## ② Skeleton

- **Chapters**: No catalog page — the deck is short and every slide is numbered; a catalog would cost a slide and force matching section dividers.
- **Hero pages**: 01 (cover), 07 (backend architecture), 09 (trade-offs), 14 (closing). 4 / 14 = 29%. No two heroes adjacent (01→07→09→14, each separated by ≥1 supporting page).
- **Rhythm**: 01 peak · 02 valley · 03 peak · 04 valley · 05 peak · 06 valley · 07 peak · 08 valley · 09 peak · 10 valley · 11 peak · 12 valley · 13 peak · 14 peak. No run of ≥3 valleys.
- **Asymmetric layouts**: 02, 04, 05, 06, 07, 08, 10, 11, 12, 13 = 10 / 14 = 71% (≥40% ✓). `左大图+右侧文字` ×2 (06, 12) + `非对称双栏` ×3 (02, 07, 10) = 5 / 14 = 36% (≤40% ✓).
- **Symmetric pages**: 01, 03, 09, 14 — never adjacent. `N卡片横排` used 0 times.

## ③ Page Outline

| # | title | type | role | rhythm | layout | visual | visual_role | density | anti_pattern | description |
|:--|:------|:-----|:-----|:-------|:-------|:-------|:------------|:--------|:-------------|:------------|
| 01 | WeDelivery — AI-Powered Last-Mile Dispatch | cover | hero | peak | 全屏视觉+大标题 | L1 cover_hero.png (full bleed) | atmosphere | ~35 words / 1 img / 40% | 禁止把标题缩到左上一角；禁止留白>50% 的空封面 | Positioning line + team + date. The hero image carries the whole slide. |
| 02 | The Last-Mile Is the Expensive Mile | content | supporting | valley | 非对称双栏 60:40 | L1 last_mile.png (left 60%) | anchor | ~150 words / 1 img / 25% | 禁止把图缩成 280×180 缩略图；禁止只放文字 | Last mile is the cost bottleneck: 3 urban hubs, two vehicle classes with hard physical limits — Drone 3 kg / 45 km/h / 30 min vs Robot 15 kg / 15 km/h / 240 min. The dispatcher must respect those limits or the plan is fiction. |
| 03 | Why We Rebuilt It | content | supporting | peak | 上下分栏 | L3 + Table | evidence | ~140 words / 1 table / 22% | 禁止四项并列卡片；禁止只列优点不列代价 | The 2022 reference (external Tomcat + RDS MySQL + CRA) needs a provisioned database just to boot. Ours boots with two commands — which is why the whole team could develop in parallel. |
| 04 | Why a Web App | content | supporting | valley | 左标题+右内容 | L2 icon-ish SVG mark | evidence | ~160 words / 0 img / 25% | 禁止等宽三卡；禁止满屏段落 | No install, no app-store review, and dispatch/monitoring is information-dense — a 13" laptop shows a fleet map far better than a phone. |
| 05 | What It Does | content | supporting | peak | 上大图+下方卡片 | L1 fleet_ops.png (top 58%) + 4 cards | anchor | ~170 words / 1 img + 4 cards / 20% | 禁止四卡等宽横排抢主视觉；禁止卡片无尾条 | Quote → choose → pay → track, plus VIP plans, an admin fleet console, and a Gemini assistant that turns one sentence into a filled order draft. |
| 06 | Frontend Design | content | supporting | valley | 左大图+右侧文字 | L1 ui_register.png (left 56%) | anchor | ~150 words / 1 img / 22% | 禁止 50:50 等分；禁止把截图缩到 200×70 | Real screenshot on the left, stack + four design decisions on the right: Zustand over Redux, Leaflet over Google Maps, wizard state machine, assistant floating above every page. |
| 07 | Backend Architecture | content | hero | peak | 非对称双栏 65:35 | SVG layered diagram (65%) | anchor | ~120 words / 1 diagram / 25% | 禁止用文字列表代替架构图；禁止图占<40% B区 | Four layers: Controller → Service → JPA → PostgreSQL/H2, with the AI branch hanging off AiDeliveryService and never touching the DB directly. |
| 08 | Tech Stack | content | supporting | valley | 左标题+右内容 | L3 + FAIcon rows | evidence | ~170 words / 0 img / 25% | 禁止把技术名做成等宽图标墙 | Java 11 / Spring Boot 2.7.18 / JPA / PostgreSQL+H2 / React 19 / Vite 6 / Ant Design 6 / Leaflet / Zustand 5 / Gemini 1.5 Flash. 144 automated tests. |
| 09 | Trade-offs We Made | content | hero | peak | 图表+洞察 | Table (main) + insight bar | anchor | ~200 words / 1 table / 18% | 禁止只写选型不写代价；禁止把表缩到半页 | Nine decisions, each with the alternative we rejected and the price we accepted. This is the page that proves we chose, not defaulted. |
| 10 | Challenge · Concurrency | content | supporting | valley | 非对称双栏 60:40 | SVG lock/timeline | evidence | ~160 words / 1 diagram / 24% | 禁止只讲概念不展示机制；禁止代码块全屏 | Two users click "Pay" on the last idle vehicle at the same moment. `SELECT … FOR UPDATE` + `@Transactional(rollbackFor=Exception.class)` makes lock→charge→assign→PAID atomic; the `0000` test card proves the rollback path. |
| 11 | Challenge · Virtual GPS | content | supporting | peak | 巨型数字+洞察 | 大数字 1,862 + SVG bar | anchor | ~140 words / big number / 30% | 禁止把 1862 塞进表格单元格；禁止无判断句 | No hardware GPS: position is derived from elapsed time over a 1,862-point polyline. The hard part was that milestone fractions cannot be hardcoded — `TripTimeline` derives them from real leg distances, so one source of truth feeds pickup, delivery and dock checks. |
| 12 | Challenge · Frontend | content | supporting | valley | 左大图+右侧文字 | L2 SVG map+chat | evidence | ~150 words / 1 diagram / 24% | 禁止与 06 页版式完全同构(改配色+图形)；禁止纯文字 | Smooth marker motion along the polyline every 3 s, plus a `generation` counter that discards in-flight AI replies after the user clears chat or logs out — the classic race condition. |
| 13 | Demo | content | supporting | peak | 上大图+下方卡片 | L1 demo_scene.png (top 55%) + 3 cards | anchor | ~90 words / 1 img + 3 cards / 28% | 禁止放外链二维码占位；禁止无时间轴 | Pre-recorded 4-minute walkthrough: register → quote → pay → live tracking → assistant. Captured, not live, so the defense cannot be broken by wifi. |
| 14 | What We'd Build Next | ending | hero | peak | 居中金句/巨型数字 | 大数字 144 + closing line | anchor | ~60 words / 0 img / 38% | 禁止致谢页空无一物；禁止堆简历式列表 | 144 green tests, one deployable stack, and three honest next steps. Closes the loop opened on slide 03. |

### Data landing check
- 02: `3 kg / 45 km/h / 30 min` → "the dispatcher must respect physics, not averages."
- 03: `2022 → 2026` → "four years is enough for a stack to become un-runnable."
- 08: `144 tests` → "a demo that only works on one laptop is not an engineering result."
- 09: nine rows, each ending in an accepted cost.
- 11: `1,862 points` → "interpolation is only correct if the milestone fractions come from real geometry."
- 14: `144` → "the number we'd defend under questioning."
