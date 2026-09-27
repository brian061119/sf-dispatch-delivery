// Owner: Yuning Zhang (tracking). Wireframe: wireframes/08_Tracking.svg
//
// SUPERSEDED 2026-09-28 — this file is no longer routed. The route
// /tracking/:code now renders pages/GuestTrack.jsx, which already implements
// everything listed below (StatusBadge + StatusTimeline + MapView, 5s polling
// that stops at terminal states, public access by trackingCode).
// Kept only so these notes are not lost; delete once Yuning confirms.
//
// Original placeholder: route works, implementation pending. Use api/tracking.js:
//  - getTracking(orderId)   — logged-in, GET /api/orders/:orderNumber/tracking
//  - getTrackingByCode(code) — PUBLIC, GET /api/tracking/:trackingCode
// Both are already normalized to the contract 4-state status by the api layer
// (the raw backend state arrives as detailStatus — feed that to StatusBadge
// if you want to show PICKING_UP etc.).
// Demo-stage spec (agreed 2026-09-23): StatusBadge + StatusTimeline + MapView courier
// position; poll every 5s ONLY while PENDING/IN_TRANSIT and STOP at
// DELIVERED/CANCELLED (terminal pages stay viewable — no more polling).
// PUBLIC page: guests get a "Log in to manage this order" hint; never link guests
// to /order/:id (detail carries PII + receipt/review actions — login required).
export default function Tracking() {
    return (<div style={{ padding: 48 }}>
      <h2>Live Tracking</h2>
      <p>Placeholder — owner: Yuning Zhang. Implementation pending.</p>
    </div>);
}
