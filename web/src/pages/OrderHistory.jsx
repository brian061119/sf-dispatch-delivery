// Owner: Zihang Cao (orders — list). Wireframe: wireframes/09_OrderHistory.svg
import PagePlaceholder from '../components/PagePlaceholder';
export default function OrderHistory() {
    return (<PagePlaceholder owner="Zihang Cao" page="Order History" wireframe="wireframes/09_OrderHistory.svg" apis={['useOrders()  ->  GET /api/orders  (shared store — do not refetch what Dashboard already fetched)']} todos={[
            'antd <Table> of all orders; columns = the 5 contract list-item fields (orderId / packageDescription / status / estimatedCost / createdAt)',
            'Row actions: Detail -> /order/:orderId, Track -> /tracking/:orderId',
            'Pagination + a status filter (contract 4-state)',
            'Reuse <StatusBadge/> for the status column',
        ]}/>);
}
