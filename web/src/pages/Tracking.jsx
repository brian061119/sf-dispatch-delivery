// Owner: Yuning Zhang (tracking). Wireframe: wireframes/08_Tracking.svg
// Placeholder: route works, implementation pending. Use api/tracking.js (getTracking).
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
