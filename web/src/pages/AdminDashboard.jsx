import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, Button, Card, Col, Progress, Row, Space, Spin, Statistic, Table, Tag, Typography } from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import { getAdminDashboard } from "../api/admin";
import { StatusBadge } from "../components/StatusBadge";
import { VehicleIcon } from "../components/VehicleIcon";
import { apiErrorMessage } from "../lib/http";

const { Title, Text } = Typography;

// Admin console (Role.ADMIN only — the backend enforces it on
// GET /api/admin/dashboard; a regular user sees the 403 explainer).
//
// Everything here is live: the backend rebuilds the payload from the database
// on every call, and its SimulationScheduler advances orders / returns and
// charges vehicles every 10 s, so the page re-polls on the same cadence.
// Field names follow AdminDashboardDto (orderNumber, customerUsername,
// finalPrice, stations[].vehicles[]), NOT the customer order-list shape.
const POLL_MS = 10000;

// VehicleStatus enum → English label + tag color. statusLabel from the
// backend is Chinese, so it is not shown.
const VEHICLE_STATUS = {
  IDLE: { label: "Idle", color: "success" },
  IN_DELIVERY: { label: "Delivering", color: "processing" },
  CHARGING: { label: "Charging", color: "warning" },
  FAULT: { label: "Fault", color: "error" },
  OFFLINE: { label: "Offline", color: "default" },
};

const countBy = (vehicles, status) => vehicles.filter((v) => v.status === status).length;
const batteryColor = (pct) => (pct < 30 ? "#ff4d4f" : pct < 60 ? "#faad14" : "#52c41a");

export default function AdminDashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [forbidden, setForbidden] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [updatedAt, setUpdatedAt] = useState(null);
  const liveRef = useRef(true);

  // A failed background refresh keeps the last good data on screen and shows
  // a warning; only the very first load replaces the page with an error.
  const load = useCallback(async () => {
    setRefreshing(true);
    try {
      const d = await getAdminDashboard();
      if (!liveRef.current) return;
      setData(d);
      setError("");
      setUpdatedAt(new Date());
    } catch (err) {
      if (!liveRef.current) return;
      if (err?.response?.status === 403) setForbidden(true);
      else setError(apiErrorMessage(err, "Unable to load the admin dashboard."));
    } finally {
      if (liveRef.current) { setLoading(false); setRefreshing(false); }
    }
  }, []);

  useEffect(() => {
    liveRef.current = true;
    load();
    const timer = setInterval(load, POLL_MS);
    return () => { liveRef.current = false; clearInterval(timer); };
  }, [load]);

  if (loading) return <div style={{ minHeight: 420, display: "grid", placeItems: "center" }}><Spin size="large" /></div>;
  if (forbidden) return <Alert type="warning" showIcon message="Admin access required" description="This console is restricted to ADMIN accounts. Your account does not have the ADMIN role." />;
  if (!data) return <Alert type="error" showIcon message={error || "Unable to load the admin dashboard."} />;

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
      <Row justify="space-between" align="bottom" gutter={[12, 12]}>
        <Col>
          <Title level={2} style={{ marginBottom: 4 }}>Admin console</Title>
          <Text type="secondary">Live fleet, station and order status across the SF network.</Text>
        </Col>
        <Col>
          <Space>
            <Text type="secondary">{updatedAt ? `Updated ${updatedAt.toLocaleTimeString()} · auto-refresh every ${POLL_MS / 1000}s` : ""}</Text>
            <Button icon={<ReloadOutlined />} loading={refreshing} onClick={load}>Refresh</Button>
          </Space>
        </Col>
      </Row>

      {error && <Alert type="warning" showIcon message={`Showing the last loaded data. ${error}`} style={{ marginTop: 16 }} />}

      <Row gutter={[16, 16]} style={{ marginTop: 20 }}>
        {fleet.map((f) => (
          <Col xs={12} sm={8} lg={4} key={f.title}><Card style={{ borderRadius: 12 }}><Statistic title={f.title} value={f.value ?? 0} /></Card></Col>
        ))}
      </Row>

      <Card title="Stations" extra={<Text type="secondary">Expand a station to see its vehicles</Text>} style={{ borderRadius: 12, marginTop: 24 }}>
        <Table
          rowKey="stationId"
          pagination={false}
          scroll={{ x: 760 }}
          dataSource={data.stations ?? []}
          expandable={{ expandedRowRender: (station) => <VehicleTable vehicles={station.vehicles ?? []} />, rowExpandable: (station) => (station.vehicles ?? []).length > 0 }}
          columns={[
            { title: "Station", dataIndex: "name", render: (name, s) => <Space direction="vertical" size={0}><Text strong>{name}</Text><Text type="secondary">{s.address}</Text></Space> },
            ...["IDLE", "IN_DELIVERY", "CHARGING", "FAULT", "OFFLINE"].map((status) => ({
              title: VEHICLE_STATUS[status].label,
              align: "center",
              render: (_, s) => {
                const n = countBy(s.vehicles ?? [], status);
                return n ? <Tag color={VEHICLE_STATUS[status].color}>{n}</Tag> : <Text type="secondary">0</Text>;
              },
            })),
            { title: "Vehicles", align: "center", render: (_, s) => <Text strong>{(s.vehicles ?? []).length}</Text> },
            // Parking capacity of the station (drone bays + robot bays), not a vehicle count.
            { title: "Bays (drone / robot)", align: "center", render: (_, s) => <Text type="secondary">{s.maxCapacity ?? "—"} ({s.totalDroneBays ?? "—"} / {s.totalRobotBays ?? "—"})</Text> },
          ]}
          summary={(stations) => <StationTotals stations={stations} />}
        />
      </Card>

      <Card title="Recent orders" extra={<Text type="secondary">Latest 15, all customers</Text>} style={{ borderRadius: 12, marginTop: 24 }}>
        <Table
          rowKey="orderNumber"
          pagination={{ pageSize: 8 }}
          scroll={{ x: 900 }}
          dataSource={data.recentOrders ?? []}
          locale={{ emptyText: "No orders yet" }}
          columns={[
            { title: "Order", dataIndex: "orderNumber", render: (id) => <Text strong>{id}</Text> },
            { title: "Customer", dataIndex: "customerUsername" },
            { title: "Station", dataIndex: "stationName", render: (v) => v?.replace(/^Station \d+ - /, "") ?? "—" },
            { title: "Vehicle", render: (_, o) => o.vehicleType ? <Space size={6}><VehicleIcon vehicle={o.vehicleType} />{o.vehicleCode ?? o.vehicleType}</Space> : "—" },
            { title: "Status", dataIndex: "status", render: (s) => (s ? <StatusBadge status={s} /> : "—") },
            { title: "Amount", dataIndex: "finalPrice", render: (v) => `$${Number(v ?? 0).toFixed(2)}` },
            { title: "Created", dataIndex: "createdAt", render: (v) => (v ? new Date(v).toLocaleString() : "—") },
          ]}
        />
      </Card>
    </div>
  );
}

// Totals row: the per-status sums equal the stat cards at the top of the page.
// The leading empty cell sits under the table's expand-arrow column.
function StationTotals({ stations }) {
  const vehicles = stations.flatMap((s) => s.vehicles ?? []);
  const bays = stations.reduce((sum, s) => sum + Number(s.maxCapacity ?? 0), 0);
  return (
    <Table.Summary.Row>
      <Table.Summary.Cell index={0} />
      <Table.Summary.Cell index={1}><Text strong>All stations</Text></Table.Summary.Cell>
      {["IDLE", "IN_DELIVERY", "CHARGING", "FAULT", "OFFLINE"].map((status, i) => (
        <Table.Summary.Cell key={status} index={i + 2} align="center"><Text strong>{countBy(vehicles, status)}</Text></Table.Summary.Cell>
      ))}
      <Table.Summary.Cell index={7} align="center"><Text strong>{vehicles.length}</Text></Table.Summary.Cell>
      <Table.Summary.Cell index={8} align="center"><Text type="secondary">{bays}</Text></Table.Summary.Cell>
    </Table.Summary.Row>
  );
}

function VehicleTable({ vehicles }) {
  return (
    <Table
      rowKey="id"
      size="small"
      pagination={false}
      dataSource={vehicles}
      columns={[
        { title: "Vehicle", dataIndex: "vehicleCode", render: (code, v) => <Space size={6}><VehicleIcon vehicle={v.vehicleType} />{code}</Space> },
        { title: "Status", dataIndex: "status", render: (s) => <Tag color={VEHICLE_STATUS[s]?.color}>{VEHICLE_STATUS[s]?.label ?? s}</Tag> },
        { title: "Battery", dataIndex: "batteryLevel", width: 180, render: (b) => { const pct = Math.round(Number(b ?? 0)); return <Progress percent={pct} size="small" strokeColor={batteryColor(pct)} />; } },
        { title: "Speed", dataIndex: "currentSpeed", render: (s) => (Number(s) > 0 ? `${Number(s).toFixed(0)} km/h` : "—") },
        { title: "Location", dataIndex: "locationCode", render: (code) => (code > 0 ? "At station" : "En route") },
        { title: "Max load", dataIndex: "maxWeight", render: (w) => (w != null ? `${Number(w)} kg` : "—") },
        { title: "Updated", dataIndex: "updatedAt", render: (v) => (v ? new Date(v).toLocaleTimeString() : "—") },
      ]}
    />
  );
}
