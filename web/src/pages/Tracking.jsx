import { Card, Space } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { getTracking } from '../api/tracking';
import { MapView } from '../components/MapView';
import { StatusTimeline } from '../components/StatusTimeline';
// Owner: Yuning Zhang (追踪). Polls GET /api/orders/:orderId/tracking every 5s
// and renders the live map + timeline. (Contract's WS push is a stretch feature;
// P0/P1 uses polling only.)
export default function Tracking() {
    const { orderId = '' } = useParams();
    const [track, setTrack] = useState();
    const timer = useRef();
    const load = useCallback(async () => setTrack(await getTracking(orderId)), [orderId]);
    useEffect(() => {
        load();
        timer.current = window.setInterval(load, 5000); // 5s polling for "live" movement
        return () => window.clearInterval(timer.current);
    }, [load]);
    if (!track) return null;
    // Contract tracking payload has the current position only, no route history.
    // TODO(Yuning): if the backend later exposes a route array, pass it to MapView.
    const vehicle = track.currentLat != null ? { lat: track.currentLat, lng: track.currentLng } : undefined;
    return (
        <Space direction="vertical" style={{ width: '100%' }} size={16}>
            <Card
                title={`Order #${track.orderId} · ${track.vehicleType ?? ''}`}
                extra={track.estimatedArrival ? `ETA ${new Date(track.estimatedArrival).toLocaleTimeString()}` : undefined}
            >
                <StatusTimeline status={track.status} />
                {/* TODO(team): the contract has no cancel endpoint — the Cancel button
                    from the wireframe is parked until the backend adds one. See 交接文档.md. */}
            </Card>

            <Card title="Live map" size="small">
                <MapView vehicle={vehicle} />
            </Card>
        </Space>
    );
}
