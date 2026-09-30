import { useEffect, useMemo, useState } from "react";
import { Alert, App, Button, Card, Empty, Popconfirm, Space, Spin, Table, Typography } from "antd";
import { Link } from "react-router-dom";
import { cancelOrder } from "../api/order";
import { StatusBadge } from "../components/StatusBadge";
import { VehicleIcon } from "../components/VehicleIcon";
import { useOrders } from "../store/orders";

const { Title } = Typography;

// Vehicle column reads order.vehicleType (returned by the backend list/detail;
// mocked too). Never guess from the package description — descriptions are
// free text and "drone-shaped birthday cake" would flip the icon.
const vehicleCell = (order) => order.vehicleType
  ? <Space size={6}><VehicleIcon vehicle={order.vehicleType} />{order.vehicleType === "DRONE" ? "Drone" : "Robot"}</Space>
  : "—";

export default function OrderHistory() {
  const { message } = App.useApp();
  const { list, loading, refresh } = useOrders();
  const [error, setError] = useState("");
  const [cancelBusyId, setCancelBusyId] = useState(null);
  useEffect(() => { refresh().catch(() => setError("Unable to load your orders.")); }, [refresh]);
  const rows = useMemo(() => list.map((order) => ({ ...order, key: order.orderId })), [list]);

  async function doCancel(order) {
    setCancelBusyId(order.orderId);
    try {
      await cancelOrder(order.orderId);
      message.success(`Order ${order.orderId} cancelled.`);
      await refresh();
    } catch (err) {
      if (err?.response?.status === 404) {
        message.warning("Cancel is not supported by the backend yet (API pending).");
      } else {
        message.error(err?.response?.data?.message || "Could not cancel this order.");
      }
    } finally { setCancelBusyId(null); }
  }

  const columns = [
    { title: "Order ID", dataIndex: "orderId", render: (id) => <Link to={`/order/${id}`}>{id}</Link> },
    { title: "Date", dataIndex: "createdAt", render: (value) => value ? new Date(value).toLocaleString() : "—" },
    { title: "Package", dataIndex: "packageDescription", render: (value) => value || "Package" },
    { title: "Vehicle", render: (_, order) => vehicleCell(order) },
    { title: "Status", dataIndex: "status", render: (status) => <StatusBadge status={status} /> },
    { title: "Amount", dataIndex: "estimatedCost", render: (value) => `$${Number(value ?? 0).toFixed(2)}` },
    {
      title: "Action",
      render: (_, order) => (
        <Space size={12}>
          {order.status === "IN_TRANSIT" || order.status === "PENDING" ? (
            <>
              {order.trackingCode ? <Link to={`/track?code=${encodeURIComponent(order.trackingCode)}`}>Track →</Link> : <Link to={`/order/${order.orderId}`}>View</Link>}
              <Popconfirm title="Cancel this order?" description="This cannot be undone." okText="Cancel order" okButtonProps={{ danger: true }} onConfirm={() => doCancel(order)}>
                <Button type="link" danger size="small" loading={cancelBusyId === order.orderId} style={{ padding: 0 }}>Cancel</Button>
              </Popconfirm>
            </>
          ) : order.status === "DELIVERED" ? <Link to={`/order/${order.orderId}`}>Feedback</Link> : "—"}
        </Space>
      ),
    },
  ];
  return <div style={{ padding: "38px 0 60px" }}><Title level={2}>My Orders</Title>{error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}<Card style={{ borderRadius: 12, minHeight: 520 }} styles={{ body: { padding: 0 } }}>
    {loading ? <div style={{ minHeight: 380, display: "grid", placeItems: "center" }}><Spin size="large" /></div> : rows.length ? <Table columns={columns} dataSource={rows} pagination={{ pageSize: 6, position: ["bottomRight"] }} scroll={{ x: 900 }} /> : <Empty style={{ paddingTop: 150 }} description="No orders yet" />}
  </Card></div>;
}
