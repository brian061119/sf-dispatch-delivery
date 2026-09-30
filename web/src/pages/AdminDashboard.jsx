import { useEffect, useState } from "react";
import { Alert, Card, Col, Row, Spin, Statistic, Table, Tag, Typography } from "antd";
import { getAdminDashboard } from "../api/admin";
import { StatusBadge } from "../components/StatusBadge";

const { Title, Text } = Typography;

// Admin console (Role.ADMIN only — the backend enforces it on
// GET /api/admin/dashboard; a regular user sees the 403 explainer).
export default function AdminDashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [forbidden, setForbidden] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let live = true;
    getAdminDashboard()
      .then((d) => { if (live) setData(d); })
      .catch((err) => {
        if (!live) return;
        if (err?.response?.status === 403) setForbidden(true);
        else setError(err?.response?.data?.message || "Unable to load the admin dashboard.");
      })
      .finally(() => live && setLoading(false));
    return () => { live = false; };
  }, []);

  if (loading) return <div style={{ minHeight: 420, display: "grid", placeItems: "center" }}><Spin size="large" /></div>;
  if (forbidden) return <Alert type="warning" showIcon message="Admin access required" description="This console is restricted to ADMIN accounts. Your account does not have the ADMIN role." />;
  if (error) return <Alert type="error" showIcon message={error} />;
  if (!data) return null;

  const fleet = [
    { title: "Total vehicles", value: data.totalVehicles },
    { title: "Idle", value: data.idleVehicles },
    { title: "Delivering", value: data.busyVehicles },
    { title: "Charging", value: data.chargingVehicles },
    { title: "Fault", value: data.faultVehicles },
    { title: "Offline", value: data.offlineVehicles },
  ];

  return (
    <div style={{ padding: "22px 0 48px" }}>
      <Title level={2}>Admin console</Title>
      <Text type="secondary">Fleet and station overview across the SF network.</Text>

      <Row gutter={[16, 16]} style={{ marginTop: 20 }}>
        {fleet.map((f) => (
          <Col xs={12} sm={8} lg={4} key={f.title}><Card style={{ borderRadius: 12 }}><Statistic title={f.title} value={f.value ?? 0} /></Card></Col>
        ))}
      </Row>

      <Card title="Stations" style={{ borderRadius: 12, marginTop: 24 }}>
        <Table
          rowKey="stationId"
          pagination={false}
          dataSource={data.stations ?? []}
          columns={[
            { title: "Station", dataIndex: "name" },
            { title: "Address", dataIndex: "address" },
            { title: "Phone", dataIndex: "contactPhone" },
            { title: "Drone bays", dataIndex: "totalDroneBays", align: "center" },
            { title: "Robot bays", dataIndex: "totalRobotBays", align: "center" },
          ]}
        />
      </Card>

      <Card title="Recent orders" style={{ borderRadius: 12, marginTop: 24 }}>
        <Table
          rowKey="orderId"
          pagination={{ pageSize: 6 }}
          dataSource={data.recentOrders ?? []}
          columns={[
            { title: "Order", dataIndex: "orderId" },
            { title: "Package", dataIndex: "packageDescription" },
            { title: "Vehicle", dataIndex: "vehicleType", render: (v) => v ? <Tag>{v}</Tag> : "—" },
            { title: "Status", dataIndex: "status", render: (s) => <StatusBadge status={s} /> },
            { title: "Amount", dataIndex: "estimatedCost", render: (v) => `$${Number(v ?? 0).toFixed(2)}` },
            { title: "Created", dataIndex: "createdAt", render: (v) => (v ? new Date(v).toLocaleString() : "—") },
          ]}
        />
      </Card>
    </div>
  );
}
