import { Button, Card, Space, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { StatusBadge } from './StatusBadge';
// One card = one order. Reused by Dashboard (active list) and History.
// Fields follow the contract list item: orderId / status / createdAt /
// packageDescription / estimatedCost (no vehicleType in the list payload).
export function OrderCard({ order, mini }) {
    return (<Card size="small" style={{ marginBottom: 12 }}>
      <Space orientation="vertical" style={{ width: '100%' }}>
        <Space style={{ justifyContent: 'space-between', width: '100%' }}>
          <Typography.Text strong>
            #{order.orderId} · {order.packageDescription}
          </Typography.Text>
          <StatusBadge status={order.status}/>
        </Space>
        <Space size="middle">
          <span>${Number(order.estimatedCost).toFixed(2)}</span>
          {order.createdAt && <span>{new Date(order.createdAt).toLocaleString()}</span>}
        </Space>
        <Link to={`/tracking/${order.orderId}`}>
          <Button type="primary" size="small" block={mini}>
            Track
          </Button>
        </Link>
      </Space>
    </Card>);
}
