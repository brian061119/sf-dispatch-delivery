import { useEffect, useState } from "react";
import { Alert, Button, Card, Col, Descriptions, Form, Input, Popconfirm, Rate, Row, Spin, Switch, Typography } from "antd";
import { Link, useParams } from "react-router-dom";
import { cancelOrder, confirmReceipt, getOrder, submitReview, updateOrder } from "../api/order";
import { normalizeStatus } from "../api/tracking";
import { StatusBadge } from "../components/StatusBadge";

const { Title, Text } = Typography;
const formatAddress = (value) => value?.line1 ? [value.line1, value.city, value.zip].filter(Boolean).join(", ") : "Address details are unavailable";
const cardStyle = { borderRadius: 12, minHeight: 220 };
// GET /api/orders/:id returns the RAW order record (orderNumber, pickupAddress,
// packageWeight, vehicleType, finalPrice, 6-state status) while the mock returns
// the contract shape — map both onto the fields this page renders.
const toView = (raw) => ({
  ...raw,
  orderId: raw.orderId ?? raw.orderNumber,
  status: normalizeStatus(raw.status),
  pickup: raw.pickup ?? (raw.pickupAddress ? { line1: raw.pickupAddress } : undefined),
  dropoff: raw.dropoff ?? (raw.dropoffAddress ? { line1: raw.dropoffAddress } : undefined),
  package: raw.package ?? (raw.packageWeight != null ? { weightKg: raw.packageWeight } : undefined),
  candidate: raw.candidate ?? (raw.vehicleType ? { vehicleType: raw.vehicleType } : {}),
  estimatedCost: raw.estimatedCost ?? raw.finalPrice,
  vehicleType: raw.vehicleType ?? raw.candidate?.vehicleType,
  hasBeenModified: raw.hasBeenModified === true || (raw.modifiedCount != null && raw.modifiedCount > 0),
  droneUpgradeAvailable: raw.droneUpgradeAvailable === true,
});

export default function OrderDetail() {
  const { orderId } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [receiptBusy, setReceiptBusy] = useState(false);
  const [reviewBusy, setReviewBusy] = useState(false);
  const [cancelBusy, setCancelBusy] = useState(false);
  const [upgradeBusy, setUpgradeBusy] = useState(false);
  const [message, setMessage] = useState("");
  useEffect(() => { let live = true; getOrder(orderId).then((data) => { if (live) setOrder(toView(data)); }).catch(() => live && setError("Unable to load this order.")).finally(() => live && setLoading(false)); return () => { live = false; }; }, [orderId]);
  async function receipt() { setReceiptBusy(true); setError(""); try { const result = await confirmReceipt(orderId); setOrder((current) => ({ ...current, status: result.status })); setMessage("Receipt confirmed. Thank you!"); } catch (err) { setError(err?.response?.data?.message || "Could not confirm receipt."); } finally { setReceiptBusy(false); } }
  async function review(values) { setReviewBusy(true); setError(""); try { await submitReview(orderId, values); setMessage("Thanks for your feedback!"); } catch (err) { setError(err?.response?.status === 404 ? "Review submission is not enabled by the backend yet." : "Could not submit your review."); } finally { setReviewBusy(false); } }
  async function doCancel() { setCancelBusy(true); setError(""); try { const result = await cancelOrder(orderId); setOrder((current) => ({ ...current, status: result.status ?? "CANCELLED" })); setMessage("Order cancelled."); } catch (err) { setError(err?.response?.status === 404 ? "Cancel is not supported by the backend yet (API pending)." : err?.response?.data?.message || "Could not cancel this order."); } finally { setCancelBusy(false); } }
  async function doUpgradeToDrone() {
    setUpgradeBusy(true);
    setError("");
    try {
      const updated = await updateOrder(orderId, { upgradeToDrone: true });
      setOrder(toView(updated));
      setMessage("Order upgraded to Drone Express! Surcharge has been processed.");
    } catch (err) {
      setError(err?.response?.data?.message || "Could not upgrade order.");
    } finally {
      setUpgradeBusy(false);
    }
  }

  if (loading) return <div style={{ minHeight: 450, display: "grid", placeItems: "center" }}><Spin size="large" /></div>;
  if (error && !order) return <Alert type="error" showIcon message={error} style={{ marginTop: 30 }} />;
  const status = order.status || "PENDING";
  const candidate = order.candidate || {};
  const isRobot = (order.vehicleType === "ROBOT" || candidate.vehicleType === "ROBOT");
  const canShowUpgrade = isRobot && !order.hasBeenModified && (status === "PENDING" || status === "PAID");

  return <div style={{ padding: "28px 0 50px" }}><Row align="middle" justify="space-between" gutter={[12, 12]}><Col><Title level={2} style={{ margin: 0 }}>Order #{order.orderId || orderId} <StatusBadge status={status} /></Title></Col><Col>{order.trackingCode && <Link to={`/track?code=${encodeURIComponent(order.trackingCode)}`}>Live tracking →</Link>}</Col></Row>
    {(error || message) && <Alert type={error ? "error" : "success"} showIcon message={error || message} style={{ margin: "18px 0" }} />}
    <Row gutter={[28, 28]} style={{ marginTop: 8 }}>
      <Col xs={24} lg={12}><Card title="Addresses" style={cardStyle}><Descriptions column={1}><Descriptions.Item label="🟢 Pickup">{formatAddress(order.pickup)}</Descriptions.Item><Descriptions.Item label="🔴 Destination">{formatAddress(order.dropoff)}</Descriptions.Item></Descriptions></Card></Col>
      <Col xs={24} lg={12}><Card title="Package & cost" style={cardStyle}><Descriptions column={1}><Descriptions.Item label="Package">{order.packageDescription || order.package?.description || "Package"}</Descriptions.Item><Descriptions.Item label="Weight">{order.package?.weightKg ? `${order.package.weightKg} kg` : "—"}</Descriptions.Item><Descriptions.Item label="Delivery">{candidate.vehicleType || "—"}{candidate.stationName ? ` · ${candidate.stationName}` : ""}</Descriptions.Item></Descriptions><Title level={2}>${Number(order.estimatedCost ?? 0).toFixed(2)}</Title>
        {canShowUpgrade && (
          order.droneUpgradeAvailable ? (
            <div style={{ marginTop: 14, padding: "10px 14px", background: "#f0f5ff", borderRadius: 8, border: "1px solid #adc6ff" }}>
              <Text strong style={{ color: "#1d39c4" }}>⚡ In a hurry? Upgrade to Drone Express</Text>
              <br />
              <Text type="secondary" style={{ fontSize: 12 }}>Expedited aerial delivery. Surcharge automatically calculated. (Only 1 modification permitted per order)</Text>
              <div style={{ marginTop: 8 }}>
                <Popconfirm title="Upgrade to Drone Express?" description="This will dispatch a high-speed drone. Any price difference will be charged. (1/1 modification limit)" okText="Upgrade now" onConfirm={doUpgradeToDrone}>
                  <Button type="primary" size="small" loading={upgradeBusy}>Upgrade to Drone Express</Button>
                </Popconfirm>
              </div>
            </div>
          ) : (
            <div style={{ marginTop: 14, padding: "8px 12px", background: "#fafafa", borderRadius: 8, border: "1px dashed #d9d9d9" }}>
              <Text type="secondary" style={{ fontSize: 12 }}>⚡ Drone Express upgrade is currently unavailable (no idle drones in fleet or weight exceeds 3kg).</Text>
            </div>
          )
        )}
        {order.hasBeenModified && <Text type="secondary" style={{ fontSize: 12, display: "block", marginTop: 8 }}>ℹ️ Order has been modified (1/1 limit reached).</Text>}
      </Card></Col>
      <Col xs={24} lg={12}><Card title="Confirm receipt" style={cardStyle}><Text type="secondary">Confirm only after the package has arrived. This changes the order status to DELIVERED.</Text><br /><Button type="primary" disabled={status !== "IN_TRANSIT"} loading={receiptBusy} onClick={receipt} style={{ marginTop: 28 }}>{status === "DELIVERED" ? "Receipt confirmed ✓" : status === "IN_TRANSIT" ? "Confirm receipt" : status === "CANCELLED" ? "Order cancelled" : "Available once in transit"}</Button></Card></Col>
      <Col xs={24} lg={12}><Card title="Cancel order" style={cardStyle}><Text type="secondary">Orders can be cancelled before the package is picked up. Once picked up and in transit, cancellation is not allowed. Cancelling before vehicle dispatch is 100% free; cancelling while en route to pickup incurs a $2.50 dispatch service fee.</Text><br /><Popconfirm title="Cancel this order?" description="This cannot be undone." okText="Cancel order" okButtonProps={{ danger: true }} onConfirm={doCancel}><Button danger disabled={status !== "PENDING" && status !== "PAID"} loading={cancelBusy} style={{ marginTop: 28 }}>{status === "CANCELLED" ? "Order cancelled" : status === "DELIVERED" ? "Already delivered" : status === "IN_TRANSIT" ? "In transit (cannot cancel)" : "Cancel this order"}</Button></Popconfirm></Card></Col>
      <Col xs={24} lg={12}><Card title="Review" style={cardStyle}><Form layout="vertical" onFinish={review} initialValues={{ rating: 5, damageReported: false }}><Form.Item name="rating" label="Rating" rules={[{ required: true }]}><Rate /></Form.Item><Form.Item name="comment" label="Comment"><Input.TextArea rows={4} placeholder="Tell us about your delivery experience" /></Form.Item><Form.Item name="damageReported" label="Package damaged" valuePropName="checked"><Switch /></Form.Item><Button htmlType="submit" type="primary" loading={reviewBusy}>Submit review</Button></Form></Card></Col>
    </Row></div>;
}

