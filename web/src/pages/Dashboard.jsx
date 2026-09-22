// Owner: Zihang Cao (orders — list) + AI card shell. Wireframe: wireframes/03_Dashboard.svg
import PagePlaceholder from '../components/PagePlaceholder';
export default function Dashboard() {
    return (<PagePlaceholder owner="Zihang Cao" page="Dashboard" wireframe="wireframes/03_Dashboard.svg" apis={[
            'useOrders().refresh()  ->  GET /api/orders  (store/orders.js, one fetch shared with History)',
            'parseOrderText(text)  ->  POST /api/ai/parse  (P1 proposal, not in contract — degrade gracefully on failure)',
            'useWizard().prefill(draft)  (pour the AI parse result into the order draft)',
        ]} todos={[
            'Greeting header + a "create order" entry that navigates to /order/new',
            'AI one-sentence order card: textarea -> parseOrderText -> prefill() -> navigate to /order/new',
            'Active deliveries list: reuse <OrderCard mini/>, filter out DELIVERED / CANCELLED',
            'Loading and empty states (no active orders)',
        ]}/>);
}
