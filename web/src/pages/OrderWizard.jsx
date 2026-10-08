import { useEffect, useRef, useState } from "react";
import { Alert, AutoComplete, Button, Card, Checkbox, Col, Descriptions, Empty, Form, Input, InputNumber, Popover, Radio, Row, Segmented, Space, Spin, Steps, Tag, Typography } from "antd";
import { AimOutlined, EnvironmentOutlined, RobotOutlined, RocketOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { createOrder } from "../api/order";
import { getRecommendations } from "../api/recommendation";
import { getStations } from "../api/station";
import { MapView } from "../components/MapView";
import { autocomplete, geocode, geocodePlaceId, isWithinSanFrancisco, reverseGeocode } from "../lib/geocode";
import { DEMO_ADDRESSES } from "../lib/addresses";
import { getRole } from "../lib/auth";
import { apiErrorMessage } from "../lib/http";
import { useWizard } from "../store/wizard";

const { Title, Text } = Typography;
const steps = ["Addresses", "Package", "Delivery option", "Review & Pay"].map((title) => ({ title }));
const cardStyle = { borderRadius: 12, minHeight: 480 };

// Vehicle payload limits — mirror backend VehicleType defaults (ROBOT 15kg / DRONE 3kg).
const ROBOT_MAX_KG = 15;
const DRONE_MAX_KG = 3;

// An address is only usable once it has REAL coordinates from a geocoder (or a
// demo shortcut / map click). status: empty → draft → verifying → valid | invalid.
// Never fall back to default coordinates: an unverifiable address blocks Continue.
const EMPTY_ADDR = { street: "", city: "San Francisco", zip: "", lat: null, lng: null, status: "empty", displayName: "" };

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
      wizard.setCandidates(result.candidates ?? []);
      wizard.select(undefined); // stale picks must not survive a re-quote
      setStep(2);
    } catch (err) { setError(apiErrorMessage(err, "Could not load delivery options.")); }
    finally { setBusy(false); }
  }

  async function pay({ cardNumber }) {
    setBusy(true); setError("");
    try {
      // no paymentMethodId: the backend prefers it over cardNumber, which would skip the 0000 decline check
      const order = await createOrder({ candidateId: wizard.selected.candidateId, pickup: wizard.pickup, dropoff: wizard.dropoff, package: wizard.pkg, priority: wizard.priority, cardNumber: cardNumber.replace(/\s/g, "") });
      if (!order.trackingCode) {
        throw new Error("Order was created, but no public tracking code was returned.");
      }
      wizard.reset(); // the draft has become an order — do not resurrect it next visit
      navigate(`/track?code=${encodeURIComponent(order.trackingCode)}`);
    } catch (err) {
      if (err?.response?.data?.code === "PAYMENT_DECLINED") {
        setError("Your card was declined. The order was NOT created — no charge was made. Try a different card.");
      } else if (err?.response?.data?.code === "NO_VEHICLE_AVAILABLE") {
        setError("No vehicle is free for this delivery right now. Go back and pick a different option, or try again shortly.");
      } else if (err?.response?.status === 409) {
        setError("That delivery option is no longer available. Please pick an option again.");
        setStep(1); // re-quote from the package step
      } else {
        setError(apiErrorMessage(err, "Payment could not be completed. Please try again."));
      }
    }
    finally { setBusy(false); }
  }

  return <div style={{ padding: "24px 0 40px" }}>
    <Steps current={step} items={steps} style={{ maxWidth: 780, margin: "0 auto 34px" }} />
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 20 }} />}
    {step === 0 && <AddressStep stations={stations} initial={{ pickup: wizard.pickup, dropoff: wizard.dropoff }} onNext={(pickup, dropoff) => { wizard.setAddress(pickup, dropoff); setStep(1); }} />}
    {step === 1 && <PackageStep busy={busy} initial={{ pkg: wizard.pkg, priority: wizard.priority }} onBack={() => setStep(0)} onNext={(v) => { const pkg = { description: v.description, weightKg: v.weightKg, lengthCm: v.lengthCm ?? null, widthCm: v.widthCm ?? null, heightCm: v.heightCm ?? null, fragile: !!v.fragile }; wizard.setPackage(pkg, v.priority); recommendations(pkg, v.priority); }} />}
    {step === 2 && <OptionStep options={wizard.candidates} selected={wizard.selected} onSelect={wizard.select} onBack={() => setStep(1)} onNext={() => setStep(3)} busy={busy} />}
    {step === 3 && <PayStep wizard={wizard} busy={busy} onBack={() => setStep(2)} onPay={pay} />}
  </div>;
}

/* ------------------------------------------------------------------ */
/* Step 1 — real street addresses: autocomplete + map click + geocode  */
/* ------------------------------------------------------------------ */

function AddressStep({ stations, initial, onNext }) {
  const revive = (saved) => (saved?.lat != null ? { ...EMPTY_ADDR, ...saved, street: saved.street || saved.line1 || "", status: "valid" } : { ...EMPTY_ADDR, ...saved, street: saved?.street || saved?.line1 || "", status: saved?.line1 ? "draft" : "empty" });
  const [pickup, setPickup] = useState(() => revive(initial.pickup));
  const [dropoff, setDropoff] = useState(() => revive(initial.dropoff));
  const [active, setActive] = useState("pickup");
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);

  async function handleMapClick(latlng) {
    if (!isWithinSanFrancisco(latlng.lat, latlng.lng)) {
      setErrors((e) => ({ ...e, [active]: "Selected location is outside our San Francisco service area. Please click within SF." }));
      return;
    }
    const setField = active === "pickup" ? setPickup : setDropoff;
    setField((a) => ({ ...a, status: "verifying" }));
    try {
      const r = await reverseGeocode(latlng.lat, latlng.lng);
      if (!r) throw new Error("not found");
      setField({ street: r.street || r.displayName, city: r.city || "San Francisco", zip: r.zip || "", lat: r.lat, lng: r.lng, status: "valid", displayName: r.displayName });
      setErrors((e) => ({ ...e, [active]: "" }));
    } catch {
      setField((a) => ({ ...a, lat: null, lng: null, status: "invalid" }));
      setErrors((e) => ({ ...e, [active]: "No street address found at that spot — click on a road or building within San Francisco." }));
    }
  }

  async function ensureValid(key, addr, setField) {
    if (addr.status === "valid" && addr.lat != null) {
      if (!isWithinSanFrancisco(addr.lat, addr.lng)) {
        setField((a) => ({ ...a, status: "invalid", lat: null, lng: null }));
        setErrors((e) => ({ ...e, [key]: "This address is outside our San Francisco service area." }));
        return null;
      }
      return addr;
    }
    if (addr.status === "verifying") return null; // a lookup is already running
    if (!addr.street.trim()) { setErrors((e) => ({ ...e, [key]: "Enter a street address, or pick one on the map." })); return null; }
    setField((a) => ({ ...a, status: "verifying" }));
    try {
      const r = await geocode(addr);
      if (!r || !isWithinSanFrancisco(r.lat, r.lng)) throw new Error("not found");
      const fixed = { ...addr, lat: r.lat, lng: r.lng, status: "valid", displayName: r.displayName, zip: addr.zip || r.zip };
      setField(fixed);
      setErrors((e) => ({ ...e, [key]: "" }));
      return fixed;
    } catch {
      setField((a) => ({ ...a, lat: null, lng: null, status: "invalid" }));
      setErrors((e) => ({ ...e, [key]: "Address could not be found or is outside San Francisco. Pick a suggestion from the list or click the map." }));
      return null;
    }
  }

  async function submit() {
    setBusy(true);
    try {
      const [p, d] = await Promise.all([
        ensureValid("pickup", pickup, setPickup),
        ensureValid("dropoff", dropoff, setDropoff),
      ]);
      if (!p || !d) return;
      if (p.lat === d.lat && p.lng === d.lng) {
        setErrors((e) => ({ ...e, dropoff: "Destination must differ from pickup." }));
        return;
      }
      onNext(toContract(p), toContract(d));
    } finally { setBusy(false); }
  }

  const toContract = (a) => ({ addressId: null, line1: a.street, city: a.city, zip: a.zip, lat: a.lat, lng: a.lng });
  const toPoint = (a) => (a.lat != null ? { lat: a.lat, lng: a.lng, line1: a.street } : undefined);

  return (
    <Row gutter={[28, 28]}>
      <Col xs={24} lg={10}>
        <Card style={cardStyle}>
          <Title level={3}>Step 1 of 4: Addresses</Title>
          <Text type="secondary">Type a real street address and pick a suggestion, or click the exact spot on the map. Only verified addresses can be used.</Text>
          <Segmented
            block
            style={{ margin: "16px 0" }}
            value={active}
            onChange={setActive}
            options={[
              { label: "Set pickup on map", value: "pickup" },
              { label: "Set destination on map", value: "dropoff" },
            ]}
          />
          <Space direction="vertical" size={20} style={{ width: "100%" }}>
            <AddressField label="Pickup address" color="#2f9e44" value={pickup} error={errors.pickup} onFocus={() => setActive("pickup")} onChange={setPickup} />
            <AddressField label="Destination address" color="#e03131" value={dropoff} error={errors.dropoff} onFocus={() => setActive("dropoff")} onChange={setDropoff} />
          </Space>
          <Button type="primary" block size="large" loading={busy} style={{ marginTop: 28 }} onClick={submit}>Continue →</Button>
        </Card>
      </Col>
      <Col xs={24} lg={14}>
        <Card style={cardStyle} title={<Space><AimOutlined />Route preview — click the map to set the {active === "pickup" ? "pickup 🟢" : "destination 🔴"}</Space>}>
          <MapView pickup={toPoint(pickup)} destination={toPoint(dropoff)} stations={stations} route={pickup.lat != null && dropoff.lat != null ? [toPoint(pickup), toPoint(dropoff)] : undefined} height={480} onMapClick={handleMapClick} />
          <Text type="secondary">🟢 pickup · 🔴 destination · 🏠 service stations (blue). The map reframes to fit both points.</Text>
        </Card>
      </Col>
    </Row>
  );
}

function AddressField({ label, color, value, error, onChange, onFocus }) {
  const [options, setOptions] = useState([]);
  const foundRef = useRef(new Map()); // option value → full geocode result
  const timerRef = useRef(null);

  function handleSearch(text) {
    onChange({ ...value, street: text, status: text.trim() ? "draft" : "empty", lat: null, lng: null });
    clearTimeout(timerRef.current);
    if (text.trim().length < 3) { setOptions([]); return; }
    timerRef.current = setTimeout(async () => {
      const q = text.trim().toLowerCase();
      // Demo shortcuts always match locally (they carry real coordinates)…
      const demoHits = DEMO_ADDRESSES.filter((a) => a.line1.toLowerCase().includes(q))
        .map((a) => ({ value: a.line1, label: `${a.line1} · demo`, result: { lat: a.lat, lng: a.lng, street: a.line1, city: a.city, zip: a.zip, displayName: `${a.line1}, ${a.city} ${a.zip}` } }));
      // …and live Nominatim suggestions merge in when the network allows.
      let liveHits = [];
      try {
        liveHits = (await autocomplete(text)).map((r) => ({ value: r.displayName, label: r.displayName, result: r }));
      } catch { /* offline / rate-limited: demo hits still work */ }
      const merged = [...demoHits, ...liveHits].slice(0, 6);
      foundRef.current = new Map(merged.map((o) => [o.value, o.result]));
      setOptions(merged.map(({ value, label }) => ({ value, label })));
    }, 350);
  }

  async function handleSelect(optionValue) {
    let r = foundRef.current.get(optionValue);
    if (!r) return;
    if (r.placeId && (r.lat == null || r.lng == null)) {
      onChange({ ...value, street: r.street || r.displayName, status: "verifying" });
      const resolved = await geocodePlaceId(r.placeId);
      if (resolved) r = resolved;
    }
    const isValid = r.lat != null && isWithinSanFrancisco(r.lat, r.lng);
    onChange({
      street: r.street || r.displayName,
      city: r.city || "San Francisco",
      zip: r.zip || "",
      lat: isValid ? r.lat : null,
      lng: isValid ? r.lng : null,
      status: isValid ? "valid" : "invalid",
      displayName: r.displayName || r.street,
    });
    setOptions([]);
  }

  const statusTag = value.status === "valid" ? <Tag color="green">verified ✓</Tag>
    : value.status === "verifying" ? <Tag color="blue">checking…</Tag>
    : value.status === "invalid" ? <Tag color="red">not found</Tag>
    : value.status === "draft" ? <Tag>unverified</Tag> : null;

  return (
    <div onFocusCapture={onFocus}>
      <Space style={{ marginBottom: 6 }}>
        <span style={{ display: "inline-block", width: 10, height: 10, borderRadius: "50%", background: color }} />
        <Text strong>{label}</Text>
        {statusTag}
      </Space>
      <AutoComplete value={value.street} options={options} onSearch={handleSearch} onSelect={handleSelect} style={{ width: "100%" }}>
        <Input prefix={<EnvironmentOutlined />} placeholder="Street address, e.g. 500 Howard St" size="large" />
      </AutoComplete>
      <Row gutter={8} style={{ marginTop: 8 }}>
        <Col span={14}><Input value={value.city} onChange={(e) => onChange({ ...value, city: e.target.value, status: value.lat != null ? value.status : "draft" })} placeholder="City" /></Col>
        <Col span={10}><Input value={value.zip} onChange={(e) => onChange({ ...value, zip: e.target.value.replace(/[^\d-]/g, "").slice(0, 10), status: value.lat != null ? value.status : "draft" })} placeholder="ZIP" /></Col>
      </Row>
      {error && <div style={{ color: "#cf1322", marginTop: 6 }}>{error}</div>}
      {value.status === "valid" && value.displayName && <Text type="secondary" style={{ fontSize: 12 }}>📍 {value.displayName}</Text>}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Step 2 — package                                                    */
/* ------------------------------------------------------------------ */

function PackageStep({ onBack, onNext, busy, initial }) {
  const [form] = Form.useForm();
  const isVip = getRole() === "VIP";
  const robotMax = isVip ? 16.5 : ROBOT_MAX_KG;
  const droneMax = isVip ? 3.3 : DRONE_MAX_KG;
  const [weight, setWeight] = useState(initial.pkg?.weightKg ?? null);
  const tooHeavy = weight != null && weight > robotMax;
  const robotOnly = weight != null && weight > droneMax && !tooHeavy;

  return (
    <Card style={{ ...cardStyle, maxWidth: 620, margin: "auto" }}>
      <Title level={3}>Step 2 of 4: Package</Title>
      <Form form={form} layout="vertical" onFinish={onNext} requiredMark={false}
        initialValues={{
          description: initial.pkg?.description,
          weightKg: initial.pkg?.weightKg,
          lengthCm: initial.pkg?.lengthCm ?? undefined,
          widthCm: initial.pkg?.widthCm ?? undefined,
          heightCm: initial.pkg?.heightCm ?? undefined,
          fragile: initial.pkg?.fragile ?? false,
          priority: initial.priority ?? "STANDARD",
        }}
        onValuesChange={(changed) => { if (changed.weightKg !== undefined) setWeight(changed.weightKg); }}
      >
        <Form.Item name="description" label="Item name" rules={[{ required: true, message: "Enter an item name" }, { max: 60, message: "Keep it under 60 characters" }]}><Input placeholder="e.g. Textbooks" /></Form.Item>
        <Form.Item name="weightKg" label={`Weight (kg) — up to ${robotMax} kg`} rules={[{ required: true, message: "Enter the weight" }]}>
          <InputNumber min={0.1} max={999} precision={1} style={{ width: "100%" }} placeholder="e.g. 2.5" />
        </Form.Item>
        {tooHeavy && <Alert type="error" showIcon style={{ marginBottom: 16 }} message={`No vehicle can carry more than ${robotMax} kg.`} description="Split this into multiple deliveries, or contact support for freight." />}
        {robotOnly && <Alert type="warning" showIcon style={{ marginBottom: 16 }} message={`Over ${droneMax} kg — only robots can carry this. Drones will be unavailable.`} />}
        {!tooHeavy && !robotOnly && <Alert type="info" showIcon style={{ marginBottom: 16 }} message={`Robot limit: ${robotMax} kg · Drone limit: ${droneMax} kg ${isVip ? "(VIP +10% capacity applied 👑)" : ""}`} />}
        <Row gutter={12}>
          <Col span={8}><Form.Item name="lengthCm" label="Length (cm)"><InputNumber min={1} max={500} style={{ width: "100%" }} /></Form.Item></Col>
          <Col span={8}><Form.Item name="widthCm" label="Width (cm)"><InputNumber min={1} max={500} style={{ width: "100%" }} /></Form.Item></Col>
          <Col span={8}><Form.Item name="heightCm" label="Height (cm)"><InputNumber min={1} max={500} style={{ width: "100%" }} /></Form.Item></Col>
        </Row>
        <Form.Item name="fragile" valuePropName="checked"><Checkbox>Fragile item</Checkbox></Form.Item>
        <Form.Item name="priority" label="Priority"><Radio.Group options={[{ label: "Standard", value: "STANDARD" }, { label: "Express", value: "EXPRESS" }]} /></Form.Item>
        <Space style={{ display: "flex", justifyContent: "space-between" }}>
          <Button onClick={onBack}>← Back</Button>
          <Button htmlType="submit" type="primary" loading={busy} disabled={tooHeavy}>Continue →</Button>
        </Space>
      </Form>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/* Step 3 — delivery option (a selection is required to continue)      */
/* ------------------------------------------------------------------ */

/** Off-peak plans are identified by candidateId (CAND-OFF_PEAK); the contract DTO carries no planType field. */
function isOffPeak(option) {
  return (option?.candidateId ?? "").toUpperCase().includes("OFF_PEAK");
}

function OptionStep({ options, selected, onSelect, onBack, onNext, busy }) {
  if (busy) return <div style={{ minHeight: 300, display: "grid", placeItems: "center" }}><Spin size="large" /></div>;
  const isVip = getRole() === "VIP";
  return (
    <div style={{ maxWidth: 1140, margin: "auto" }}>
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Title level={3}>Recommended delivery options {isVip && <Tag color="gold">VIP −10% applied</Tag>}</Title>
        {!options.length && <Card><Empty description="No vehicles can take this delivery right now. Try a different pickup address or a lighter package." /></Card>}
        {options.map((option) => (
          <Card key={option.candidateId} style={{ border: selected?.candidateId === option.candidateId ? "2px solid #1677ff" : undefined, borderRadius: 12 }}>
            <Row align="middle" gutter={16}>
              <Col flex="auto">
                <Space direction="vertical" size={6}>
                  <Title level={4} style={{ margin: 0 }}>
                    {option.vehicleType === "DRONE" ? <RocketOutlined /> : <RobotOutlined />} {isOffPeak(option) ? "Off-peak" : (option.vehicleType === "DRONE" ? "Drone" : "Robot")} {isOffPeak(option) && <Tag color="cyan">−15% · starts 1h later</Tag>} {option.isFastest && <Tag color="blue">Fastest</Tag>} {option.isCheapest && <Tag color="green">Best value</Tag>}
                  </Title>
                  {isOffPeak(option) && <Text type="secondary">Eco-friendly off-peak plan: the {option.vehicleType === "DRONE" ? "drone" : "robot"} departs 1 hour later, and you save 15% on shipping.</Text>}
                  <Text>${Number(option.estimatedCost).toFixed(2)} · {option.estimatedTimeMinutes} min · {option.stationName}</Text>
                  <Text type={option.availableUnits ? "success" : "secondary"}>{option.availableUnits ? `${option.availableUnits} vehicles available` : "Currently unavailable"}</Text>
                </Space>
              </Col>
              <Col><Button type={selected?.candidateId === option.candidateId ? "primary" : "default"} disabled={!option.availableUnits} onClick={() => onSelect(option)}>{selected?.candidateId === option.candidateId ? "Selected ✓" : "Select"}</Button></Col>
            </Row>
          </Card>
        ))}
        {!selected && <Alert type="info" showIcon message="Select a delivery option above to continue." />}
        <Space style={{ display: "flex", justifyContent: "space-between", marginTop: 22 }}>
          <Button onClick={onBack}>← Back</Button>
          <Popover content={selected ? "" : "Select a delivery option first"}><Button type="primary" disabled={!selected} onClick={onNext}>Continue →</Button></Popover>
        </Space>
      </Space>
    </div>
  );
}

/* ------------------------------------------------------------------ */
/* Step 4 — review & pay (price comes from the selected option only)   */
/* ------------------------------------------------------------------ */

/** Luhn checksum — catches typos before the request ever leaves the browser. */
function luhnValid(digits) {
  let sum = 0;
  let dbl = false;
  for (let i = digits.length - 1; i >= 0; i -= 1) {
    let d = digits.charCodeAt(i) - 48;
    if (dbl) { d *= 2; if (d > 9) d -= 9; }
    sum += d;
    dbl = !dbl;
  }
  return digits.length > 0 && sum % 10 === 0;
}

function PayStep({ wizard, onBack, onPay, busy }) {
  const option = wizard.selected;
  const total = Number(option?.estimatedCost ?? 0);
  return (
    <Row gutter={[28, 28]}>
      <Col xs={24} lg={10}>
        <Card style={cardStyle}>
          <Title level={3}>Order Summary</Title>
          <Descriptions column={1} layout="vertical">
            <Descriptions.Item label="Pickup">{wizard.pickup?.line1}</Descriptions.Item>
            <Descriptions.Item label="Destination">{wizard.dropoff?.line1}</Descriptions.Item>
            <Descriptions.Item label="Package">{wizard.pkg?.description} · {wizard.pkg?.weightKg} kg{wizard.pkg?.fragile ? " · fragile" : ""}</Descriptions.Item>
            <Descriptions.Item label="Option">{option?.vehicleType}{isOffPeak(option) ? " (Off-peak −15%, departs 1h later)" : ""} · {option?.estimatedTimeMinutes} min · {option?.stationName}</Descriptions.Item>
          </Descriptions>
          <Title level={4} style={{ marginTop: 42 }}>Total ${total.toFixed(2)}</Title>
          <Text type="secondary">Same price as the option you selected — the backend re-computes it from your exact route and package at checkout.</Text>
        </Card>
      </Col>
      <Col xs={24} lg={14}>
        <Card style={cardStyle}>
          <Title level={3}>Payment</Title>
          <Form layout="vertical" onFinish={onPay} requiredMark={false}>
            <Form.Item
              name="cardNumber"
              label="Card number"
              normalize={(v) => (v ?? "").replace(/[^\d]/g, "").replace(/(.{4})/g, "$1 ").trim().slice(0, 23)}
              rules={[
                { required: true, message: "Enter a card number" },
                {
                  validator: (_, v) => {
                    const digits = (v ?? "").replace(/\s/g, "");
                    if (!/^\d{13,19}$/.test(digits)) return Promise.reject(new Error("Card numbers are 13–19 digits (numbers only)."));
                    if (!luhnValid(digits)) return Promise.reject(new Error("That card number looks mistyped — please check it."));
                    return Promise.resolve();
                  },
                },
              ]}
            >
              <Input placeholder="4242 4242 4242 4242" inputMode="numeric" />
            </Form.Item>
            <Text type="secondary">Demo rule: cards ending in 0000 are declined by the backend (order rolls back, nothing is kept).</Text>
            <Button htmlType="submit" type="primary" size="large" block loading={busy} style={{ marginTop: 28 }}>Pay ${total.toFixed(2)} →</Button>
            <Button block onClick={onBack} style={{ marginTop: 14 }}>← Back</Button>
          </Form>
        </Card>
      </Col>
    </Row>
  );
}
