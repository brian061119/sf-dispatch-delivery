# Legacy: standalone orders-table prototype

Kept from `main` (added through the GitHub web UI, commits `b453ad3` and `61c0ec6`).

This is a **standalone prototype** of the Order History screen: one React page, no
router, plain `fetch`, hand-written CSS, and a mock-order fallback.

## Why it is kept

It holds display logic worth porting into the new app:

| File | What to reuse |
| --- | --- |
| `orders.js` | `toDisplayOrder()` — maps backend fields (`orderNumber`, `packageDescription`, `weightKg`, `vehicleType`, `estimatedCost`) into table cells, with a mock fallback list |
| `styles.css` | Orders-table styling (status pills, cancelled row, pagination) |
| `App.jsx` | Table markup and the `statusMeta` status → label/class map |

## Rules

- This folder is **not** wired into the app. The entry point is now `web/src/main.jsx`
  (antd + react-router + zustand).
- Do **not** import from here in production code. Port what you need into
  `web/src/pages/OrderHistory.jsx` and `web/src/components/OrderCard.jsx`.
- Delete this folder only after the Order History screen is finished and the useful
  pieces above have been ported.
