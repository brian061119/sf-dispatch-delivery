import { Tag } from 'antd';
// Contract OrderStatus: PENDING | IN_TRANSIT | DELIVERED | CANCELLED.
// detailStatus may also carry the backend's internal states (PENDING_PAYMENT /
// PAID / PICKING_UP — see types/api.js InternalOrderStatus); render them with
// sensible colors instead of an unstyled tag.
const COLORS = {
    PENDING: 'default',
    // internal "not yet in transit" states → same family as PENDING
    PENDING_PAYMENT: 'default',
    PAID: 'gold',
    PICKING_UP: 'gold',
    IN_TRANSIT: 'warning',
    DELIVERED: 'success',
    CANCELLED: 'error',
};
export function StatusBadge({ status }) {
    return <Tag color={COLORS[status]}>{status.replace(/_/g, ' ')}</Tag>;
}
