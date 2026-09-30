import { useEffect, useState } from "react";
import { Alert, Button, Card, Checkbox, Col, Descriptions, Form, Input, InputNumber, Radio, Row, Space, Spin, Steps, Tag, Typography } from "antd";
import { EnvironmentOutlined, RobotOutlined, RocketOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { createOrder } from "../api/order";
import { getRecommendations } from "../api/recommendation";
import { getStations } from "../api/station";
import { MapView } from "../components/MapView";
import { useWizard } from "../store/wizard";

const { Title, Text } = Typography;
const steps = ["Addresses", "Package", "Delivery option", "Review & Pay"].map((title) => ({ title }));
const sf = { city: "San Francisco", zip: "94103", lat: 37.7749, lng: -122.4194, addressId: null };
const cardStyle = { borderRadius: 12, minHeight: 480 };

export default function OrderWizard() {
  const navigate = useNavigate();
  const wizard = useWizard();
  const [step, setStep] = useState(0);
  const [stations, setStations] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => { getStations().then((items) => setStations(items.map((s) => ({ ...s, lat: s.lat ?? s.latitude, lng: s.lng ?? s.longitude })))).catch(() => {}); }, []);

  // pkg/priority come in as arguments: `wizard` is this render's snapshot, so
  // reading wizard.pkg right after setPackage() would still see the old value.
  async function recommendations(pkg, priority) {
    setBusy(true); setError("");
    try {
      const result = await getRecommendations({ pickup: wizard.pickup, dropoff: wizard.dropoff, package: pkg, priority });
      wizard.setCandidates(result.candidates); setStep(2);
    } catch (err) { setError(err?.response?.data?.message || "Could not load delivery options."); }
    finally { setBusy(false); }
  }
  async function pay({ cardNumber }) {
    setBusy(true); setError("");
    try {
      const order = await createOrder({ candidateId: wizard.selected.candidateId, pickup: wizard.pickup, dropoff: wizard.dropoff, package: wizard.pkg, priority: wizard.priority, cardNumber: cardNumber.replace(/\s/g, "") }); // no paymentMethodId: the backend prefers it over cardNumber, which would skip the 0000 decline check
      if (!order.trackingCode) {
        throw new Error("Order was created, but no public tracking code was returned.");
      }
      navigate(`/track?code=${encodeURIComponent(order.trackingCode)}`);
    } catch (err) { setError(err?.response?.data?.message || "Payment could not be completed. Please try again."); }
    finally { setBusy(false); }
  }
  return <div style={{ padding: "24px 0 40px" }}>
    <Steps current={step} items={steps} style={{ maxWidth: 780, margin: "0 auto 34px" }} />
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 20 }} />}
    {step === 0 && <AddressStep stations={stations} onNext={(v) => { wizard.setAddress({ ...sf, line1: v.pickup }, { ...sf, line1: v.dropoff, lat: 37.788, lng: -122.398 }); setStep(1); }} />}
    {step === 1 && <PackageStep busy={busy} onBack={() => setStep(0)} onNext={(v) => { const pkg = { description: v.description, weightKg: v.weightKg, lengthCm: v.lengthCm ?? null, widthCm: v.widthCm ?? null, heightCm: v.heightCm ?? null, fragile: !!v.fragile }; wizard.setPackage(pkg, v.priority); recommendations(pkg, v.priority); }} />}
    {step === 2 && <OptionStep options={wizard.candidates} selected={wizard.selected} onSelect={wizard.select} onBack={() => setStep(1)} onNext={() => setStep(3)} busy={busy} />}
    {step === 3 && <PayStep wizard={wizard} busy={busy} onBack={() => setStep(2)} onPay={pay} />}
  </div>;
}

function AddressStep({ stations, onNext }) {
  const [form] = Form.useForm();
  const [points, setPoints] = useState({});
  const setMap = (_, values) => setPoints({ pickup: values.pickup ? { ...sf, line1: values.pickup } : undefined, dropoff: values.dropoff ? { ...sf, line1: values.dropoff, lat: 37.788, lng: -122.398 } : undefined });
  return <Row gutter={[28, 28]}><Col xs={24} lg={10}><Card style={cardStyle}><Title level={3}>Step 1 of 4: Addresses</Title><Form form={form} layout="vertical" onFinish={onNext} onValuesChange={setMap} requiredMark={false}>
    <Form.Item name="pickup" label="Pickup address" rules={[{ required: true, message: "Enter a pickup address" }]}><Input prefix={<EnvironmentOutlined />} placeholder="e.g. 123 Market St, San Francisco, CA" /></Form.Item>
    <Form.Item name="dropoff" label="Destination address" rules={[{ required: true, message: "Enter a destination address" }]}><Input prefix={<EnvironmentOutlined />} placeholder="e.g. 456 Mission St, San Francisco, CA" /></Form.Item>
    <Form.Item name="phone" label="Contact phone (optional)"><Input placeholder="+1 …" /></Form.Item>
    <Button htmlType="submit" type="primary" block size="large" style={{ marginTop: 120 }}>Continue →</Button>
  </Form></Card></Col><Col xs={24} lg={14}><Card style={cardStyle} title="Route preview"><MapView pickup={points.pickup} destination={points.dropoff} route={points.pickup && points.dropoff ? [points.pickup, points.dropoff] : undefined} height={480} />{stations[0] && <Text type="secondary">Delivery options use current station availability.</Text>}</Card></Col></Row>;
}

function PackageStep({ onBack, onNext, busy }) {
  return <Card style={{ ...cardStyle, maxWidth: 620, margin: "auto" }}><Title level={3}>Step 2 of 4: Package</Title><Form layout="vertical" onFinish={onNext} initialValues={{ priority: "STANDARD" }} requiredMark={false}>
    <Form.Item name="description" label="Item name" rules={[{ required: true, message: "Enter an item name" }]}><Input placeholder="e.g. Textbooks" /></Form.Item>
    <Form.Item name="weightKg" label="Weight (kg)" rules={[{ required: true, message: "Enter the weight" }]}><InputNumber min={0.1} precision={1} style={{ width: "100%" }} placeholder="e.g. 2.5" /></Form.Item>
    <Row gutter={12}><Col span={8}><Form.Item name="lengthCm" label="Length (cm)"><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col><Col span={8}><Form.Item name="widthCm" label="Width (cm)"><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col><Col span={8}><Form.Item name="heightCm" label="Height (cm)"><InputNumber min={1} style={{ width: "100%" }} /></Form.Item></Col></Row>
    <Form.Item name="fragile" valuePropName="checked"><Checkbox>Fragile item</Checkbox></Form.Item><Form.Item name="priority" label="Priority"><Radio.Group options={[{ label: "Standard", value: "STANDARD" }, { label: "Express", value: "EXPRESS" }]} /></Form.Item>
    <Alert type="warning" showIcon message="Robot limit: 15 kg · Drone limit: 3 kg" style={{ marginBottom: 24 }} />
    <Space style={{ display: "flex", justifyContent: "space-between" }}><Button onClick={onBack}>← Back</Button><Button htmlType="submit" type="primary" loading={busy}>Continue →</Button></Space>
  </Form></Card>;
}

function OptionStep({ options, selected, onSelect, onBack, onNext, busy }) {
  if (busy) return <div style={{ minHeight: 300, display: "grid", placeItems: "center" }}><Spin size="large" /></div>;
  return <div style={{ maxWidth: 1140, margin: "auto" }}><Space direction="vertical" size={16} style={{ width: "100%" }}><Title level={3}>Recommended delivery options</Title>{options.map((option) => <Card key={option.candidateId} style={{ border: selected?.candidateId === option.candidateId ? "2px solid #1677ff" : undefined, borderRadius: 12 }}>
    <Row align="middle" gutter={16}><Col flex="auto"><Space direction="vertical" size={6}><Title level={4} style={{ margin: 0 }}>{option.vehicleType === "DRONE" ? <RocketOutlined /> : <RobotOutlined />} {option.vehicleType === "DRONE" ? "Drone" : "Robot"} {option.isFastest && <Tag color="blue">Fastest</Tag>} {option.isCheapest && <Tag color="green">Best value</Tag>}</Title><Text>${Number(option.estimatedCost).toFixed(2)} · {option.estimatedTimeMinutes} min · {option.stationName}</Text><Text type={option.availableUnits ? "success" : "secondary"}>{option.availableUnits ? `${option.availableUnits} vehicles available` : "Currently unavailable"}</Text></Space></Col><Col><Button type={selected?.candidateId === option.candidateId ? "primary" : "default"} disabled={!option.availableUnits} onClick={() => onSelect(option)}>Select</Button></Col></Row>
  </Card>)}<Space style={{ display: "flex", justifyContent: "space-between", marginTop: 22 }}><Button onClick={onBack}>← Back</Button><Button type="primary" disabled={!selected} onClick={onNext}>Continue →</Button></Space></Space></div>;
}

function PayStep({ wizard, onBack, onPay, busy }) {
  const option = wizard.selected;
  return <Row gutter={[28, 28]}><Col xs={24} lg={10}><Card style={cardStyle}><Title level={3}>Order Summary</Title><Descriptions column={1} layout="vertical"><Descriptions.Item label="Pickup">{wizard.pickup?.line1}</Descriptions.Item><Descriptions.Item label="Destination">{wizard.dropoff?.line1}</Descriptions.Item><Descriptions.Item label="Package">{wizard.pkg?.description} · {wizard.pkg?.weightKg} kg</Descriptions.Item><Descriptions.Item label="Option">{option?.vehicleType} · {option?.estimatedTimeMinutes} min</Descriptions.Item></Descriptions><Title level={4} style={{ marginTop: 42 }}>Total ${Number(option?.estimatedCost ?? 0).toFixed(2)}</Title></Card></Col><Col xs={24} lg={14}><Card style={cardStyle}><Title level={3}>Payment</Title><Form layout="vertical" onFinish={onPay} requiredMark={false}><Form.Item name="cardNumber" label="Card number" rules={[{ required: true, message: "Enter a card number" }]}><Input placeholder="4242 4242 4242 4242" inputMode="numeric" /></Form.Item><Button htmlType="submit" type="primary" size="large" block loading={busy} style={{ marginTop: 180 }}>Pay ${Number(option?.estimatedCost ?? 0).toFixed(2)} →</Button><Button block onClick={onBack} style={{ marginTop: 14 }}>← Back</Button></Form></Card></Col></Row>;
}
