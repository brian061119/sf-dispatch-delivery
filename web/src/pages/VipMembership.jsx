import { useEffect, useState } from "react";
import { Alert, Badge, Button, Card, Col, Divider, Form, Modal, Radio, Row, Spin, Tag, Typography, message } from "antd";
import {
  CrownOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  SafetyCertificateOutlined,
  DollarOutlined,
  EditOutlined,
  RocketOutlined,
  InboxOutlined
} from "@ant-design/icons";
import { getVipStatus, subscribeVip } from "../api/vip";
import { useAuth } from "../store/auth";

const { Title, Text, Paragraph } = Typography;

export default function VipMembership() {
  const { setRole } = useAuth();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [vipData, setVipData] = useState(null);
  const [selectedPlan, setSelectedPlan] = useState("MONTHLY");
  const [checkoutModalVisible, setCheckoutModalVisible] = useState(false);

  async function loadData() {
    try {
      setLoading(true);
      const data = await getVipStatus();
      setVipData(data);
      if (data?.isVip) {
        setRole("VIP");
      }
    } catch (err) {
      message.error(err?.response?.data?.message || "Failed to load VIP membership status.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadData();
  }, []);

  async function handleSubscribe() {
    try {
      setSubmitting(true);
      const res = await subscribeVip({
        planType: selectedPlan,
        paymentMethodId: "pm_card_mock_vip"
      });
      setVipData(res);
      setRole("VIP");
      setCheckoutModalVisible(false);
      Modal.success({
        title: "Welcome to WeDelivery VIP!",
        content: `Your ${selectedPlan === "ANNUAL" ? "Annual" : "Monthly"} VIP membership has been activated successfully! You now enjoy 10% off all deliveries, waived dispatch fees on cancellations, 2 modifications per order, and +10% payload tolerance.`,
      });
    } catch (err) {
      message.error(err?.response?.data?.message || "Failed to process VIP subscription.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div style={{ minHeight: 400, display: "grid", placeItems: "center" }}>
        <Spin size="large" />
      </div>
    );
  }

  const isVip = Boolean(vipData?.isVip);
  const expireDate = vipData?.vipExpireAt ? new Date(vipData.vipExpireAt).toLocaleDateString() : "N/A";

  return (
    <div style={{ padding: "24px 0 60px" }}>
      {/* 1. Header Hero Card */}
      <Card
        style={{
          borderRadius: 16,
          background: isVip
            ? "linear-gradient(135deg, #1f1f1f 0%, #2c2518 50%, #433215 100%)"
            : "linear-gradient(135deg, #092b00 0%, #003a8c 100%)",
          color: "#fff",
          marginBottom: 28,
          border: isVip ? "1px solid #d48806" : "none",
          boxShadow: "0 8px 24px rgba(0,0,0,0.12)",
        }}
      >
        <Row align="middle" gutter={[24, 24]}>
          <Col xs={24} md={16}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 8 }}>
              <CrownOutlined style={{ fontSize: 32, color: "#faad14" }} />
              <Title level={2} style={{ color: "#fff", margin: 0 }}>
                {isVip ? "WeDelivery VIP Member" : "WeDelivery VIP Membership"}
              </Title>
              {isVip ? (
                <Tag color="gold" style={{ fontSize: 13, padding: "2px 10px" }}>ACTIVE</Tag>
              ) : (
                <Tag color="default" style={{ color: "#fff", borderColor: "#fff" }}>FREE TIER</Tag>
              )}
            </div>
            <Paragraph style={{ color: "rgba(255,255,255,0.85)", fontSize: 15, margin: 0, maxWidth: 620 }}>
              {isVip
                ? `You are enjoying full access to San Francisco's premier autonomous air and ground delivery network with VIP priority dispatching, exclusive pricing, and flexibility.`
                : "Upgrade to VIP and unlock instant 10% savings on every order, waived cancellation dispatch fees, 2 order modifications, and priority autonomous fleet dispatching."}
            </Paragraph>
          </Col>

          <Col xs={24} md={8} style={{ textAlign: "right" }}>
            {isVip ? (
              <div style={{ background: "rgba(0,0,0,0.35)", padding: "16px 20px", borderRadius: 12, display: "inline-block", textAlign: "left" }}>
                <Text style={{ color: "rgba(255,255,255,0.7)", display: "block", fontSize: 13 }}>Membership Validity</Text>
                <Text strong style={{ color: "#faad14", fontSize: 18 }}>Valid until {expireDate}</Text>
                {vipData?.daysRemaining != null && (
                  <Text style={{ color: "rgba(255,255,255,0.85)", display: "block", fontSize: 12, marginTop: 4 }}>
                    ⏳ {vipData.daysRemaining} days remaining
                  </Text>
                )}
                <Button
                  size="small"
                  type="primary"
                  style={{ marginTop: 10, background: "#faad14", borderColor: "#faad14", color: "#000", fontWeight: "bold" }}
                  onClick={() => setCheckoutModalVisible(true)}
                >
                  Renew / Extend
                </Button>
              </div>
            ) : (
              <Button
                type="primary"
                size="large"
                style={{
                  background: "linear-gradient(90deg, #faad14, #ffc53d)",
                  borderColor: "#faad14",
                  color: "#000",
                  fontWeight: "bold",
                  height: 48,
                  padding: "0 28px",
                  fontSize: 16,
                  borderRadius: 24,
                  boxShadow: "0 4px 16px rgba(250, 173, 20, 0.4)",
                }}
                onClick={() => setCheckoutModalVisible(true)}
              >
                Join VIP Now
              </Button>
            )}
          </Col>
        </Row>
      </Card>

      {/* 2. Lifetime Savings & Status Overview */}
      <Row gutter={[20, 20]} style={{ marginBottom: 28 }}>
        <Col xs={24} sm={8}>
          <Card style={{ borderRadius: 12, textAlign: "center", height: "100%" }}>
            <DollarOutlined style={{ fontSize: 32, color: "#52c41a", marginBottom: 8 }} />
            <div style={{ fontSize: 14, color: "#8c8c8c" }}>Total Money Saved</div>
            <div style={{ fontSize: 26, fontWeight: "bold", color: "#1f1f1f", marginTop: 4 }}>
              ${Number(vipData?.totalSaved || 0).toFixed(2)}
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>Cumulative delivery discounts</Text>
          </Card>
        </Col>

        <Col xs={24} sm={8}>
          <Card style={{ borderRadius: 12, textAlign: "center", height: "100%" }}>
            <EditOutlined style={{ fontSize: 32, color: "#1890ff", marginBottom: 8 }} />
            <div style={{ fontSize: 14, color: "#8c8c8c" }}>Order Modifications</div>
            <div style={{ fontSize: 26, fontWeight: "bold", color: "#1f1f1f", marginTop: 4 }}>
              {isVip ? "2 per order" : "1 per order"}
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>Change addresses, method, or payload</Text>
          </Card>
        </Col>

        <Col xs={24} sm={8}>
          <Card style={{ borderRadius: 12, textAlign: "center", height: "100%" }}>
            <InboxOutlined style={{ fontSize: 32, color: "#722ed1", marginBottom: 8 }} />
            <div style={{ fontSize: 14, color: "#8c8c8c" }}>Cargo Payload Tolerance</div>
            <div style={{ fontSize: 26, fontWeight: "bold", color: "#1f1f1f", marginTop: 4 }}>
              {isVip ? "+10% Elastic" : "Strict Limits"}
            </div>
            <Text type="secondary" style={{ fontSize: 12 }}>Drone up to 3.3kg / Robot up to 16.5kg</Text>
          </Card>
        </Col>
      </Row>

      {/* 3. Five Dimensions of VIP Exclusive Privileges */}
      <Title level={3} style={{ marginBottom: 20 }}>
        🌟 VIP Exclusive Privileges (5 Key Dimensions)
      </Title>

      <Row gutter={[20, 20]} style={{ marginBottom: 32 }}>
        <Col xs={24} sm={12} lg={8}>
          <Card hoverable style={{ borderRadius: 12, height: "100%" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12 }}>
              <div style={{ background: "#fff7e6", padding: 10, borderRadius: 10 }}>
                <DollarOutlined style={{ fontSize: 24, color: "#fa8c16" }} />
              </div>
              <div>
                <Text strong style={{ fontSize: 16 }}>10% Off Every Delivery</Text>
                <div style={{ fontSize: 12, color: "#8c8c8c" }}>Guaranteed Savings</div>
              </div>
            </div>
            <Paragraph type="secondary" style={{ fontSize: 13 }}>
              Enjoy an automatic 10% flat discount on all delivery plans, including Drone Express, Ground Robot, and Off-Peak Eco options.
            </Paragraph>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={8}>
          <Card hoverable style={{ borderRadius: 12, height: "100%" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12 }}>
              <div style={{ background: "#f6ffed", padding: 10, borderRadius: 10 }}>
                <SafetyCertificateOutlined style={{ fontSize: 24, color: "#52c41a" }} />
              </div>
              <div>
                <Text strong style={{ fontSize: 16 }}>$0 Dispatch Fee Waiver</Text>
                <div style={{ fontSize: 12, color: "#8c8c8c" }}>Free Cancellation Privilege</div>
              </div>
            </div>
            <Paragraph type="secondary" style={{ fontSize: 13 }}>
              Need to cancel while carrier is en-route to pickup? Normal users pay a $2.50 dispatch fee, but VIP members receive a 100% full refund with zero penalty.
            </Paragraph>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={8}>
          <Card hoverable style={{ borderRadius: 12, height: "100%" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12 }}>
              <div style={{ background: "#e6f7ff", padding: 10, borderRadius: 10 }}>
                <EditOutlined style={{ fontSize: 24, color: "#1890ff" }} />
              </div>
              <div>
                <Text strong style={{ fontSize: 16 }}>2 Order Modifications</Text>
                <div style={{ fontSize: 12, color: "#8c8c8c" }}>Double Flexibility</div>
              </div>
            </div>
            <Paragraph type="secondary" style={{ fontSize: 13 }}>
              Made a mistake with the delivery location or package size? VIP members are allowed to modify orders twice before transit, re-routing carriers seamlessly.
            </Paragraph>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={8}>
          <Card hoverable style={{ borderRadius: 12, height: "100%" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12 }}>
              <div style={{ background: "#f9f0ff", padding: 10, borderRadius: 10 }}>
                <InboxOutlined style={{ fontSize: 24, color: "#722ed1" }} />
              </div>
              <div>
                <Text strong style={{ fontSize: 16 }}>+10% Cargo Tolerance</Text>
                <div style={{ fontSize: 12, color: "#8c8c8c" }}>Capacity Flexibility</div>
              </div>
            </div>
            <Paragraph type="secondary" style={{ fontSize: 13 }}>
              Pack a little extra without worry. VIP orders enjoy a 10% elastic capacity bonus: Drone allows up to 3.3kg / 0.055m³ and Robot allows up to 16.5kg / 0.33m³.
            </Paragraph>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={8}>
          <Card hoverable style={{ borderRadius: 12, height: "100%" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12 }}>
              <div style={{ background: "#fff0f6", padding: 10, borderRadius: 10 }}>
                <RocketOutlined style={{ fontSize: 24, color: "#eb2f96" }} />
              </div>
              <div>
                <Text strong style={{ fontSize: 16 }}>Priority Fleet Dispatch</Text>
                <div style={{ fontSize: 12, color: "#8c8c8c" }}>Peak Demand Guarantee</div>
              </div>
            </div>
            <Paragraph type="secondary" style={{ fontSize: 13 }}>
              When city-wide autonomous dispatch queues are busy, VIP orders receive highest locking priority and are paired with the best-conditioned, high-battery carriers.
            </Paragraph>
          </Card>
        </Col>
      </Row>

      {/* 4. Plan Selection Modal */}
      <Modal
        title={
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <CrownOutlined style={{ color: "#faad14", fontSize: 20 }} />
            <span>Select Your VIP Membership Plan</span>
          </div>
        }
        open={checkoutModalVisible}
        onCancel={() => setCheckoutModalVisible(false)}
        footer={null}
        width={520}
        destroyOnClose
      >
        <div style={{ margin: "16px 0 24px" }}>
          <Radio.Group
            value={selectedPlan}
            onChange={(e) => setSelectedPlan(e.target.value)}
            style={{ width: "100%" }}
          >
            <Row gutter={[16, 16]}>
              <Col span={12}>
                <Card
                  hoverable
                  onClick={() => setSelectedPlan("MONTHLY")}
                  style={{
                    borderColor: selectedPlan === "MONTHLY" ? "#faad14" : "#f0f0f0",
                    borderWidth: 2,
                    borderRadius: 12,
                    textAlign: "center",
                    cursor: "pointer",
                    background: selectedPlan === "MONTHLY" ? "#fffbe6" : "#fff",
                  }}
                >
                  <Radio value="MONTHLY" style={{ marginBottom: 8 }} />
                  <div style={{ fontWeight: "bold", fontSize: 16 }}>Monthly Plan</div>
                  <div style={{ fontSize: 22, fontWeight: "bold", color: "#faad14", margin: "6px 0" }}>
                    $9.99 <span style={{ fontSize: 12, color: "#8c8c8c" }}>/ mo</span>
                  </div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Flexible, cancel anytime</Text>
                </Card>
              </Col>

              <Col span={12}>
                <Badge.Ribbon text="Save 25%" color="red">
                  <Card
                    hoverable
                    onClick={() => setSelectedPlan("ANNUAL")}
                    style={{
                      borderColor: selectedPlan === "ANNUAL" ? "#faad14" : "#f0f0f0",
                      borderWidth: 2,
                      borderRadius: 12,
                      textAlign: "center",
                      cursor: "pointer",
                      background: selectedPlan === "ANNUAL" ? "#fffbe6" : "#fff",
                    }}
                  >
                    <Radio value="ANNUAL" style={{ marginBottom: 8 }} />
                    <div style={{ fontWeight: "bold", fontSize: 16 }}>Annual Plan</div>
                    <div style={{ fontSize: 22, fontWeight: "bold", color: "#faad14", margin: "6px 0" }}>
                      $89.99 <span style={{ fontSize: 12, color: "#8c8c8c" }}>/ yr</span>
                    </div>
                    <Text type="secondary" style={{ fontSize: 12 }}>Best value ($7.50/mo)</Text>
                  </Card>
                </Badge.Ribbon>
              </Col>
            </Row>
          </Radio.Group>
        </div>

        <div style={{ background: "#f8f9fa", padding: 14, borderRadius: 8, marginBottom: 20 }}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
            <Text type="secondary">Selected Plan:</Text>
            <Text strong>{selectedPlan === "ANNUAL" ? "Annual Pass (12 Months)" : "Monthly Pass (1 Month)"}</Text>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
            <Text type="secondary">Billing Amount:</Text>
            <Text strong style={{ fontSize: 16, color: "#fa8c16" }}>
              {selectedPlan === "ANNUAL" ? "$89.99" : "$9.99"}
            </Text>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between" }}>
            <Text type="secondary">Payment Method:</Text>
            <Text>Mock Card (•••• 4242)</Text>
          </div>
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
          <Button onClick={() => setCheckoutModalVisible(false)}>Cancel</Button>
          <Button
            type="primary"
            loading={submitting}
            style={{ background: "#faad14", borderColor: "#faad14", color: "#000", fontWeight: "bold" }}
            onClick={handleSubscribe}
          >
            Confirm & Pay {selectedPlan === "ANNUAL" ? "$89.99" : "$9.99"}
          </Button>
        </div>
      </Modal>
    </div>
  );
}
