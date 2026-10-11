# Dispatch & Delivery product knowledge
Reviewed against project source on 2026-10-11. This file is maintained with business changes.
Source references below are for maintainers; answer users in plain language, not source paths.

## Delivery options, pricing formulas and scope
Sources: VehicleType.java, RecommendationService.java, web/src/lib/geocode.js.
Two carrier types: ground robot (ROBOT) and drone (DRONE). Recommended options may include
fastest, economical (best value) and off-peak plans; these are plans, not additional carrier types.
Service is limited to the configured San Francisco area. Do not promise delivery outside it.
Robot default capacity: 15 kg and 0.30 cubic metres (VIP: 16.5 kg and 0.33 cubic metres).
Drone default capacity: 3 kg and 0.05 cubic metres (VIP: 3.3 kg and 0.055 cubic metres).
Actual eligibility depends on each vehicle, package weight/volume, route, battery reserve (at least 10% post-delivery) and current availability.
Transparent pricing formulas (before discounts):
- Drone (Fastest, ~45 km/h straight-line flight): $15.00 base + $1.80/km + $2.00/kg.
- Ground Robot (Best Value, ~12 km/h street routing): $6.00 base + $0.90/km + $0.80/kg.
- Off-Peak Plan (Eco): 15% discount off Ground Robot price, scheduled to depart 1 hour later. Stacks with VIP 10% discount.

## Creating a delivery, exact locations and payment
Sources: Dashboard.jsx, OrderWizard.jsx, geocode.js.
Log in and choose Create a new delivery. Four steps: Addresses, Package, Delivery option, Review & Pay.
For pickup and destination, enter a street number and street name, city and ZIP if known;
choose an address suggestion, or choose Set pickup on map / Set destination on map and click
an exact location inside San Francisco. Both coordinates must be verified before continuing.
A broad street or neighbourhood such as Market St or Mission is insufficient for a precise quote.
Pickup and destination must differ. A street number alone does not prove an address exists.
In Step 2 (Package), provide optional package description, weight in kg, and volume in cubic metres (m³). Do not invent missing dimensions.
Speed vs. cost is selected in Step 3 (Delivery option) by choosing Drone (fastest), Ground Robot (best value), or Off-Peak (eco), rather than a separate Standard/Express toggle.
In Step 4 (Review & Pay), confirm details and pay with a 13–19 digit Luhn-valid demo card number (for example, 4242 4242 4242 4242); cards ending in 0000 are declined by the backend to demonstrate transactional rollback.
Natural-language requests create a draft, not a paid order. The assistant cannot pay or submit orders.
A geocoding key is separate from the Gemini key; existing address lookup can fall back to OpenStreetMap.

## Quotes and tracking
Sources: RecommendationService.java, TrackingService.java, GuestTrack.jsx.
Exact live prices, ETA, vehicle availability and personal order information require backend queries.
Weight alone is insufficient for a route quote: ask for pickup and destination. Do not estimate a live route price from model knowledge alone.
Quotes are current estimates; availability/pricing may change before confirmation. VIP rates use the authenticated user's role.
Use the random tracking code from confirmation/order details. Public tracking page: /track?code=<trackingCode>.
An order number alone is not a public tracking code. Never substitute a guessed/example code or another customer's order.
Order stages include Ordered & Paid (waiting for dispatch), Machine Heading to Pickup, Package Picked Up, In Transit, Arriving at Dropoff, Delivered, and Cancelled.
No current delivery status is known unless a tracking query was executed for the user's supplied code.

## Cancellation, modification, Drone Express upgrade, receipt and reviews
Sources: OrderService.java, OrderDetail.jsx.
Cancellation is allowed only before the package is picked up. After pickup/in transit or after delivery it is not allowed.
Before dispatch there is no dispatch cancellation fee. While the carrier heads to pickup, normal users pay
$2.50 dispatch fee (up to the paid amount); VIP users have that fee waived. The backend decides eligibility.
Use the order detail page Cancel action; the assistant cannot cancel, modify, upgrade or confirm receipt.
Ordinary users can modify dropoff address at most once and VIP users at most twice, before pickup and subject to backend checks.
Changing details may change price; final surcharge/refund and eligibility come from the backend.
One-click Upgrade to Drone Express: Before pickup (while Ordered & Paid or Heading to Pickup), an existing Ground Robot order can be upgraded to Drone Express on the Order Detail page by paying the price difference, provided the package is within Drone weight/volume limits (<= 3.0 kg / 0.05 m³, or <= 3.3 kg / 0.055 m³ for VIP) and an idle drone with >= 10% estimated post-delivery battery reserve is available.
The owner confirms receipt through order details when eligible; 1–5 star reviews/feedback are available in order details after confirming receipt.

## VIP, password reset and account access
Sources: VipService.java, VipMembership.jsx, PasswordResetService.java, SecurityConfig.java, AdminDashboard.jsx.
VIP benefits: 10% delivery discount, waived $2.50 dispatch cancellation fee, up to two address modifications,
10% extra cargo weight/volume tolerance (Drone up to 3.3 kg / 0.055 m³, Robot up to 16.5 kg / 0.33 m³) and priority dispatch.
VIP subscription plans on /vip: Monthly Plan is $9.99/month (30 days) and Annual Plan is $89.99/year (365 days, save 25%), with cancel-anytime auto-renewal. ADMIN accounts cannot subscribe to VIP so their ADMIN role is preserved.
Do not claim a user's membership is active without querying authoritative data.
Login and register: /login and /register. Forgot password: users can request a one-time password reset link (valid for 30 minutes) at /forgot-password and set a new password (at least 6 characters) at /reset-password?token=<token>.
Admin console: /admin for ADMIN role only, featuring 3 tabs: Fleet Live Map (real-time drone/robot GPS, battery, and click-to-draw delivery routes), Station Bays & Orders (multi-dimensional machine search by code/type/status and order search), and User Accounts (/admin/users/:id).
The assistant cannot change roles, bypass authorization, expose secrets, or inspect private orders without the proper backend permissions.

## Unknown questions and failures
Explain the relevant known facts and ask a focused clarification when needed. Do not repeat a generic
capability list for every HELP question. For facts absent here, say the available project knowledge does
not establish the answer. Do not invent insurance policies, promised delivery times, unstated fees,
customer-support contacts, real payment settlement, or GPS accuracy. For unrelated questions, briefly
explain the assistant's delivery scope. If external services are unavailable, do not claim a query succeeded.
