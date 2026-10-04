import { Steps, Typography } from 'antd';
import dayjs from 'dayjs';
// Progress bar for the Tracking page. Contract has 4 states; CANCELLED is an
// error branch off the happy path PENDING -> IN_TRANSIT -> DELIVERED.
// The backend's internal states (PENDING_PAYMENT / PAID / PICKING_UP, see
// types/api.js InternalOrderStatus) are "not yet in transit" — they land at
// index -1 and clamp to step 0 (PENDING) via Math.max below.
const FLOW = ['PENDING', 'IN_TRANSIT', 'DELIVERED'];

// Enhanced vertical variant (used by GuestTrack when tracking events exist).
// Maps backend TrackingStage milestones onto the customer-facing timeline:
//   Confirmed  <- TO_PICKUP   ("Payment succeeded. Dispatched ROBOT-…")
//   Picked up  <- TO_DROPOFF  ("Pickup complete, en route…")
//   Delivered  <- RETURNING   ("Recipient confirmed receipt…")
// COMPLETED (vehicle back at the station bay) is internal and not shown.
const fmtTime = (t) => (t ? dayjs(t).format('MMM D, HH:mm') : '');

export function StatusTimeline({ status, events = [], currentStage }) {
    if (!events.length) {
        const idx = FLOW.indexOf(status);
        const current = Math.max(0, idx);
        const isCancelled = status === 'CANCELLED';
        return (<Steps size="small" current={isCancelled ? 0 : current} status={isCancelled ? 'error' : idx === FLOW.length - 1 ? 'finish' : 'process'} items={FLOW.map((s) => ({ title: s.replace(/_/g, ' ') }))}/>);
    }

    const byStage = Object.fromEntries(events.map((e) => [e.stage, e]));
    const confirmed = byStage.TO_PICKUP;
    const pickedUp = byStage.TO_DROPOFF;
    const delivered = byStage.RETURNING;
    // CANCELLED is recorded by OrderService.cancelOrder with the fee/refund
    // breakdown in its description — surface it as the final timeline step.
    const cancelledEvt = byStage.CANCELLED;

    const items = [
        {
            title: 'Confirmed 已确认',
            description: (
                <span>
                    {fmtTime(confirmed?.eventTime)}
                    {confirmed?.statusDescription && (
                        <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>{confirmed.statusDescription}</Typography.Text>
                    )}
                </span>
            ),
        },
        {
            title: 'Picked up 已取件',
            description: pickedUp
                ? <span>{fmtTime(pickedUp.eventTime)}<Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>{pickedUp.statusDescription}</Typography.Text></span>
                : 'Waiting for the courier to arrive at the pickup point…',
        },
        {
            title: 'Delivered 已送达',
            description: delivered
                ? <span>{fmtTime(delivered.eventTime)}<Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>{delivered.statusDescription}</Typography.Text></span>
                : 'The courier will hand over the package and head back to its station.',
        },
    ];

    // Current step: PENDING → waiting for pickup (0); en route to drop-off →
    // in transit (1); DELIVERED (incl. the RETURNING/COMPLETED vehicle leg) → done.
    let current = 0;
    if (status === 'DELIVERED' || currentStage === 'RETURNING' || currentStage === 'COMPLETED') current = 3;
    else if (currentStage === 'TO_DROPOFF' || status === 'IN_TRANSIT') current = 2;
    else if (pickedUp) current = 2;

    // A cancelled order's last word is the cancellation itself: point the
    // (error-styled) current step at the Cancelled entry, not at Confirmed.
    if (cancelledEvt) {
        items.push({
            title: 'Cancelled 已取消',
            description: (
                <span>
                    {fmtTime(cancelledEvt.eventTime)}
                    {cancelledEvt.statusDescription && (
                        <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>{cancelledEvt.statusDescription}</Typography.Text>
                    )}
                </span>
            ),
        });
        current = items.length - 1;
    }

    return (
        <Steps
            direction="vertical"
            size="small"
            current={current}
            status={status === 'CANCELLED' ? 'error' : current === 3 ? 'finish' : 'process'}
            items={items}
        />
    );
}
