# Dispatch & Delivery Web — Frontend Design

## 1. Goal

This folder provides the shared frontend foundation for the dispatch and delivery app. It includes routing, authentication hooks, API wrappers, shared UI components, state stores, mock data, and page placeholders.

## 2. Technology

- React 19 + JavaScript
- Vite for local development and production builds
- Ant Design for UI components
- React Router for page navigation
- Zustand for shared state
- Axios for HTTP requests
- React-Leaflet for maps

## 3. Application structure

```text
src/
├── main.jsx             # React entry point, providers, and router
├── App.jsx              # Layout, header, and route declarations
├── theme.js             # Ant Design theme
├── pages/               # Route-level pages
├── components/          # Reusable UI components
├── api/                 # Endpoint wrappers and mock implementations
├── store/               # Shared auth, wizard, and order state
├── lib/                 # Axios and token helpers
└── types/api.js         # JSDoc contract mirror
```

## 4. Routes and page owners

| Route                | Page               | Responsibility                                  |
| -------------------- | ------------------ | ----------------------------------------------- |
| `/login`             | `Login.jsx`        | Sign in                                         |
| `/register`          | `Register.jsx`     | Create an account                               |
| `/dashboard`         | `Dashboard.jsx`    | Current orders and shortcuts                    |
| `/order/new`         | `OrderWizard.jsx`  | Addresses, package, delivery option, review/pay |
| `/orders`            | `OrderHistory.jsx` | Previous orders                                 |
| `/order/:orderId`    | `OrderDetail.jsx`  | Order details, receipt, review                  |
| `/tracking/:orderId` | `Tracking.jsx`     | Live status and map                             |

`App.jsx` owns the route tree. Authentication is checked by `RequireAuth`; it is currently a temporary pass-through while the login page is a placeholder.

## 5. Data flow

1. A page calls a function in `src/api/`.
2. `src/lib/http.js` adds the stored bearer token and handles common HTTP errors.
3. The API function returns contract-shaped data.
4. Zustand stores keep data shared by multiple pages.
5. Shared components render the same order, status, vehicle, and map patterns everywhere.

Use mock mode when the backend is unavailable:

```powershell
$env:VITE_MOCK="1"; npm run dev
```

## 6. Shared components

- `AppHeader`: global navigation and logout
- `OrderCard`: reusable order summary
- `StatusBadge`: status label
- `StatusTimeline`: delivery progress
- `VehicleIcon`: robot/drone indicator
- `MapView`: pickup, destination, route, and vehicle position

## 7. API areas

The endpoint paths and request/response shapes are defined in the repository-level `api-contract.md`. Frontend wrappers are grouped by area:

- `api/auth.js`: registration, login, logout, current user
- `api/recommendation.js`: delivery candidates
- `api/order.js`: create, list, detail, receipt, review
- `api/tracking.js`: tracking data
- `api/station.js`: dispatch stations
- `api/ai.js`: optional natural-language parsing proposal

Do not call Axios or `fetch` directly from page components. Add or update a wrapper in `src/api/` first.

## 8. Design assessment

The design is suitable for an MVP because it separates route pages, reusable UI, API access, and shared state. The main follow-up work is implementation rather than restructuring:

- replace page placeholders with real forms and order flows;
- confirm the backend contract for registration, order details, stations, and payment errors;
- restore the authentication redirect after Login/Register is implemented;
- keep the four-step order wizard in one route;
- use the shared map and order components instead of duplicating them.
