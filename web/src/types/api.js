// ============================================================================
// WeDelivery — frontend <-> backend contract
//
// ⚠️ URL + method follow api-contract.md (2026-09-21 confirmed version) in the
//    team repo. This file is its frontend mirror: field shapes are expressed as
//    JSDoc @typedef, TBD parts are marked as-is — do NOT invent fields that are
//    not in the contract yet.
//
// Reference from other files:  /** @type {import('../types/api.js').OrderDetail} */
// ============================================================================

// ---- Enums -------------------------------------------------------------------

/** Delivery vehicle. @typedef {'ROBOT'|'DRONE'} VehicleType */

/** Order state machine (4 states, per contract). PENDING -> IN_TRANSIT -> DELIVERED;
 * any earlier state may -> CANCELLED.
 * Note: confirm-receipt flips status to DELIVERED — whether "DELIVERED means
 * arrived vs. signed for" is tracked as an open question in HANDOFF.md.
 * @typedef {'PENDING'|'IN_TRANSIT'|'DELIVERED'|'CANCELLED'} OrderStatus */

/** Delivery priority. @typedef {'STANDARD'|'EXPRESS'} Priority */

// ---- Base shapes (contract) ----------------------------------------------------

/**
 * Address. When addressId is non-null the backend trusts it (a saved address);
 * otherwise the detail fields are used.
 * city is fixed to San Francisco (course scenario). lat/lng are nullable
 * (null until the map picker lands).
 * @typedef {{ addressId: string|null, line1: string, city: string, zip: string, lat: number|null, lng: number|null }} ContractAddress
 */

/**
 * Package. Unit contract: weight in kg, dimensions in cm.
 * @typedef {{ description: string, weightKg: number, lengthCm: number|null, widthCm: number|null, heightCm: number|null, fragile: boolean }} ContractPackage
 */

// ---- Auth (Ziyuan Xu) ----------------------------------------------------------

/** POST /api/auth/login request body. @typedef {{ username: string, password: string }} LoginRequest */

/** User object inside the login response. @typedef {{ id: string, username: string, email: string }} UserInfo */

/** POST /api/auth/login response (200). @typedef {{ token: string, user: UserInfo }} AuthResponse */

/** POST /api/auth/register request body.
 * ⚠️ Contract TBD: body not finalized; we send {username,password,email} for now,
 * password policy to be confirmed with the team.
 * @typedef {{ username: string, password: string, email?: string }} RegisterRequest */

// ---- Recommendations (Zihang Cao) ----------------------------------------------

/** POST /api/recommendations request body.
 * @typedef {{ pickup: ContractAddress, dropoff: ContractAddress, package: ContractPackage, priority: Priority }} RecommendationsRequest */

/** One candidate delivery option. isFastest/isCheapest are computed by the
 * backend RecommendationService; the frontend only displays them.
 * Candidates with availableUnits=0 are disabled in the UI.
 * @typedef {{ candidateId: string, stationId: string, stationName: string, vehicleType: VehicleType, estimatedTimeMinutes: number, estimatedCost: number, availableUnits: number, score: number, isFastest: boolean, isCheapest: boolean }} Candidate */

/** POST /api/recommendations response (200). @typedef {{ candidates: Candidate[] }} RecommendationsResponse */

// ---- Orders (Zihang: create + list / Y: detail + receipt + review) -------------

/** POST /api/orders request body (embedded payment: pay + create in ONE call,
 * no payment callback page).
 * paymentMethodId: a real implementation would be a Stripe Elements-style token;
 * the course mock just sends a placeholder string.
 * @typedef {{ candidateId: string, pickup: ContractAddress, dropoff: ContractAddress, package: ContractPackage, priority: Priority, paymentMethodId: string }} CreateOrderRequest */

/** POST /api/orders response (201).
 * ⚠️ Contract TBD: error shapes for payment failure vs. order failure are not
 * defined; the frontend needs to distinguish them for messaging.
 * @typedef {{ orderId: string, status: OrderStatus, estimatedTimeMinutes: number, estimatedCost: number }} CreateOrderResponse */

/** GET /api/orders list item. @typedef {{ orderId: string, status: OrderStatus, createdAt: string, packageDescription: string, estimatedCost: number }} OrderSummary */

/** GET /api/orders response (200). @typedef {{ orders: OrderSummary[] }} OrderListResponse */

/** GET /api/orders/:orderId response.
 * ⚠️ Contract TBD: only confirmed to be a superset of the list item (full
 * address/package/candidate/timestamps); fields undefined.
 * The OrderDetail page renders defensively with optional chaining until the
 * contract is finalized.
 * @typedef {Object} OrderDetail
 * @property {string} orderId
 * @property {OrderStatus} status
 * @property {string} [createdAt]
 * @property {string} [packageDescription]
 * @property {number} [estimatedCost]
 * @property {ContractAddress} [pickup]
 * @property {ContractAddress} [dropoff]
 * @property {ContractPackage} [package]
 * @property {Candidate} [candidate]
 */

/** PATCH /api/orders/:orderId/confirm-receipt response (200).
 * @typedef {{ orderId: string, status: 'DELIVERED' }} ConfirmReceiptResponse */

/** POST /api/orders/:orderId/review request body. damageReported=true is the
 * damage report (details go into comment).
 * @typedef {{ rating: number, comment: string|null, damageReported: boolean }} ReviewRequest */

/** POST /api/orders/:orderId/review response (200). @typedef {{ orderId: string, reviewId: string }} ReviewResponse */

// ---- Tracking (Yuning Zhang) ---------------------------------------------------

/** GET /api/orders/:orderId/tracking response (200) — this is what the Tracking
 * page polls every 5s.
 * The contract has no route-history array; if a route polyline is needed, file a
 * request with the backend (open item).
 * @typedef {{ orderId: string, status: OrderStatus, vehicleType: VehicleType, currentLat: number, currentLng: number, estimatedArrival: string }} TrackingResponse */

// ---- Stations ------------------------------------------------------------------

/** GET /api/stations response item. ⚠️ Contract TBD (pending StationRepository owner).
 * @typedef {{ stationId: string, name: string, address?: string, lat?: number, lng?: number }} Station */

// ---- AI natural-language ordering (P1, NOT in contract) ------------------------

/**
 * ⚠️ This endpoint is proposed by the frontend for the P1 feature; it is NOT in
 * api-contract.md and only counts once a backend owner claims it.
 * Parse result = an incomplete order draft, poured into the wizard for the user
 * to verify ("AI drafts, wizard verifies").
 * @typedef {{ itemName?: string, weight?: number, fragile?: boolean }} AiParseResponse
 */

export {};
