// Owner: Yuning Zhang (tracking). No wireframe yet — simple order-number form.
// Placeholder: route works, implementation pending. PUBLIC page (no login):
// guest enters an order number, then navigate(`/tracking/${orderNo}`).
// Reuses api/tracking.js (getTracking) — requires the backend to allow
// anonymous reads on the tracking endpoint (see HANDOFF.md open items).
export default function GuestTrack() {
    return (<div style={{ padding: 48 }}>
      <h2>Track Your Order</h2>
      <p>Placeholder — owner: Yuning Zhang. Implementation pending.</p>
    </div>);
}
