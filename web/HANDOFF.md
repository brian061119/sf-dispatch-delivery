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
- Auth guard is ON: any `token` in localStorage counts as logged in (the backend
  validates for real). **Demo shortcut while Login is a stub: DevTools → Application →
  Local Storage → set `token` = anything → refresh.** Public routes that must never
  be guarded: `/login` `/register` `/track` `/tracking/:orderId`.

## 2. Your task card

| You | Pages | Routes | APIs (already written, just import) | Definition of done |
|---|---|---|---|---|
| **Ziyuan Xu** | `Login.jsx`, `Register.jsx` | `/login`, `/register` | `api/auth.js`: `login` `register` `logout` `getMe` | Form validation; errors surfaced; success → `/dashboard`; refresh stays logged in (**register body is contract-TBD — confirm first**) |
| **Zihang Cao** | `Dashboard.jsx`, `OrderWizard.jsx`, `OrderHistory.jsx` | `/dashboard`, `/order/new`, `/orders` | `api/recommendation.js`: `getRecommendations`; `api/order.js`: `createOrder` `getOrders`; `api/station.js`: `getStations` | 4-step wizard completes an order; map click-to-pick fills lat/lng; step-4 summary card; history pagination usable |
| **Y** | `OrderDetail.jsx` | `/order/:orderId` | `api/order.js`: `getOrder` `confirmReceipt` `submitReview` | Detail displayed (**body is contract-TBD** — render with optional chaining); receipt button state correct; review feedback shown |
| **Yuning Zhang** | `Tracking.jsx`, `GuestTrack.jsx` | `/tracking/:orderId`, `/track` (public) | `api/tracking.js`: `getTracking` (login) `getTrackingByCode` (public) | Demo spec: StatusBadge + StatusTimeline + MapView courier dot; poll every 5s ONLY while PENDING/IN_TRANSIT, stop at DELIVERED/CANCELLED (page stays viewable); guests see "Log in to manage this order" — never link guests to `/order/:id`. GuestTrack: **trackingCode form → `getTrackingByCode(code)`** (backend added the public endpoint; see §5); both routes stay public |

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

> Backend integrated 2026-09-27 (main @ 1db0c22). Updated statuses below.

- [ ] `POST /api/auth/register` body + response (TBD) — frontend assumes `{username,password,email}` → `{token,user}` — **partially resolved: `POST /api/auth/register` exists, body = `{username,password}` (AuthRequest); email not accepted yet**
- [ ] `GET /api/orders/:orderId` full body (TBD) — backend returns the raw `Order` entity; OrderDetail keeps rendering defensively until field names are contract-frozen
- [ ] 4-state model has no "awaiting signature" state — confirm-receipt flips straight to DELIVERED; intended? — **still open (backend has 6 internal states, external mapping keeps 4)**
- [ ] No cancel-order endpoint — still true on main; wireframe Cancel button stays parked
- [ ] `GET /api/stations` response — **resolved: returns `StationInfoDto[]`** (id/name/lat/lng/...); wizard map can consume it
- [ ] Payment-failure vs order-failure error shapes — **partially resolved: declined payment → HTTP 402 with rollback (see Postman 02-orders collection); order-error shape still open**
- [ ] `POST /api/ai/parse` (P1 frontend proposal, not in contract) — which backend owner? — still unclaimed
- [ ] `POST /api/auth/logout`: server-side invalidation or client-side only? (TBD)
- [x] ~~Anonymous access to tracking (guest lookup at `/track`)~~ — **RESOLVED 2026-09-26: `GET /api/tracking/:trackingCode` is public (SecurityConfig permitAll). The by-order-number endpoint stays behind JWT by design (order numbers are guessable). Frontend: use `getTrackingByCode`.**
- [x] ~~Enum double-check~~ — **backend confirmed: vehicleType `ROBOT|DRONE`, priority `STANDARD|EXPRESS`. BUT the order status enum is 6-state internally (`PENDING_PAYMENT, PAID, PICKING_UP, IN_TRANSIT, DELIVERED, CANCELLED`); contract consumers get the 4-state via server-side mapping (list) / frontend normalization (tracking).**
- [ ] **NEW: `status` in the tracking response is still the RAW internal enum** (`PICKING_UP` etc. can reach the client). Frontend normalizes it today; ask backend to map it in `TrackingController.toContractResponse` (same as `getOrdersContract` does) for a clean contract.
- [ ] **NEW: `POST /api/orders/:orderId/review` is in the contract but NOT implemented on main** — `submitReview` will 404 against the real backend until then; keep the review UI gated/disabled in demo.
- [ ] **NEW: branch `boyuan/tracking` is an older, pre-contract tracking v1** (anonymous by order number, raw DTO shape, `GET /api/tracking/{orderNumber}` by order number instead of tracking code). It conflicts with main's tracking v2. Recommendation: **do not merge as-is**; rebase its milestone-backfill tests onto main's controller.

## 6. Who to ask

Foundation / contract / shared components / integration → Yiting Qi.
antd usage on your own page → antd docs first, then ask.
