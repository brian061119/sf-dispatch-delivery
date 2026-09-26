# Postman collections

Import any of these into Postman (**Import** → select the file). All use `{{baseUrl}}`, default `http://localhost:8080` (collection → *Variables* tab). Seeded accounts: `admin`, `vip_user`, `normal_user`, all with password `password123`.

## One file per area — `tests/`

Each file is **self-contained** (logs in by itself, creates the order it needs) and **repeatable** (cleans up after itself). Run one with right-click → *Run collection* → *Run*.

| File | Tests | Requests / checks |
|---|---|---|
| `tests/01-login` | Login with seeded accounts, wrong/unknown credentials → 401, missing password → 400, `/api/auth/me` | 9 / 16 |
| `tests/02-signup` | Register, `role: ADMIN` ignored, duplicate username/email → 400, missing password → 400 | 7 / 12 |
| `tests/03-stations-vehicles` | Public station & vehicle reads, unknown vehicle → 404 | 9 / 14 |
| `tests/04-recommendations-quote` | Recommendations (login required), public quote | 4 / 6 |
| `tests/05-orders` | Charged price = recommended price, declined card (402), unavailable plan → 409, order lists, order detail & ownership | 18 / 35 |
| `tests/06-tracking` | Owner tracking by order number, public tracking by tracking code | 15 / 24 |
| `tests/07-confirm-receipt` | Only the owner can confirm; saved; not reverted by tracking | 13 / 24 |
| `tests/08-admin-dashboard` | Admin 200, customer 403, no login 401 | 5 / 7 |
| `tests/09-dispatch-writes` | Admin-only tick, telemetry, location, imports (all changes undone) | 19 / 30 |

Files 05–07 create one order each; their *Cleanup* folder confirms it and runs the admin simulate tick, which returns the vehicle to its station.

## All at once

| File | Use it for |
|---|---|
| `WeDelivery-Backend-Tests.postman_collection.json` | Every test above in one run (77 requests, 122 checks). Folder 0 must run first. |
| `WeDelivery.postman_collection.json` | Quick manual walkthrough of the main flow (13 requests). |

## Notes

- The tests hit whichever database the running backend uses. Signup creates real users and 05–07 create real orders: fine locally (H2 is wiped on restart), but with `SPRING_PROFILES_ACTIVE=aws` they stay in the shared AWS database.
- If *Create order* returns **409**, the chosen plan has no idle vehicle right now — run `tests/09-dispatch-writes` (the tick frees delivered vehicles) or restart the local backend.
