# Dispatch & Delivery — Handoff Doc (for the 4 teammates)

> The foundation (scaffolding / design system / API layer / state layer / routing /
> contract / mock mode) is complete and `npm run build` passes.
> **Your page files are minimal text placeholders** — each renders just the page name
> and your name. Replace your stub file(s) with the real page; everything around them
> is already wired. Don't rebuild shared pieces — reuse first, create second.
>
> **Single source of truth for the contract: `api-contract.md` (2026-09-21 confirmed
> version) in the team repo root.** The frontend `src/types/api.js` is its mirror; when
> URLs/methods disagree, the contract wins.

## 1. Get it running (5 minutes)

```bash
git clone https://github.com/brian061119/sf-dispatch-delivery.git
cd sf-dispatch-delivery && git checkout YitingQi   # the foundation lives on this branch
cd web
npm install
npm run dev          # http://localhost:3000
```

- Requires Node.js ≥ 20 (22 recommended).
- The frontend proxies `/api` to `http://localhost:8080` by default. Different backend
  address? `VITE_API_PROXY_TARGET=http://<backend-host:port> npm run dev`
  (Windows PowerShell: `$env:VITE_API_PROXY_TARGET="..."; npm run dev`).
- **Backend not up yet?** Use mock mode: `VITE_MOCK=1 npm run dev` (PowerShell:
  `$env:VITE_MOCK="1"; npm run dev`). `src/api/mock.js` serves contract-shaped fake
  data (seed orders / candidates / a drifting courier position) so you can develop
  your page without the backend. All pages currently show one-line text placeholders;
  **the auth guard in `App.jsx` is temporarily a pass-through so every page URL opens
  directly — restore the `isAuthed()` check when Ziyuan's real Login lands.** For
  integration just drop `VITE_MOCK` — zero business-code changes (the switch is the
  first line of every function in `api/*.js`).

## 2. Your task card

| You | Pages (files) | Routes | APIs (already written, just import) | Definition of done |
|---|---|---|---|---|
| **Ziyuan Xu** | `pages/Login.jsx`, `pages/Register.jsx` | `/login`, `/register` | `api/auth.js`: `login` `register` `logout` `getMe` | Full form validation; errors surfaced; success → `/dashboard`; refresh stays logged in; integration with `/api/auth/*` passes (**register body is contract-TBD — confirm with the backend first**) |
| **Zihang Cao** | `pages/Dashboard.jsx`, `pages/OrderWizard.jsx`, `pages/OrderHistory.jsx` | `/dashboard`, `/order/new`, `/orders` | `api/recommendation.js`: `getRecommendations`; `api/order.js`: `createOrder` `getOrders`; `api/station.js`: `getStations` | The 4-step wizard completes an order; map click-to-pick fills lat/lng; step-4 order summary card; history pagination usable |
| **Y** | `pages/OrderDetail.jsx` | `/order/:orderId` | `api/order.js`: `getOrder` `confirmReceipt` `submitReview` | Detail fully displayed (**detail body is contract-TBD** — render defensively with optional chaining until confirmed); receipt button state logic correct; review submission gives feedback |
| **Yuning Zhang** | `pages/Tracking.jsx`, `pages/GuestTrack.jsx` | `/tracking/:orderId`, `/track` (public) | `api/tracking.js`: `getTracking` (poll every 5s) | Live position rendered on the map (the contract has current position only, no route history — if you need a trail, file a backend request); the polling interval is cleaned up on unmount. GuestTrack: order-number form (no login) that navigates to `/tracking/:orderId`; both routes must stay outside the auth guard |

Every file's header comment names its owner and the matching wireframe number
(`wireframes/NN_xxx.svg`). **Your page file is a minimal text stub — replace it with
your real page in the same file** (the route in `App.jsx` already points at it). The
task card above is your checklist; the Definition of done column is the acceptance bar.

## 3. The 6 rules everyone must follow

1. **API calls only through `src/api/`**. No direct `fetch`/`axios` in components —
   need a new endpoint? Propose a contract change first, then add a function in `api/`.
2. **Contract changes are announced first.** `api-contract.md` / `src/types/api.js` is
   the single frontend↔backend contract; say it in the group chat before changing any
   field/enum (frontend and backend change together). **The frontend never invents
   endpoints or fields the contract doesn't have** (that's exactly how the cancel-order
   button got "parked").
3. **Shared state only through `src/store/`**. Don't drill props three levels deep
   between components, and don't write to localStorage yourself (only `lib/auth.js`
   touches the token).
4. **Reuse shared components first**: `OrderCard` `StatusBadge` `StatusTimeline`
   `VehicleIcon` `MapView` `AppHeader` (usage in DESIGN_WALKTHROUGH.md §4).
5. **UI uses antd components**; style tweaks via `style` or the theme (`theme.js`). No
   new component libraries / CSS frameworks.
6. **Commit messages in English**, one sentence saying what you did (e.g.
   `feat: map click-to-pick in order wizard`).

## 4. Git flow

```
YitingQi (foundation) ──► main (after the foundation merges, main is the runnable baseline)
main ──► feature/ziyuan-auth
     ──► feature/zihang-order
     ──► feature/y-detail
     ──► feature/yuning-tracking
```

1. Branch off `main`: `git checkout -b feature/<your-name>-<scope>`
2. Only touch files you own; to change shared files (components/store/api/types), say
   so in the group chat first.
3. Open a PR to `main` when done; get a review from Yiting or any teammate, then merge.
4. `git pull origin main` every morning before starting work.

## 5. Open items to confirm with the backend (walk through at the next meeting)

Marked TBD in the contract or discovered during frontend implementation, by priority:

- [ ] **`POST /api/auth/register` body and response** (TBD) — frontend currently assumes `{username,password,email}` → `{token,user}`
- [ ] **`GET /api/orders/:orderId` full body** (TBD) — OrderDetail currently renders defensively with optional chaining; complete the fields once defined
- [ ] **No separate "arrived, awaiting signature" state in the 4-state model**: confirm-receipt flips status straight to DELIVERED, so a user can sign while IN_TRANSIT. Is that the intended product logic?
- [ ] **Missing cancel-order endpoint** — the wireframe has a Cancel button but the contract has no endpoint. Either the backend adds `POST /api/orders/:id/cancel` or we cut the feature
- [ ] **`GET /api/stations` response** (TBD) — needed for the wizard step-1 map
- [ ] **Error shapes for payment failure vs. order failure** (TBD) — the payment page needs to distinguish them in its messaging
- [ ] **`POST /api/ai/parse`** (P1 frontend proposal, not in the contract) — which backend owner claims it? A regex stub is fine to start
- [ ] **`POST /api/auth/logout`**: server-side invalidation or pure client-side? (TBD) — the frontend currently "calls best-effort, always clears locally"
- [ ] **Anonymous access to `GET /api/orders/:orderId/tracking`** (new requirement:
  guest order lookup at `/track`, no login) — the order number acts as the credential
  (FedEx model). Backend should exempt this endpoint from JWT auth, expose no PII in
  its response, and ideally rate-limit it. Order DETAIL (`/api/orders/:id`) stays
  behind auth — it carries addresses and the receipt/review actions
- [ ] Enum double-check: `ROBOT|DRONE`; `PENDING|IN_TRANSIT|DELIVERED|CANCELLED`; `STANDARD|EXPRESS`

## 6. Who to ask

- Foundation / contract / shared components / integration debugging → Yiting Qi
- antd usage on your own page → check DESIGN_WALKTHROUGH.md's FAQ first, then the antd
  docs, then ask
