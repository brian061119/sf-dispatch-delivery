# Dispatch & Delivery — Handoff Doc (for the 4 teammates)

> The foundation (routing / guards / stores / API layer / shared components / mock
> mode) is done and `npm run build` passes. **Your page files are minimal text stubs —
> replace yours with the real page; everything around it is already wired.**
>
> Contract single source of truth: **`api-contract.md`** (team repo root). Frontend
> mirror: `src/types/api.js`. When they disagree, the contract wins.

## 1. Get it running

```bash
git clone https://github.com/brian061119/sf-dispatch-delivery.git
cd sf-dispatch-delivery && git checkout YitingQi
cd web && npm install
VITE_MOCK=1 npm run dev          # mock mode, no backend needed (PowerShell: $env:VITE_MOCK="1"; npm run dev)
```

- Node.js ≥ 20 (22 recommended). Real backend: drop `VITE_MOCK`, it proxies `/api` to
  `http://localhost:8080` (override with `VITE_API_PROXY_TARGET`).
- Mock mode: `src/api/mock.js` serves contract-shaped fake data (seed orders /
  candidates / drifting courier position); the switch is the first line of every
  function in `api/*.js` — zero business-code changes for integration.
- The auth guard in `App.jsx` is temporarily a pass-through so every URL opens
  directly — **restore the `isAuthed()` check when Ziyuan's Login lands**, keeping
  `/login` `/register` `/track` `/tracking/:orderId` public.

## 2. Your task card

| You | Pages | Routes | APIs (already written, just import) | Definition of done |
|---|---|---|---|---|
| **Ziyuan Xu** | `Login.jsx`, `Register.jsx` | `/login`, `/register` | `api/auth.js`: `login` `register` `logout` `getMe` | Form validation; errors surfaced; success → `/dashboard`; refresh stays logged in (**register body is contract-TBD — confirm first**) |
| **Zihang Cao** | `Dashboard.jsx`, `OrderWizard.jsx`, `OrderHistory.jsx` | `/dashboard`, `/order/new`, `/orders` | `api/recommendation.js`: `getRecommendations`; `api/order.js`: `createOrder` `getOrders`; `api/station.js`: `getStations` | 4-step wizard completes an order; map click-to-pick fills lat/lng; step-4 summary card; history pagination usable |
| **Y** | `OrderDetail.jsx` | `/order/:orderId` | `api/order.js`: `getOrder` `confirmReceipt` `submitReview` | Detail displayed (**body is contract-TBD** — render with optional chaining); receipt button state correct; review feedback shown |
| **Yuning Zhang** | `Tracking.jsx`, `GuestTrack.jsx` | `/tracking/:orderId`, `/track` (public) | `api/tracking.js`: `getTracking` | Demo spec: StatusBadge + StatusTimeline + MapView courier dot; poll every 5s ONLY while PENDING/IN_TRANSIT, stop at DELIVERED/CANCELLED (page stays viewable); guests see "Log in to manage this order" — never link guests to `/order/:id`. GuestTrack: order-number form → `/tracking/:orderId`; both routes stay public |

Every page file's header comment names its owner and wireframe (`wireframes/NN_*.svg`).
Write your real page **in the same file** — the route in `App.jsx` already points at it.

## 3. The 6 rules

1. **API calls only through `src/api/`** — no direct `fetch`/`axios` in pages; new
   endpoint = contract change first.
2. **Contract changes are announced first** — never invent endpoints/fields the
   contract doesn't have (that's how the cancel button got parked).
3. **Shared state only through `src/store/`** — no prop drilling, no DIY localStorage
   (only `lib/auth.js` touches the token).
4. **Reuse shared components first** — `AppHeader` `OrderCard` `StatusBadge`
   `StatusTimeline` `VehicleIcon` `MapView` (usage table in README).
5. **UI uses antd only**; styling via `style` or `src/theme.js`. No new UI libraries.
6. **Commit messages in English**, one sentence (e.g. `feat: map click-to-pick in wizard`).

## 4. Git flow

1. Branch off `main`: `git checkout -b feature/<your-name>-<scope>`
2. Touch only files you own; shared files (components/store/api/types) → ask in the
   group chat first.
3. PR to `main`, one review (Yiting or any teammate), then merge.
4. `git pull origin main` every morning.

## 5. Open items for the backend (next meeting)

- [ ] `POST /api/auth/register` body + response (TBD) — frontend assumes `{username,password,email}` → `{token,user}`
- [ ] `GET /api/orders/:orderId` full body (TBD) — OrderDetail renders defensively until defined
- [ ] 4-state model has no "awaiting signature" state — confirm-receipt flips straight to DELIVERED; intended?
- [ ] No cancel-order endpoint — wireframe has a Cancel button; add `POST /api/orders/:id/cancel` or cut the feature
- [ ] `GET /api/stations` response (TBD) — needed for wizard step-1 map
- [ ] Payment-failure vs order-failure error shapes (TBD)
- [ ] `POST /api/ai/parse` (P1 frontend proposal, not in contract) — which backend owner?
- [ ] `POST /api/auth/logout`: server-side invalidation or client-side only? (TBD)
- [ ] Anonymous access to `GET /api/orders/:orderId/tracking` (guest lookup at `/track`): exempt from JWT, no PII in response, rate-limit. Order DETAIL stays behind auth
- [ ] Enum double-check: `ROBOT|DRONE`; `PENDING|IN_TRANSIT|DELIVERED|CANCELLED`; `STANDARD|EXPRESS`

## 6. Who to ask

Foundation / contract / shared components / integration → Yiting Qi.
antd usage on your own page → antd docs first, then ask.
