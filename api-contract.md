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
Poll tracking status. Response (200):
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

### WS /api/ws/orders/:orderId
Real-time push (optional stretch feature). Message shape: **TBD** — not required for P0/P1; design only if the team decides to build this optional feature. Likely mirrors the tracking response above, pushed on each position/status update instead of polled.

## Stations (internal)

**Confirmed 2026-09-26 by the dispatch module owner.** No auth required on `/api/stations/**`.
Note: the station id *is* the station number (architecture doc §4.2), and `maxCapacity` is derived as
`totalDroneBays + totalRobotBays` — there is deliberately no separate capacity column.
`/api/stations` previously returned the raw `Station` entity; the response is now the shape below
(`id` → `stationId`, plus `stationCode` / `contactPhone` / `maxCapacity`).

### GET /api/stations
Basic station info. Response (200):
```json
[
  {
    "stationId": 1,
    "stationCode": "1",
    "name": "Station 1 - SF Downtown Hub",
    "address": "500 Howard St, San Francisco, CA 94105",
    "latitude": 37.7891720,
    "longitude": -122.3970420,
    "contactPhone": "(415) 555-0101",
    "totalDroneBays": 10,
    "totalRobotBays": 15,
    "maxCapacity": 25
  }
]
```

### GET /api/stations/:id/availability
Real-time station capacity. Response (200):
```json
{
  "stationId": 1,
  "stationCode": "1",
  "name": "Station 1 - SF Downtown Hub",
  "maxCapacity": 25,
  "onSiteCount": 7,
  "capacityRemaining": 18,
  "droneCount": 3,
  "robotCount": 4,
  "droneUnitsAvailable": 2,
  "robotUnitsAvailable": 2,
  "available": true,
  "maxDroneRangeKm": 22.5,
  "maxRobotRangeKm": 60
}
```
Counting rules: `droneCount` / `robotCount` are all vehicles assigned to the station (`station_id`);
the `*UnitsAvailable` figures additionally require the vehicle to be `IDLE` **and** physically parked
there (`locationCode == stationId`). `available` means at least one dispatchable unit exists.
`maxDroneRangeKm` / `maxRobotRangeKm` are the largest `enduranceMinutes / 60 × cruiseSpeed` among the
station's dispatchable units — i.e. the reach of the station, used to decide whether it can serve a
given dropoff point. This is what `RecommendationService` uses internally for `availableUnits` in the
recommendations response; the frontend does not need to call it.

Also available: `GET /api/dispatch/stations/realtime` (all stations, same shape).

## Vehicles (internal)

**Confirmed 2026-09-26.** No auth required on `/api/vehicles/**`.
Master data is fed to the order system; realtime data is fed to the tracking system.

### GET /api/vehicles
Vehicle master data (basic info). Response (200):
```json
[
  {
    "id": 1,
    "vehicleCode": "DRONE-DT-01",
    "vehicleType": "DRONE",
    "vehicleTypeLabel": "无人机",
    "stationId": 1,
    "maxWeight": 3.00,
    "maxVolume": 0.05,
    "cruiseSpeed": 45.00,
    "enduranceMinutes": 30.00,
    "maxDeliverableDistanceKm": 22.5
  }
]
```

### GET /api/vehicles/:vehicleCode
Vehicle realtime info. Response (200):
```json
{
  "vehicleCode": "DRONE-DT-01",
  "vehicleType": "DRONE",
  "vehicleTypeLabel": "无人机",
  "status": "IDLE",
  "statusLabel": "待命",
  "locationCode": 1,
  "locationLabel": "Station 1 - SF Downtown Hub",
  "currentLat": 37.7891720,
  "currentLng": -122.3970420,
  "currentSpeed": 0.00,
  "batteryLevel": 100.00,
  "positionUpdatedAt": "2026-09-26T21:54:58",
  "statusUpdatedAt": "2026-09-26T21:54:58",
  "speedUpdatedAt": "2026-09-26T21:54:58",
  "updatedAt": "2026-09-26T21:54:58"
}
```
`status` is one of `IDLE` (待命) / `IN_DELIVERY` (配送中) / `CHARGING` (充电) / `FAULT` (故障) /
`OFFLINE` (关机). `locationCode` is `0` when the vehicle is not at any station, otherwise the station
number (1/2/3). Also available: `GET /api/vehicles/:vehicleCode/info` (master data for one vehicle).

## Dispatch ingestion & import (`/api/dispatch/**`, no auth)

These model the module's inbound boundaries — device/map feeds and master-data import. They are
machine-to-machine endpoints; the auth story for them is not settled yet (they currently sit under the
existing `/api/dispatch/**` permitAll rule).

### POST /api/dispatch/vehicles/:vehicleCode/telemetry
Machine feed: status + update info. Every field is optional; only what is sent gets applied, and each
field stamps its own update time. Request:
```json
{ "status": "FAULT", "batteryLevel": 41.5, "currentSpeed": 0, "enduranceMinutes": 30 }
```
Response: same shape as `GET /api/vehicles/:vehicleCode`.

### POST /api/dispatch/vehicles/:vehicleCode/location
Map feed: current lat/lng. `locationCode` is derived server-side (nearest station within 150 m, else 0).
Request:
```json
{ "latitude": 37.7891720, "longitude": -122.3970420, "currentSpeed": 0 }
```
Response: same shape as `GET /api/vehicles/:vehicleCode`.

### POST /api/dispatch/stations/import and POST /api/dispatch/vehicles/import
Bulk master-data import, idempotent upsert keyed on station `id` / `vehicleCode`. Request: a JSON array
of station or vehicle master records. Rows are processed independently — a bad row is reported rather
than failing the batch. Response (200):
```json
{ "created": 2, "updated": 0, "total": 2, "errors": [] }
```

### POST /api/dispatch/simulate/tick
Demo helper that stands in for real machine/map heartbeats — advances in-flight orders (reusing the
tracking interpolation), walks orphaned `IN_DELIVERY` vehicles back to their station, and tops up
`CHARGING` vehicles. Response (200):
```json
{
  "tickAt": "2026-09-26T22:02:32",
  "movedVehicles": 1,
  "returnedVehicles": 0,
  "chargedVehicles": 0,
  "vehicles": [ { "...VehicleRealtimeDto snapshot..." } ]
}
```

## Seed accounts

All three seeded accounts (`admin`, `vip_user`, `normal_user`) use password `password123`. The BCrypt
hash originally shipped in `data.sql` was a placeholder that did **not** match, which made
`/api/auth/login` return 500 — corrected 2026-09-26.
