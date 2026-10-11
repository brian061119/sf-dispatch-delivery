import { useCallback, useEffect, useState } from "react";
import { Alert, Breadcrumb, Button, Card, Col, Descriptions, Row, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import { ArrowLeftOutlined, ReloadOutlined } from "@ant-design/icons";
import { Link, useParams } from "react-router-dom";
import { getAdminUser } from "../api/admin";
import { StatusBadge } from "../components/StatusBadge";
import { VehicleIcon } from "../components/VehicleIcon";
import { apiErrorMessage } from "../lib/http";

const { Title, Text } = Typography;

// Read-only view of one customer for admins (GET /api/admin/users/:id).
// The admin stays logged in as admin. Customer pages (/order/:id, /track)
// redirect admins back to /admin, so order details are shown inline here
// (expand a row) instead of linking out.
const ROLE_COLOR = { ADMIN: "geekblue", VIP: "gold", USER: "default" };
const fmt = (v) => (v ? new Date(v).toLocaleString() : "—");
const money = (v) => `$${Number(v ?? 0).toFixed(2)}`;

export default function AdminUserDetail() {
  const { userId } = useParams();
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getAdminUser(userId));
      setError("");
    } catch (err) {
      setError(err?.response?.status === 404 ? `There is no user with id ${userId}.` : apiErrorMessage(err, "Unable to load this user."));
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => { load(); }, [load]);

  const back = <Link to="/admin?tab=users"><ArrowLeftOutlined /> Back to admin console</Link>;
  if (loading && !data) return <div style={{ minHeight: 420, display: "grid", placeItems: "center" }}><Spin size="large" /></div>;
  if (!data) return <div style={{ padding: "22px 0" }}>{back}<Alert type="error" showIcon message={error} style={{ marginTop: 16 }} /></div>;

  const { user, orders = [] } = data;
  const fullName = [user.firstName, user.lastName].filter(Boolean).join(" ");
  const spent = orders.filter((o) => o.status !== "CANCELLED").reduce((sum, o) => sum + Number(o.finalPrice ?? 0), 0);

  return (
    <div style={{ padding: "22px 0 48px" }}>
      <Breadcrumb items={[{ title: <Link to="/admin?tab=users">Admin console</Link> }, { title: "Users" }, { title: user.username }]} />

      <Row justify="space-between" align="bottom" gutter={[12, 12]} style={{ marginTop: 12 }}>
        <Col>
          <Title level={2} style={{ marginBottom: 4 }}>
            {user.username} <Tag color={ROLE_COLOR[user.role] ?? "default"} style={{ verticalAlign: "middle" }}>{user.role}</Tag>
          </Title>
          <Text type="secondary">Read-only view. You are still signed in as admin.</Text>
        </Col>
        <Col><Space>{back}<Button icon={<ReloadOutlined />} loading={loading} onClick={load}>Refresh</Button></Space></Col>
      </Row>

      {error && <Alert type="warning" showIcon message={`Showing the last loaded data. ${error}`} style={{ marginTop: 16 }} />}

      <Row gutter={[16, 16]} style={{ marginTop: 20 }}>
        <Col xs={24} lg={14}>
          <Card title="Profile" style={{ borderRadius: 12, height: "100%" }}>
            <Descriptions column={{ xs: 1, sm: 2 }} size="small">
              <Descriptions.Item label="User ID">{user.id}</Descriptions.Item>
              <Descriptions.Item label="Username">{user.username}</Descriptions.Item>
              <Descriptions.Item label="Name">{fullName || "—"}</Descriptions.Item>
              <Descriptions.Item label="Email">{user.email || "—"}</Descriptions.Item>
              <Descriptions.Item label="Role">{user.role}</Descriptions.Item>
              <Descriptions.Item label="VIP">{user.isVip ? `Yes, until ${new Date(user.vipExpireAt).toLocaleDateString()}` : user.role === "VIP" ? "Expired" : "No"}</Descriptions.Item>
              <Descriptions.Item label="Joined">{fmt(user.createdAt)}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Row gutter={[16, 16]}>
            <Col span={12}><Card style={{ borderRadius: 12 }}><Statistic title="Orders" value={user.orderCount} /></Card></Col>
            <Col span={12}><Card style={{ borderRadius: 12 }}><Statistic title="Active" value={user.activeOrderCount} /></Card></Col>
            <Col span={24}><Card style={{ borderRadius: 12 }}><Statistic title="Total spent (excl. cancelled)" value={spent} precision={2} prefix="$" /></Card></Col>
          </Row>
        </Col>
      </Row>

      <Card title={`Orders (${orders.length})`} extra={<Text type="secondary">Newest first · expand a row for details</Text>} style={{ borderRadius: 12, marginTop: 24 }}>
        <Table
          rowKey="orderNumber"
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          scroll={{ x: 760 }}
          dataSource={orders}
          locale={{ emptyText: "This user hasn't placed any orders yet" }}
          expandable={{
            expandedRowRender: (o) => (
              <Descriptions size="small" column={{ xs: 1, md: 2 }}>
                <Descriptions.Item label="Tracking code"><Text copyable>{o.trackingCode || "—"}</Text></Descriptions.Item>
                <Descriptions.Item label="Detailed status">{o.detailStatus?.replace(/_/g, " ") ?? "—"}</Descriptions.Item>
                <Descriptions.Item label="Pickup">{o.pickupAddress || "—"}</Descriptions.Item>
                <Descriptions.Item label="Destination">{o.dropoffAddress || "—"}</Descriptions.Item>
                <Descriptions.Item label="Arrived">{fmt(o.actualDeliveryTime)}</Descriptions.Item>
              </Descriptions>
            ),
          }}
          columns={[
            { title: "Order", dataIndex: "orderNumber", render: (id) => <Text strong>{id}</Text> },
            { title: "Status", dataIndex: "detailStatus", render: (s, o) => <StatusBadge status={s || o.status} /> },
            { title: "Vehicle", dataIndex: "vehicleType", render: (v) => (v ? <Space size={6}><VehicleIcon vehicle={v} />{v === "DRONE" ? "Drone" : "Robot"}</Space> : "—") },
            { title: "Amount", dataIndex: "finalPrice", align: "right", render: money },
            { title: "Created", dataIndex: "createdAt", render: fmt },
          ]}
        />
      </Card>
    </div>
  );
}
