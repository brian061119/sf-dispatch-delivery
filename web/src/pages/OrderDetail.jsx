import { App, Button, Card, Descriptions, Form, Input, Rate, Space, Switch } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { confirmReceipt, getOrder, submitReview } from '../api/order';
import { StatusBadge } from '../components/StatusBadge';
// Owner: Y (order detail + confirm receipt + review). The live map stays on
// Tracking (Yuning); this page shows the static detail + post-delivery actions
// and links over.
// Contract: GET /api/orders/:orderId body is TBD -> render defensively with ?.
export default function OrderDetailPage() {
    const { orderId = '' } = useParams();
    const { message } = App.useApp();
    const [order, setOrder] = useState();
    const load = useCallback(async () => setOrder(await getOrder(orderId)), [orderId]);
    useEffect(() => {
        load();
    }, [load]);
    if (!order)
        return null;
    // 4-state contract: confirm-receipt flips status to DELIVERED, so the button is
    // clickable until then. (Open question for the team: should "arrived, awaiting
    // receipt" be its own state? Tracked in HANDOFF.md.)
    const signed = order.status === 'DELIVERED';
    return (<Space orientation="vertical" style={{ width: '100%' }} size={16}>
      <Card title={<>
            Order #{order.orderId} <StatusBadge status={order.status}/>
          </>} extra={<Link to={`/tracking/${order.orderId}`}>Live tracking →</Link>}>
        <Descriptions column={1} size="small">
          <Descriptions.Item label="Item">{order.packageDescription ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Vehicle">{order.candidate?.vehicleType ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Cost">{order.estimatedCost != null ? `$${Number(order.estimatedCost).toFixed(2)}` : '—'}</Descriptions.Item>
          <Descriptions.Item label="Pickup">{order.pickup?.line1 ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Dropoff">{order.dropoff?.line1 ?? '—'}</Descriptions.Item>
          {order.createdAt && (<Descriptions.Item label="Created">{new Date(order.createdAt).toLocaleString()}</Descriptions.Item>)}
        </Descriptions>

        {/* Confirm receipt — PATCH /api/orders/:orderId/confirm-receipt */}
        <Button type="primary" disabled={signed || order.status === 'CANCELLED'} onClick={async () => {
            await confirmReceipt(orderId);
            message.success('Signed');
            load();
        }}>
          {signed ? 'Signed ✓' : 'Confirm receipt'}
        </Button>
      </Card>

      {/* Review + damage report — POST /api/orders/:orderId/review */}
      <Card title="Rate this delivery">
        <Form layout="vertical" onFinish={async (v) => {
            await submitReview(orderId, { rating: v.rating, comment: v.comment ?? null, damageReported: !!v.damageReported });
            message.success('Thanks for the feedback');
        }}>
          <Form.Item name="rating" label="Rating" rules={[{ required: true, message: 'Required' }]}>
            <Rate />
          </Form.Item>
          <Form.Item name="comment" label="Comment">
            <Input.TextArea rows={2}/>
          </Form.Item>
          <Form.Item name="damageReported" label="Package damaged? (damage report)" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Button type="primary" htmlType="submit">
            Submit review
          </Button>
        </Form>
      </Card>
    </Space>);
}
