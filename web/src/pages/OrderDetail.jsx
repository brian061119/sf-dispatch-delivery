import { useEffect, useState } from "react";
import { Alert, Button, Card, Col, Descriptions, Form, Input, Rate, Row, Spin, Switch, Typography } from "antd";
import { Link, useParams } from "react-router-dom";
import { confirmReceipt, getOrder, submitReview } from "../api/order";
import { StatusBadge } from "../components/StatusBadge";

const { Title, Text } = Typography;
const formatAddress = (value) => value?.line1 ? [value.line1, value.city, value.zip].filter(Boolean).join(", ") : "Address details are unavailable";
const cardStyle = { borderRadius: 12, minHeight: 220 };

export default function OrderDetail() {
  const { orderId } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [receiptBusy, setReceiptBusy] = useState(false);
  const [reviewBusy, setReviewBusy] = useState(false);
  const [message, setMessage] = useState("");
  useEffect(() => { let live = true; getOrder(orderId).then((data) => { if (live) setOrder(data); }).catch(() => live && setError("Unable to load this order.")).finally(() => live && setLoading(false)); return () => { live = false; }; }, [orderId]);
  async function receipt() { setReceiptBusy(true); setError(""); try { const result = await confirmReceipt(orderId); setOrder((current) => ({ ...current, status: result.status })); setMessage("Receipt confirmed. Thank you!"); } catch (err) { setError(err?.response?.data?.message || "Could not confirm receipt."); } finally { setReceiptBusy(false); } }
  async function review(values) { setReviewBusy(true); setError(""); try { await submitReview(orderId, values); setMessage("Thanks for your feedback!"); } catch (err) { setError(err?.response?.status === 404 ? "Review submission is not enabled by the backend yet." : "Could not submit your review."); } finally { setReviewBusy(false); } }
  if (loading) return <div style={{ minHeight: 450, display: "grid", placeItems: "center" }}><Spin size="large" /></div>;
  if (error && !order) return <Alert type="error" showIcon message={error} style={{ marginTop: 30 }} />;
  const status = order.status || "PENDING";
  const candidate = order.candidate || {};
  return <div style={{ padding: "28px 0 50px" }}><Row align="middle" justify="space-between" gutter={[12, 12]}><Col><Title level={2} style={{ margin: 0 }}>Order #{order.orderId || orderId} <StatusBadge status={status} /></Title></Col><Col>{order.trackingCode && <Link to={`/track?code=${encodeURIComponent(order.trackingCode)}`}>Live tracking →</Link>}</Col></Row>
    {(error || message) && <Alert type={error ? "error" : "success"} showIcon message={error || message} style={{ margin: "18px 0" }} />}
    <Row gutter={[28, 28]} style={{ marginTop: 8 }}>
      <Col xs={24} lg={12}><Card title="Addresses" style={cardStyle}><Descriptions column={1}><Descriptions.Item label="🟢 Pickup">{formatAddress(order.pickup)}</Descriptions.Item><Descriptions.Item label="🔴 Destination">{formatAddress(order.dropoff)}</Descriptions.Item></Descriptions></Card></Col>
      <Col xs={24} lg={12}><Card title="Package & cost" style={cardStyle}><Descriptions column={1}><Descriptions.Item label="Package">{order.packageDescription || order.package?.description || "Package"}</Descriptions.Item><Descriptions.Item label="Weight">{order.package?.weightKg ? `${order.package.weightKg} kg` : "—"}</Descriptions.Item><Descriptions.Item label="Delivery">{candidate.vehicleType || "—"}{candidate.stationName ? ` · ${candidate.stationName}` : ""}</Descriptions.Item></Descriptions><Title level={2}>${Number(order.estimatedCost ?? 0).toFixed(2)}</Title></Card></Col>
      <Col xs={24} lg={12}><Card title="Confirm receipt" style={cardStyle}><Text type="secondary">Confirm only after the package has arrived. This changes the order status to DELIVERED.</Text><br /><Button type="primary" disabled={status === "DELIVERED" || status === "CANCELLED"} loading={receiptBusy} onClick={receipt} style={{ marginTop: 28 }}>{status === "DELIVERED" ? "Receipt confirmed ✓" : "Confirm receipt"}</Button></Card></Col>
      <Col xs={24} lg={12}><Card title="Review" style={cardStyle}><Form layout="vertical" onFinish={review} initialValues={{ rating: 5, damageReported: false }}><Form.Item name="rating" label="Rating" rules={[{ required: true }]}><Rate /></Form.Item><Form.Item name="comment" label="Comment"><Input.TextArea rows={4} placeholder="Tell us about your delivery experience" /></Form.Item><Form.Item name="damageReported" label="Package damaged" valuePropName="checked"><Switch /></Form.Item><Button htmlType="submit" type="primary" loading={reviewBusy}>Submit review</Button></Form></Card></Col>
    </Row></div>;
}
