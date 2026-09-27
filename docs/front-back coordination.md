# Frontend ↔ Backend Coordination

Notes for the frontend team. Based on:
- **Backend:** `main` @ `1db0c22` (after PR #3, order pipeline); `main` is now at `277a97e`, where Melody-Qi merged the frontend (§8)
- **Frontend:** branch `YitingQi` @ `7b943e6` (contains all of `auth`)

Every response shape below was checked against the running backend, not just the contract.

---

## 1. Most important items for the frontend

1. **There is no logout endpoint.** `POST /api/auth/logout` returns **404**. Logout is client-side only: delete the token. Remove the call, or ignore its error.
2. **A 401 from protected endpoints has an empty body.** Don't parse JSON on 401. Only the **login** endpoint returns a JSON `message` with its 401.
3. **Use the `code` field on errors when it's present:** `PAYMENT_DECLINED` (402), `NO_VEHICLE_AVAILABLE` (409). A **409 without `code`** means "selected plan no longer available" → re-fetch recommendations and let the user pick again.
4. **Order detail (`GET /api/orders/:id`) is the raw order record.** The ID field is **`orderNumber`** (not `orderId`), and `status` is the **internal 6-state** value (e.g. `PAID`). Run it through the same `normalizeStatus()` used for tracking.
5. **Validate required fields on the client.** Because of a backend bug (§5), missing or invalid fields currently return **500** instead of 400: `candidateId`, `pickup`, `dropoff` on orders; `username`, `password` on login/signup.
6. **Show the tracking code** after checkout and in the order list/detail (with a copy/share button). The guest page (`/track`) must look up by tracking code. Your branch already does this correctly.
7. **Review and cancel don't exist** (404). Keep those buttons disabled for the demo.
8. **The frontend is now on `main`.** Melody-Qi merged `YitingQi` into `main` (commit `277a97e`); from now on, branch off `main` for frontend work and open PRs back to `main` (§8.2). The separately uploaded order-history page was kept in `web/legacy-orders-table/` (§8.1).

---

## 2. Running against the real backend

| | |
|---|---|
| Backend URL | `http://localhost:8080`; your Vite proxy already forwards `/api` there |
| Database | Default = local in-memory H2 (reset on restart). `SPRING_PROFILES_ACTIVE=aws` = shared AWS PostgreSQL |
| AWS access | Your IP must be allowlisted: send your IP from https://checkip.amazonaws.com to the backend team. At a new location (e.g. the demo venue) the IP changes |
| Test accounts | `admin`, `vip_user` (VIP discount), `normal_user`, all with password `password123` |
| Reference requests | `postman/tests/*.json`: working examples of every call, including error cases |
| Mock mode | `VITE_MOCK=1` still works if the backend is down |

---

## 3. Answers to the open items in `web/HANDOFF.md` §5

| Open item | Answer / status |
|---|---|
| `GET /api/orders/:orderId` full body | Returns the raw order record; fields listed in §4.5. The backend proposes a proper contract response (§6); until then, render defensively. |
| No "awaiting signature" state | Correct: confirm-receipt goes straight to `DELIVERED`. The backend has 6 internal states; the contract exposes 4. No extra state is planned. |
| No cancel endpoint | Still true: `PATCH /api/orders/:id/cancel` → 404. Keep Cancel parked. |
| Payment-failure vs order-failure errors | **Resolved:** declined payment → **402** `code: PAYMENT_DECLINED` (order fully rolled back). No free vehicle → **409** `code: NO_VEHICLE_AVAILABLE`. Plan unavailable → **409** without code (backend will add `code: PLAN_UNAVAILABLE`, §6). Other errors: see §4.8. |
| `POST /api/ai/parse` | Not implemented, no backend owner yet. Needs a decision (§7). |
| Logout: server or client? | **Client-side only.** JWTs are stateless and valid for **24 hours**; the endpoint doesn't exist (404). |
| Tracking `status` is the raw enum | Confirmed: e.g. `PICKING_UP`. Keep `normalizeStatus()` for now; the backend plans to map it (§6). |
| Review endpoint missing | Confirmed: 404. Keep the review UI gated. |
| Branch `boyuan/tracking` | Agreed: **don't merge as-is**. It's the old anonymous-by-order-number tracking and conflicts with `main`. |

---

## 4. API as it behaves now (verified)

### 4.1 Auth
- `POST /api/auth/register` `{username, password, email?, firstName?, lastName?}` → `{token, user{id, username, email, role}}`. `role` in the request is **ignored** (always `USER`). Duplicate username/email → 400.
- `POST /api/auth/login` `{username, password}` → same shape. Wrong password or unknown user → **401** `{"message": "Invalid username or password."}` (same message for both).
- `GET /api/auth/me` → `{id, username, email, role}`.
- Send the token as `Authorization: Bearer <token>`. It expires after 24 h → the next call returns 401 → your interceptor sends the user to `/login`.

### 4.2 Stations (public)
- `GET /api/stations` → `StationInfoDto[]` (`stationId`, `name`, `address`, **`latitude` / `longitude`**, …).
- `GET /api/stations/:id/availability` → `droneUnitsAvailable`, `robotUnitsAvailable`, capacity counts. Based on live vehicle state.

### 4.3 Recommendations (login required → 401 without)
- `POST /api/recommendations` `{pickup, dropoff, package, priority}` → `{candidates: [...]}` with `candidateId` (e.g. `CAND-FASTEST`, `CAND-BEST_VALUE`, `CAND-OFF_PEAK`), `estimatedCost`, `estimatedTimeMinutes`, `stationId`, `vehicleType`, `availableUnits`, `isFastest`, `isCheapest`. VIP prices already include the discount.

### 4.4 Create order
`POST /api/orders` (login required). Request:
```json
{
  "candidateId": "CAND-FASTEST",
  "pickup":  { "line1": "Market St",  "lat": 37.7858, "lng": -122.4065 },
  "dropoff": { "line1": "Mission St", "lat": 37.7596, "lng": -122.4269 },
  "package": { "description": "docs", "weightKg": 1.5, "lengthCm": 30, "widthCm": 20, "heightCm": 10 },
  "priority": "STANDARD",
  "paymentMethodId": "pm_xxx"
}
```
- **Required:** `candidateId`, `pickup`, `dropoff`.
- **Send the same `pickup` / `dropoff` / `package` you sent to `/recommendations`.** The backend recalculates the plan and charges its price. **Never send a price.**
- **Payment:** `paymentMethodId` or `cardNumber`; if both are missing, a mock card is used. For the demo, a card ending in **`0000`** is declined (402).

Response **201**:
```json
{
  "orderId": "SFORD20260927133649397",
  "trackingCode": "D24RQKUWGD3HMFV6",
  "status": "PENDING",
  "estimatedTimeMinutes": 8,
  "estimatedCost": 30.03,
  "transactionNo": "TXN-MOCK-7AECCE9A-9",
  "assignedVehicleCode": "DRONE-DT-01",
  "message": "Order created and vehicle locked successfully."
}
```
`estimatedCost` / `estimatedTimeMinutes` equal the chosen candidate's values.

### 4.5 Order list and detail
- `GET /api/orders` → `{orders: [{orderId, trackingCode, status (4-state), detailStatus (6-state), createdAt, packageDescription, estimatedCost}]}`. Only the logged-in user's orders.
- `GET /api/orders/:orderId` → **raw record**; owner or admin only (others get **403**):
  `id, orderNumber, trackingCode, userId, stationId, vehicleId, vehicleType, planType, status (6-state), isStationPickup, pickupAddress, pickupLat, pickupLng, dropoffAddress, dropoffLat, dropoffLng, packageWeight, packageVolume, totalDistance, originPrice, discountAmount, finalPrice, scheduledStartTime, estimatedDeliveryTime, actualDeliveryTime, createdAt`

### 4.6 Confirm receipt
- `PATCH /api/orders/:orderId/confirm-receipt` → `{orderId, status: "DELIVERED"}`. It also frees the vehicle immediately.
- **Show the button only to the customer who placed the order.** The backend currently also lets admins confirm; that's a pending decision (§7).

### 4.7 Tracking
| | Owner tracking | Public (guest) tracking |
|---|---|---|
| Endpoint | `GET /api/orders/:orderNumber/tracking` | `GET /api/tracking/:trackingCode` |
| Login | Required (owner or admin; others 403) | **None**; works even with a stale token |
| Unknown | 404 | 404 (the order number doesn't work here) |

Both return the same shape: `orderId, status (raw 6-state), vehicleType, currentLat, currentLng, estimatedArrival, progressPercent, currentStageDescription, events[{stage, statusDescription, eventLat, eventLng, eventTime}]`. Polling every 5 s is fine.

**Tracking code input on `/track`:** 16 characters from `A–Z` and `2–9` without `0/O/1/I`. Uppercase the input and strip spaces/dashes before calling.

### 4.8 Errors
JSON errors look like `{timestamp, status, error, message}`, plus `code` for payment and vehicle errors.

| Status | When | Body |
|---|---|---|
| 400 | Duplicate username/email; bad input (**currently 500**, see §5) | JSON with `message` |
| 401 | Not logged in / expired token | **Empty** (except login: JSON `message`) |
| 402 | Card declined | JSON, `code: PAYMENT_DECLINED` |
| 403 | Not your order; not allowed to confirm | JSON `message` |
| 403 | Admin-only URL as a customer (e.g. admin dashboard) | **Empty** |
| 404 | Order / tracking code / vehicle not found | JSON `message` |
| 409 | No free vehicle | JSON, `code: NO_VEHICLE_AVAILABLE` |
| 409 | Chosen plan no longer offered | JSON `message` (no `code` yet) |
| 500 | Unexpected server error | JSON `message` |

### 4.9 Formats
- **Money** comes as numbers (`9.4`, not `"9.40"`). Format with 2 decimals in the UI.
- **Timestamps** are ISO without a timezone (`2026-09-27T13:36:49.249571`), in the server's local time. Parse as local time.

---

## 5. Known backend bug affecting the frontend

**Validation errors return 500 instead of 400.** PR #3 added a catch-all error handler that also catches Spring's request errors: missing required fields, malformed JSON, wrong HTTP method. They come back as **500** with an internal Java message. Examples: order without `candidateId`, login/signup without `password`.

- **Status:** the backend team is fixing it (it also breaks one backend test).
- **Frontend until then:** validate required fields before sending, and don't show the raw `message` of a 500 to users.

---

## 6. Backend to-dos (proposed, to confirm at the next meeting)

| # | Change | Effect on the frontend |
|---|---|---|
| B1 | Fix the error handler: 400/405/415 for bad requests, generic message for real 500s | Validation errors become 400 with a readable `message` |
| B2 | Add `code: "PLAN_UNAVAILABLE"` to the "plan no longer available" 409 | Distinguish both 409s by `code` instead of "no code" |
| B3 | Tracking `status` → 4-state + `detailStatus` (same as the order list) | `normalizeStatus()` becomes a no-op; keep it as a safety net |
| B4 | Contract response for order detail (`orderId`, 4-state `status` + `detailStatus`, prices, addresses, times) | Replace the defensive rendering; **breaking change**, announce before merging |
| B5 | Decide logout (client-only is fine) and update `api-contract.md` | Remove the `/auth/logout` call |

---

## 7. Decisions needed from both teams

1. **Can admins confirm receipt on a customer's behalf?** Currently yes (since PR #3); before that it was owner-only.
2. **Review endpoint** (`POST /api/orders/:id/review`): who builds it, and is it in scope for the demo?
3. **Cancel order:** in scope or not?
4. **`POST /api/ai/parse`:** in scope, and who owns it?
5. **Order detail shape (B4):** agree on field names before anyone implements it.

---

## 8. Merging the frontend into `main`

### 8.1 The files uploaded to `main` (commits `b453ad3`, `61c0ec6`, "Add files via upload")
Six files were uploaded directly to `main` through the GitHub website: `web/index.html`, `web/package.json`, `web/src/App.jsx`, `web/src/main.jsx`, `web/src/orders.js`, `web/src/styles.css`. It's a standalone order-history page. Nothing was deleted and no backend files were touched, but:

- **It collides with `YitingQi`.** `index.html`, `package.json`, `App.jsx` and `main.jsx` exist in both with different content, so merging `YitingQi` will conflict on all four. `YitingQi` already has an order history page (`pages/OrderHistory.jsx`).
- **No `vite.config.js` → no `/api` proxy.** On the dev server, `/api/orders` doesn't reach the backend.
- **It hides errors.** `orders.js` catches every error and silently shows **hard-coded mock orders**. When the backend is down or the call fails, the page still looks like it works, which is misleading in a demo.
- **Unpinned dependencies.** `package.json` uses `"latest"` for every package, so every install can get different versions. It also has no lockfile, and `"build": "vite"` should be `"vite build"`.

**Done (merge `277a97e`, Melody-Qi):** `YitingQi`'s versions of the four files are used. The uploaded page (`App.jsx`, `orders.js`, `styles.css`) was moved unchanged to `web/legacy-orders-table/`, whose README lists what's worth porting. It isn't wired in or built. **To do:** Zihang Cao / Yuning Zhang decide whether to port it into `pages/OrderHistory.jsx` (still a placeholder) or delete the folder.

### 8.2 `YitingQi` is merged into `main` (commit `277a97e`)
- All 24 commits of `YitingQi` (including `auth`) are in `main` with their history. `web/` on `main` is identical to `YitingQi`, apart from the legacy folder above (and six empty `.gitkeep` placeholders, which can be deleted). `auth` and `YitingQi` can be retired.
- `api-contract.md` needed no changes: every line of `YitingQi`'s version was already in `main`.
- Verified (same code as `YitingQi`): `npm ci` and `npm run build` succeed. The dev server's `/api` proxy reaches the backend (stations, login, recommendations, order creation, public tracking by code, order list).
- **From now on:** pull `main`, create a branch for your work (e.g. `feature/order-history`), open a PR to `main`, and get it reviewed. Run the app against the real backend (not only mock mode) before opening the PR.
- **Please don't upload files through the GitHub website.** Commit on a branch and open a PR. An upload earlier this week deleted 39 files from `main`, and the latest upload (§8.1) created a second frontend directly on `main`.
- **Don't merge `boyuan/tracking`** as-is (see §3).
