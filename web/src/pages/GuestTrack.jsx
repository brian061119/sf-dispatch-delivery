import { Alert, Card, Descriptions, Empty, Input, Progress, Space, Timeline, Typography } from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { getTrackingByCode } from '../api/tracking';
import { MapView } from '../components/MapView';
import { StatusBadge } from '../components/StatusBadge';
import { StatusTimeline } from '../components/StatusTimeline';
import { VehicleIcon } from '../components/VehicleIcon';
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
//   /tracking/<code>   canonical, shareable result link
//   /track             search box, guests land here by default
//   /track?code=<code> legacy deep link, still accepted and auto-upgraded

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
    const { code: pathCode } = useParams();
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const urlCode = searchParams.get('code') ?? '';
    // A code can arrive either as a path segment (/tracking/<code>) or as a
    // query string (/track?code=<code>) from links shared before the change.
    const urlLookupCode = normalizeCode(pathCode ?? urlCode);

    const [code, setCode] = useState(pathCode ?? urlCode);
    const [tracking, setTracking] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    // Guards against a slow response overwriting a newer lookup, and keeps the
    // polling callback reading the current code without re-creating itself.
    const activeCodeRef = useRef(null);
    // Remembers which code the URL already asked for, so moving the code into
    // the path does not trigger a second lookup (each call advances the
    // backend simulation, so duplicate requests are not harmless).
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
            // Move the code into the path so the URL can be copied and sent to
            // the recipient as-is. replace=true keeps Back leaving the page
            // normally instead of walking back through every lookup.
            if (normalizeCode(pathCode ?? urlCode) !== normalized) {
                navigate(`/tracking/${normalized}`, { replace: true });
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
    }, [navigate, pathCode, urlCode]);

    // Poll only while the delivery can still move. Note that a tracking
    // request is not a pure read: the backend advances its simulation on every
    // call, so stopping at the terminal state matters.
    useEffect(() => {
        if (!tracking || TERMINAL_STATUSES.includes(tracking.status)) return undefined;
        const timer = setInterval(() => lookup(activeCodeRef.current, { silent: true }), POLL_MS);
        return () => clearInterval(timer);
    }, [tracking, lookup]);

    // Deep link: /tracking/<code> (or the legacy /track?code=<code>) runs the
    // lookup on load. The ref guard stops the URL rewrite below from firing a
    // second request for the same code.
    useEffect(() => {
        if (!urlLookupCode || lastLookupRef.current === urlLookupCode) return;
        setCode(urlLookupCode);
        lookup(urlLookupCode);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [urlLookupCode]);

    const events = tracking?.events ?? [];
    const hasPosition = Number.isFinite(Number(tracking?.currentLat)) && Number.isFinite(Number(tracking?.currentLng));
    const progress = deliveryProgress(tracking?.progressPercent);

    return (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <div>
                <Typography.Title level={3} style={{ marginBottom: 4 }}>Track your delivery</Typography.Title>
                <Typography.Text type="secondary">
                    Enter the tracking code from your order confirmation — no account needed.
                </Typography.Text>
            </div>

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
            />

            {error && <Alert type="error" showIcon message={error} />}

            {!tracking && !error && !loading && (
                <Card>
                    <Empty description="Enter a tracking code to see where your delivery is." />
                </Card>
            )}

            {tracking && (
                <Card
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

                        <Typography.Text type="secondary">
                            Have an account? <Link to="/login">Log in</Link> to see all your orders and manage this delivery.
                        </Typography.Text>
                    </Space>
                </Card>
            )}
        </Space>
    );
}
