# API Contract — SF Dispatch & Delivery

Source of truth for URL + method: the team's page→endpoint contract table (confirmed 2026-09-21).
Where the table did not specify a request/response body, this doc marks it **TBD** instead of inventing fields — do not treat TBD sections as final.

Confirmed body-shape decisions (from the recommendations/orders design pass):
- Address fields keep both `addressId` (saved address) and full detail fields. Backend treats non-null `addressId` as the source of truth.
- Units: weight in **kg**, dimensions in **cm**.
- `isFastest` / `isCheapest` are computed by the backend (`RecommendationService`), not the frontend.

## Auth

### POST /api/auth/register
Register a new user. Request/response body: **TBD** — needs team definition (likely username/email/password; confirm password policy before implementing).

### POST /api/auth/login
Request:
```json
{
  "username": "string",
  "password": "string"
}
```
Response (200):
```json
{
  "token": "string",
  "user": {
    "id": "string",
    "username": "string",
    "email": "string"
  }
}
```

### POST /api/auth/logout
Request: none (token identifies session, likely via Authorization header).
Response: **TBD** — confirm whether this invalidates a server-side session/token or is client-side-only.

### GET /api/auth/me
Get the currently logged-in user's info. Response body: **TBD** — likely the same `user` shape as the login response, needs confirmation.

## Recommendations

### POST /api/recommendations
Core recommendation/scoring endpoint. Request:
```json
{
  "pickup": {
    "addressId": "string | null",
    "line1": "string",
    "city": "San Francisco",
    "zip": "string",
    "lat": "number | null",
    "lng": "number | null"
  },
  "dropoff": {
    "addressId": "string | null",
    "line1": "string",
    "city": "San Francisco",
    "zip": "string",
    "lat": "number | null",
    "lng": "number | null"
  },
  "package": {
    "description": "string",
    "weightKg": "number",
    "lengthCm": "number | null",
    "widthCm": "number | null",
    "heightCm": "number | null",
    "fragile": "boolean"
  },
  "priority": "STANDARD | EXPRESS"
}
```
Response (200):
```json
{
  "candidates": [
    {
      "candidateId": "string",
      "stationId": "string",
      "stationName": "string",
      "vehicleType": "ROBOT | DRONE",
      "estimatedTimeMinutes": "number",
      "estimatedCost": "number",
      "availableUnits": "number",
      "score": "number",
      "isFastest": "boolean",
      "isCheapest": "boolean"
    }
  ]
}
```

## Orders

### POST /api/orders
Create an order. **Confirmed (2026-09-21): embedded payment, not a hosted-checkout redirect.** Payment info is collected on the order confirmation page itself — either a saved `paymentMethodId` or newly entered card details via a client-side library (e.g. Stripe Elements) — and submitted together with the order. The backend processes payment and creates the order in this one call; there is no separate payment/callback page. On success, the frontend routes directly to the tracking page (`/orders/:orderId/tracking`).

Request:
```json
{
  "candidateId": "string",
  "pickup": { "...same shape as recommendations request": true },
  "dropoff": { "...same shape as recommendations request": true },
  "package": { "...same shape as recommendations request": true },
  "priority": "STANDARD | EXPRESS",
  "paymentMethodId": "string"
}
```
Response (201):
```json
{
  "orderId": "string",
  "trackingCode": "string (16 chars, random — share this for public tracking)",
  "status": "PENDING",
  "estimatedTimeMinutes": "number",
  "estimatedCost": "number"
}
```
Still TBD: the exact error shape when payment fails (e.g. card declined) vs. when the order itself fails for another reason — the frontend needs to distinguish these to show the right message on the order confirmation page. Confirm with the Order module owner.

### GET /api/orders
Get the order list. Response (200):
```json
{
  "orders": [
    {
      "orderId": "string",
      "trackingCode": "string",
      "status": "PENDING | IN_TRANSIT | DELIVERED | CANCELLED",
      "createdAt": "ISO-8601 string",
      "packageDescription": "string",
      "estimatedCost": "number"
    }
  ]
}
```

### GET /api/orders/:orderId
Get order detail. Response body: **TBD** — likely a superset of the list-item shape above (full pickup/dropoff, package, chosen candidate, timestamps), needs confirmation.

### PATCH /api/orders/:orderId/confirm-receipt
Confirm delivery receipt. Request: none. Response (200):
```json
{
  "orderId": "string",
  "status": "DELIVERED"
}
```

### POST /api/orders/:orderId/review
Submit a review. Request:
```json
{
  "rating": "number (1-5)",
  "comment": "string | null",
  "damageReported": "boolean"
}
```
Response (200):
```json
{
  "orderId": "string",
  "reviewId": "string"
}
```

## Tracking

### GET /api/orders/:orderId/tracking
Poll tracking status for a logged-in customer. **Requires login** — only the customer who placed the order or an admin (otherwise 403). Response (200) — same shape as the public tracking endpoint below:
```json
{
  "orderId": "string",
  "status": "PENDING | IN_TRANSIT | DELIVERED | CANCELLED",
  "vehicleType": "ROBOT | DRONE",
  "currentLat": "number",
  "currentLng": "number",
  "estimatedArrival": "ISO-8601 string"
}
```

### GET /api/tracking/:trackingCode
**Public, no login required.** Anyone who has the order's random `trackingCode` (returned by `POST /api/orders` and `GET /api/orders`) can view its status and location. Read-only — only `GET` is allowed. The guessable `orderId` does not work here. Response (200): same shape as `GET /api/orders/:orderId/tracking` above. Unknown code → 404.

### WS /api/ws/orders/:orderId
Real-time push (optional stretch feature). Message shape: **TBD** — not required for P0/P1; design only if the team decides to build this optional feature. Likely mirrors the tracking response above, pushed on each position/status update instead of polled.

## Stations (internal)

### GET /api/stations
Basic station info. Response body: **TBD** — likely an array of `{ stationId, name, address, lat, lng }`, needs confirmation from whoever owns `StationRepository`.

### GET /api/stations/:id/availability
Available capacity at a station. Response body: **TBD** — likely `{ stationId, robotUnitsAvailable, droneUnitsAvailable }`, needs confirmation. This is presumably what `RecommendationService` calls internally to fill `availableUnits` in the recommendations response — not necessarily exposed to the frontend directly, confirm whether the frontend calls this at all or it's backend-internal only.
