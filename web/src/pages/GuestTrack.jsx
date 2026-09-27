// Owner: Yuning Zhang (tracking). Wireframe: wireframes/11_GuestTrack.svg
// Placeholder: route works, implementation pending. PUBLIC page (no login):
// RESOLVED 2026-09-26 — guests do NOT need anonymous access to the order
// endpoint anymore. The backend added GET /api/tracking/:trackingCode
// (public, SecurityConfig permitAll). Flow: guest pastes the trackingCode
// (from the order confirmation) → api/tracking.js getTrackingByCode(code).
// Fallback if they only have the order number: ask them to log in.
export default function GuestTrack() {
    return (<div style={{ padding: 48 }}>
      <h2>Track Your Order</h2>
      <p>Placeholder — owner: Yuning Zhang. Implementation pending.</p>
    </div>);
}
