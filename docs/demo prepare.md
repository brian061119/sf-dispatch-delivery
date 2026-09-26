# Demo Preparation — Q&A for the Project Leader

Likely questions from the project leader, with answers structured as **What / Why / How**. Section 1 is the demo script, sections 2–9 are the Q&A, and section 10 lists the hard questions about known weak spots. Prepare section 10 in particular: the honest answer plus the planned fix is the right response.

State of the code: `main` on GitHub (backend + tests). The frontend is still on feature branches.

---

## 0. 30-second pitch

> **SF Dispatch & Delivery** is a same-day delivery backend for San Francisco that dispatches **drones and ground robots** from three stations. A customer enters pickup, drop-off and package details. The system recommends delivery plans (fastest / best value / off-peak) based on the real vehicles available, locks a vehicle when the order is placed, and tracks it in real time. Anyone with the random tracking code can view it without logging in. It's a **Spring Boot REST API** on **PostgreSQL (AWS RDS)** with JWT authentication and role-based access. It's covered by **61 automated tests** and a **Postman suite with 122 checks**.

---

## 1. Demo script

### Before the demo (checklist)

- [ ] **Add the venue's IP to AWS.** Open https://checkip.amazonaws.com on the demo laptop, then add it in RDS → security group → Inbound rules: **PostgreSQL / 5432 / `<IP>/32`**. *Without this the app can't reach the database, and the demo fails.*
- [ ] Pull the latest `main` and restart the backend in IntelliJ with `SPRING_PROFILES_ACTIVE=aws`, then wait for `Started WeDeliveryApplication`.
- [ ] In Postman, import `postman/tests/*.json` (9 collections) and check `baseUrl` = `http://localhost:8080`.
- [ ] Rehearse once and run the full suite: 122/122 should pass.
- [ ] **Plan B:** if AWS or the Wi-Fi fails, remove `SPRING_PROFILES_ACTIVE=aws` and restart. The app runs on the local H2 database with the same demo data, and every step below still works.

### Live demo (about 10 minutes)

| # | Show | Postman | What to point out |
|---|---|---|---|
| 1 | Login | `01-login` → *Login admin*, *wrong password* | JWT token returned; wrong password and unknown user give the **same 401 message** |
| 2 | Signup can't create admins | `02-signup` → *role=ADMIN is ignored* + next request | Role comes back `USER`; the admin dashboard answers 403 |
| 3 | Stations & live vehicles | `03-stations-vehicles` → *Station 1 availability* | Counts come from real vehicle state, not hardcoded values |
| 4 | Recommendations | `04-recommendations-quote` → *Get recommendations* | Fastest (drone) / best value (robot) / off-peak, with price and ETA |
| 5 | Place an order | `05-orders` → *Recommendation…* then *Create order* | **Charged price = recommended price**; order ID + **random 16-character tracking code** |
| 6 | Ownership | `05-orders` → *detail (other customer) → 403* | Customers can't see each other's orders; admins can |
| 7 | Public tracking | `06-tracking` → *Public tracking by code (no login)* | No login needed; the guessable order number is rejected (404) |
| 8 | Confirm receipt | `07-confirm-receipt` | Only the owner can confirm; admins are also refused (403) |
| 9 | Dispatch / machines | `09-dispatch-writes` → *FAULT* → *availability* → *IDLE* | A vehicle reports a fault → the station has one fewer available drone → it recovers |
| 10 | Finale | Full suite → *Run collection* | **122 green checks** in about 10 seconds |

---

## 2. Architecture & tech stack

**Q: What is the architecture?**
- **What:** A layered Spring Boot REST backend. **Controllers** (HTTP) → **Services** (business logic) → **Repositories** (Spring Data JPA) → **PostgreSQL**. **Security** sits in front of the controllers as a JWT filter.
- **Why:** Each layer has one job, so changes stay local. For example, switching MySQL → PostgreSQL touched no service code.
- **How:** Main modules: auth (`AuthController`, `UserService`, `JwtUtils`), orders (`OrderController`, `OrderService`), tracking (`TrackingController`, `TrackingService`), dispatch/vehicles (`DispatchController`, `VehicleController`, `StationService`, `VehicleService`, `SimulationService`), recommendations (`RecommendationService`), admin (`AdminController`).

**Q: Which technologies, and why?**
- **What:** Java 11 target, Spring Boot 2.7, Spring Security, Spring Data JPA/Hibernate, JWT (jjwt), BCrypt, PostgreSQL 16 on AWS RDS, H2 for local development, JUnit 5 + MockMvc, Postman/Newman.
- **Why:** Mainstream, well-documented stack. Spring Boot gives security, validation, persistence and testing out of the box, so the team can focus on the dispatch logic.
- **How:** One Maven project in `server/`. The React frontend (`web/`) is developed separately.

**Q: How does the frontend talk to the backend?**
- **What:** JSON over HTTP under `/api/...`, as defined in `api-contract.md`.
- **Why:** A written contract lets frontend and backend work in parallel.
- **How:** The frontend dev server forwards `/api` to `localhost:8080`. Logged-in calls send `Authorization: Bearer <token>`.

**Q: Why do all URLs start with `/api`?**
- **What:** A prefix separating data endpoints from frontend pages.
- **Why:** With one domain, `/orders` can be a React page while `/api/orders` is the data. It also allows one proxy/load-balancer rule and security rules by prefix, and it leaves room for `/api/v2` later.
- **How:** Every controller maps under `/api/...`.

---

## 3. Database & AWS

**Q: Why PostgreSQL instead of MySQL?**
- **What:** We moved from MySQL/H2 syntax to PostgreSQL.
- **Why:**
  - **PostGIS:** this is a location-based app, so "nearest available drone" can later become one indexed database query.
  - **Stricter data validation:** better for orders and payments.
  - **Transactional schema changes:** a failed migration rolls back completely.
  - **Common default:** it's what most new projects choose.
- **How:** New PostgreSQL driver; `schema.sql` uses `GENERATED BY DEFAULT AS IDENTITY` and `TIMESTAMP`; seed data uses `INSERT … ON CONFLICT DO NOTHING`. No Java service code had to change.

**Q: How is the AWS database set up?**
- **What:** Amazon RDS PostgreSQL 16, region us-east-2 (Ohio), free-tier micro instance, database `wedelivery`.
- **Why:** A managed database: AWS handles patching, backups and availability, and the whole team shares one dataset.
- **How:**
  - *Public access* is on, but the security group only allows port 5432 from listed IPs (each teammate's `/32`).
  - SSL is required (`sslmode=require`).
  - Settings are in `application-aws.yml`.

**Q: How do you switch between local and AWS?**
- **What:** Spring profiles.
- **Why:** Developers can work offline with a throwaway database, and the demo/shared environment uses AWS.
- **How:** No profile gives in-memory **H2** in PostgreSQL mode, wiped on restart. `SPRING_PROFILES_ACTIVE=aws` gives **RDS**. Every setting can be overridden by environment variables (`DB_HOST`, `DB_PASSWORD`, `JWT_SECRET`, …).

**Q: How are schema changes applied to the existing AWS database?**
- **What:** Small migrations at the end of `schema.sql` that are safe to run repeatedly (`ALTER TABLE … ADD COLUMN IF NOT EXISTS`, `CREATE UNIQUE INDEX IF NOT EXISTS`, a backfill `UPDATE … WHERE … IS NULL`).
- **Why:** `CREATE TABLE IF NOT EXISTS` doesn't change existing tables, so new columns would never reach RDS.
- **How:** They run on every startup and do nothing once applied. Two so far:
  - **v1.1:** order tracking code.
  - **v1.2:** the teammate's 8 vehicle columns. Existing vehicles are backfilled as parked at their station with default endurance.
  - Both were tested on a copy of the RDS state before being applied to AWS.

**Q: Why don't the seed inserts use fixed IDs?**
- **What:** Demo stations, vehicles and users are inserted without `id` values.
- **Why:** In PostgreSQL, inserting explicit IDs doesn't advance the ID counter, so the first real signup would get ID 1 and fail with a duplicate key. We hit this in testing and fixed it.
- **How:** Vehicles reference their station by name. `ON CONFLICT DO NOTHING` makes restarts safe (no duplicates).

**Q: What does AWS cost?**
- **What:** Covered by the free tier for a micro instance with 20 GB of storage.
- **How:** Single-AZ, no storage autoscaling, minimal backups. Stop the instance when not needed and delete it after the project.

---

## 4. Authentication & security

**Q: How does login work?**
- **What:** Username + password → a signed **JWT** valid for 24 hours. The client sends it as `Authorization: Bearer <token>`.
- **Why:** Stateless: no server-side sessions, so it scales horizontally and suits a single-page frontend.
- **How:** Passwords are stored as **BCrypt** hashes. A filter (`JwtAuthFilter`) validates the token on each request and loads the user and role **from the database**, so role changes take effect immediately. Invalid or expired tokens are ignored, so the request is treated as anonymous.

**Q: What roles exist, and who can do what?**
- **What:** `USER`, `VIP` (10% off, 10% extra payload allowance) and `ADMIN`.
- **How:**
  - **Public:** login, signup, station/vehicle reads, quote, public tracking.
  - **Logged in:** recommendations, orders, own tracking, `/me`.
  - **Owner only:** confirm receipt.
  - **Admin only:** admin dashboard, dispatch writes (imports, telemetry, location, simulate tick).

**Q: Can someone sign up as an admin?**
- **What:** No. Signup always creates `USER`.
- **Why:** The old code accepted a `role` field, so anyone could register as `ADMIN` or get free `VIP`. We reproduced it, fixed it, and confirmed that nobody had used it on AWS.
- **How:** The `role` field was removed from the request, and any value sent is ignored. Only a developer can grant roles, via SQL (`UPDATE users SET role = 'ADMIN' …`).

**Q: Why do you return 401 sometimes and 403 other times?**
- **What:** **401** = not logged in. **403** = logged in but not allowed.
- **Why:** The frontend reacts differently: 401 → redirect to login, 403 → "you don't have access".
- **How:** Spring Security's `HttpStatusEntryPoint` sends 401. Previously everything returned 403.

**Q: How do you stop users from seeing each other's orders?**
- **What:** Ownership checks.
- **Why:** Orders contain home addresses, which are private data.
- **How:** `OrderService.getAccessibleOrder()` compares the order's `userId` with the logged-in user; only the owner or an admin gets through, otherwise 403. Confirm receipt is owner only; admins can view but not confirm on the customer's behalf.

**Q: Why does a wrong password and an unknown username give the same error?**
- **What:** Both return 401 "Invalid username or password."
- **Why:** Different messages would let an attacker find out which usernames exist.

**Q: What bugs did you fix?**
- **What / How:**
  1. **Demo accounts couldn't log in:** the seed password hash was wrong, so it was replaced.
  2. **Order detail crashed with 500:** Hibernate lazy proxies broke JSON serialization; fixed with `@JsonIgnore` on the associations.
  3. **`/me` exposed the password hash:** it now returns a DTO.
  4. **Confirm receipt didn't save:** it was never persisted; also stopped the tracking simulation from reverting it.
  5. **Errors all returned 500:** added a global error handler.

**Q: What does an error look like?**
- **What:** `{"timestamp", "status", "error", "message"}` with **400** (bad input), **401**, **403**, **404** (not found), **409** (no free vehicle).
- **Why:** One consistent format the frontend can rely on.
- **How:** `GlobalExceptionHandler` (`@RestControllerAdvice`) maps exception types to status codes.

---

## 5. Orders & tracking

**Q: What happens when a customer places an order?**
- **What:** One call, `POST /api/orders`: pick a vehicle, charge, create the order, start tracking.
- **How:** In one database transaction:
  0. Recalculate the recommendation on the server and take the chosen plan's station, vehicle type, price and times (the client never sends a price; see 10.1).
  1. Lock an idle vehicle that meets the requirements (battery ≥ 20%, payload, volume).
  2. Mock payment.
  3. Save the order with a random tracking code.
  4. Set the vehicle to `IN_DELIVERY`.
  5. Write the first tracking event.

  If any step fails, everything rolls back.

**Q: What if two customers try to book the last drone at the same moment?**
- **What:** Only one gets it; the other gets 409 "no available vehicle".
- **Why:** Prevents double-booking.
- **How:** The vehicle query uses a **pessimistic write lock** (`@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE`) inside the transaction, so the second request waits and then sees the vehicle as taken.

**Q: How does tracking work?**
- **What:** Status and position of the vehicle over the delivery.
- **How:** It's time-based. Progress = elapsed / planned time between start and estimated delivery (30 minutes by default):
  - 0–25%: to pickup
  - 25–75%: to drop-off
  - 75–100%: returning, and the order counts as **DELIVERED**
  - 100%: completed; the vehicle is parked and idle again

  The position is interpolated along the route and written back to the vehicle's live data, so the tracking endpoint and the vehicle endpoints always show the same coordinates.

**Q: What is the tracking code, and why not just use the order number?**
- **What:** Each order gets a random **16-character code** (e.g. `AL3E8TNAUEQTSTCN`). Public tracking is `GET /api/tracking/{trackingCode}`, with no login and read-only.
- **Why:** Order numbers are `SFORD` + timestamp + 3 digits, so they're easy to guess. Public tracking by order number would let anyone watch someone else's delivery and learn the drop-off location.
- **How:**
  - `SecureRandom`, 32 unambiguous characters (no 0/O/1/I): 32¹⁶ ≈ **10²⁴** possibilities.
  - A unique database index.
  - Tracking by order number (`/api/orders/{id}/tracking`) requires login as owner or admin.

**Q: Why can anyone track without logging in? Isn't that a security risk?**
- **What:** It's intentional, like a parcel tracking link you can forward to someone.
- **Why:** The recipient often doesn't have an account.
- **How:** Knowing the code is the permission. The code can't be guessed, and the endpoint only allows GET: no changes are possible.

---

## 6. Recommendations, pricing & dispatch (station/vehicle module)

> Built mainly by the backend teammate (commit `e2d1ec8`). Answer at a high level and hand over to the teammate for details.

**Q: How are delivery options recommended?**
- **What:** Up to three plans: **Fastest** (drone), **Best Value** (robot), **Off-Peak Eco** (15% off, delayed).
- **How:** The nearest station to the pickup point is chosen. Each vehicle must pass three checks:
  1. **Payload:** weight and volume within limits (VIP +10%).
  2. **Range:** round trip ≤ 90% of endurance × max speed.
  3. **Battery:** predicted use ≤ 90% and ≥ 10% left at return, weighted by load.

  `availableUnits` is the number of vehicles that actually pass.

**Q: How is the price calculated?**
- **What:** Base + per km × round-trip distance + per kg × weight.
  - **Drone:** $15 + $1.80/km + $2.00/kg.
  - **Robot:** $6 + $0.90/km + $0.80/kg.
  - **Discounts:** VIP 10%; off-peak 15%; they stack.
- **How:** Distances use the Haversine (straight-line) formula.

**Q: How do real drones and robots connect?**
- **What:** Machine endpoints: `POST /api/dispatch/vehicles/{code}/telemetry` (status, battery, speed, endurance) and `/location` (latitude/longitude; the station is worked out from the coordinates).
- **Why:** Station availability and recommendations are then based on live vehicle state. For example, a vehicle reporting `FAULT` is immediately no longer offered.
- **How:** Admin-only for now. Without real hardware, `POST /api/dispatch/simulate/tick` advances the simulation: moves vehicles, charges batteries, and returns finished vehicles to their station.

**Q: Why are the dispatch write endpoints admin-only?**
- **What:** Imports, telemetry, location and simulate tick require the ADMIN role. Reads and quotes stay public.
- **Why:** After the merge they required no login at all. Anyone could overwrite station/vehicle data or mark vehicles as faulty.
- **How:** A `SecurityConfig` rule, proven by 16 dedicated tests (401 / 403 / 200).

---

## 7. Testing

**Q: How do you know it works?**
- **What:** Three layers:
  1. **61 JUnit tests** (40 from the teammate + 16 access-control + 5 order-pricing tests), using MockMvc against an in-memory database.
  2. **Postman suite:** 77 requests, **122 automatic checks**, plus the same tests split into 9 per-area collections.
  3. **Manual checks against real PostgreSQL**, including a copy of the AWS database's earlier state, to test the migrations.
- **Why:** Unit/integration tests catch regressions in code; Postman tests the real running HTTP API end to end, the same way the frontend uses it.
- **How:** Run `mvn test` in `server/`. For Postman, *Run collection*, or the command-line runner `newman run postman/WeDelivery-Backend-Tests.postman_collection.json`. The Postman tests clean up after themselves, so they can be run repeatedly.

**Q: What does the Postman suite cover?**
- Login, signup (role ignored, duplicates), public reads, recommendations, order ownership, owner vs public tracking, owner-only confirm receipt, admin dashboard, admin-only dispatch writes. Every allowed *and* forbidden case is checked (200/401/403/404/400).

---

## 8. Teamwork & the merge incident

**Q: What happened with the teammate's commit?**
- **What:** Commit `e2d1ec8` added the station/vehicle module but also **deleted 39 files** (including `pom.xml`, the main class, authentication and orders), so `main` couldn't build.
- **Why it happened:** A "changed files only" folder was uploaded as the whole repository. Git treated every file not in the folder as deleted. The commit's own notes said nothing was meant to be deleted.
- **How it was fixed (nothing lost, no force-push):**
  1. Saved my work on its own branch.
  2. Restored the 39 files on top of his commit. All 40 of his tests then passed, which proved the repair was complete.
  3. Merged my branch and resolved 4 conflicts (security config, tracking service, schema, seed data), keeping both sides' changes.
  4. Adapted 2 of his tests to the new security rules (401 instead of 403; tracking via the owner endpoint).
  5. Converted his SQL to PostgreSQL and added the migration for AWS.
  6. Verified everything, then pushed.

**Q: What did the team learn?**
- Commit through Git (branches + pull requests) instead of uploading folders.
- Pull/clone fresh before starting work.
- Run the tests before pushing.

**Q: How is the team's code merged in the future?**
- Feature branches → pull request → someone else reviews → tests pass → merge to `main`. The frontend branches (`auth`, `YitingQi`) and `boyuan/tracking` are next.

---

## 9. Roadmap / what's next

| Next step | Why |
|---|---|
| Merge the frontend branches | The guest tracking page must switch to `/api/tracking/{trackingCode}` |
| Device keys for vehicle telemetry | Real machines shouldn't use an admin login |
| Flyway for migrations | Versioned, auditable schema changes |
| PostGIS nearest-vehicle queries | The main reason we chose PostgreSQL |
| Credentials into environment variables / AWS Secrets Manager; rotate the database password | See 10.2 |
| Rate limiting on login | Slows password guessing (10.6) |

---

## 10. Hard questions — know these answers

**10.1 "Does the customer pay the price the recommendation showed? Can a client change the price?"**
- **What:** Yes, the charged price always equals the recommended price. No, the client can't set it.
- **Why:** Earlier, `POST /api/orders` ignored the chosen plan (always station 1, a fixed $18.50, fixed ETA), and an older `/api/orders/checkout` endpoint even accepted the price from the request body. Both were fixed before the demo.
- **How:** On order creation the server **recalculates the recommendation** from the same pickup, drop-off and package, picks the plan with the chosen `candidateId`, and uses *its* station, vehicle type, distance, price (including VIP/off-peak discounts) and delivery times. If that plan is no longer available (e.g. the last vehicle was just booked), it returns **409** "Selected plan … is no longer available. Please refresh the recommendations." The unused `/checkout` endpoint was removed. Proven by 5 JUnit tests (regular, VIP, drone, station 3, unknown plan) and Postman checks.
- **Known remaining gap:** the vehicle actually locked is the idle one with the most battery at that station, which may differ from the specific vehicle the recommendation evaluated. Price, station and type always match.

**10.2 "Why are the database password and JWT secret in a public GitHub repo?"**
- **Honest answer:** A team decision for convenience during development.
- **Mitigation:** The database only accepts connections from IPs on the allowlist.
- **Production fix:** Environment variables or AWS Secrets Manager, a new database password and JWT secret, and remove them from the file (the old values stay in Git history, which is why rotating them is required).

**10.3 "How does logout work?"**
- **Honest answer:** JWTs are stateless. Logout is done by the client deleting the token; the server can't revoke it before its 24-hour expiry.
- **Fix:** Short-lived access tokens + refresh tokens, or a server-side deny-list.

**10.4 "Is payment real?"**
- **Answer:** No, it's a mock (`MockPaymentServiceImpl`); a card ending in `0000` is declined. The contract endpoint currently uses a fixed test card, so orders always succeed. The error format for declined payments is still TBD in `api-contract.md` (currently 500).

**10.5 "Can other websites call your API?"**
- **Honest answer:** CORS currently allows all origins, for development convenience.
- **Fix:** Restrict it to the frontend's domain before going live.

**10.6 "Can someone brute-force passwords?"**
- **Answer:** Passwords are BCrypt-hashed (slow to crack), and the error message doesn't reveal whether a username exists. There's **no rate limiting yet**; next step is limiting login attempts per IP/account.

**10.7 "What if AWS is down during the demo?"**
- **Answer:** Restart without the `aws` profile. It runs on local H2 with the same demo data, and the entire demo and test suite work the same.

**10.8 "Why not use Flyway/Liquibase for migrations?"**
- **Answer:** For two small migrations, SQL that's safe to rerun was the lightest option. It's the planned next step once the schema changes more often.

**10.9 "Is the H2 console a security hole?"**
- **Answer:** It's only enabled with the local (default) profile and is disabled in the `aws` profile.

**10.10 "Why is `/api/recommendations` login-only when the quote endpoint is public?"**
- **Answer:** Recommendations use the user's role (VIP discount and payload allowance), so they need to know who's asking. The older quote endpoint was kept public for backward compatibility.

---

## Numbers cheat sheet

| | |
|---|---|
| Stations / demo vehicles | 3 / 15 (drones + robots, all 5 statuses) |
| Roles | USER, VIP, ADMIN |
| Token lifetime | 24 hours |
| Tracking code | 16 characters, ~10²⁴ combinations |
| JUnit tests | 61 (40 teammate + 16 access control + 5 order pricing) |
| Postman | 77 requests / 122 checks (full suite); 9 per-area collections |
| AWS | RDS PostgreSQL 16, us-east-2, free tier, IP allowlist + SSL |
| Accidentally deleted files restored | 39 |
| Merge conflicts resolved | 4 files |
