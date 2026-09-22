# WeDelivery Web — FlagCamp frontend scaffolding

**React 19 + JavaScript + Vite + antd v6 + React-Leaflet + Zustand + axios**

This repo is the frontend lead's (Yiting Qi) **foundation delivery**: scaffolding + design system + `api.js` + backend alignment + routing integration.
The other 4 teammates fill in business logic on their own pages; shared pieces are ready in `components/`, `api/`, `store/`.

> Converted from TypeScript to plain JavaScript in 2026-09 (aligned with the social-ai course stack).
> The old `types/api.ts` contract is now **JSDoc @typedef** in `src/types/api.js` — same contract, different notation.
>
> **API URLs + methods follow `api-contract.md` (2026-09-21 confirmed version) in the team repo root.** `src/types/api.js` is its frontend mirror.

## Quick start

```bash
npm install        # first time
npm run dev        # http://localhost:3000 (needs the backend on :8080)
npm run build      # production build
```

**No backend yet? Use mock mode:**

```bash
VITE_MOCK=1 npm run dev              # Git Bash / macOS / Linux
$env:VITE_MOCK="1"; npm run dev      # Windows PowerShell
```

Mock mode serves contract-shaped fake data from `src/api/mock.js` (seed orders, delivery
candidates, a courier position that drifts). **All 7 pages are minimal text placeholders
waiting for their owners** — the auth guard in `App.jsx` is temporarily a pass-through
so every page URL opens directly (restore the `isAuthed()` check when Login lands).
Refresh resets everything.

The backend is proxied to `http://localhost:8080` by default (see `vite.config.js`).
Change it with `VITE_API_PROXY_TARGET=http://<backend-host:port> npm run dev`.

## Directory layout

| Path | Role |
|---|---|
| `src/types/api.js` | Full API contract (JSDoc @typedef, mirrors api-contract.md) — changes must be synced with the whole team + backend |
| `src/lib/http.js` | axios instance: baseURL + JWT interceptor + 401 → login redirect |
| `src/lib/auth.js` | token storage (localStorage) |
| `src/api/*` | API functions grouped by contract area (see table below) |
| `src/store/*` | Zustand: `auth` / `wizard` / `orders` |
| `src/components/*` | Design-system shared components |
| `src/pages/*` | 7 minimal text stubs, one per owner — owners replace them with real pages |

## Ownership ↔ routes ↔ endpoints

| Owner | Pages / routes | Endpoints used (all `/api`-prefixed) | Status |
|---|---|---|---|
| **Ziyuan Xu** | Login `/login`, Register `/register` | `POST /auth/register`, `POST /auth/login` (+ `logout`/`me`) | 🔲 Placeholder — owner implements |
| **Zihang Cao** | Dashboard `/dashboard`, History `/orders`, Wizard `/order/new` | `POST /recommendations`, `POST /orders`, `GET /orders`, `GET /stations` | 🔲 Placeholder — owner implements |
| **Y** | OrderDetail `/order/:orderId` | `GET /orders/:id`, `PATCH /orders/:id/confirm-receipt`, `POST /orders/:id/review` | 🔲 Placeholder — owner implements |
| **Yuning Zhang** | Tracking `/tracking/:orderId` | `GET /orders/:id/tracking` (5s polling) | 🔲 Placeholder — owner implements |
| **Yiting Qi** | Foundation (everything above: routing / guards / stores / api layer / components / mock) | All | ✅ Done |

> Every page file is a minimal text stub (page name + owner, nothing else). Owners
> replace their stub file entirely — the foundation around it (routes, stores, api) is
> already wired. The auth guard in `App.jsx` is temporarily a pass-through so every URL
> opens directly; restore the `isAuthed()` check when the real Login lands.

> **Route ↔ wireframe mapping**: wireframes 04–07 (the 4 order steps) are merged into ONE
> route `/order/new` with 4 internal steps; 03 Dashboard / 09 History share `OrderCard`;
> 08 Tracking and the detail page cross-link via `Live tracking →`.
> **Do NOT build two versions of an order page** (detail = static info + receipt + review;
> tracking = live map + timeline).

## 3 things the backend must align on

1. Base path `/api`, JSON responses, auth via `Authorization: Bearer <token>`.
2. Enums stay consistent: `VehicleType = ROBOT | DRONE`; `OrderStatus = PENDING | IN_TRANSIT | DELIVERED | CANCELLED`.
3. The TBD items in HANDOFF.md §5 (register body, order detail body, stations, ...).

## Shared components cheat sheet

| Component | Purpose |
|---|---|
| `<AppHeader/>` | Top bar (logo / Dashboard / Orders / New / logout), mounted in App.jsx |
| `<OrderCard order mini/>` | Order card (reused by Dashboard / History) |
| `<StatusBadge status/>` | Colored status tag (contract 4-state) |
| `<StatusTimeline status/>` | 3-step progress bar + CANCELLED special case (Tracking) |
| `<VehicleIcon vehicle/>` | ROBOT/DRONE icon (wizard candidate cards) |
| `<MapView pickup destination vehicle route/>` | Shared Leaflet map (Zihang's picker and Yuning's tracking both use it) |

## Theming

All antd colors/radii live in `src/theme.js`. Change `colorPrimary` once to re-skin the
whole app (the wireframe's black buttons can be tuned here).

## How to use the stores

- `useAuth()` — login/register/logout; survives refresh (localStorage hydration).
- `useWizard()` — order draft; **pour AI parse results in via `prefill()`** and let the
  wizard confirm them as-is ("AI drafts, wizard verifies").
- `useOrders()` — order list; `refresh()` fetches, shared by Dashboard/History.
