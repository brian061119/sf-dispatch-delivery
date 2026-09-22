// Owner: Yuning Zhang (tracking). Wireframe: wireframes/08_Tracking.svg
import PagePlaceholder from '../components/PagePlaceholder';
export default function Tracking() {
    return (<PagePlaceholder owner="Yuning Zhang" page="Live Tracking" wireframe="wireframes/08_Tracking.svg" apis={['getTracking(orderId)  ->  GET /api/orders/:orderId/tracking  (poll every 5s; the optional WS in the contract is a stretch goal)']} todos={[
            'Poll getTracking every 5s with setInterval; clear the interval on unmount (memory-leak cleanup is part of the task)',
            'Render <StatusTimeline status/> + <MapView/> with the courier\'s current position',
            'The contract returns the current position only — no route history. If you want a trail, file a backend request first',
            'The contract has NO cancel endpoint: the wireframe\'s Cancel button stays parked until the backend adds one',
        ]}/>);
}
