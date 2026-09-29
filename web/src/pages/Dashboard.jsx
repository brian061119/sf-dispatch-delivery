import { useEffect, useState } from "react";
import { Alert, Button, Card, Col, Empty, Input, Row, Space, Spin, Typography } from "antd";
import { Link, useNavigate } from "react-router-dom";
import { parseOrderText } from "../api/ai";
import { StatusBadge } from "../components/StatusBadge";
import { getUsername } from "../lib/auth";
import { useOrders } from "../store/orders";
import { useWizard } from "../store/wizard";

const { Title, Text } = Typography;
const isActive = (order) => ["PENDING", "IN_TRANSIT"].includes(order.status);
const trackingHref = (order) => order.trackingCode ? `/track?code=${encodeURIComponent(order.trackingCode)}` : `/order/${order.orderId}`;

export default function Dashboard() {
  const navigate = useNavigate();
  const { list, loading, refresh } = useOrders();
  const wizard = useWizard();
  const [note, setNote] = useState("");
  const [aiBusy, setAiBusy] = useState(false);
  const [error, setError] = useState("");
  const username = getUsername() || "there";
  useEffect(() => { refresh().catch(() => setError("Unable to load your orders.")); }, [refresh]);
  async function startFromText() {
    if (!note.trim()) return;
    setAiBusy(true); setError("");
    try {
      const draft = await parseOrderText(note);
      wizard.prefill({ pkg: { description: draft.itemName || "", weightKg: draft.weight, fragile: !!draft.fragile } });
      navigate("/order/new");
    } catch { setError("Could not read that request. You can still create a delivery manually."); }
    finally { setAiBusy(false); }
  }
  const activeOrders = list.filter(isActive);
  const recentOrders = list.filter((order) => !isActive(order)).slice(0, 4);
  return <div style={page}>
    <Title level={2} style={{ marginBottom: 4 }}>Good afternoon, {username} 👋</Title>
    <Text type="secondary">Manage deliveries, track active orders, and create a new shipment.</Text>
    <Button type="primary" size="large" block onClick={() => navigate("/order/new")} style={createButton}>+ Create a new delivery</Button>
    <Card style={aiCard} size="small" title="🤖 Describe a delivery in one sentence (optional)">
      <Space.Compact style={{ width: "100%" }}><Input value={note} onChange={(event) => setNote(event.target.value)} placeholder="e.g. Send a 2kg box from Market St to Mission St, express and fragile" onPressEnter={startFromText} /><Button type="primary" onClick={startFromText} loading={aiBusy}>Start order →</Button></Space.Compact>
    </Card>
    {error && <Alert type="error" showIcon message={error} style={{ marginTop: 18 }} />}
    <Row gutter={[24, 24]} style={{ marginTop: 22 }}>
      <Col xs={24} lg={15}><Card title={`Active deliveries (${activeOrders.length})`} style={cardStyle} extra={<Link to="/orders">View all →</Link>}>
        {loading ? <CenterSpin /> : activeOrders.length ? <Space direction="vertical" style={{ width: "100%" }} size={16}>{activeOrders.map((order) => <ActiveOrder key={order.orderId} order={order} />)}</Space> : <Empty description="No active deliveries" />}
      </Card></Col>
      <Col xs={24} lg={9}><Card title="Quick track" style={cardStyle}><QuickTrack /></Card></Col>
    </Row>
    <Card title="Recent orders" extra={<Link to="/orders">View all →</Link>} style={{ ...cardStyle, marginTop: 24 }}>
      {loading ? <CenterSpin /> : recentOrders.length ? <Space direction="vertical" size={14} style={{ width: "100%" }}>{recentOrders.map((order) => <Row key={order.orderId} align="middle" gutter={12}><Col flex="auto"><Link to={`/order/${order.orderId}`} style={{ color: "inherit" }}>#{order.orderId} · {order.packageDescription}</Link></Col><Col><StatusBadge status={order.status} /></Col><Col>${Number(order.estimatedCost ?? 0).toFixed(2)}</Col></Row>)}</Space> : <Empty description="No recent orders" />}
    </Card>
  </div>;
}
function ActiveOrder({ order }) { return <Card size="small"><Row align="middle" gutter={12}><Col flex="auto"><Space direction="vertical" size={3}><Text strong>#{order.orderId} · {order.packageDescription}</Text><Text type="secondary">Created {order.createdAt ? new Date(order.createdAt).toLocaleString() : "recently"}</Text><StatusBadge status={order.status} /></Space></Col><Col><Link to={trackingHref(order)}><Button type="primary">Track →</Button></Link></Col></Row></Card>; }
function QuickTrack() { const navigate = useNavigate(); const [code, setCode] = useState(""); const go = () => code.trim() && navigate(`/track?code=${encodeURIComponent(code.trim())}`); return <Space direction="vertical" style={{ width: "100%" }}><Input value={code} onChange={(event) => setCode(event.target.value)} placeholder="Enter tracking code" onPressEnter={go} /><Button block onClick={go}>Track</Button><Text type="secondary">A tracking code can be shared with the recipient without requiring an account.</Text></Space>; }
function CenterSpin() { return <div style={{ minHeight: 120, display: "grid", placeItems: "center" }}><Spin /></div>; }
const page = { padding: "22px 0 40px" };
const cardStyle = { borderRadius: 12 };
const createButton = { marginTop: 22, height: 64, fontSize: 18, borderRadius: 12 };
const aiCard = { marginTop: 20, border: "2px solid #722ed1", borderRadius: 12 };
