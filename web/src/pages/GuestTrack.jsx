import { Alert, Card, Col, Descriptions, Input, Progress, Row, Space, Timeline, Typography } from 'antd';
import { EnvironmentOutlined, SafetyOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { getTrackingByCode } from '../api/tracking';
import { MapView } from '../components/MapView';
import { StatusBadge } from '../components/StatusBadge';
import { StatusTimeline } from '../components/StatusTimeline';
import { VehicleIcon } from '../components/VehicleIcon';
import { BRAND_GRADIENT, BRAND_HERO_BG, BRAND_NAME, CARD_SHADOW, FULL_BLEED, HERO_BG_SIZE } from '../lib/brand';
import { isAuthed } from '../lib/auth';
// Owner: Yuning Zhang (tracking). Wireframe: wireframes/11_GuestTrack.svg
// PUBLIC page (no login). This is also the landing page for guests — App.jsx
// sends every unauthenticated visit to /track.
//
// Lookup key is the 16-char trackingCode (random, safe to share), NOT the order
// number: GET /api/tracking/:trackingCode is permitAll, while
// GET /api/orders/:orderNumber/tracking requires a JWT and ownership.
// If the visitor only has the order number we cannot look it up anonymously —
// we tell them to log in instead of failing silently.
//
// URLs (both public, both render this page):
//   /track             search box, guests land here by default
//   /track?code=<code> canonical, shareable result link

const POLL_MS = 5000;
// Once the delivery reaches one of these the backend stops moving it, so
// polling would only write to the database for nothing.
const TERMINAL_STATUSES = ['DELIVERED', 'CANCELLED'];
const ORDER_NUMBER_PREFIX = 'SFORD';

/** Codes are case-insensitive and people paste them with spaces. */
function normalizeCode(raw) {
    return (raw ?? '').trim().replace(/\s+/g, '').toUpperCase();
}

// The backend simulation ends the *delivery* at 75% of progressPercent: the
// last quarter is the empty vehicle driving back to its station (RETURNING),
// which means nothing to the recipient. Stretch 0-75 onto 0-100 so the bar
// reads 100% exactly when the package arrives.
function deliveryProgress(progressPercent) {
    const pct = Number(progressPercent);
    if (!Number.isFinite(pct) || pct <= 0) return 0;
    return Math.min(100, Math.round((pct / 75) * 100));
}

export default function GuestTrack() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const urlCode = searchParams.get('code') ?? '';
    const urlLookupCode = normalizeCode(urlCode);

    const [code, setCode] = useState(urlCode);
    const [tracking, setTracking] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    // Guards against a slow response overwriting a newer lookup, and keeps the
    // polling callback reading the current code without re-creating itself.
    const activeCodeRef = useRef(null);
    // Remembers which code the URL already asked for, so writing the code into
    // the query string does not trigger a second lookup (each call advances
    // the backend simulation, so duplicate requests are not harmless).
    const lastLookupRef = useRef(null);

    const lookup = useCallback(async (raw, { silent = false } = {}) => {
        const normalized = normalizeCode(raw);
        if (!normalized) {
            setError('Enter the tracking code from your order confirmation.');
            return;
        }
        if (normalized.startsWith(ORDER_NUMBER_PREFIX)) {
            setTracking(null);
            setError('That looks like an order number. Order numbers need a login — ask the sender for the tracking code, or log in to see your orders.');
            return;
        }
        activeCodeRef.current = normalized;
        lastLookupRef.current = normalized;
        if (!silent) setLoading(true);
        setError(null);
        try {
            const data = await getTrackingByCode(normalized);
            if (activeCodeRef.current !== normalized) return; // stale response
            setTracking(data);
            // Put the code into the query string so the URL can be copied and
            // sent to the recipient as-is. replace=true keeps Back leaving the
            // page normally instead of walking back through every lookup.
            if (urlLookupCode !== normalized) {
                navigate(`/track?code=${encodeURIComponent(normalized)}`, { replace: true });
            }
        } catch (err) {
            if (activeCodeRef.current !== normalized) return;
            setTracking(null);
            setError(err?.response?.status === 404
                ? 'No delivery found for that tracking code. Check the code and try again.'
                : 'Could not reach the tracking service. Please try again in a moment.');
        } finally {
            if (!silent) setLoading(false);
        }
    }, [navigate, urlLookupCode]);

    // Poll only while the delivery can still move. Note that a tracking
    // request is not a pure read: the backend advances its simulation on every
    // call, so stopping at the terminal state matters.
    useEffect(() => {
        if (!tracking || TERMINAL_STATUSES.includes(tracking.status)) return undefined;
        const timer = setInterval(() => lookup(activeCodeRef.current, { silent: true }), POLL_MS);
        return () => clearInterval(timer);
    }, [tracking, lookup]);

    // Deep link: /track?code=<code> runs the lookup on load. The ref guard
    // stops the URL rewrite above from firing a second request for the same
    // code.
    useEffect(() => {
        if (!urlLookupCode || lastLookupRef.current === urlLookupCode) return;
        setCode(urlLookupCode);
        lookup(urlLookupCode);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [urlLookupCode]);

    const events = tracking?.events ?? [];
    const hasPosition = Number.isFinite(Number(tracking?.currentLat)) && Number.isFinite(Number(tracking?.currentLng));
    const progress = deliveryProgress(tracking?.progressPercent);
    const authed = isAuthed();

    // Hero banner, FULL-BLEED (FULL_BLEED escapes the centered 1100px content
    // column so the gradient touches the real viewport edges — the old version
    // floated as a banner and left a big gray void below). The search box lives
    // inside the hero; the empty space under it is filled with the 3-step
    // "how it works" row instead of bare page background.
    const heroStyle = {
        ...FULL_BLEED,
        margin: 0,
        padding: '56px 24px 48px',
        background: BRAND_GRADIENT,
        backgroundImage: BRAND_HERO_BG,
        backgroundSize: HERO_BG_SIZE,
        color: '#fff',
        textAlign: 'center',
    };

    const steps = [
        { icon: <SearchOutlined />, title: 'Enter your code', desc: 'Find the 16-character tracking code in your order confirmation.' },
        { icon: <EnvironmentOutlined />, title: 'Watch it move', desc: 'The map and progress bar update live while the vehicle is on the road.' },
        { icon: <SafetyOutlined />, title: 'Signed & delivered', desc: 'Get the arrival time and the full event history — no account needed.' },
    ];

    return (
        // minHeight = 100vh - header(64) - content padding(2×24): the column
        // always fills the viewport, and the footer's marginTop:auto pins it
        // to the bottom so the page never ends in a bare gray void.
        <div style={{ minHeight: 'calc(100vh - 112px)', display: 'flex', flexDirection: 'column', gap: 24 }}>
            <div style={heroStyle}>
                <Typography.Title level={2} style={{ color: '#fff', marginBottom: 8 }}>Track your delivery</Typography.Title>
                <Typography.Text style={{ color: 'rgba(255,255,255,.88)' }}>
                    Enter the tracking code from your order confirmation — no account needed.
                </Typography.Text>
                <div style={{ maxWidth: 620, margin: '28px auto 0', textAlign: 'left' }}>
                    <Input.Search
                        size="large"
                        allowClear
                        value={code}
                        onChange={(e) => setCode(e.target.value)}
                        onSearch={(value) => lookup(value)}
                        loading={loading}
                        enterButton="Track"
                        placeholder="Tracking code, e.g. A1B2C3D4E5F6G7H8"
                        maxLength={32}
                        style={{ boxShadow: '0 8px 24px rgba(10,30,80,.25)', borderRadius: 8 }}
                    />
                    {error && <div style={{ marginTop: 16 }}><Alert type="error" showIcon message={error} /></div>}
                </div>
            </div>

            {!tracking && !error && !loading && (
                <div>
                    <Row gutter={[16, 16]}>
                        {steps.map((step, index) => (
                            <Col xs={24} md={8} key={step.title}>
                                <Card style={{ borderRadius: 14, boxShadow: CARD_SHADOW, height: '100%' }}>
                                    <Space size={12} align="flex-start">
                                        <div style={{
                                            width: 40, height: 40, borderRadius: 10, flexShrink: 0,
                                            background: 'linear-gradient(135deg, #e6f1fb, #f3eaf8)',
                                            color: '#1677ff', fontSize: 18,
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                        }}>
                                            {step.icon}
                                        </div>
                                        <div>
                                            <Typography.Text strong>{index + 1} · {step.title}</Typography.Text>
                                            <div><Typography.Text type="secondary">{step.desc}</Typography.Text></div>
                                        </div>
                                    </Space>
                                </Card>
                            </Col>
                        ))}
                    </Row>
                    <Typography.Text type="secondary" style={{ display: 'block', textAlign: 'center', marginTop: 20 }}>
                        Tip: tracking codes look like <Typography.Text code>A1B2C3D4E5F6G7H8</Typography.Text>. Order numbers (SFORD…) need a <Link to="/login">logged-in</Link> account.
                    </Typography.Text>
                </div>
            )}

            {tracking && (
                <Card
                    style={{ borderRadius: 14, boxShadow: CARD_SHADOW }}
                    title={<Space><span>Order {tracking.orderId}</span><StatusBadge status={tracking.status} /></Space>}
                    extra={tracking.vehicleType ? (<Space size={6}><VehicleIcon vehicle={tracking.vehicleType} />{tracking.vehicleType}</Space>) : null}
                >
                    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                        <StatusTimeline status={tracking.status} />

                        <div>
                            <Progress
                                percent={progress}
                                status={tracking.status === 'CANCELLED' ? 'exception' : progress >= 100 ? 'success' : 'active'}
                            />
                            <Typography.Text type="secondary">
                                {tracking.currentStageDescription}
                            </Typography.Text>
                        </div>

                        <Descriptions size="small" column={2} bordered>
                            <Descriptions.Item label="Detailed status">{tracking.detailStatus ?? tracking.status}</Descriptions.Item>
                            <Descriptions.Item label="Estimated arrival">
                                {tracking.estimatedArrival ? dayjs(tracking.estimatedArrival).format('MMM D, HH:mm') : '—'}
                            </Descriptions.Item>
                        </Descriptions>

                        {hasPosition ? (
                            <MapView vehicle={{ lat: Number(tracking.currentLat), lng: Number(tracking.currentLng) }} height={320} />
                        ) : (
                            <Alert type="info" showIcon message="Location will appear once the vehicle is on the move." />
                        )}

                        <Timeline
                            items={events.length
                                ? events.map((event) => ({
                                    children: (
                                        <div>
                                            <Typography.Text strong>{String(event.stage ?? 'UPDATE').replace(/_/g, ' ')}</Typography.Text>
                                            <div>
                                                <Typography.Text type="secondary">
                                                    {event.eventTime ? dayjs(event.eventTime).format('MMM D, HH:mm') : ''}
                                                </Typography.Text>
                                            </div>
                                            <div>{event.statusDescription}</div>
                                        </div>
                                    ),
                                }))
                                : [{ children: tracking.currentStageDescription ?? 'Waiting for the first update.' }]}
                        />

                        {/* Guests get the login nudge; logged-in users already have
                            the header nav, so we do NOT show a Log in link to them. */}
                        {!authed && (
                            <Typography.Text type="secondary">
                                Have an account? <Link to="/login">Log in</Link> to see all your orders and manage this delivery.
                            </Typography.Text>
                        )}
                    </Space>
                </Card>
            )}

            {/* Shared footer so the page never ends in bare gray background. */}
            <Typography.Text type="secondary" style={{ display: 'block', textAlign: 'center', padding: '4px 0 8px', marginTop: 'auto' }}>
                {BRAND_NAME} — robot & drone delivery demo · 3 stations across San Francisco
            </Typography.Text>
        </div>
    );
}
