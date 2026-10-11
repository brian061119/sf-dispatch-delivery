# Frontend ↔ Backend Coordination

Notes for the frontend team. **Last updated 2026-10-08 against `main` @ `e8b366e`** (after PR #8 cancel/modify/review, PR #9 `guoqing`, and the Oct 5–7 tracking, VIP, charging and AI work). Frontend and backend both live on `main` now (§8).

Every response shape and status code below was checked against the running backend, not just the contract.

---

## 1. Most important items for the frontend

1. **There is no logout endpoint.** `POST /api/auth/logout` returns **404**. Logout is client-side only: delete the token. Remove the call, or ignore its error.
2. **A 401 from protected endpoints has an empty body.** Don't parse JSON on 401. Only the **login** endpoint returns a JSON `message` with its 401.
3. **Use the `code` field on errors when it's present:** `PAYMENT_DECLINED` (402), `NO_VEHICLE_AVAILABLE` (409). A **409 without `code`** means "selected plan no longer available" → re-fetch recommendations and let the user pick again.
4. **Order detail (`GET /api/orders/:id`) is the raw order record.** The ID field is **`orderNumber`** (not `orderId`), and `status` is the **internal 6-state** value (e.g. `PAID`). Run it through the same `normalizeStatus()` used for tracking.
5. **Missing or invalid fields now return 400** with a readable `message` such as `"password: must not be blank"`. Malformed requests (broken JSON, empty body, wrong method or content type) still return **500** (§5), so keep client-side validation and don't show the raw `message` of a 500.
6. **Show the tracking code** after checkout and in the order list/detail (with a copy/share button). The guest page (`/track`) must look up by tracking code. Your branch already does this correctly.
7. **Only confirm-receipt makes an order `DELIVERED`** (oski, `db53558`). When the package arrives the order **stays `IN_TRANSIT`** (with `actualDeliveryTime` set) until the customer confirms. Show an "Arrived, please confirm receipt" state instead of waiting for `DELIVERED` (§4.6, §4.7).
8. **Cancel, modify and review exist** (PR #8, §4.10). Each has state rules; the backend answers **409** with a readable `message` when an action isn't allowed, so show that message.
9. **Orders advance on their own** (PR #9, §4.7). Statuses and vehicle availability change every 10 s even if nobody is tracking, so re-fetch instead of assuming a status is final.
10. **New: VIP membership and AI assistant endpoints** (§4.11, §4.12). The AI needs `GEMINI_API_KEY` on the backend, otherwise most AI calls return **503**.
11. **Branch off `main` and open PRs back to `main`** (§8). `YitingQi` and `guoqing` are both merged.

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
| AI assistant | Set `GEMINI_API_KEY` (and optionally `GEMINI_MODEL`, default `gemini-3.8-flash`) in the environment that starts the backend. Without it, `/api/ai/parse` returns 503 |
| Road routing | `ROUTING_PROVIDER=osrm` (default) asks the public OSRM server for street routes, so it **needs internet**. If OSRM fails it falls back to straight lines. `ROUTING_PROVIDER=haversine` = straight lines, offline |
| Password reset demo | `DEMO_SHOW_RESET_LINK=true` makes `/api/auth/forgot-password` return the reset link so `/forgot-password` can show it. **Demo only:** anyone who knows a username could then reset that account. Default `false`: the link is only in the backend log. `FRONTEND_BASE_URL` (default `http://localhost:3000`) sets the link's host. New table `password_reset_tokens` (created automatically locally; on AWS run the `CREATE TABLE` from `schema.sql`) |

---

## 3. Answers to the open items in `web/HANDOFF.md` §5

| Open item | Answer / status |
|---|---|
| `GET /api/orders/:orderId` full body | Returns the raw order record; fields listed in §4.5. The backend proposes a proper contract response (§6); until then, render defensively. |
| No "awaiting signature" state | Correct: confirm-receipt goes straight to `DELIVERED`. The backend has 6 internal states; the contract exposes 4. No extra state is planned. |
| No cancel endpoint | **Resolved (PR #8):** `PATCH /api/orders/:orderNumber/cancel`, see §4.10. |
| Payment-failure vs order-failure errors | **Resolved:** declined payment → **402** `code: PAYMENT_DECLINED` (order fully rolled back). No free vehicle → **409** `code: NO_VEHICLE_AVAILABLE`. Plan unavailable → **409** without code (backend will add `code: PLAN_UNAVAILABLE`, §6). Other errors: see §4.8. |
| `POST /api/ai/parse` | **Resolved (YuningZhang, `e8b366e`):** `POST /api/ai/parse` and `POST /api/ai/chat` exist, backed by Gemini (§4.12). |
| Logout: server or client? | **Client-side only.** JWTs are stateless and valid for **24 hours**; the endpoint doesn't exist (404). |
| Tracking `status` is the raw enum | Confirmed: e.g. `PICKING_UP`. Keep `normalizeStatus()` for now; the backend plans to map it (§6). |
| Review endpoint missing | **Resolved (PR #8):** `POST` / `GET /api/orders/:orderNumber/review`, see §4.10. |
| Branch `boyuan/tracking` | Agreed: **don't merge as-is**. It's the old anonymous-by-order-number tracking and conflicts with `main`. |

---

## 4. API as it behaves now (verified)

### 4.1 Auth
- `POST /api/auth/register` `{username, password, email?, firstName?, lastName?}` → `{token, user{id, username, email, role}}`. `role` in the request is **ignored** (always `USER`). Duplicate username/email → 400.
- `POST /api/auth/login` `{username, password}` → same shape. Wrong password or unknown user → **401** `{"message": "Invalid username or password."}` (same message for both).
- `GET /api/auth/me` → `{id, username, email, role, isVip, vipExpireAt}`. Login/register `user` has the same fields. `role` is `USER`, `VIP` or `ADMIN`; use `isVip` for VIP pricing and badges, because a `VIP` whose membership expired keeps `role: VIP` but gets `isVip: false`.
- **Forgot password** (2026-10-08, `guoqing`; both public):
  - `POST /api/auth/forgot-password` `{identifier}` (username or email, email is case-insensitive) → always **200** `{message}`, the same whether or not the account exists. A one-time link valid for 30 minutes is printed in the backend log (`[PASSWORD RESET]`), since there is no email service. In demo mode the response also has `resetLink`. Requesting a new link retires the previous one.
  - `POST /api/auth/reset-password` `{token, newPassword}` (6–128 chars) → **200** `{message}`. Invalid, used or expired token → **400** `"This reset link is invalid or has expired..."`.
  - UI: "Forgot password?" on `/login` → `/forgot-password` → link → `/reset-password?token=...`.
- Send the token as `Authorization: Bearer <token>`. It expires after 24 h → the next call returns 401 → your interceptor sends the user to `/login`.

### 4.2 Stations (public)
- `GET /api/stations` → `StationInfoDto[]` (`stationId`, `name`, `address`, **`latitude` / `longitude`**, …).
- `GET /api/stations/:id/availability` → `droneUnitsAvailable`, `robotUnitsAvailable`, capacity counts. Based on live vehicle state.

### 4.3 Recommendations (login required → 401 without)
- `POST /api/recommendations` `{pickup, dropoff, package, priority}` → `{candidates: [...]}` with `candidateId` (e.g. `CAND-FASTEST`, `CAND-BEST_VALUE`, `CAND-OFF_PEAK`), `estimatedCost`, `estimatedTimeMinutes`, `stationId`, `vehicleType`, `availableUnits`, `isFastest`, `isCheapest`. VIP prices already include the discount.
- **Station choice (B6, done):** stations are ranked by distance to pickup + distance to dropoff, and **each vehicle type falls back to the next station** when the best one has no suitable free vehicle. So one option can come from a different station than another (verified: with Station 1's drones out of service, a Downtown trip got its drone from Station 3 and its robots from Station 1). `candidates` is only empty when no station can serve the trip; keep the empty state for that.

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
- `GET /api/orders` → `{orders: [{orderId, trackingCode, status (4-state), detailStatus (6-state), vehicleType, createdAt, packageDescription, estimatedCost}]}`. Only the logged-in user's orders. `packageDescription` is actually `"<pickup> -> <dropoff>"`.
- `GET /api/orders/:orderId` → **raw record**; owner or admin only (others get **403**):
  `id, orderNumber, trackingCode, userId, stationId, vehicleId, vehicleType, planType, status (6-state), isStationPickup, pickupAddress, pickupLat, pickupLng, dropoffAddress, dropoffLat, dropoffLng, packageWeight, packageVolume, totalDistance, originPrice, discountAmount, finalPrice, scheduledStartTime, estimatedDeliveryTime, actualDeliveryTime, createdAt`
  - New since PR #8: `cancellationFee`, `refundAmount`, `returnStationId`, `hasBeenModified`, `droneUpgradeAvailable`, `maxModificationsAllowed` (1, or 2 for VIP; only on `GET`, not in the `PATCH` response). Still the raw record (no `orderId`, 6-state `status`); `OrderDetail.jsx`'s `toView()` handles it (B4 still open).

### 4.6 Confirm receipt
- `PATCH /api/orders/:orderId/confirm-receipt` → `{orderId, status: "DELIVERED"}`. **This is now the only way an order becomes `DELIVERED`** (tracking and the simulation never set it).
- **Allowed only after the package arrived** (≥ 75% of the trip, i.e. drop-off done). Earlier → **409** `"The package has not been delivered yet..."`; on a `CANCELLED` order → **409**; on an already `DELIVERED` order → 200 (no change).
- **How to know it arrived:** the order is still `IN_TRANSIT`, but `actualDeliveryTime` is set (order detail) and tracking `currentStage` is `RETURNING` or `COMPLETED`. Enable the button then, not on `IN_TRANSIT` alone.
- The vehicle is freed when it's back at the station, whether or not the customer has confirmed. Confirming adds a `DELIVERY_CONFIRMED` tracking event.
- **Show the button only to the customer who placed the order.** The backend still lets admins confirm (re-verified 2026-10-08); that's a pending decision (§7).
- If the customer never confirms, the order stays `IN_TRANSIT` forever (§7).

### 4.7 Tracking
| | Owner tracking | Public (guest) tracking |
|---|---|---|
| Endpoint | `GET /api/orders/:orderNumber/tracking` | `GET /api/tracking/:trackingCode` |
| Login | Required (owner or admin; others 403) | **None**; works even with a stale token |
| Unknown | 404 | 404 (the order number doesn't work here) |

Both return the same shape: `orderId` (= order number), `orderNumber`, `orderDbId` (numeric id), `status` (raw 6-state), `currentStage` (`TO_PICKUP` / `TO_DROPOFF` / `RETURNING` / `COMPLETED`), `vehicleType`, `vehicleCode`, `currentLat`, `currentLng`, `pickupLat/Lng`, `destinationLat/Lng`, `routePolyline` (`[[lat, lng], ...]` for station → pickup → drop-off → station), `estimatedArrival`, `progressPercent`, `currentStageDescription`, `events[{stage, statusDescription, eventLat, eventLng, eventTime}]`. Polling every 5 s is fine.
- `routePolyline`: robots follow streets when OSRM is reachable (verified: 210 points); drones, or robots without OSRM, get just the corner points.
- `events[].stage` can also be `ORDER_UPDATED`, `DELIVERY_CONFIRMED` or `CANCELLED`, and those can repeat. They aren't route milestones.
- `estimatedArrival` is `null` for `DELIVERED` and `CANCELLED`. While an arrived order waits for confirmation it's just "now", so don't show it as an ETA then.

**Orders advance on their own (PR #9).** `SimulationScheduler` runs `SimulationService.tick()` every 10 s: active orders move `PAID → PICKING_UP → IN_TRANSIT` by the clock and their arrival is recorded, but **`DELIVERED` still needs confirm-receipt** (§4.6). Vehicles fly back after drop-off; at the station they become `CHARGING` if their battery isn't full (then `IDLE` once refilled), or `IDLE` straight away. Before PR #9 an order only moved while someone polled its tracking. Config: `SIMULATION_AUTO_TICK` (default `true`), `SIMULATION_TICK_MS` (default `10000`). Each tick counts as 1 simulated minute for returning and charging, so those run about 6x faster than real time. Off in tests (`src/test/resources/config/application.yml`).

**Tracking code input on `/track`:** 16 characters from `A–Z` and `2–9` without `0/O/1/I`. Uppercase the input and strip spaces/dashes before calling.

### 4.8 Errors
JSON errors look like `{timestamp, status, error, message}`, plus `code` for payment and vehicle errors.

| Status | When | Body |
|---|---|---|
| 400 | Duplicate username/email; missing/blank/invalid field (e.g. `"candidateId: must not be blank"`, `"rating: Rating must be between 1 and 5"`); package too heavy for the vehicle; address outside SF | JSON with `message` |
| 401 | Not logged in / expired token | **Empty** (except login: JSON `message`) |
| 402 | Card declined | JSON, `code: PAYMENT_DECLINED` |
| 403 | Not your order and not an admin (view, confirm, cancel, modify, review) | JSON `message` |
| 403 | Admin-only URL as a customer (e.g. admin dashboard) | **Empty** |
| 404 | Order / tracking code / vehicle not found | JSON `message` |
| 409 | No free vehicle | JSON, `code: NO_VEHICLE_AVAILABLE` |
| 409 | Chosen plan no longer offered | JSON `message` (no `code` yet) |
| 409 | Action not allowed in the order's current state (cancel after pickup or delivery, second modification, review before delivery, second review) | JSON `message` |
| 500 | Malformed request (broken JSON, empty body, wrong HTTP method or content type), see §5; or a real server error | JSON `message` |

### 4.9 Formats
- **Money** comes as numbers (`9.4`, not `"9.40"`). Format with 2 decimals in the UI.
- **Timestamps** are ISO without a timezone (`2026-09-27T13:36:49.249571`), in the server's local time. Parse as local time.

### 4.10 Cancel, modify, review (PR #8)
"Before pickup" below means: not `IN_TRANSIT`, and less than 25% of the way from scheduled start to estimated delivery.

| Action | Endpoint | Who | When allowed | Response |
|---|---|---|---|---|
| Cancel | `PATCH /api/orders/:orderNumber/cancel` | Owner or admin | Before pickup. Cancelling twice returns the same result | `{orderId, status: "CANCELLED", cancellationFee, refundAmount, returnStationId}` |
| Modify | `PATCH /api/orders/:orderNumber` | Owner or admin | Before pickup, **once** (VIP: twice), not `DELIVERED` / `CANCELLED` | The updated raw order record (same shape as `GET /api/orders/:id`) |
| Submit review | `POST /api/orders/:orderNumber/review` `{rating (1–5, required), comment? (≤1000), damageReported?}` | Owner or admin | Only when `DELIVERED`, **once only** | `{orderId, reviewId, rating, comment, damageReported, createdAt, message}` |
| Get review | `GET /api/orders/:orderNumber/review` | Owner or admin | Any time | Same shape; **404** if there's no review yet |

- **Cancellation fee:** `$0` (full refund) if the vehicle hasn't left yet (`PAID`); **$2.50** dispatch fee if it's already driving to the pickup (`PICKING_UP`), **waived for VIP**. The vehicle is sent to the nearest station with free bays (`returnStationId`).
- **Modify request** (all fields optional): `pickupAddress/Lat/Lng`, `dropoffAddress/Lat/Lng`, `packageDescription`, `packageWeight`, `packageLengthCm/WidthCm/HeightCm`, `vehicleType` or `upgradeToDrone: true`, plus `cardNumber` / `paymentMethodId` for a price difference. Addresses outside SF and packages over the vehicle limit (drone 3 kg / 0.05 m³, robot 15 kg / 0.30 m³) → **400**.
- **Watch out:** modifying an **off-peak** order (even just the weight) turns it into a `BEST_VALUE` order that starts now, and it's re-priced (re-verified 2026-10-08: 1 kg → 2 kg took it from $8.79 to $18.33). See B7.

---

### 4.11 VIP membership (Zilun Chen, `feature/vip-privileges`)
| Endpoint | Body | Response |
|---|---|---|
| `GET /api/vip/status` (login) | – | `{role, isVip, vipExpireAt, daysRemaining, totalSaved, discountRate, maxModificationsAllowed, capacityTolerancePercent, benefits[]}` |
| `POST /api/vip/subscribe` (login) | `{planType: "MONTHLY" \| "ANNUAL", paymentMethodId?}` | Same as status. Extends from the current expiry if already VIP |

- **Benefits** (from `benefits[]`): 10% off every order, $2.50 cancellation fee waived, 2 modifications per order, +10% weight/volume tolerance, priority dispatch and high-battery vehicles.
- **Admins can't subscribe** (B8, fixed): `POST /api/vip/subscribe` → **409** for `ADMIN` accounts; their role is never changed.
- **Known issue (B9):** any `planType` other than `ANNUAL` (even `"LIFETIME"`) is treated as monthly, and no payment is taken.
- After subscribing, the existing token keeps working, and `/api/auth/me` returns the new `role` / `isVip` right away.

### 4.12 AI assistant (YuningZhang, `e8b366e`)
Both need login (401 without). Answers come from Gemini plus `server/src/main/resources/delivery-knowledge.md`; the model never sets prices (quotes go through `/api/recommendations`).

| Endpoint | Body | Response |
|---|---|---|
| `POST /api/ai/parse` | `{text}` (≤ 4000 chars) | `{prefill: {pickup?, dropoff?, pkg, priority}, missingFields[], mode: "gemini"}` |
| `POST /api/ai/chat` | `{message, history?: [{role: "user" \| "assistant", content}]}` (≤ 40 turns) | `{reply, cards?: [{type: "order" \| "quote" \| "prefill", ...}], prefill?}` |

- **Without `GEMINI_API_KEY`:** `parse` → **503** `{"message": "GEMINI_API_KEY is missing..."}`. `chat` still answers simple package questions without Gemini (verified: "how much for a 2kg package?" → 200 asking for addresses), but anything that needs the model returns 503.
- Addresses are only filled in when they include a house number. Otherwise they're listed in `missingFields`.
- Empty `message` / `text` → 400. Gemini errors → **503** with a readable `message`; show it and let the user use the wizard instead.

### 4.13 Admin: users (2026-10-08, `guoqing`)
Admin only (no token → 401, customer → 403). Read-only; responses never include password data.

| Endpoint | Response |
|---|---|
| `GET /api/admin/users` | `[{id, username, firstName, lastName, email, role, isVip, vipExpireAt, createdAt, orderCount, activeOrderCount}]`, sorted by `id`. `activeOrderCount` = orders not `DELIVERED` / `CANCELLED` |
| `GET /api/admin/users/:userId` | `{user: <same as a list item>, orders: [{orderNumber, trackingCode, status (4-state), detailStatus (6-state), vehicleType, pickupAddress, dropoffAddress, finalPrice, createdAt, actualDeliveryTime}]}`, orders newest first. Unknown id → 404 |

UI: the **Users** table on `/admin` (search, sort by order count); clicking a username opens `/admin/users/:userId`. Order details are shown inline there because customer pages (`/order/:id`, `/track`) redirect admins back to `/admin`.

## 5. Known backend bug affecting the frontend

**Malformed requests return 500 instead of 400/405/415** (partly fixed; re-verified 2026-10-08).

- **Fixed:** missing, blank or invalid fields (`@Valid` errors) now return **400** with a readable `message`, e.g. login without `password`, order without `candidateId` or `pickup`. The backend test that used to fail now passes (86/86).
- **Still 500**, with an internal Spring message: broken JSON, empty request body, wrong HTTP method (e.g. `DELETE /api/orders`), wrong content type (e.g. `text/plain`). The catch-all `@ExceptionHandler(Exception.class)` in `GlobalExceptionHandler` still catches Spring's `HttpMessageNotReadableException`, `HttpRequestMethodNotSupportedException` and `HttpMediaTypeNotSupportedException`.
- **Frontend:** the app shouldn't send these, but don't show the raw `message` of a 500 to users.

---

## 6. Backend to-dos (proposed, to confirm at the next meeting)

| # | Change | Effect on the frontend |
|---|---|---|
| B1 | **Partly done.** Field validation → 400 ✅. Still to do: 400/405/415 for malformed JSON, empty body, wrong method/content type; generic message for real 500s (§5) | Malformed requests stop returning 500 |
| B2 | Add `code: "PLAN_UNAVAILABLE"` to the "plan no longer available" 409 | Distinguish both 409s by `code` instead of "no code" |
| B3 | Tracking `status` → 4-state + `detailStatus` (same as the order list) | `normalizeStatus()` becomes a no-op; keep it as a safety net |
| B4 | Contract response for order detail (`orderId`, 4-state `status` + `detailStatus`, prices, addresses, times) | Replace the defensive rendering; **breaking change**, announce before merging |
| B5 | Decide logout (client-only is fine) and update `api-contract.md`. `POST /api/auth/logout` is still 404 | Remove the `/auth/logout` call |
| B6 | ✅ **Done** (`9dca22b`, ty2610-Columbia, 2026-10-03; details in `docs/调度引擎变更-2026-10-03.md`). Stations ranked by distance to pickup + dropoff, with per-vehicle-type fallback to the next station (§4.3) | Fewer empty results; options can come from different stations |
| B7 | **New:** modifying an off-peak order turns it into `BEST_VALUE`, moves its start to now and re-prices it (§4.10). Intended? If not, keep the plan type and scheduled start when only the package or address changes | If intended, warn the user in the modify dialog before saving |
| B8 | ✅ **Fixed** (2026-10-08, `guoqing`): `POST /api/vip/subscribe` now refuses admin accounts with **409** `"Admin accounts can't subscribe to VIP..."`, so the role stays `ADMIN`. Before, it replaced `ADMIN` with `VIP` and locked the account out of the admin console. Test: `VipPrivilegesApiTest.adminCannotSubscribeAndKeepsAdminRole` | Admins already can't reach `/vip` (`RequireCustomer`, no header link); show the 409 `message` if it ever happens |
| B9 | **New:** VIP subscribe accepts any `planType` (unknown values become monthly) and takes no payment. Validate `planType` (400 otherwise) and charge through the mock payment like orders | Show plan prices; handle 402 like checkout |

---

## 7. Decisions needed from both teams

1. **Can admins confirm receipt on a customer's behalf?** Currently yes (since PR #3, re-verified 2026-10-08); before that it was owner-only. Since PR #8 admins can also cancel, modify and **review** any customer's order. A review written by an admin is stored as that customer's review, so this probably should be owner-only.
2. **Orders nobody confirms stay `IN_TRANSIT` forever** (since `db53558`). Auto-confirm after some time (e.g. 24 h after arrival), or keep it manual? This also affects the admin console and the "active deliveries" counts.
3. **Order detail shape (B4):** agree on field names before anyone implements it.
4. **Off-peak orders and modify (B7):** intended behavior or bug?
5. **`.claude/settings.local.json` was committed** in `f2d304c`. It's a personal Claude Code settings file; remove it from the repo and add it to `.gitignore`.

~~Review endpoint~~ and ~~cancel order~~: built in PR #8. ~~AI endpoints~~: built in `e8b366e` (§4.12).

---

## 8. Merging the frontend into `main`

> §8.1–8.2 are the history of the first frontend merge. Current state: `main` contains `YitingQi` (merged via `guoqing`, PR #9) and PR #8. Work on a branch off `main` and open a PR.

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
