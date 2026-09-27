import { Tag } from 'antd';
// Contract OrderStatus: PENDING | IN_TRANSIT | DELIVERED | CANCELLED.
const COLORS = {
    PENDING: 'default',
    IN_TRANSIT: 'warning',
    DELIVERED: 'success',
    CANCELLED: 'error',
};
export function StatusBadge({ status }) {
    return <Tag color={COLORS[status]}>{status.replace(/_/g, ' ')}</Tag>;
}
