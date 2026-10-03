import { useEffect, useState } from "react";
import { Alert, Button, Card, Col, Descriptions, Form, Input, InputNumber, Modal, Popconfirm, Rate, Row, Spin, Switch, Tag, Tooltip, Typography } from "antd";
import { EditOutlined } from "@ant-design/icons";
import { Link, useParams } from "react-router-dom";
import { cancelOrder, confirmReceipt, getOrder, getOrderReview, submitReview, updateOrder } from "../api/order";
import { normalizeStatus } from "../api/tracking";
import { StatusBadge } from "../components/StatusBadge";

const { Title, Text } = Typography;
const formatAddress = (value) => value?.line1 ? [value.line1, value.city, value.zip].filter(Boolean).join(", ") : "Address details are unavailable";
const cardStyle = { borderRadius: 12, minHeight: 220 };

// Map backend order record onto the view fields this page renders
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
  const [message, setMessage] = useState("");

  const [receiptBusy, setReceiptBusy] = useState(false);
  const [reviewBusy, setReviewBusy] = useState(false);
  const [cancelBusy, setCancelBusy] = useState(false);
  const [upgradeBusy, setUpgradeBusy] = useState(false);

  // Order modification modal states
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editBusy, setEditBusy] = useState(false);
  const [editForm] = Form.useForm();

  // Existing review state
  const [existingReview, setExistingReview] = useState(null);

  useEffect(() => {
    let live = true;
    getOrder(orderId)
      .then((data) => {
        if (!live) return;
        const viewData = toView(data);
        setOrder(viewData);
        if (viewData.status === "DELIVERED") {
          fetchReview(orderId);
        }
      })
      .catch(() => live && setError("Unable to load this order."))
      .finally(() => live && setLoading(false));

    return () => {
      live = false;
    };
  }, [orderId]);

  async function fetchReview(id) {
    try {
      const rev = await getOrderReview(id);
      if (rev) setExistingReview(rev);
    } catch {
      // Review may not exist yet, which is expected
      setExistingReview(null);
    }
  }

  async function receipt() {
    setReceiptBusy(true);
    setError("");
    try {
      const result = await confirmReceipt(orderId);
      setOrder((current) => ({ ...current, status: result.status ?? "DELIVERED" }));
      setMessage("Receipt confirmed. Thank you! You can now review your delivery below.");
      fetchReview(orderId);
    } catch (err) {
      setError(err?.response?.data?.message || "Could not confirm receipt.");
    } finally {
      setReceiptBusy(false);
    }
  }

  async function review(values) {
    setReviewBusy(true);
    setError("");
    try {
      const res = await submitReview(orderId, values);
      setExistingReview(res);
      setMessage("Thanks for your review! Your feedback helps us improve.");
    } catch (err) {
      setError(err?.response?.data?.message || "Could not submit your review.");
    } finally {
      setReviewBusy(false);
    }
  }

  async function doCancel() {
    setCancelBusy(true);
    setError("");
    try {
      const result = await cancelOrder(orderId);
      setOrder((current) => ({ ...current, status: result.status ?? "CANCELLED" }));
      setMessage("Order cancelled. Any eligible refund has been processed.");
    } catch (err) {
      setError(err?.response?.data?.message || "Could not cancel this order.");
    } finally {
      setCancelBusy(false);
    }
  }

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

  function openEditModal() {
    editForm.setFieldsValue({
      dropoffAddress: order?.dropoff?.line1 || order?.dropoffAddress || "",
      packageDescription: order?.packageDescription || order?.package?.description || "",
      packageWeight: order?.package?.weightKg || order?.packageWeight || 1.5,
    });
    setEditModalVisible(true);
  }

  async function handleEditOrder(values) {
    setEditBusy(true);
    setError("");
    try {
      const payload = {
        dropoffAddress: values.dropoffAddress?.trim(),
        packageDescription: values.packageDescription?.trim(),
        packageWeight: values.packageWeight ? Number(values.packageWeight) : undefined,
      };
      const updated = await updateOrder(orderId, payload);
      setOrder(toView(updated));
      setMessage("Order details updated successfully! Price adjustments have been settled.");
      setEditModalVisible(false);
    } catch (err) {
      setError(err?.response?.data?.message || "Could not update order.");
    } finally {
      setEditBusy(false);
    }
  }

  if (loading) return <div style={{ minHeight: 450, display: "grid", placeItems: "center" }}><Spin size="large" /></div>;
  if (error && !order) return <Alert type="error" showIcon message={error} style={{ marginTop: 30 }} />;

  const status = order.status || "PENDING";
  const candidate = order.candidate || {};
  const isRobot = (order.vehicleType === "ROBOT" || candidate.vehicleType === "ROBOT");
  const canModify = !order.hasBeenModified && (status === "PENDING" || status === "PAID");
  const canShowUpgrade = isRobot && canModify;

  return (
    <div style={{ padding: "28px 0 50px" }}>
      <Row align="middle" justify="space-between" gutter={[12, 12]}>
        <Col>
          <Title level={2} style={{ margin: 0 }}>
            Order #{order.orderId || orderId} <StatusBadge status={status} />
          </Title>
        </Col>
        <Col>
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <Button
              icon={<EditOutlined />}
              disabled={!canModify}
              onClick={openEditModal}
            >
              {order.hasBeenModified ? "Modified (1/1 limit reached)" : status === "IN_TRANSIT" ? "Cannot modify once in transit" : "Modify order"}
            </Button>
            {order.trackingCode && <Link to={`/track?code=${encodeURIComponent(order.trackingCode)}`}>Live tracking →</Link>}
          </div>
        </Col>
      </Row>

      {(error || message) && (
        <Alert
          type={error ? "error" : "success"}
          showIcon
          message={error || message}
          style={{ margin: "18px 0" }}
          closable
          onClose={() => { setError(""); setMessage(""); }}
        />
      )}

      <Row gutter={[28, 28]} style={{ marginTop: 8 }}>
        {/* Addresses card with quick edit button */}
        <Col xs={24} lg={12}>
          <Card
            title="Addresses"
            style={cardStyle}
            extra={
              canModify ? (
                <Button size="small" icon={<EditOutlined />} onClick={openEditModal}>
                  Edit
                </Button>
              ) : null
            }
          >
            <Descriptions column={1}>
              <Descriptions.Item label="🟢 Pickup">{formatAddress(order.pickup)}</Descriptions.Item>
              <Descriptions.Item label="🔴 Destination">{formatAddress(order.dropoff)}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        {/* Package & Cost Card with Drone Express upgrade option */}
        <Col xs={24} lg={12}>
          <Card title="Package & cost" style={cardStyle}>
            <Descriptions column={1}>
              <Descriptions.Item label="Package">{order.packageDescription || order.package?.description || "Package"}</Descriptions.Item>
              <Descriptions.Item label="Weight">{order.package?.weightKg ? `${order.package.weightKg} kg` : "—"}</Descriptions.Item>
              <Descriptions.Item label="Delivery">
                <Tag color={order.vehicleType === "DRONE" ? "blue" : "default"}>{order.vehicleType || candidate.vehicleType || "—"}</Tag>
                {candidate.stationName ? ` · ${candidate.stationName}` : ""}
              </Descriptions.Item>
            </Descriptions>
            <Title level={2} style={{ marginTop: 12 }}>${Number(order.estimatedCost ?? 0).toFixed(2)}</Title>

            {canShowUpgrade && (
              order.droneUpgradeAvailable ? (
                <div style={{ marginTop: 14, padding: "10px 14px", background: "#f0f5ff", borderRadius: 8, border: "1px solid #adc6ff" }}>
                  <Text strong style={{ color: "#1d39c4" }}>⚡ In a rush? Upgrade to Drone Express</Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 12 }}>Expedited aerial delivery. Surcharge automatically calculated. (Only 1 modification permitted per order)</Text>
                  <div style={{ marginTop: 8 }}>
                    <Popconfirm
                      title="Upgrade to Drone Express?"
                      description="This will dispatch a high-speed drone. Any price difference will be charged. (1/1 modification limit)"
                      okText="Upgrade now"
                      onConfirm={doUpgradeToDrone}
                    >
                      <Button type="primary" size="small" loading={upgradeBusy}>Upgrade to Drone Express</Button>
                    </Popconfirm>
                  </div>
                </div>
              ) : (
                <div style={{ marginTop: 14, padding: "8px 12px", background: "#fafafa", borderRadius: 8, border: "1px dashed #d9d9d9" }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>⚡ Drone Express upgrade is currently unavailable (no idle drones with sufficient battery or weight exceeds 3kg).</Text>
                </div>
              )
            )}
            {order.hasBeenModified && <Text type="secondary" style={{ fontSize: 12, display: "block", marginTop: 8 }}>ℹ️ Order has been modified (1/1 limit reached).</Text>}
          </Card>
        </Col>

        {/* Confirm receipt card */}
        <Col xs={24} lg={12}>
          <Card title="Confirm receipt" style={cardStyle}>
            <Text type="secondary">Confirm only after the package has arrived. This changes the order status to DELIVERED and unlocks the review section.</Text>
            <br />
            <Button
              type="primary"
              disabled={status !== "IN_TRANSIT"}
              loading={receiptBusy}
              onClick={receipt}
              style={{ marginTop: 28 }}
            >
              {status === "DELIVERED" ? "Receipt confirmed ✓" : status === "IN_TRANSIT" ? "Confirm receipt" : status === "CANCELLED" ? "Order cancelled" : "Available once in transit"}
            </Button>
          </Card>
        </Col>

        {/* Cancel order card */}
        <Col xs={24} lg={12}>
          <Card title="Cancel order" style={cardStyle}>
            <Text type="secondary">Orders can be cancelled before the package is picked up. Once picked up and in transit, cancellation is not allowed. Cancelling before vehicle dispatch is 100% free; cancelling while en route to pickup incurs a $2.50 dispatch service fee.</Text>
            <br />
            <Popconfirm
              title="Cancel this order?"
              description="This cannot be undone."
              okText="Cancel order"
              okButtonProps={{ danger: true }}
              onConfirm={doCancel}
            >
              <Button
                danger
                disabled={status !== "PENDING" && status !== "PAID"}
                loading={cancelBusy}
                style={{ marginTop: 28 }}
              >
                {status === "CANCELLED" ? "Order cancelled" : status === "DELIVERED" ? "Already delivered" : status === "IN_TRANSIT" ? "In transit (cannot cancel)" : "Cancel this order"}
              </Button>
            </Popconfirm>
          </Card>
        </Col>

        {/* Review & Feedback Card: strictly locked until DELIVERED */}
        <Col xs={24} lg={12}>
          <Card title="Review & feedback" style={cardStyle}>
            {status !== "DELIVERED" ? (
              <div>
                <Text type="secondary" style={{ display: "block", marginBottom: 12 }}>
                  {status === "CANCELLED"
                    ? "Reviews are not available for cancelled orders."
                    : "Reviews unlock once the delivery is completely finished and receipt has been confirmed."}
                </Text>
                <Alert
                  type="info"
                  showIcon
                  message="Available post-delivery only"
                  description={
                    status === "CANCELLED"
                      ? "This order was cancelled."
                      : "Once your package arrives and receipt is confirmed, you will be able to rate the delivery, leave comments, and report any package issues."
                  }
                />
              </div>
            ) : existingReview ? (
              <div>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 10 }}>
                  <Text strong style={{ color: "#389e0d" }}>✓ Review Submitted</Text>
                  {existingReview.reviewId && <Text type="secondary" style={{ fontSize: 12 }}>Code: {existingReview.reviewId}</Text>}
                </div>
                <div style={{ marginBottom: 10 }}>
                  <Rate disabled value={existingReview.rating} />
                </div>
                {existingReview.comment && (
                  <p style={{ background: "#f5f5f5", padding: "10px 14px", borderRadius: 8, fontStyle: "italic", margin: "10px 0" }}>
                    "{existingReview.comment}"
                  </p>
                )}
                <div style={{ marginTop: 8 }}>
                  {existingReview.damageReported ? (
                    <Tag color="red">⚠️ Package damage was reported</Tag>
                  ) : (
                    <Tag color="green">✓ No damage reported</Tag>
                  )}
                </div>
              </div>
            ) : (
              <Form layout="vertical" onFinish={review} initialValues={{ rating: 5, damageReported: false }}>
                <Form.Item name="rating" label="Rating" rules={[{ required: true, message: "Please select a rating" }]}>
                  <Rate />
                </Form.Item>
                <Form.Item name="comment" label="Comment">
                  <Input.TextArea rows={3} placeholder="Tell us about your delivery experience" />
                </Form.Item>
                <Form.Item name="damageReported" label="Package damaged" valuePropName="checked">
                  <Switch />
                </Form.Item>
                <Button htmlType="submit" type="primary" loading={reviewBusy}>
                  Submit review
                </Button>
              </Form>
            )}
          </Card>
        </Col>
      </Row>

      {/* Modify Order Modal */}
      <Modal
        title="Modify Order Details"
        open={editModalVisible}
        onCancel={() => setEditModalVisible(false)}
        footer={null}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          message="Single Modification Policy"
          description="Orders can only be modified once before pickup begins. Updating destination or weight will recalculate delivery charges and re-verify carrier availability."
          style={{ marginBottom: 16 }}
        />
        <Form
          form={editForm}
          layout="vertical"
          onFinish={handleEditOrder}
        >
          <Form.Item
            name="dropoffAddress"
            label="Destination Address"
            rules={[{ required: true, message: "Please enter the destination address" }]}
          >
            <Input placeholder="Enter destination address in San Francisco" />
          </Form.Item>
          <Form.Item
            name="packageDescription"
            label="Delivery Notes / Description"
          >
            <Input placeholder="e.g. Leave with concierge, Gate code #1234" />
          </Form.Item>
          <Form.Item
            name="packageWeight"
            label="Package Weight (kg)"
            rules={[{ required: true, message: "Please enter package weight" }]}
          >
            <InputNumber min={0.1} max={15.0} step={0.1} style={{ width: "100%" }} placeholder="e.g. 2.0" />
          </Form.Item>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 20 }}>
            <Button onClick={() => setEditModalVisible(false)}>Cancel</Button>
            <Button type="primary" htmlType="submit" loading={editBusy}>
              Confirm Modification
            </Button>
          </div>
        </Form>
      </Modal>
    </div>
  );
}
