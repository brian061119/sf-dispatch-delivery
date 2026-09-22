// Owner: Y (order detail + confirm receipt + review). Wireframe: detail page
import PagePlaceholder from '../components/PagePlaceholder';
export default function OrderDetail() {
    return (<PagePlaceholder owner="Y" page="Order Detail" apis={[
            'getOrder(orderId)  ->  GET /api/orders/:orderId  (detail body is contract-TBD — render defensively with optional chaining)',
            'confirmReceipt(orderId)  ->  PATCH /api/orders/:orderId/confirm-receipt',
            'submitReview(orderId, { rating, comment, damageReported })  ->  POST /api/orders/:orderId/review',
        ]} todos={[
            'Static info section: addresses / package / candidate fields once the detail body is confirmed',
            'Confirm-receipt button with correct state logic (disabled after signing; note the 4-state model has no separate "arrived" state — see HANDOFF §5)',
            'Review form: rating stars + comment + damageReported switch; give success/error feedback on submit',
            'Cross-link to /tracking/:orderId ("Live tracking")',
        ]}/>);
}
