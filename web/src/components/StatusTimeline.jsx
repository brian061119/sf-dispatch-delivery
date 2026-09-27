import { Steps } from 'antd';
// Progress bar for the Tracking page. Contract has 4 states; CANCELLED is an
// error branch off the happy path PENDING -> IN_TRANSIT -> DELIVERED.
const FLOW = ['PENDING', 'IN_TRANSIT', 'DELIVERED'];
export function StatusTimeline({ status }) {
    const idx = FLOW.indexOf(status);
    const current = Math.max(0, idx);
    const isCancelled = status === 'CANCELLED';
    return (<Steps size="small" current={isCancelled ? 0 : current} status={isCancelled ? 'error' : idx === FLOW.length - 1 ? 'finish' : 'process'} items={FLOW.map((s) => ({ title: s.replace(/_/g, ' ') }))}/>);
}
