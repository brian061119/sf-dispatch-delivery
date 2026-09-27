# Dispatch & Delivery

**React 19 + JavaScript + Vite + antd v6 + React-Leaflet + Zustand + axios**

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

The backend is proxied to `http://localhost:8080` by default (see `vite.config.js`).
Change it with `VITE_API_PROXY_TARGET=http://<backend-host:port> npm run dev`.

## Directory layout

| Path               | Role                                                                                                               |
| ------------------ | ------------------------------------------------------------------------------------------------------------------ |
| `src/types/api.js` | Full API contract (JSDoc @typedef, mirrors api-contract.md) — changes must be synced with the whole team + backend |
| `src/lib/http.js`  | axios instance: baseURL + JWT interceptor + 401 → login redirect                                                   |
| `src/lib/auth.js`  | token storage (localStorage)                                                                                       |
| `src/lib/brand.js` | `BRAND_NAME` — single source of truth for the UI brand text                                                         |
| `src/api/*`        | API functions grouped by contract area (+ `mock.js` for mock mode)                                                  |
| `src/store/*`      | Zustand: `auth` / `wizard` / `orders`                                                                              |
| `src/components/*` | Design-system shared components                                                                                    |
| `src/pages/*`      | 8 minimal text stubs, one per owner — owners replace them with real pages                                          |

## Shared components cheat sheet

| Component                                     | Purpose                                                                                |
| --------------------------------------------- | -------------------------------------------------------------------------------------- |
| `<AppHeader/>`                                | Top bar on every route; two variants by auth state (guest: brand + Log in / authed: full nav) |
| `<OrderCard order mini/>`                     | Order card (reused by Dashboard / History)                                             |
| `<StatusBadge status/>`                       | Colored status tag (contract 4-state)                                                  |
| `<StatusTimeline status/>`                    | 3-step progress bar + CANCELLED special case (Tracking)                                |
| `<VehicleIcon vehicle/>`                      | ROBOT/DRONE icon (wizard candidate cards)                                              |
| `<MapView pickup destination vehicle route/>` | Shared Leaflet map (Zihang's picker and Yuning's tracking both use it)                 |

## Routes

Public: `/login` `/register` `/track` `/tracking/:orderId` (guest order lookup, no login).
Behind auth: `/dashboard` `/orders` `/order/new` `/order/:orderId`.
Landing `/` is auth-aware (WeDelivery/FedEx model): guests → `/track`, logged-in → `/dashboard`.
The guard accepts ANY token string (social-ai style — the backend is what validates it).
Demo phase shortcut while Login is a stub: DevTools → Application → Local Storage → set
`token` to any value → refresh = "logged in"; delete the key = guest again.

## Theming

All antd colors/radii live in `src/theme.js`. Change `colorPrimary` once to re-skin the
whole app.

## Renaming the product

The UI brand name lives in ONE constant: `src/lib/brand.js` → `BRAND_NAME`
(currently `Dispatch & Delivery`). The header logo and browser tab title both read it.
On a real rename also update the doc titles and `index.html`'s static fallback; the npm
`package.json` name is an internal identifier and does not need to change.

## How to use the stores

- `useAuth()` — login/register/logout; survives refresh (localStorage hydration).
- `useWizard()` — order draft; **pour AI parse results in via `prefill()`** and let the
  wizard confirm them as-is ("AI drafts, wizard verifies").
- `useOrders()` — order list; `refresh()` fetches, shared by Dashboard/History.
