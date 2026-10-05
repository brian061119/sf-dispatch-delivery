import { Alert, Button, Card, Col, Descriptions, Input, Popconfirm, Progress, Row, Space, Timeline, Typography, message } from 'antd';
import { EnvironmentOutlined, SafetyOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { getTrackingByCode } from '../api/tracking';
import { cancelOrder } from '../api/order';
import { getStations } from '../api/station';
import { MapView } from '../components/MapView';
import { StatusBadge } from '../components/StatusBadge';
import { StatusTimeline } from '../components/StatusTimeline';
import { VehicleIcon } from '../components/VehicleIcon';
import { BRAND_GRADIENT, BRAND_HERO_BG, BRAND_NAME, CARD_SHADOW, FULL_BLEED, HERO_BG_SIZE } from '../lib/brand';
import { isAuthed } from '../lib/auth';
import { apiErrorMessage } from '../lib/http';
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
    const [stations, setStations] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [cancelBusy, setCancelBusy] = useState(false);
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

    // Cancel (owner only): the tracking endpoint itself is public, so gate the
    // button on a login; the cancel API enforces ownership server-side and
    // returns 403 for anyone who is not the owner.
    const canCancel = Boolean(
        tracking
        && isAuthed()
        && ['PENDING_PAYMENT', 'PAID', 'PICKING_UP'].includes(tracking.detailStatus ?? tracking.status)
    );
    async function doCancel() {
        setCancelBusy(true);
        try {
            // Cancel endpoints look the order up by its SFORD order number,
            // not the numeric database id (contract-wide "orderId" semantics).
            await cancelOrder(tracking.orderNumber ?? tracking.orderId);
            message.success('Order cancelled. Any eligible refund has been processed.');
            await lookup(activeCodeRef.current, { silent: true });
        } catch (err) {
            message.error(apiErrorMessage(err, 'Could not cancel this order.'));
        } finally {
            setCancelBusy(false);
        }
    }

    // The 3 service stations are public data (GET /api/stations, permitAll).
    // StationInfoDto uses latitude/longitude — normalize once for MapView.
    useEffect(() => {
        getStations()
            .then((items) => setStations((items ?? []).map((s) => ({ ...s, lat: s.lat ?? s.latitude, lng: s.lng ?? s.longitude }))))
            .catch(() => {}); // stations are decorative on this page — ignore failures
    }, []);

    const events = tracking?.events ?? [];
    const hasPosition = Number.isFinite(Number(tracking?.currentLat)) && Number.isFinite(Number(tracking?.currentLng));
    const vehicleNow = hasPosition ? { lat: Number(tracking.currentLat), lng: Number(tracking.currentLng) } : null;
    const toPoint = (lat, lng) => (Number.isFinite(Number(lat)) && Number.isFinite(Number(lng)) ? { lat: Number(lat), lng: Number(lng) } : null);
    const pickupPoint = toPoint(tracking?.pickupLat, tracking?.pickupLng);
    const destinationPoint = toPoint(tracking?.destinationLat, tracking?.destinationLng);
    // Planned route as a polyline. Preferred source is `routePolyline`: the real
    // road-network geometry (station → pickup → dropoff → station) returned by the
    // routing provider, so the line follows streets instead of cutting through
    // buildings. When it is absent (offline/straight-line provider, or an order
    // that has not started) we fall back to the milestone coordinates, which at
    // least give the route skeleton.
    const roadGeometry = Array.isArray(tracking?.routePolyline)
        ? tracking.routePolyline
            .map((pair) => (Array.isArray(pair) ? toPoint(pair[0], pair[1]) : null))
            .filter(Boolean)
        : [];
    const usesRoadGeometry = roadGeometry.length > 1;
    const routePoints = usesRoadGeometry
        ? roadGeometry
        : events
            .map((e) => toPoint(e.eventLat, e.eventLng))
            .filter(Boolean)
            .filter((p, i, arr) => i === 0 || p.lat !== arr[i - 1].lat || p.lng !== arr[i - 1].lng);
    // With real road geometry the live position already lies on the drawn line
    // (and has its own marker), so only the skeleton needs it appended.
    if (!usesRoadGeometry && vehicleNow && (routePoints.length === 0 || vehicleNow.lat !== routePoints[routePoints.length - 1].lat || vehicleNow.lng !== routePoints[routePoints.length - 1].lng)) {
        routePoints.push(vehicleNow);
    }
    // A cancelled order has no route worth drawing: the vehicle was released
    // and the plan is dead. Showing markers (pickup/destination/stations) is
    // honest; a stray line stitched from leftover event coordinates is not.
    const showRoute = tracking?.status !== 'CANCELLED' && routePoints.length > 1;

    // Split the planned route into "already travelled" (solid, darker) and
    // "still ahead" (dashed, lighter) so the user can see live progress.
    // Anchor the split at the vehicle's live position when we have it; otherwise
    // fall back to progressPercent projected onto the polyline arc length.
    const splitRoute = (pts, veh, progress) => {
        if (!pts || pts.length < 2) return { traveledPoints: [], remainingPoints: pts ?? [] };
        if (veh) {
            let best = 0;
            let bestD = Infinity;
            for (let i = 0; i < pts.length; i++) {
                const d = (pts[i].lat - veh.lat) ** 2 + (pts[i].lng - veh.lng) ** 2;
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            return {
                traveledPoints: [...pts.slice(0, best + 1), veh],
                remainingPoints: pts.slice(best),
            };
        }
        const frac = Number(progress);
        if (Number.isFinite(frac) && frac > 0 && frac < 100) {
            const seg = [];
            let total = 0;
            for (let i = 1; i < pts.length; i++) {
                seg.push(Math.hypot(pts[i].lat - pts[i - 1].lat, pts[i].lng - pts[i - 1].lng));
                total += seg[seg.length - 1];
            }
            let target = total * (frac / 100);
            let acc = 0;
            let idx = 0;
            for (let i = 0; i < seg.length; i++) {
                if (acc + seg[i] >= target) {
                    idx = i;
                    break;
                }
                acc += seg[i];
                idx = i;
            }
            return { traveledPoints: pts.slice(0, idx + 1), remainingPoints: pts.slice(idx) };
        }
        return { traveledPoints: [], remainingPoints: pts };
    };
    const { traveledPoints, remainingPoints } = splitRoute(routePoints, vehicleNow, tracking?.progressPercent);
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
                // flex:1 + centered → the empty state sits in the middle of the
                // free space instead of hugging the hero, which left a large
                // bare gap above the footer on tall windows.
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
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
                    title={<Space><span>Order {tracking.orderNumber ?? tracking.orderId}</span><StatusBadge status={tracking.status} /></Space>}
                    extra={(
                        <Space size={10}>
                            {tracking.vehicleType ? (<Space size={6}><VehicleIcon vehicle={tracking.vehicleType} />{tracking.vehicleType}</Space>) : null}
                            {canCancel && (
                                <Popconfirm
                                    title="Cancel this order?"
                                    description="Free before the vehicle departs; a $2.50 dispatch fee applies once it is en route to pickup."
                                    okText="Cancel order"
                                    okButtonProps={{ danger: true }}
                                    onConfirm={doCancel}
                                >
                                    <Button danger size="small" loading={cancelBusy}>Cancel</Button>
                                </Popconfirm>
                            )}
                        </Space>
                    )}
                >
                    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                        <StatusTimeline status={tracking.status} events={events} currentStage={tracking.currentStage} />

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

                        {hasPosition || pickupPoint || destinationPoint ? (
                            <MapView
                                pickup={pickupPoint}
                                destination={destinationPoint}
                                stations={stations}
                                route={showRoute ? remainingPoints : undefined}
                                traveled={showRoute ? traveledPoints : undefined}
                                vehicle={vehicleNow ? { ...vehicleNow, type: tracking.vehicleType, code: tracking.vehicleCode } : undefined}
                                height={360}
                            />
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
