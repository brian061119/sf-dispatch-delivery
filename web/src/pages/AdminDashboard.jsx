import { useCallback, useEffect, useRef, useState } from "react";
import { Alert, Button, Card, Col, Input, Progress, Row, Select, Space, Spin, Statistic, Table, Tabs, Tag, Tooltip, Typography } from "antd";
import { ClearOutlined, CompassOutlined, ReloadOutlined, SearchOutlined, TableOutlined, UserOutlined } from "@ant-design/icons";
import { Link, useSearchParams } from "react-router-dom";
import { getAdminDashboard, getAdminUsers } from "../api/admin";
import { StatusBadge } from "../components/StatusBadge";
import { VehicleIcon } from "../components/VehicleIcon";
import { AdminFleetMap } from "../components/AdminFleetMap";
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
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTabKey = searchParams.get("tab") || "map";
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [forbidden, setForbidden] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [updatedAt, setUpdatedAt] = useState(null);
  const [users, setUsers] = useState(null);
  const [usersError, setUsersError] = useState("");
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

      // Loaded separately so a failing user list (e.g. an older backend
      // without /api/admin/users) never takes the rest of the console down.
      try {
        const u = await getAdminUsers();
        if (liveRef.current) { setUsers(u); setUsersError(""); }
      } catch (err) {
        if (liveRef.current) setUsersError(apiErrorMessage(err, "Unable to load users."));
      }
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

      <div style={{ marginTop: 24 }}>
        <Tabs
          activeKey={activeTabKey}
          onChange={(key) => setSearchParams(key === "map" ? {} : { tab: key })}
          type="card"
          items={[
            {
              key: "map",
              label: (
                <span style={{ fontSize: 14, fontWeight: 600 }}>
                  <CompassOutlined style={{ marginRight: 6 }} />
                  Fleet Live Map
                </span>
              ),
              children: (
                <div style={{ marginTop: 8 }}>
                  <AdminFleetMap
                    stations={data.stations ?? []}
                    onRefresh={load}
                    refreshing={refreshing}
                  />
                </div>
              ),
            },
            {
              key: "tables",
              label: (
                <span style={{ fontSize: 14, fontWeight: 600 }}>
                  <TableOutlined style={{ marginRight: 6 }} />
                  Station Bays & Orders
                </span>
              ),
              children: (
                <div style={{ marginTop: 8 }}>
                  <StationsCard stations={data.stations ?? []} />
                  <RecentOrdersCard orders={data.recentOrders ?? []} />
                </div>
              ),
            },
            {
              key: "users",
              label: (
                <span style={{ fontSize: 14, fontWeight: 600 }}>
                  <UserOutlined style={{ marginRight: 6 }} />
                  {`User Accounts${users ? ` (${users.length})` : ""}`}
                </span>
              ),
              children: (
                <div style={{ marginTop: 8 }}>
                  <UsersCard users={users} error={usersError} />
                </div>
              ),
            },
          ]}
        />
      </div>
    </div>
  );
}

// Users: every account with its order count. Clicking a username opens the
// read-only user view (/admin/users/:id); the admin stays logged in as admin.
const ROLE_COLOR = { ADMIN: "geekblue", VIP: "gold", USER: "default" };

function UsersCard({ users, error }) {
  const [query, setQuery] = useState("");
  const q = query.trim().toLowerCase();
  const rows = (users ?? []).filter((u) => !q || [u.username, u.email, u.firstName, u.lastName].some((v) => v?.toLowerCase().includes(q)));
  return (
    <Card
      title={`Users${users ? ` (${users.length})` : ""}`}
      extra={<Input allowClear size="small" prefix={<SearchOutlined />} placeholder="Search name or email" value={query} onChange={(e) => setQuery(e.target.value)} style={{ width: 220 }} />}
      style={{ borderRadius: 12 }}
    >
      {error && <Alert type="warning" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Table
        rowKey="id"
        size="middle"
        loading={!users && !error}
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
        scroll={{ x: 720 }}
        dataSource={rows}
        locale={{ emptyText: q ? "No users match your search" : "No users" }}
        columns={[
          { title: "ID", dataIndex: "id", width: 70, sorter: (a, b) => a.id - b.id },
          {
            title: "User",
            dataIndex: "username",
            sorter: (a, b) => a.username.localeCompare(b.username),
            render: (name, u) => {
              const fullName = [u.firstName, u.lastName].filter(Boolean).join(" ");
              return (
                <Space direction="vertical" size={0}>
                  <Link to={`/admin/users/${u.id}`}><strong>{name}</strong></Link>
                  {fullName && <Text type="secondary">{fullName}</Text>}
                </Space>
              );
            },
          },
          { title: "Email", dataIndex: "email", render: (v) => v || "—" },
          {
            title: "Role",
            dataIndex: "role",
            render: (role, u) => <Space size={4}><Tag color={ROLE_COLOR[role] ?? "default"}>{role}</Tag>{role === "VIP" && !u.isVip && <Tag>expired</Tag>}</Space>,
          },
          {
            title: "Orders",
            dataIndex: "orderCount",
            align: "center",
            defaultSortOrder: "descend",
            sorter: (a, b) => a.orderCount - b.orderCount,
            render: (n, u) => <Space size={6}><Text strong>{n}</Text>{u.activeOrderCount > 0 && <Text type="secondary">· {u.activeOrderCount} active</Text>}</Space>,
          },
          { title: "Joined", dataIndex: "createdAt", render: (v) => (v ? new Date(v).toLocaleDateString() : "—") },
        ]}
      />
    </Card>
  );
}

function StationsCard({ stations }) {
  const [query, setQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [expandedKeys, setExpandedKeys] = useState([]);

  const q = query.trim().toLowerCase();
  const isFiltered = Boolean(q || typeFilter !== "ALL" || statusFilter !== "ALL");

  const resetFilters = () => {
    setQuery("");
    setTypeFilter("ALL");
    setStatusFilter("ALL");
  };

  const matchesVehicle = (v) => {
    if (typeFilter !== "ALL" && v.vehicleType !== typeFilter) return false;
    if (statusFilter !== "ALL" && v.status !== statusFilter) return false;
    if (q) {
      const codeMatch = v.vehicleCode?.toLowerCase().includes(q);
      const typeMatch = v.vehicleType?.toLowerCase().includes(q);
      const statusLabel = VEHICLE_STATUS[v.status]?.label?.toLowerCase() ?? "";
      const statusMatch = statusLabel.includes(q) || v.status?.toLowerCase().includes(q);
      const stationMatch = (v.stationName || "").toLowerCase().includes(q);
      return codeMatch || typeMatch || statusMatch || stationMatch;
    }
    return true;
  };

  // Stations matching the filters
  const stationRows = (stations ?? []).filter((s) => {
    const stationNameMatch = q && [s.name, s.address].some((txt) => txt?.toLowerCase().includes(q));
    if (stationNameMatch && typeFilter === "ALL" && statusFilter === "ALL") {
      return true;
    }
    return (s.vehicles ?? []).some(matchesVehicle);
  });

  // Count total matching vehicles across all stations
  const totalMatchingVehicles = (stations ?? []).reduce(
    (sum, s) => sum + (s.vehicles ?? []).filter(matchesVehicle).length,
    0
  );
  const totalVehicles = (stations ?? []).reduce((sum, s) => sum + (s.vehicles ?? []).length, 0);

  // Auto-expand stations when any vehicle filter is active
  useEffect(() => {
    if (isFiltered) {
      setExpandedKeys(stationRows.map((s) => s.stationId));
    }
  }, [query, typeFilter, statusFilter]);

  return (
    <Card
      title={`Stations (${stationRows.length}${stations ? ` / ${stations.length}` : ""})`}
      extra={
        <Space wrap>
          <Input
            allowClear
            size="small"
            prefix={<SearchOutlined />}
            placeholder="Search machine (e.g. D-101), station..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ width: 260 }}
          />
          <Select
            size="small"
            value={typeFilter}
            onChange={setTypeFilter}
            style={{ width: 120 }}
            options={[
              { label: "All types", value: "ALL" },
              { label: "🚁 Drone", value: "DRONE" },
              { label: "🤖 Robot", value: "ROBOT" },
            ]}
          />
          <Select
            size="small"
            value={statusFilter}
            onChange={setStatusFilter}
            style={{ width: 130 }}
            options={[
              { label: "All statuses", value: "ALL" },
              { label: "🟢 Idle", value: "IDLE" },
              { label: "🔵 Delivering", value: "IN_DELIVERY" },
              { label: "🟡 Charging", value: "CHARGING" },
              { label: "🔴 Fault", value: "FAULT" },
              { label: "⚪ Offline", value: "OFFLINE" },
            ]}
          />
          {isFiltered && (
            <Button size="small" icon={<ClearOutlined />} onClick={resetFilters}>
              Reset
            </Button>
          )}
        </Space>
      }
      style={{ borderRadius: 12 }}
    >
      {isFiltered && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={
            <Space wrap>
              <span>
                Found <strong>{totalMatchingVehicles}</strong> matching vehicle{totalMatchingVehicles === 1 ? "" : "s"} across{" "}
                <strong>{stationRows.length}</strong> station{stationRows.length === 1 ? "" : "s"} (out of {totalVehicles} total vehicles).
              </span>
              <Text type="secondary">Matching stations have been expanded automatically.</Text>
            </Space>
          }
        />
      )}

      <Table
        rowKey="stationId"
        pagination={false}
        scroll={{ x: 760 }}
        dataSource={stationRows}
        expandedRowKeys={expandedKeys}
        onExpandedRowsChange={setExpandedKeys}
        locale={{ emptyText: isFiltered ? "No stations or machines match your search" : "No stations" }}
        expandable={{
          expandedRowRender: (station) => {
            const stationNameMatch = q && [station.name, station.address].some((txt) => txt?.toLowerCase().includes(q));
            const visible = stationNameMatch && typeFilter === "ALL" && statusFilter === "ALL"
              ? station.vehicles ?? []
              : (station.vehicles ?? []).filter(matchesVehicle);

            return (
              <div>
                {isFiltered && (
                  <Text type="secondary" style={{ display: "block", marginBottom: 8, fontSize: 12 }}>
                    Showing {visible.length} of {(station.vehicles ?? []).length} vehicles in this station
                  </Text>
                )}
                <VehicleTable vehicles={visible} />
              </div>
            );
          },
          rowExpandable: (station) => (station.vehicles ?? []).length > 0,
        }}
        columns={[
          {
            title: "Station",
            dataIndex: "name",
            render: (name, s) => (
              <Space direction="vertical" size={0}>
                <Text strong>{name}</Text>
                <Text type="secondary">{s.address}</Text>
              </Space>
            ),
          },
          ...["IDLE", "IN_DELIVERY", "CHARGING", "FAULT", "OFFLINE"].map((status) => ({
            title: VEHICLE_STATUS[status].label,
            align: "center",
            render: (_, s) => {
              const list = isFiltered
                ? (s.vehicles ?? []).filter(matchesVehicle)
                : (s.vehicles ?? []);
              const n = countBy(list, status);
              return n ? <Tag color={VEHICLE_STATUS[status].color}>{n}</Tag> : <Text type="secondary">0</Text>;
            },
          })),
          {
            title: "Vehicles",
            align: "center",
            render: (_, s) => {
              const count = isFiltered
                ? (s.vehicles ?? []).filter(matchesVehicle).length
                : (s.vehicles ?? []).length;
              return <Text strong>{count}</Text>;
            },
          },
          {
            title: "Bays (drone / robot)",
            align: "center",
            render: (_, s) => (
              <Text type="secondary">
                {s.maxCapacity ?? "—"} ({s.totalDroneBays ?? "—"} / {s.totalRobotBays ?? "—"})
              </Text>
            ),
          },
        ]}
        summary={() => <StationTotals stations={stationRows} />}
      />
    </Card>
  );
}

function RecentOrdersCard({ orders }) {
  const [query, setQuery] = useState("");
  const q = query.trim().toLowerCase();
  const rows = (orders ?? []).filter(
    (o) =>
      !q ||
      [o.orderNumber, o.customerUsername, o.stationName, o.vehicleCode, o.status].some((v) =>
        v?.toLowerCase().includes(q)
      )
  );

  return (
    <Card
      title={`Recent orders${orders ? ` (${orders.length})` : ""}`}
      extra={
        <Space wrap>
          <Input
            allowClear
            size="small"
            prefix={<SearchOutlined />}
            placeholder="Search order, customer, station, vehicle..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            style={{ width: 280 }}
          />
          <Text type="secondary">Latest 15, all customers</Text>
        </Space>
      }
      style={{ borderRadius: 12, marginTop: 24 }}
    >
      <Table
        rowKey="orderNumber"
        pagination={{ pageSize: 8 }}
        scroll={{ x: 900 }}
        dataSource={rows}
        locale={{ emptyText: q ? "No orders match your search" : "No orders yet" }}
        columns={[
          { title: "Order", dataIndex: "orderNumber", render: (id) => <Text strong>{id}</Text> },
          { title: "Customer", dataIndex: "customerUsername" },
          { title: "Station", dataIndex: "stationName", render: (v) => v?.replace(/^Station \d+ - /, "") ?? "—" },
          {
            title: "Vehicle",
            render: (_, o) =>
              o.vehicleType ? (
                <Space size={6}>
                  <VehicleIcon vehicle={o.vehicleType} />
                  {o.vehicleCode ?? o.vehicleType}
                </Space>
              ) : (
                "—"
              ),
          },
          { title: "Status", dataIndex: "status", render: (s) => (s ? <StatusBadge status={s} /> : "—") },
          { title: "Amount", dataIndex: "finalPrice", render: (v) => `$${Number(v ?? 0).toFixed(2)}` },
          { title: "Created", dataIndex: "createdAt", render: (v) => (v ? new Date(v).toLocaleString() : "—") },
        ]}
      />
    </Card>
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
