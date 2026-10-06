import { useEffect, useState, useRef } from "react";
import { Alert, Button, Card, Col, Descriptions, Form, Input, InputNumber, Modal, Popconfirm, Radio, Rate, Row, Spin, Switch, Tag, Tooltip, Typography } from "antd";
import { EditOutlined, ThunderboltOutlined, CarOutlined } from "@ant-design/icons";
import { Link, useParams } from "react-router-dom";
import { cancelOrder, confirmReceipt, getOrder, getOrderReview, submitReview, updateOrder } from "../api/order";
import { getRecommendations } from "../api/recommendation";
import { normalizeStatus } from "../api/tracking";
import { geocode, isWithinSanFrancisco } from "../lib/geocode";
import { StatusBadge } from "../components/StatusBadge";
import { apiErrorMessage } from "../lib/http";

const { Title, Text } = Typography;
const formatAddress = (value) => value?.line1 ? [value.line1, value.city, value.zip].filter(Boolean).join(", ") : "Address details are unavailable";
const cardStyle = { borderRadius: 12, minHeight: 220 };

// Map backend order record onto the view fields this page renders
const toView = (raw) => ({
  ...raw,
  orderId: raw.orderId ?? raw.orderNumber,
  status: normalizeStatus(raw.status),
  pickup: raw.pickup ?? (raw.pickupAddress ? { line1: raw.pickupAddress, lat: raw.pickupLat, lng: raw.pickupLng } : undefined),
  dropoff: raw.dropoff ?? (raw.dropoffAddress ? { line1: raw.dropoffAddress, lat: raw.dropoffLat, lng: raw.dropoffLng } : undefined),
  package: raw.package ?? (raw.packageWeight != null ? { weightKg: raw.packageWeight, volumeM3: raw.packageVolume } : undefined),
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

  // Price estimate preview states
  const [pricingLoading, setPricingLoading] = useState(false);
  const [estimatedNewPrice, setEstimatedNewPrice] = useState(null);
  const [pricingNotice, setPricingNotice] = useState("");
  const [calcVolumeM3, setCalcVolumeM3] = useState(0.003);
  const debounceTimerRef = useRef(null);

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
      setError(apiErrorMessage(err, "Could not confirm receipt."));
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
      setError(apiErrorMessage(err, "Could not submit your review."));
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
      setError(apiErrorMessage(err, "Could not cancel this order."));
    } finally {
      setCancelBusy(false);
    }
  }

  async function doUpgradeToDrone() {
    setUpgradeBusy(true);
    setError("");
    try {
      const updated = await updateOrder(orderId, { upgradeToDrone: true, vehicleType: "DRONE" });
      setOrder(toView(updated));
      setMessage("Order upgraded to Drone Express! Surcharge has been processed.");
    } catch (err) {
      setError(apiErrorMessage(err, "Could not upgrade order."));
    } finally {
      setUpgradeBusy(false);
    }
  }

  // Real-time price and carrier availability calculation
  function updatePriceEstimate(currentVals) {
    if (!currentVals) return;
    const pType = currentVals.vehicleType || "ROBOT";
    const weight = Number(currentVals.packageWeight || 1.5);
    const pAddr = currentVals.pickupAddress || order?.pickup?.line1 || "";
    const dAddr = currentVals.dropoffAddress || order?.dropoff?.line1 || "";
    const L = Number(currentVals.packageLengthCm || 20);
    const W = Number(currentVals.packageWidthCm || 15);
    const H = Number(currentVals.packageHeightCm || 10);
    const vol = (L * W * H) / 1000000;
    setCalcVolumeM3(vol);

    // Immediate physical validation
    if (pType === "DRONE" && weight > 3.0) {
      setPricingNotice("⚠️ Package weight exceeds Drone Express limit of 3.0 kg. Please select Ground Robot or reduce weight.");
      setEstimatedNewPrice(null);
      return;
    }
    if (pType === "DRONE" && vol > 0.05) {
      setPricingNotice("⚠️ Package volume exceeds Drone cargo bay limit of 0.05 m³.");
      setEstimatedNewPrice(null);
      return;
    }
    if (pType === "ROBOT" && weight > 15.0) {
      setPricingNotice("⚠️ Package weight exceeds Ground Robot limit of 15.0 kg.");
      setEstimatedNewPrice(null);
      return;
    }
    if (pType === "ROBOT" && vol > 0.30) {
      setPricingNotice("⚠️ Package volume exceeds Ground Robot cargo bay limit of 0.30 m³.");
      setEstimatedNewPrice(null);
      return;
    }

    if (debounceTimerRef.current) clearTimeout(debounceTimerRef.current);
    debounceTimerRef.current = setTimeout(async () => {
      setPricingLoading(true);
      setPricingNotice("");
      try {
        const pLat = order?.pickup?.lat || 37.789172;
        const pLng = order?.pickup?.lng || -122.397042;
        const dLat = order?.dropoff?.lat || 37.759600;
        const dLng = order?.dropoff?.lng || -122.426900;

        const reqBody = {
          pickup: { line1: pAddr, lat: pLat, lng: pLng },
          dropoff: { line1: dAddr, lat: dLat, lng: dLng },
          package: {
            weightKg: weight,
            lengthCm: L,
            widthCm: W,
            heightCm: H
          }
        };

        const res = await getRecommendations(reqBody);
        const candidates = res?.candidates || [];
        const match = candidates.find(c => c.vehicleType === pType);
        if (match) {
          setEstimatedNewPrice(Number(match.estimatedCost));
          setPricingNotice("");
        } else {
          setEstimatedNewPrice(null);
          if (pType === "DRONE") {
            setPricingNotice("⚠️ Drone Express is unavailable for these parameters (no idle drones with ≥10% reserve battery).");
          } else {
            setPricingNotice("⚠️ Ground Robot is currently unavailable for these parameters.");
          }
        }
      } catch {
        setEstimatedNewPrice(null);
      } finally {
        setPricingLoading(false);
      }
    }, 400);
  }

  function openEditModal() {
    const initialVals = {
      vehicleType: order?.vehicleType || "ROBOT",
      pickupAddress: order?.pickup?.line1 || order?.pickupAddress || "",
      dropoffAddress: order?.dropoff?.line1 || order?.dropoffAddress || "",
      packageDescription: order?.packageDescription || order?.package?.description || "",
      packageWeight: order?.package?.weightKg || order?.packageWeight || 1.5,
      packageLengthCm: 20,
      packageWidthCm: 15,
      packageHeightCm: 10,
    };
    editForm.setFieldsValue(initialVals);
    setEstimatedNewPrice(Number(order?.estimatedCost ?? 0));
    setPricingNotice("");
    setCalcVolumeM3((20 * 15 * 10) / 1000000);
    setEditModalVisible(true);
    updatePriceEstimate(initialVals);
  }

  async function handleEditOrder(values) {
    setEditBusy(true);
    setError("");
    try {
      // 1. Validate physical weight and volume
      const weight = Number(values.packageWeight);
      const L = Number(values.packageLengthCm || 20);
      const W = Number(values.packageWidthCm || 15);
      const H = Number(values.packageHeightCm || 10);
      const vol = (L * W * H) / 1000000;

      if (values.vehicleType === "DRONE") {
        if (weight > 3.0) {
          throw new Error("Package weight (" + weight + " kg) exceeds drone maximum capacity of 3.0 kg.");
        }
        if (vol > 0.05) {
          throw new Error("Package volume (" + vol.toFixed(4) + " m³) exceeds drone cargo bay limit of 0.05 m³.");
        }
      } else if (values.vehicleType === "ROBOT") {
        if (weight > 15.0) {
          throw new Error("Package weight (" + weight + " kg) exceeds robot maximum capacity of 15.0 kg.");
        }
        if (vol > 0.30) {
          throw new Error("Package volume (" + vol.toFixed(4) + " m³) exceeds robot cargo bay limit of 0.30 m³.");
        }
      }

      // 2. Validate Origin / Pickup Address in San Francisco
      let pLat = order?.pickup?.lat || order?.pickupLat;
      let pLng = order?.pickup?.lng || order?.pickupLng;
      const currentPickup = order?.pickup?.line1 || order?.pickupAddress || "";
      if (values.pickupAddress && values.pickupAddress.trim() !== currentPickup.trim()) {
        const geo = await geocode({ street: values.pickupAddress.trim() });
        if (!geo || !isWithinSanFrancisco(geo.lat, geo.lng)) {
          throw new Error("Pickup address must be a valid street location within the San Francisco service area.");
        }
        pLat = geo.lat;
        pLng = geo.lng;
      }

      // 3. Validate Destination Address in San Francisco
      let dLat = order?.dropoff?.lat || order?.dropoffLat;
      let dLng = order?.dropoff?.lng || order?.dropoffLng;
      const currentDropoff = order?.dropoff?.line1 || order?.dropoffAddress || "";
      if (values.dropoffAddress && values.dropoffAddress.trim() !== currentDropoff.trim()) {
        const geo = await geocode({ street: values.dropoffAddress.trim() });
        if (!geo || !isWithinSanFrancisco(geo.lat, geo.lng)) {
          throw new Error("Destination address must be a valid street location within the San Francisco service area.");
        }
        dLat = geo.lat;
        dLng = geo.lng;
      }

      const payload = {
        vehicleType: values.vehicleType,
        upgradeToDrone: values.vehicleType === "DRONE" && order?.vehicleType !== "DRONE",
        pickupAddress: values.pickupAddress?.trim(),
        pickupLat: pLat,
        pickupLng: pLng,
        dropoffAddress: values.dropoffAddress?.trim(),
        dropoffLat: dLat,
        dropoffLng: dLng,
        packageDescription: values.packageDescription?.trim(),
        packageWeight: weight,
        packageLengthCm: L,
        packageWidthCm: W,
        packageHeightCm: H,
      };

      const updated = await updateOrder(orderId, payload);
      setOrder(toView(updated));
      setMessage("Order details modified successfully! Carrier route and price adjustments have been settled.");
      setEditModalVisible(false);
    } catch (err) {
      setError(apiErrorMessage(err, err?.message || "Could not update order."));
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
  const currentCost = Number(order.estimatedCost ?? 0);
  const priceDiff = estimatedNewPrice != null ? (estimatedNewPrice - currentCost) : 0;

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
          <Card
            title="Package & cost"
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

      {/* Modify Order Modal with Full Verification & Surcharge Reminder */}
      <Modal
        title="Modify Order Details"
        open={editModalVisible}
        onCancel={() => setEditModalVisible(false)}
        footer={null}
        width={600}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          message="Single Modification Policy"
          description="Orders can only be modified once before pickup begins. Modifying delivery method, addresses, or package dimensions will re-calculate routes and delivery fees."
          style={{ marginBottom: 16 }}
        />

        <Form
          form={editForm}
          layout="vertical"
          onValuesChange={(_, all) => updatePriceEstimate(all)}
          onFinish={handleEditOrder}
        >
          {/* 1. Delivery Method */}
          <Form.Item
            name="vehicleType"
            label={<Text strong>Delivery Method</Text>}
            rules={[{ required: true }]}
          >
            <Radio.Group buttonStyle="solid" style={{ width: "100%", display: "flex", gap: 10 }}>
              <Radio.Button value="ROBOT" style={{ flex: 1, textAlign: "center", height: 42, lineHeight: "40px" }}>
                <CarOutlined style={{ marginRight: 6 }} /> Ground Robot (Max 15kg, 0.30m³)
              </Radio.Button>
              <Radio.Button value="DRONE" style={{ flex: 1, textAlign: "center", height: 42, lineHeight: "40px" }}>
                <ThunderboltOutlined style={{ marginRight: 6 }} /> Drone Express (Max 3kg, 0.05m³)
              </Radio.Button>
            </Radio.Group>
          </Form.Item>

          {/* 2. Addresses */}
          <Row gutter={12}>
            <Col span={24}>
              <Form.Item
                name="pickupAddress"
                label={<Text strong>🟢 Pickup Address (San Francisco)</Text>}
                rules={[{ required: true, message: "Please enter pickup address" }]}
              >
                <Input placeholder="Enter pickup address in San Francisco" />
              </Form.Item>
            </Col>
            <Col span={24}>
              <Form.Item
                name="dropoffAddress"
                label={<Text strong>🔴 Destination Address (San Francisco)</Text>}
                rules={[{ required: true, message: "Please enter destination address" }]}
              >
                <Input placeholder="Enter destination address in San Francisco" />
              </Form.Item>
            </Col>
          </Row>

          {/* 3. Package Notes */}
          <Form.Item
            name="packageDescription"
            label={<Text strong>Delivery Notes / Description</Text>}
          >
            <Input placeholder="e.g. Fragile electronics, leave at concierge" />
          </Form.Item>

          {/* 4. Package Weight & Dimensions */}
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                name="packageWeight"
                label={<Text strong>Weight (kg)</Text>}
                rules={[{ required: true, message: "Please specify weight" }]}
              >
                <InputNumber min={0.1} max={15.0} step={0.1} style={{ width: "100%" }} placeholder="e.g. 2.0" />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="packageLengthCm" label={<Text strong>Length</Text>}>
                <InputNumber min={1} max={200} style={{ width: "100%" }} placeholder="cm" />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="packageWidthCm" label={<Text strong>Width</Text>}>
                <InputNumber min={1} max={200} style={{ width: "100%" }} placeholder="cm" />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="packageHeightCm" label={<Text strong>Height</Text>}>
                <InputNumber min={1} max={200} style={{ width: "100%" }} placeholder="cm" />
              </Form.Item>
            </Col>
          </Row>

          {/* Live volume & capacity badge */}
          <div style={{ marginBottom: 16 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              📦 Calculated Volume: <strong>{calcVolumeM3.toFixed(4)} m³</strong>
            </Text>
          </div>

          {/* Real-time Pricing Preview & Surcharge Reminder */}
          <div style={{ background: "#f8f9fa", border: "1px solid #e9ecef", borderRadius: 8, padding: "12px 16px", marginBottom: 16 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
              <Text type="secondary">Current Order Cost:</Text>
              <Text strong>${currentCost.toFixed(2)}</Text>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
              <Text type="secondary">Estimated New Total:</Text>
              <Text strong style={{ fontSize: 16 }}>
                {pricingLoading ? <Spin size="small" /> : estimatedNewPrice != null ? `$${estimatedNewPrice.toFixed(2)}` : "—"}
              </Text>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", paddingTop: 8, borderTop: "1px dashed #d9d9d9" }}>
              <Text strong>Price Adjustment:</Text>
              {pricingLoading ? (
                <Text type="secondary">Recalculating...</Text>
              ) : estimatedNewPrice != null ? (
                priceDiff > 0 ? (
                  <Tag color="orange" style={{ fontSize: 13, padding: "2px 8px" }}>
                    ⚠️ Additional Surcharge: +${priceDiff.toFixed(2)}
                  </Tag>
                ) : priceDiff < 0 ? (
                  <Tag color="green" style={{ fontSize: 13, padding: "2px 8px" }}>
                    ✓ Partial Refund: -${Math.abs(priceDiff).toFixed(2)}
                  </Tag>
                ) : (
                  <Tag color="default" style={{ fontSize: 13, padding: "2px 8px" }}>
                    No Price Change ($0.00)
                  </Tag>
                )
              ) : (
                <Tag color="red">Unable to quote</Tag>
              )}
            </div>
          </div>

          {/* Validation Notice or Alert */}
          {pricingNotice && (
            <Alert
              type="warning"
              showIcon
              message={pricingNotice}
              style={{ marginBottom: 16 }}
            />
          )}

          {/* Action buttons */}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 10, marginTop: 12 }}>
            <Button onClick={() => setEditModalVisible(false)}>Cancel</Button>
            <Popconfirm
              title="Confirm Order Modification?"
              description={
                priceDiff > 0
                  ? `An additional surcharge of $${priceDiff.toFixed(2)} will be charged to your card. Confirm?`
                  : priceDiff < 0
                  ? `A partial refund of $${Math.abs(priceDiff).toFixed(2)} will be returned. Confirm?`
                  : "Confirm modification with no price change?"
              }
              onConfirm={() => editForm.submit()}
              okText="Confirm & Pay"
              disabled={pricingLoading || !!pricingNotice || (estimatedNewPrice == null)}
            >
              <Button
                type="primary"
                loading={editBusy}
                disabled={pricingLoading || !!pricingNotice || (estimatedNewPrice == null)}
              >
                {priceDiff > 0 ? `Confirm (+$${priceDiff.toFixed(2)})` : priceDiff < 0 ? `Confirm (-$${Math.abs(priceDiff).toFixed(2)})` : "Confirm Modification"}
              </Button>
            </Popconfirm>
          </div>
        </Form>
      </Modal>
    </div>
  );
}
