// Owner: Zihang Cao (recommendation + order creation). Wireframes 04–07, ONE route with 4 steps.
import {
    Alert,
    Button,
    Card,
    Checkbox,
    Col,
    Descriptions,
    Divider,
    Empty,
    Form,
    Input,
    InputNumber,
    Radio,
    Row,
    Segmented,
    Select,
    Space,
    Steps,
    Tag,
    Typography,
    message,
} from 'antd';
import {
    ArrowLeftOutlined,
    ArrowRightOutlined,
    CheckCircleOutlined,
    EnvironmentOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createOrder } from '../api/order';
import { getRecommendations } from '../api/recommendation';
import { getStations } from '../api/station';
import { MapView } from '../components/MapView';
import { VehicleIcon } from '../components/VehicleIcon';
import { useWizard } from '../store/wizard';

const { Title, Text } = Typography;

const ADDRESS_FIELDS = [
    'pickupLine1', 'pickupZip', 'pickupLat', 'pickupLng',
    'dropoffLine1', 'dropoffZip', 'dropoffLat', 'dropoffLng',
];
const PACKAGE_FIELDS = ['description', 'weightKg', 'priority'];

function formatMoney(value) {
    return `$${Number(value ?? 0).toFixed(2)}`;
}

function formatPoint(lat, lng) {
    if (lat == null || lng == null) return 'Choose a point on the map';
    return `${Number(lat).toFixed(5)}, ${Number(lng).toFixed(5)}`;
}

function getAddress(values, prefix) {
    return {
        addressId: null,
        line1: values[`${prefix}Line1`]?.trim(),
        city: 'San Francisco',
        zip: values[`${prefix}Zip`]?.trim(),
        lat: values[`${prefix}Lat`] ?? null,
        lng: values[`${prefix}Lng`] ?? null,
    };
}

function getPackage(values) {
    return {
        description: values.description?.trim(),
        weightKg: Number(values.weightKg),
        lengthCm: values.lengthCm ?? null,
        widthCm: values.widthCm ?? null,
        heightCm: values.heightCm ?? null,
        fragile: Boolean(values.fragile),
    };
}

function CandidateCard({ candidate, selected, onSelect }) {
    const unavailable = candidate.availableUnits <= 0;

    return (
        <Card
            hoverable={!unavailable}
            onClick={() => !unavailable && onSelect(candidate)}
            style={{
                height: '100%',
                opacity: unavailable ? 0.55 : 1,
                borderColor: selected ? '#1677ff' : undefined,
                boxShadow: selected ? '0 0 0 2px rgba(22,119,255,0.18)' : undefined,
                cursor: unavailable ? 'not-allowed' : 'pointer',
            }}
        >
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                    <Space>
                        <VehicleIcon vehicle={candidate.vehicleType} />
                        <Text strong>{candidate.vehicleType === 'DRONE' ? 'Drone delivery' : 'Robot delivery'}</Text>
                    </Space>
                    {selected && <Tag color="blue">Selected</Tag>}
                </div>

                <Text type="secondary">{candidate.stationName}</Text>

                <Row gutter={12}>
                    <Col span={12}>
                        <Text type="secondary">Estimated time</Text>
                        <div><Text strong>{candidate.estimatedTimeMinutes} min</Text></div>
                    </Col>
                    <Col span={12}>
                        <Text type="secondary">Estimated cost</Text>
                        <div><Text strong>{formatMoney(candidate.estimatedCost)}</Text></div>
                    </Col>
                </Row>

                <Space wrap>
                    {candidate.isFastest && <Tag color="green">Fastest</Tag>}
                    {candidate.isCheapest && <Tag color="gold">Lowest cost</Tag>}
                    <Tag>{candidate.availableUnits} available</Tag>
                    {unavailable && <Tag color="red">Unavailable</Tag>}
                </Space>

                <Text type="secondary">
                    Recommendation score: {Math.round((candidate.score ?? 0) * 100)}%
                </Text>
            </Space>
        </Card>
    );
}

export default function OrderWizard() {
    const navigate = useNavigate();
    const [form] = Form.useForm();
    const pickup = useWizard((state) => state.pickup);
    const dropoff = useWizard((state) => state.dropoff);
    const pkg = useWizard((state) => state.pkg);
    const priority = useWizard((state) => state.priority);
    const candidates = useWizard((state) => state.candidates);
    const selected = useWizard((state) => state.selected);
    const setAddress = useWizard((state) => state.setAddress);
    const setPackage = useWizard((state) => state.setPackage);
    const setCandidates = useWizard((state) => state.setCandidates);
    const selectCandidate = useWizard((state) => state.select);
    const resetWizard = useWizard((state) => state.reset);

    const [step, setStep] = useState(0);
    const [mapTarget, setMapTarget] = useState('pickup');
    const [stations, setStations] = useState([]);
    const [stationError, setStationError] = useState(false);
    const [recommendationLoading, setRecommendationLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    const pickupLat = Form.useWatch('pickupLat', form);
    const pickupLng = Form.useWatch('pickupLng', form);
    const dropoffLat = Form.useWatch('dropoffLat', form);
    const dropoffLng = Form.useWatch('dropoffLng', form);

    const mapPickup = useMemo(() => (
        pickupLat != null && pickupLng != null
            ? { lat: pickupLat, lng: pickupLng }
            : pickup?.lat != null && pickup?.lng != null
                ? { lat: pickup.lat, lng: pickup.lng }
                : undefined
    ), [pickup?.lat, pickup?.lng, pickupLat, pickupLng]);

    const mapDropoff = useMemo(() => (
        dropoffLat != null && dropoffLng != null
            ? { lat: dropoffLat, lng: dropoffLng }
            : dropoff?.lat != null && dropoff?.lng != null
                ? { lat: dropoff.lat, lng: dropoff.lng }
                : undefined
    ), [dropoff?.lat, dropoff?.lng, dropoffLat, dropoffLng]);

    useEffect(() => {
        getStations()
            .then((data) => setStations(data))
            .catch(() => setStationError(true));
    }, []);

    useEffect(() => {
        form.setFieldsValue({
            pickupLine1: pickup?.line1,
            pickupZip: pickup?.zip,
            pickupLat: pickup?.lat,
            pickupLng: pickup?.lng,
            dropoffLine1: dropoff?.line1,
            dropoffZip: dropoff?.zip,
            dropoffLat: dropoff?.lat,
            dropoffLng: dropoff?.lng,
            description: pkg?.description,
            weightKg: pkg?.weightKg,
            lengthCm: pkg?.lengthCm,
            widthCm: pkg?.widthCm,
            heightCm: pkg?.heightCm,
            fragile: pkg?.fragile,
            priority,
            paymentMethodId: 'pm_demo_visa_4242',
        });
    }, [dropoff, form, pickup, pkg, priority]);

    const saveAddresses = async () => {
        const values = await form.validateFields(ADDRESS_FIELDS);
        setAddress(getAddress(values, 'pickup'), getAddress(values, 'dropoff'));
        setStep(1);
    };

    const requestRecommendations = async () => {
        const values = await form.validateFields(PACKAGE_FIELDS);
        const nextPackage = getPackage(values);
        const nextPriority = values.priority;
        const addressValues = form.getFieldsValue(ADDRESS_FIELDS);
        const nextPickup = getAddress(addressValues, 'pickup');
        const nextDropoff = getAddress(addressValues, 'dropoff');
        const request = {
            pickup: nextPickup,
            dropoff: nextDropoff,
            package: nextPackage,
            priority: nextPriority,
        };

        setAddress(nextPickup, nextDropoff);
        setPackage(nextPackage, nextPriority);
        setRecommendationLoading(true);
        try {
            const response = await getRecommendations(request);
            setCandidates(response.candidates ?? []);
            selectCandidate(undefined);
            setStep(2);
        } catch {
            message.error('Unable to get delivery recommendations. Please try again.');
        } finally {
            setRecommendationLoading(false);
        }
    };

    const createDelivery = async () => {
        if (!selected) {
            message.warning('Choose an available delivery option first.');
            setStep(2);
            return;
        }

        const values = await form.validateFields(['paymentMethodId']);
        setSubmitting(true);
        try {
            const response = await createOrder({
                candidateId: selected.candidateId,
                pickup,
                dropoff,
                package: pkg,
                priority,
                paymentMethodId: values.paymentMethodId,
            });
            message.success(`Order ${response.orderId} was created.`);
            resetWizard();
            navigate(`/tracking/${response.orderId}`);
        } catch {
            message.error('Payment or order creation failed. Please review the details and try again.');
        } finally {
            setSubmitting(false);
        }
    };

    const pickOnMap = ({ lat, lng }) => {
        form.setFieldsValue({
            [`${mapTarget}Lat`]: lat,
            [`${mapTarget}Lng`]: lng,
        });
        message.info(`${mapTarget === 'pickup' ? 'Pickup' : 'Dropoff'} point updated.`);
    };

    const moveBack = () => {
        if (step === 0) {
            navigate('/dashboard');
            return;
        }
        setStep((current) => current - 1);
    };

    const renderAddressStep = () => (
        <Space direction="vertical" size={20} style={{ width: '100%' }}>
            <Alert
                showIcon
                type="info"
                message="Set two addresses and choose their map locations"
                description="The course scenario is limited to San Francisco. Click the map once for pickup and once for destination so the backend can calculate route and vehicle availability."
            />

            <Row gutter={[20, 20]}>
                <Col xs={24} md={12}>
                    <Card title="Pickup">
                        <Form.Item name="pickupLine1" label="Street address" rules={[{ required: true, message: 'Enter a pickup address.' }]}>
                            <Input prefix={<EnvironmentOutlined />} placeholder="123 Market St" />
                        </Form.Item>
                        <Form.Item name="pickupZip" label="ZIP code" rules={[{ required: true, message: 'Enter a ZIP code.' }]}>
                            <Input placeholder="94103" />
                        </Form.Item>
                        <Text type="secondary">Map point: {formatPoint(pickupLat, pickupLng)}</Text>
                    </Card>
                </Col>
                <Col xs={24} md={12}>
                    <Card title="Dropoff">
                        <Form.Item name="dropoffLine1" label="Street address" rules={[{ required: true, message: 'Enter a dropoff address.' }]}>
                            <Input prefix={<EnvironmentOutlined />} placeholder="456 Mission St" />
                        </Form.Item>
                        <Form.Item name="dropoffZip" label="ZIP code" rules={[{ required: true, message: 'Enter a ZIP code.' }]}>
                            <Input placeholder="94105" />
                        </Form.Item>
                        <Text type="secondary">Map point: {formatPoint(dropoffLat, dropoffLng)}</Text>
                    </Card>
                </Col>
            </Row>

            <Form.Item name="pickupLat" hidden rules={[{ required: true, message: 'Click the map to choose pickup.' }]}><InputNumber /></Form.Item>
            <Form.Item name="pickupLng" hidden rules={[{ required: true, message: 'Click the map to choose pickup.' }]}><InputNumber /></Form.Item>
            <Form.Item name="dropoffLat" hidden rules={[{ required: true, message: 'Click the map to choose dropoff.' }]}><InputNumber /></Form.Item>
            <Form.Item name="dropoffLng" hidden rules={[{ required: true, message: 'Click the map to choose dropoff.' }]}><InputNumber /></Form.Item>

            <Card
                title="Location picker"
                extra={
                    <Segmented
                        value={mapTarget}
                        onChange={setMapTarget}
                        options={[
                            { label: 'Set pickup', value: 'pickup' },
                            { label: 'Set dropoff', value: 'dropoff' },
                        ]}
                    />
                }
            >
                {stationError && (
                    <Alert
                        type="warning"
                        showIcon
                        style={{ marginBottom: 12 }}
                        message="Station data is unavailable"
                        description="You can still select coordinates and continue. Recommendation results will show station information once the backend responds."
                    />
                )}
                <MapView
                    pickup={mapPickup}
                    destination={mapDropoff}
                    route={mapPickup && mapDropoff ? [mapPickup, mapDropoff] : undefined}
                    onPick={pickOnMap}
                    height={360}
                />
                {stations.length > 0 && (
                    <Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
                        Dispatch stations loaded: {stations.map((station) => station.name).join(', ')}
                    </Text>
                )}
            </Card>
        </Space>
    );

    const renderPackageStep = () => (
        <Row justify="center">
            <Col xs={24} md={18} lg={14}>
                <Card title="Package and delivery preference">
                    <Form.Item name="description" label="What are you sending?" rules={[{ required: true, message: 'Describe the package.' }]}>
                        <Input placeholder="Example: A fragile birthday cake" />
                    </Form.Item>
                    <Row gutter={16}>
                        <Col xs={24} md={12}>
                            <Form.Item name="weightKg" label="Weight (kg)" rules={[{ required: true, message: 'Enter the package weight.' }]}>
                                <InputNumber min={0.1} max={50} precision={2} style={{ width: '100%' }} placeholder="2.0" />
                            </Form.Item>
                        </Col>
                        <Col xs={24} md={12}>
                            <Form.Item name="priority" label="Delivery priority" rules={[{ required: true }]}>
                                <Select
                                    options={[
                                        { value: 'STANDARD', label: 'Standard - balanced cost and time' },
                                        { value: 'EXPRESS', label: 'Express - prioritize fastest option' },
                                    ]}
                                />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Divider orientation="left">Optional dimensions (cm)</Divider>
                    <Row gutter={16}>
                        <Col xs={24} md={8}>
                            <Form.Item name="lengthCm" label="Length"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
                        </Col>
                        <Col xs={24} md={8}>
                            <Form.Item name="widthCm" label="Width"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
                        </Col>
                        <Col xs={24} md={8}>
                            <Form.Item name="heightCm" label="Height"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item>
                        </Col>
                    </Row>
                    <Form.Item name="fragile" valuePropName="checked">
                        <Checkbox>This package is fragile and needs careful handling.</Checkbox>
                    </Form.Item>
                </Card>
            </Col>
        </Row>
    );

    const renderRecommendationStep = () => (
        <Space direction="vertical" size={18} style={{ width: '100%' }}>
            <div>
                <Title level={3} style={{ marginBottom: 4 }}>Choose a delivery option</Title>
                <Text type="secondary">
                    Recommendations come from the backend. Availability, fastest, and lowest-cost labels are computed server-side.
                </Text>
            </div>
            {candidates.length === 0 ? (
                <Empty description="No delivery options are available for this request." />
            ) : (
                <Row gutter={[16, 16]}>
                    {candidates.map((candidate) => (
                        <Col xs={24} md={12} xl={8} key={candidate.candidateId}>
                            <CandidateCard
                                candidate={candidate}
                                selected={selected?.candidateId === candidate.candidateId}
                                onSelect={selectCandidate}
                            />
                        </Col>
                    ))}
                </Row>
            )}
        </Space>
    );

    const renderReviewStep = () => (
        <Row justify="center">
            <Col xs={24} lg={18}>
                <Card title="Review and confirm payment">
                    <Descriptions bordered column={1} size="small">
                        <Descriptions.Item label="Pickup">{pickup?.line1}, {pickup?.zip}</Descriptions.Item>
                        <Descriptions.Item label="Dropoff">{dropoff?.line1}, {dropoff?.zip}</Descriptions.Item>
                        <Descriptions.Item label="Package">
                            {pkg?.description} - {pkg?.weightKg} kg{pkg?.fragile ? ' - Fragile' : ''}
                        </Descriptions.Item>
                        <Descriptions.Item label="Delivery vehicle">
                            <Space><VehicleIcon vehicle={selected?.vehicleType} />{selected?.vehicleType}</Space>
                        </Descriptions.Item>
                        <Descriptions.Item label="Dispatch station">{selected?.stationName}</Descriptions.Item>
                        <Descriptions.Item label="Estimated arrival">{selected?.estimatedTimeMinutes} minutes</Descriptions.Item>
                        <Descriptions.Item label="Estimated cost">
                            <Text strong>{formatMoney(selected?.estimatedCost)}</Text>
                        </Descriptions.Item>
                    </Descriptions>

                    <Divider />
                    <Alert
                        type="info"
                        showIcon
                        message="Demo payment method"
                        description="The course contract sends a paymentMethodId in the create-order request. A production version would collect card data through a payment provider such as Stripe Elements, never through this form."
                        style={{ marginBottom: 16 }}
                    />
                    <Form.Item
                        name="paymentMethodId"
                        label="Payment method"
                        rules={[{ required: true, message: 'Choose a payment method.' }]}
                    >
                        <Select
                            options={[
                                { value: 'pm_demo_visa_4242', label: 'Demo Visa ending in 4242' },
                                { value: 'pm_demo_mastercard_4444', label: 'Demo Mastercard ending in 4444' },
                            ]}
                        />
                    </Form.Item>
                </Card>
            </Col>
        </Row>
    );

    const nextAction = () => {
        if (step === 0) return saveAddresses();
        if (step === 1) return requestRecommendations();
        if (step === 2) {
            if (!selected) {
                message.warning('Select an available robot or drone option.');
                return;
            }
            setStep(3);
        }
        return undefined;
    };

    const stepContent = [
        renderAddressStep(),
        renderPackageStep(),
        renderRecommendationStep(),
        renderReviewStep(),
    ][step];

    return (
        <Space direction="vertical" size={24} style={{ width: '100%' }}>
            <div>
                <Title level={2} style={{ marginBottom: 4 }}>Create a delivery</Title>
                <Text type="secondary">Complete the request, compare autonomous delivery plans, then confirm the order.</Text>
            </div>

            <Steps
                current={step}
                responsive
                items={[
                    { title: 'Locations' },
                    { title: 'Package' },
                    { title: 'Recommendations' },
                    { title: 'Confirm' },
                ]}
            />

            <Form form={form} layout="vertical" initialValues={{ priority: 'STANDARD', fragile: false }}>
                {stepContent}
            </Form>

            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                <Button icon={<ArrowLeftOutlined />} onClick={moveBack}>
                    {step === 0 ? 'Back to dashboard' : 'Back'}
                </Button>
                {step < 3 ? (
                    <Button
                        type="primary"
                        icon={<ArrowRightOutlined />}
                        iconPosition="end"
                        loading={step === 1 && recommendationLoading}
                        onClick={nextAction}
                    >
                        {step === 0 ? 'Continue to package' : step === 1 ? 'Get recommendations' : 'Review order'}
                    </Button>
                ) : (
                    <Button
                        type="primary"
                        icon={<CheckCircleOutlined />}
                        loading={submitting}
                        onClick={createDelivery}
                    >
                        Pay and create order
                    </Button>
                )}
            </div>
        </Space>
    );
}
