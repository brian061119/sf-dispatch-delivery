# Dispatch & Delivery product knowledge
Reviewed against project source on 2026-10-07. This file is maintained with business changes.
Source references below are for maintainers; answer users in plain language, not source paths.

## Delivery options and scope
Sources: VehicleType.java, RecommendationService.java, web/src/lib/geocode.js.
Two carrier types: ground robot (ROBOT) and drone (DRONE). Recommended options may include
fastest, economical and off-peak plans; these are plans, not additional carrier types.
Service is limited to the configured San Francisco area. Do not promise delivery outside it.
Robot default capacity: 15 kg and 0.30 cubic metres. Drone default capacity: 3 kg and 0.05 cubic metres.
Actual eligibility depends on each vehicle, package, route, battery and current availability.
A fragile label does not guarantee that every carrier will be available.

## Creating a delivery and exact locations
Sources: Dashboard.jsx, OrderWizard.jsx, geocode.js.
Log in and choose Create a new delivery. Four steps: Addresses, Package, Delivery option, Review & Pay.
For pickup and destination, enter a street number and street name, city and ZIP if known;
choose an address suggestion, or choose Set pickup on map / Set destination on map and click
an exact location inside San Francisco. Both coordinates must be verified before continuing.
A broad street or neighbourhood such as Market St or Mission is insufficient for a precise quote.
Pickup and destination must differ. A street number alone does not prove an address exists.
Provide package description and weight; enter length, width and height in cm for accurate volume,
mark fragile when applicable, and select Standard or Express. Do not invent missing dimensions.
Select an available recommended plan, review price and details, and confirm through the payment step.
Natural-language requests create a draft, not a paid order. The assistant cannot pay or submit orders.
A geocoding key is separate from the Gemini key; existing address lookup can fall back to OpenStreetMap.

## Quotes and tracking
Sources: RecommendationService.java, TrackingService.java, GuestTrack.jsx.
Exact prices, ETA, vehicle availability and personal order information require backend queries.
Weight alone is insufficient: ask for pickup and destination. Do not estimate a live price from model knowledge.
Quotes are current estimates; availability/pricing may change before confirmation. VIP rates use the authenticated user's role.
Use the random tracking code from confirmation/order details. Public tracking page: /track?code=<trackingCode>.
An order number alone is not a public tracking code. Never substitute a guessed/example code or another customer's order.
Order stages include pending payment, paid/waiting, heading to pickup, in transit, delivered and cancelled.
No current delivery status is known unless a tracking query was executed for the user's supplied code.

## Cancellation, modification, receipt and reviews
Sources: OrderService.java, OrderDetail.jsx.
Cancellation is allowed only before the package is picked up. After pickup/in transit or after delivery it is not allowed.
Before dispatch there is no dispatch cancellation fee. While the carrier heads to pickup, normal users pay
$2.50 dispatch fee (up to the paid amount); VIP users have that fee waived. The backend decides eligibility.
Use the order detail page Cancel action; the assistant cannot cancel, modify or confirm receipt.
Ordinary users can modify at most once and VIP users at most twice, before pickup and subject to backend checks.
Changing details may change price; final surcharge/refund and eligibility come from the backend.
The owner confirms receipt through order details when eligible; reviews/feedback are available in order details.

## VIP and account access
Sources: VipService.java, SecurityConfig.java.
VIP benefits: 10% delivery discount, waived $2.50 dispatch cancellation fee, up to two modifications,
10% extra cargo weight/volume tolerance and priority dispatch. Do not claim a user's membership is active
without querying authoritative data. Upgrade page: /vip. Login and register: /login and /register.
Admin console: /admin for ADMIN role only. The assistant cannot change roles, bypass authorization,
expose secrets, or inspect private orders without the proper backend permissions.

## Unknown questions and failures
Explain the relevant known facts and ask a focused clarification when needed. Do not repeat a generic
capability list for every HELP question. For facts absent here, say the available project knowledge does
not establish the answer. Do not invent insurance policies, promised delivery times, subscription prices,
customer-support contacts, real payment settlement, or GPS accuracy. For unrelated questions, briefly
explain the assistant's delivery scope. If external services are unavailable, do not claim a query succeeded.
