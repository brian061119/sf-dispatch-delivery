import { useEffect, useMemo, useState } from "react";
import { Alert, Card, Empty, Spin, Table, Typography } from "antd";
import { Link } from "react-router-dom";
import { StatusBadge } from "../components/StatusBadge";
import { useOrders } from "../store/orders";

const { Title } = Typography;
const vehicle = (description) => /drone/i.test(description || "") ? "🚁 Drone" : "🤖 Robot";
export default function OrderHistory() {
  const { list, loading, refresh } = useOrders();
  const [error, setError] = useState("");
  useEffect(() => { refresh().catch(() => setError("Unable to load your orders.")); }, [refresh]);
  const rows = useMemo(() => list.map((order) => ({ ...order, key: order.orderId })), [list]);
  const columns = [
    { title: "Order ID", dataIndex: "orderId", render: (id) => <Link to={`/order/${id}`}>{id}</Link> },
    { title: "Date", dataIndex: "createdAt", render: (value) => value ? new Date(value).toLocaleString() : "—" },
    { title: "Package", dataIndex: "packageDescription", render: (value) => value || "Package" },
    { title: "Vehicle", dataIndex: "packageDescription", render: vehicle },
    { title: "Status", dataIndex: "status", render: (status) => <StatusBadge status={status} /> },
    { title: "Amount", dataIndex: "estimatedCost", render: (value) => `$${Number(value ?? 0).toFixed(2)}` },
    { title: "Action", render: (_, order) => order.status === "IN_TRANSIT" || order.status === "PENDING" ? order.trackingCode ? <Link to={`/track?code=${encodeURIComponent(order.trackingCode)}`}>Track →</Link> : <Link to={`/order/${order.orderId}`}>View</Link> : order.status === "DELIVERED" ? <Link to={`/order/${order.orderId}`}>Feedback</Link> : "—" },
  ];
  return <div style={{ padding: "38px 0 60px" }}><Title level={2}>My Orders</Title>{error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}<Card style={{ borderRadius: 12, minHeight: 520 }} styles={{ body: { padding: 0 } }}>
    {loading ? <div style={{ minHeight: 380, display: "grid", placeItems: "center" }}><Spin size="large" /></div> : rows.length ? <Table columns={columns} dataSource={rows} pagination={{ pageSize: 6, position: ["bottomRight"] }} scroll={{ x: 850 }} /> : <Empty style={{ paddingTop: 150 }} description="No orders yet" />}
  </Card></div>;
}
