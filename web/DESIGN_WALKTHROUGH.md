# WeDelivery Frontend — Design Walkthrough (Yiting Qi)

> Purpose: explain to the instructor and teammates **how each layer is designed and why**.
> Conclusions first, details by layer; when challenged, jump to the FAQ at the end.
> **API URLs + methods follow the team repo's `api-contract.md` (2026-09-21 confirmed
> version)** — all frontend code is aligned with it.

---

## 0. One-paragraph overview

A React SPA in five layers: **pages → store → api → lib → types**.
Components only talk to stores and api functions; **no file touches axios, localStorage,
or backend path details directly**.

```
┌────────────────────────────────────────────────────┐
│ pages/  7 pages (split by owner)                   │
│ components/  6 shared components (design system)   │
├────────────────────────────────────────────────────┤
│ store/   zustand × 3: auth (session), wizard       │
│          (order draft), orders (list cache)        │
├────────────────────────────────────────────────────┤
│ api/     contract-grouped API functions (auth /    │
│          recommendation / order / tracking /       │
│          station / ai)                             │
├────────────────────────────────────────────────────┤
│ lib/     http.js (the ONLY axios instance, JWT,    │
│          401 handling) + auth.js                   │
├────────────────────────────────────────────────────┤
│ types/api.js  frontend↔backend contract (JSDoc     │
│               @typedef, mirrors api-contract.md)   │
└────────────────────────────────────────────────────┘
              │  every request goes through the /api prefix
              ▼
        vite proxy → backend :8080
```

---

## 1. Tech choices and why

| Choice | Why |
|---|---|
| **React 19 + JavaScript** | The whole team knows JS best; aligned with the course project (social-ai) stack; zero onboarding cost |
| **Vite 6** (not CRA) | CRA is unmaintained; Vite starts in <1s with fast HMR — the current standard |
| **antd v6** | Same component library as the course project; Form/Table/Steps/Card out of the box; wireframes map directly |
| **zustand 5** (not Redux / Context) | See FAQ 1 |
| **axios** | Interceptors are the key to uniform JWT/401 handling; fetch would need a hand-rolled wrapper |
| **react-router-dom 7** | Declarative routing + guards (RequireAuth) |
| **react-leaflet + OpenStreetMap** | Real maps, free, no API key (Google Maps requires a credit card) |
| ~~TypeScript~~ → **JSDoc @typedef** | Converted TS → JS in 2026-09. The contract survives: `@typedef` in `types/api.js` keeps all type info, editors still show hover hints, but **zero runtime cost and no tsc in the build chain** |

---

## 2. API design (the key question: why the `/api` prefix)

### 2.1 Endpoint overview (all from the confirmed api-contract.md)

| Method | Path | Request → Response | Owner | Frontend call site |
|---|---|---|---|---|
| POST | `/api/auth/register` | RegisterRequest(TBD) → {token, user} | Ziyuan | `api/auth.js` |
| POST | `/api/auth/login` | LoginRequest → {token, user} | Ziyuan | `api/auth.js` |
| POST | `/api/auth/logout` | — (TBD: server-side invalidation?) | Ziyuan | `api/auth.js` |
| GET | `/api/auth/me` | — → user (TBD, reserved) | Ziyuan | `api/auth.js` |
| POST | `/api/recommendations` | {pickup, dropoff, package, priority} → {candidates[]} | Zihang | `api/recommendation.js` |
| POST | `/api/orders` | {candidateId, …, paymentMethodId} → 201 {orderId, status, …} | Zihang | `api/order.js` |
| GET | `/api/orders` | — → {orders[]} | Zihang | `api/order.js` |
| GET | `/api/orders/:id` | — → detail (body TBD, defensive rendering) | Y | `api/order.js` |
| PATCH | `/api/orders/:id/confirm-receipt` | — → {orderId, status: DELIVERED} | Y | `api/order.js` |
| POST | `/api/orders/:id/review` | {rating, comment, damageReported} → {reviewId} | Y | `api/order.js` |
| GET | `/api/orders/:id/tracking` | — → {status, vehicleType, currentLat/Lng, estimatedArrival} | Yuning | `api/tracking.js` |
| GET | `/api/stations` | — → station array (TBD) | Zihang (map) | `api/station.js` |
| POST | `/api/ai/parse` | {text} → draft (**P1 proposal, NOT in contract**) | unclaimed | `api/ai.js` |

The style is **resource-oriented REST**: nouns as paths (`/orders`), HTTP methods as
verbs (GET = read / POST = create / PATCH = partial update), sub-actions hang under the
resource (`/orders/:id/confirm-receipt`, `/orders/:id/tracking`).

Two contract design points worth explaining:
- **Order creation = pay + create in one call (embedded payment)**: `POST /api/orders`
  carries a `paymentMethodId`; the backend charges and creates in one shot, and the
  frontend jumps straight to the tracking page — no payment callback page. In a real
  project `paymentMethodId` would come from Stripe Elements tokenization — **raw card
  numbers never cross our wire**.
- **`isFastest` / `isCheapest` are computed by the backend**: the scoring rules
  (weights, heuristics) live in the backend `RecommendationService`; the frontend only
  renders the labels — so the algorithm can change without shipping a new frontend.

### 2.2 Why `/api`? — it's part of the contract, the vite proxy's "fingerprint", and the deploy "anchor"

First: **every endpoint in the confirmed team contract carries `/api`**, and the
frontend follows the contract — that alone settles it.
But even if the contract hadn't mandated it, we would still add it, for three
engineering benefits:

All frontend requests use the relative path `/api/...`. The browser actually hits the
**same-origin** `localhost:3000/api/...`, and the vite dev server forwards by prefix to
the backend at `localhost:8080` (the proxy section of `vite.config.js`):

```js
// vite.config.js
proxy: { '/api': { target: process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080', changeOrigin: true } }
```

1. **Zero CORS in development.** The browser sees a same-origin request, so no
   cross-origin preflight; the backend needs no CORS config during development.
2. **One proxy rule covers everything.** Vite's proxy matches **by path prefix**. With
   the shared `/api` prefix, one rule covers every endpoint; without it you would list
   `/auth`, `/orders`, `/recommendations`, ... one by one and edit the build config for
   every new endpoint.
3. **Production deploy = one nginx line.** After `npm run build` the app is pure static
   files with no vite proxy. Deploy the static assets and the backend on the same
   domain and let nginx reverse-proxy `/api` to the backend; changing the backend
   address is just the `VITE_API_BASE_URL` env var (read in `lib/http.js`) — no code
   changes.

### 2.3 Contrast: why don't my other course projects use `/api`?

Not every project should — **it depends on the dev server's proxy mechanism and the
deployment shape**:

| Project | Backend | API path style | Why no `/api` needed |
|---|---|---|---|
| **OnlineOrder** | Spring Boot | `/login`, `/orders`… no prefix | The frontend build output is **served by Spring itself** — same origin by construction, no proxy needed |
| **social-ai** | Go on GAE | `/upload`, `/search`… no prefix | The deployed frontend calls the backend's **full domain URL** (injected at build time via `REACT_APP_API_DOMAIN`); cross-origin solved by backend CORS |
| **agentai** | Express | `/chat`, `/upload`… no prefix | CRA's `"proxy"` field is a **catch-all forwarder**: any unrecognized request goes to the backend, no fingerprint prefix needed |
| **WeDelivery** (Leluth reference repo) | Spring + Tomcat | `/getorder`, `/cancelcart`… verb-style, no prefix | Frontend and backend ship in the same war — same origin |
| **FlagCamp (this project)** | Spring Boot | `/api/orders`… noun-style REST | ① the team contract says so; ② Vite proxy needs a prefix to match; ③ an anchor for reverse-proxying when frontend/backend deploy separately |

One-liner for the instructor: **whether to add `/api` is not right-vs-wrong; it's an
engineering question of "how does the dev server recognize API requests + how does the
deploy split traffic". CRA catch-all forwarding and Spring/Tomcat same-origin serving
don't need it; Vite's prefix proxy with separate deployment benefits from it. Our team
contract simply mandates it.**

### 2.4 Contrast: WeDelivery (Leluth)'s API style

Leluth's repo is **verb-style RPC**: `@RequestMapping("/getorder")`, `/cancelcart`,
`/tmpcart`, ... Ours (and the team contract) is **noun-style REST**: `GET /api/orders`,
`PATCH /api/orders/:id/confirm-receipt`.

| | Verb-style (WeDelivery) | Noun-style REST (this project / team contract) |
|---|---|---|
| Path semantics | The action is stuffed into the path; you must read the annotation to know if `/getorder` is POST or GET | Noun + HTTP method; the path tells you what it does |
| Endpoint bloat | One new path per action | Actions on the same resource converge under the resource |
| Industry mainstream | Old-school (RPC legacy) | Mainstream — sounds more professional in a review |

---

## 3. State design: why these three stores

| Store | Owns | Why separate |
|---|---|---|
| `auth` | token / username / login / signup / logout | Session is app-wide and changes rarely; **hydrates from localStorage** at creation so refresh stays logged in; logout calls the contract endpoint best-effort then always clears locally (server-side invalidation is TBD) |
| `wizard` | order draft (pickup/dropoff/pkg/priority/candidates/selected) | The 4 order steps share one draft; **`prefill()`'s merge semantics** are the key to AI ordering (below) |
| `orders` | order list + loading + `refresh()` | Dashboard and History share one cache; `refresh()` after checkout syncs both pages at once |

**"AI drafts, wizard verifies"** — the single most important design decision in this
project:

Dashboard's one-sentence AI ordering **does NOT create an order directly**. The parse
returns a possibly-incomplete draft, which is **merged** into the order draft via
`wizard.prefill()` (the AI's `itemName` maps onto the contract package's `description`),
then we jump into step 1 of the wizard where the user **confirms/completes each field**
before submitting.
Rationale: LLM parsing is untrustworthy (weight, fragility can be missed); ordering
directly would create wrong orders. Let AI be a "form-filling assistant" and keep the
human as the final confirmer. Even if parsing fails it degrades gracefully: a warning,
then the same jump into a blank wizard for manual entry.
(Note: `/api/ai/parse` is a frontend-proposed endpoint for P1 — **not in the confirmed
contract**; a backend owner must claim it.)

---

## 4. File-by-file walkthrough (review checklist)

### Entry & config (5)

| File | What / why |
|---|---|
| `vite.config.js` | Port 3000; `/api` proxied to the backend (`VITE_API_PROXY_TARGET` override, default :8080). The zero-CORS trick lives entirely here |
| `index.html` | Mount point `#root` + loads `/src/main.jsx` |
| `src/main.jsx` | Render entry. Three wrappers: `ConfigProvider` (theme) → `AntdApp` → `BrowserRouter`. **antd v6's message must live inside `<AntdApp>` context**, otherwise it misses the theme |
| `src/theme.js` | antd theme tokens (`colorPrimary: #1677ff`). One place to re-skin the app |
| `src/App.jsx` | Route table (7 routes) + `RequireAuth` guard (redirects to `/login`) + `AppHeader` only after login. Routes in one place = a map of the whole app |

### lib infrastructure (2)

| File | What / why |
|---|---|
| `src/lib/http.js` | **The ONLY axios instance in the app**: `baseURL=/api`, 15s timeout; request interceptor injects `Authorization: Bearer <token>`; response interceptor on 401 → clears session + redirects to login. Auth and expiry handling written once, every endpoint benefits |
| `src/lib/auth.js` | 5 tiny functions reading/writing token/username in localStorage. The key `wd_token` appears exactly once — no hand-typed key strings scattered around |

### types contract (1)

| File | What / why |
|---|---|
| `src/types/api.js` | **The frontend mirror of api-contract.md**: enums (VehicleType / OrderStatus 4-state / Priority) + base shapes (ContractAddress/ContractPackage) + all DTOs, expressed as JSDoc `@typedef`. TBD parts are marked as-is (register body, order detail, stations) — no invented fields |

### api layer (6)

| File | What / why |
|---|---|
| `src/api/auth.js` | `/auth/register` `/auth/login` `/auth/logout` `/auth/me` (Ziyuan). A comment marks the contract TBD on logout (server-side invalidation?); the frontend "calls best-effort, always clears locally" |
| `src/api/recommendation.js` | `POST /recommendations` (Zihang). Request {pickup, dropoff, package, priority}, response {candidates[]} |
| `src/api/order.js` | `POST /orders` (pay+create in one), `GET /orders`, `GET /orders/:id`, `PATCH /orders/:id/confirm-receipt` (receipt), `POST /orders/:id/review`. A trailing note records that **the contract has no cancel endpoint** |
| `src/api/tracking.js` | `GET /orders/:id/tracking` (Yuning) — this is what the Tracking page polls every 5s; the contract's optional WS is a stretch goal |
| `src/api/station.js` | `GET /stations` (response TBD, noted in a comment) |
| `src/api/ai.js` | `POST /ai/parse` (P1 proposal, not in contract). Its own file: AI is an independent capability — the backend can be a real LLM or a regex stub; the frontend only cares about the signature |

### store layer (3) — see §3

### components design system (6)

| Component | What / why |
|---|---|
| `AppHeader` | Top bar (logo / Dashboard / Orders / New button / username / logout), matches the wireframe header |
| `OrderCard` | Order card, reused by Dashboard (mini mode) and History. Fields follow the contract list item: packageDescription/estimatedCost/createdAt (the list payload has no vehicleType, so no icon) |
| `StatusBadge` | status → colored Tag (contract 4-state color table) |
| `StatusTimeline` | status → 3-step progress (PENDING→IN_TRANSIT→DELIVERED), CANCELLED special-cased to the error state |
| `VehicleIcon` | ROBOT → robot icon, DRONE → rocket approximation (the icon set has no drone; a comment marks the swap point). Used on wizard candidate cards |
| `MapView` | **Shared Leaflet map**: pickup/destination/vehicle/route all prop-driven, default center San Francisco. Zihang's order picker and Yuning's live tracking use the SAME component — map logic written once |

### pages (7, by owner)

| Page | What / why |
|---|---|
| `Login` / `Register` (Ziyuan) | antd Form validation (required / email format / password confirmation match); on success go through the auth store to `/dashboard`. Register body is contract-TBD — we send {username,password,email} for now |
| `Dashboard` (Zihang) | Greeting + create entry + **AI one-sentence order card** + active deliveries list (DELIVERED/CANCELLED filtered out) |
| `OrderWizard` (Zihang) | **4 steps, 1 route**: addresses (line1+zip, city fixed SF) → package + priority → candidates → pay. Candidate cards show isFastest/isCheapest tags, stationName, and disable availableUnits=0; mock payment only sends `paymentMethodId: mock_card_<last4>`. `TODO(Zihang)` marks the fill-in points: map picker, order summary card |
| `OrderHistory` (Zihang) | antd Table of all orders; columns = the 5 contract list-item fields; row-level Track link jumps to tracking |
| `OrderDetail` (Y) | Static info (detail body is contract-TBD → everything rendered with optional chaining) + **confirm receipt** (PATCH confirm-receipt; under the 4-state model DELIVERED means "signed") + review form (rating/comment/damageReported) |
| `Tracking` (Yuning) | **5s polling** of `/orders/:id/tracking`, rendering StatusTimeline + MapView current position; the contract has no route array (TODO left), no cancel endpoint (button parked with an explanatory comment); the interval is cleaned up on unmount |

---

## 5. FAQ (check here when challenged)

1. **Why zustand instead of Redux?** Three stores, ~20 lines each; Redux's
   action/reducer/dispatch boilerplate is overkill at this scale. Not Context because
   tracking polls every 5s — high-frequency updates through Context re-render the whole
   component tree, while zustand's selector subscriptions only re-render the components
   that use the changed slice. And it's the friendliest for teammates: if you know
   `useState`, you know zustand.
2. **Why store the JWT in localStorage?** A pragmatic course-project choice: simple,
   survives refresh. Production would prefer an httpOnly cookie (protects the token
   from XSS reads), but that needs backend Set-Cookie cooperation — a **known
   trade-off** we admit upfront when asked.
3. **Why polling instead of WebSocket for tracking?** The contract lists WS
   `/api/ws/orders/:id` explicitly as an optional stretch. 5s polling is 20 lines and
   zero backend work; WebSocket needs connection lifecycle, auth handshake, and
   reconnect logic — not worth it for P0/P1.
4. **Why are the 4 order steps one route, not four?** They share one draft. Split into
   four routes and the draft must go into the URL or be staged on the backend; merged
   into `/order/new` with a Steps component, going Back loses nothing — and wireframes
   04–07 always drew one flow anyway.
5. **Why are detail and tracking two pages?** Different responsibilities: detail =
   static info + receipt + review (Y), tracking = live map + timeline (Yuning).
   **Ownership is split by page, so the two owners never touch the same file**; the
   pages cross-link via `Live tracking →`, so the user experience is seamless.
6. **Why move from TS back to JS?** To align with the course stack (social-ai) — the
   whole team is strongest in JS; the type contract lives on as JSDoc `@typedef` in
   `types/api.js`, editor hints survive, and the build chain drops the tsc dependency.
7. **Why never send raw card numbers?** The contract is designed around
   `paymentMethodId` (tokenized; a real project would use Stripe Elements). Our mock
   also only sends `mock_card_<last4>` — **raw card numbers never touch our server**
   (basic PCI-compliance habit worth building now).
8. **Why doesn't AI parsing place the order directly?** See §3: "AI drafts, wizard
   verifies".
9. **Why is the receipt button clickable during IN_TRANSIT?** The contract is a
   4-state model (PENDING/IN_TRANSIT/DELIVERED/CANCELLED), and confirm-receipt flips
   status straight to DELIVERED — i.e. the contract has no separate "arrived, awaiting
   signature" state. Whether that matches product intuition is on the open-items list
   in HANDOFF.md; the frontend follows the contract.
10. **Where did the wireframe's Cancel button go?** The confirmed contract **has no
    cancel endpoint**. The button is parked as a TODO comment on the Tracking page
    until the backend adds one — an example of the "frontend never invents endpoints
    the contract doesn't have" rule.
