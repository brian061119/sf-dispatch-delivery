import { App, Button, Card, Form, Input, InputNumber, Radio, Space, Steps, Switch, Tag } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createOrder } from '../api/order';
import { getRecommendations } from '../api/recommendation';
import { MapView } from '../components/MapView';
import { VehicleIcon } from '../components/VehicleIcon';
import { useOrders } from '../store/orders';
import { useWizard } from '../store/wizard';
// Owner: Zihang Cao (recommendations + order create). Frames 04–07 as ONE route with 4 steps.
// Contract flow: POST /api/recommendations -> pick a candidate -> POST /api/orders
// (embedded payment: pay + create in one call, then route to tracking).
export default function OrderWizard() {
    const [step, setStep] = useState(0);
    const w = useWizard();
    const refreshOrders = useOrders((s) => s.refresh);
    const nav = useNavigate();
    const { message } = App.useApp();
    const [loading, setLoading] = useState(false);
    // Step 1 → 2: save addresses in the contract shape. city is fixed (SF scenario);
    // addressId null = free-text address; lat/lng null until the map picker lands.
    function nextFromAddresses(v) {
        const mk = (line1, zip) => ({ addressId: null, line1, city: 'San Francisco', zip, lat: null, lng: null });
        w.setAddress(mk(v.pickupLine1, v.pickupZip), mk(v.dropoffLine1, v.dropoffZip));
        setStep(1);
    }
    // Step 2 → 3: save package + priority, then fetch candidate delivery options.
    async function nextFromPackage(v) {
        const pkg = {
            description: v.description,
            weightKg: v.weightKg,
            lengthCm: v.lengthCm ?? null,
            widthCm: v.widthCm ?? null,
            heightCm: v.heightCm ?? null,
            fragile: !!v.fragile,
        };
        w.setPackage(pkg, v.priority);
        setLoading(true);
        try {
            const res = await getRecommendations({ pickup: w.pickup, dropoff: w.dropoff, package: pkg, priority: v.priority });
            w.setCandidates(res.candidates);
            setStep(2);
        }
        catch {
            message.error('Failed to load delivery options');
        }
        finally {
            setLoading(false);
        }
    }
    // Step 4: pay → create the order (embedded payment per the contract).
    async function onPay(v) {
        setLoading(true);
        try {
            const res = await createOrder({
                candidateId: w.selected.candidateId,
                pickup: w.pickup,
                dropoff: w.dropoff,
                package: w.pkg,
                priority: w.priority,
                // Mock payment. Real impl: tokenize the card with Stripe Elements
                // and send the resulting paymentMethodId — never raw card numbers.
                paymentMethodId: `mock_card_${String(v.cardNumber).slice(-4)}`,
            });
            await refreshOrders();
            w.reset();
            message.success(`Order placed: ${res.orderId}`);
            nav(`/tracking/${res.orderId}`);
        }
        catch {
            message.error('Checkout failed');
        }
        finally {
            setLoading(false);
        }
    }
    const toMarker = (a) => (a && a.lat != null ? { lat: a.lat, lng: a.lng } : undefined);
    const steps = [
        // ---- 04 Addresses ------------------------------------------------------
        <Card key="a" title="Addresses" extra="Step 1 of 4">
      <Form layout="vertical" onFinish={nextFromAddresses} initialValues={{
            pickupLine1: w.pickup?.line1, pickupZip: w.pickup?.zip,
            dropoffLine1: w.dropoff?.line1, dropoffZip: w.dropoff?.zip,
        }}>
        <Space.Compact block>
          <Form.Item name="pickupLine1" label="Pickup address" style={{ flex: 1 }} rules={[{ required: true, message: 'Required' }]}>
            <Input placeholder="e.g. 123 Market St"/>
          </Form.Item>
          <Form.Item name="pickupZip" label="ZIP" rules={[{ required: true, message: 'Required' }]}>
            <Input placeholder="94103" style={{ width: 110 }}/>
          </Form.Item>
        </Space.Compact>
        <Space.Compact block>
          <Form.Item name="dropoffLine1" label="Dropoff address" style={{ flex: 1 }} rules={[{ required: true, message: 'Required' }]}>
            <Input placeholder="e.g. 456 Mission St"/>
          </Form.Item>
          <Form.Item name="dropoffZip" label="ZIP" rules={[{ required: true, message: 'Required' }]}>
            <Input placeholder="94105" style={{ width: 110 }}/>
          </Form.Item>
        </Space.Compact>
        {/* TODO(Zihang): map click-to-pick that fills lat/lng on the address objects */}
        <MapView pickup={toMarker(w.pickup)} destination={toMarker(w.dropoff)} height={240}/>
        <Button type="primary" htmlType="submit" style={{ marginTop: 16 }}>
          Continue
        </Button>
      </Form>
    </Card>,
        // ---- 05 Package + priority ----------------------------------------------
        <Card key="p" title="Package" extra="Step 2 of 4">
      <Form layout="vertical" onFinish={nextFromPackage} initialValues={{ ...w.pkg, priority: w.priority }}>
        <Form.Item name="description" label="Item description" rules={[{ required: true, message: 'Required' }]}>
          <Input placeholder="e.g. A 2kg book"/>
        </Form.Item>
        <Form.Item name="weightKg" label="Weight (kg)" rules={[{ required: true, message: 'Required' }]}>
          <InputNumber min={0} style={{ width: '100%' }}/>
        </Form.Item>
        <Space>
          <Form.Item name="lengthCm" label="L (cm)">
            <InputNumber min={0}/>
          </Form.Item>
          <Form.Item name="widthCm" label="W (cm)">
            <InputNumber min={0}/>
          </Form.Item>
          <Form.Item name="heightCm" label="H (cm)">
            <InputNumber min={0}/>
          </Form.Item>
        </Space>
        <Form.Item name="fragile" label="Fragile" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="priority" label="Priority" rules={[{ required: true, message: 'Required' }]}>
          <Radio.Group options={['STANDARD', 'EXPRESS']} optionType="button"/>
        </Form.Item>
        <Space>
          <Button onClick={() => setStep(0)}>Back</Button>
          <Button type="primary" htmlType="submit" loading={loading}>
            Get delivery options
          </Button>
        </Space>
      </Form>
    </Card>,
        // ---- 06 Delivery option (candidates from /api/recommendations) ------------
        <Card key="o" title="Delivery option" extra="Step 3 of 4">
      <Radio.Group style={{ display: 'flex', flexDirection: 'column', gap: 12, width: '100%' }} value={w.selected?.candidateId} onChange={(e) => w.select(w.candidates.find((c) => c.candidateId === e.target.value))}>
        {[...w.candidates]
                .sort((a, b) => Number(b.isFastest || b.isCheapest) - Number(a.isFastest || a.isCheapest) || a.estimatedCost - b.estimatedCost)
                .map((c) => (<Card key={c.candidateId} size="small" style={{ borderColor: c.isFastest || c.isCheapest ? '#1677ff' : undefined }}>
              <Radio value={c.candidateId} disabled={c.availableUnits === 0}>
                <Space size="middle" wrap>
                  {c.isFastest && <Tag color="green">Fastest</Tag>}
                  {c.isCheapest && <Tag color="gold">Cheapest</Tag>}
                  <VehicleIcon vehicle={c.vehicleType}/>
                  <b>{c.vehicleType}</b>
                  <span>
                    ${c.estimatedCost.toFixed(2)} · {c.estimatedTimeMinutes} min
                  </span>
                  <span style={{ color: '#888' }}>{c.stationName}</span>
                  {c.availableUnits === 0 && <span style={{ color: 'red' }}>unavailable</span>}
                </Space>
              </Radio>
            </Card>))}
      </Radio.Group>
      <Space style={{ marginTop: 16 }}>
        <Button onClick={() => setStep(1)}>Back</Button>
        <Button type="primary" disabled={!w.selected} onClick={() => setStep(3)}>
          Continue
        </Button>
      </Space>
    </Card>,
        // ---- 07 Review & Pay ------------------------------------------------------
        <Card key="r" title="Review & Pay" extra="Step 4 of 4">
      {/* TODO(Zihang): order summary card (pickup/dropoff/package/candidate/cost) */}
      <Form layout="vertical" onFinish={onPay}>
        <Form.Item name="cardNumber" label="Card number" rules={[{ required: true, message: 'Required' }]}>
          <Input placeholder="4242 4242 4242 4242"/>
        </Form.Item>
        <Space>
          <Form.Item name="expiry" label="Expiry">
            <Input placeholder="MM/YY"/>
          </Form.Item>
          <Form.Item name="cvv" label="CVV">
            <Input.Password placeholder="123" style={{ width: 100 }}/>
          </Form.Item>
        </Space>
        <div style={{ color: '#888', marginBottom: 12 }}>Mock payment — no real charge.</div>
        <Space>
          <Button onClick={() => setStep(2)}>Back</Button>
          <Button type="primary" htmlType="submit" loading={loading} disabled={!w.selected}>
            Pay & place order
          </Button>
        </Space>
      </Form>
    </Card>,
    ];
    return (<div>
      <Steps current={step} items={[{ title: 'Addresses' }, { title: 'Package' }, { title: 'Delivery option' }, { title: 'Review & Pay' }]} style={{ marginBottom: 24 }}/>
      {steps[step]}
    </div>);
}
