import { App, Button, Card, Col, Empty, Input, Row, Spin, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { parseOrderText } from '../api/ai';
import { OrderCard } from '../components/OrderCard';
import { useAuth } from '../store/auth';
import { useOrders } from '../store/orders';
import { useWizard } from '../store/wizard';
// Owner: Zihang Cao (order list) + AI card shell. Wireframe: wireframes/03_Dashboard.svg
export default function Dashboard() {
    const username = useAuth((s) => s.username);
    const { list, loading, refresh } = useOrders();
    const prefill = useWizard((s) => s.prefill);
    const nav = useNavigate();
    const { message } = App.useApp();
    const [aiText, setAiText] = useState('');
    useEffect(() => {
        refresh();
    }, [refresh]);
    const active = list.filter((o) => !['DELIVERED', 'CANCELLED'].includes(o.status));
    async function onAiParse() {
        try {
            const draft = await parseOrderText(aiText);
            // Pour the parsed draft into the wizard, then jump into the create flow.
            // AI response fields map onto the contract's ContractPackage shape.
            prefill({
                pkg: draft.itemName
                    ? { description: draft.itemName, weightKg: draft.weight ?? 0, fragile: !!draft.fragile }
                    : undefined,
            });
            nav('/order/new');
        }
        catch {
            message.warning('AI could not parse that — please fill the form manually');
            nav('/order/new');
        }
    }
    return (<div>
      <Typography.Title level={3}>Hi {username} 👋</Typography.Title>
      <Row gutter={16}>
        <Col span={14}>
          <Button type="primary" size="large" block onClick={() => nav('/order/new')}>
            + Create a new delivery
          </Button>

          {/* AI one-sentence order — drafts into the wizard, wizard verifies */}
          <Card size="small" style={{ marginTop: 16, borderColor: '#b37feb' }} title="✨ Order in one sentence (AI)">
            <Input.TextArea rows={2} value={aiText} onChange={(e) => setAiText(e.target.value)} placeholder='e.g. "Send a 2kg book from home to office, express, fragile"'/>
            <Button style={{ marginTop: 8 }} onClick={onAiParse} disabled={!aiText.trim()}>
              Parse & order
            </Button>
          </Card>
        </Col>

        <Col span={10}>
          <Card size="small" title={`Active deliveries (${active.length})`}>
            {loading ? (<Spin />) : active.length ? (active.map((o) => <OrderCard key={o.orderId} order={o} mini/>)) : (<Empty description="No active delivery"/>)}
          </Card>
        </Col>
      </Row>
    </div>);
}
