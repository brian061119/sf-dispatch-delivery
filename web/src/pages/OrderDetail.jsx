// Owner: Y (order detail + confirm receipt + review).
import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Checkbox, Descriptions, Empty, Form, Input, Popconfirm, Rate, Skeleton, Space, Typography } from 'antd';
import { Link, useParams } from 'react-router-dom';
import { confirmReceipt, getOrder, submitReview } from '../api/order';
import { StatusBadge } from '../components/StatusBadge';

const STATUSES = ['PENDING', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED'];
const textOrMissing = (value) => typeof value === 'string' && value.trim() ? value : 'Not provided';
const isRecord = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);

export default function OrderDetail() {
    const { orderId } = useParams();
    // Remount on navigation so form state and in-flight mutations never leak to another order.
    return <OrderDetailContent key={orderId} orderId={orderId} />;
}

function OrderDetailContent({ orderId }) {
    const [order, setOrder] = useState(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState('');
    const [attempt, setAttempt] = useState(0);
    const [confirming, setConfirming] = useState(false);
    const [reviewing, setReviewing] = useState(false);
    const [receiptFeedback, setReceiptFeedback] = useState(null);
    const [reviewFeedback, setReviewFeedback] = useState(null);
    const [reviewId, setReviewId] = useState(null);
    const alive = useRef(false);
    const receiptLock = useRef(false);
    const reviewLock = useRef(false);

    useEffect(() => {
        alive.current = true;
        return () => { alive.current = false; };
    }, []);

    useEffect(() => {
        let active = true;
        setLoading(true);
        setLoadError('');
        setOrder(null);
        async function load() {
            try {
                if (!orderId) throw new Error('Missing order ID');
                const result = await getOrder(orderId);
                if (!active) return;
                if (isRecord(result) && result.orderId != null && result.orderId !== orderId) {
                    throw new Error('Unexpected order ID');
                }
                setOrder(isRecord(result) && Object.keys(result).length ? result : null);
            } catch (error) {
                if (active) setLoadError(error?.response?.status === 404
                    ? 'Order not found.' : 'Unable to load this order. Please try again.');
            } finally {
                if (active) setLoading(false);
            }
        }
        load();
        return () => { active = false; };
    }, [orderId, attempt]);

    // Detail response is still TBD. These are optional summary fields already named
    // in the contract, not a guaranteed detail schema. Do not infer nested fields.
    const status = STATUSES.includes(order?.status) ? order.status : null;
    const createdAt = typeof order?.createdAt === 'string' ? new Date(order.createdAt) : null;
    // Provisional UI policy until HANDOFF's receipt-state question is resolved:
    // allow receipt only in transit and reviews only after delivery.
    const canConfirm = status === 'IN_TRANSIT' && !confirming;
    const canReview = status === 'DELIVERED' && !reviewId && !reviewing;
    const receiptHint = {
        PENDING: 'Receipt confirmation is available when the order is in transit.',
        IN_TRANSIT: 'Confirm only after you have received your package.',
        DELIVERED: 'This order is already marked as delivered.',
        CANCELLED: 'Cancelled orders cannot be confirmed.',
    }[status] || 'Receipt confirmation is unavailable while the order status is unknown.';

    async function handleReceipt() {
        if (!canConfirm || receiptLock.current) return;
        receiptLock.current = true;
        setConfirming(true);
        setReceiptFeedback(null);
        try {
            const result = await confirmReceipt(orderId);
            if (!alive.current) return;
            if (result?.orderId !== orderId || result?.status !== 'DELIVERED') {
                setReceiptFeedback({ type: 'warning', text: 'The server returned an unexpected confirmation. Reload this page to check the order status before trying again.' });
                return;
            }
            setOrder((current) => ({ ...current, status: result.status }));
            setReceiptFeedback({ type: 'success', text: 'Receipt confirmed. Thank you! You can now leave a review.' });
        } catch {
            if (alive.current) setReceiptFeedback({ type: 'error', text: 'Unable to confirm receipt. Please check the order status before retrying.' });
        } finally {
            receiptLock.current = false;
            if (alive.current) setConfirming(false);
        }
    }

    async function handleReview(values) {
        if (!canReview || reviewLock.current) return;
        if (!Number.isInteger(values.rating) || values.rating < 1 || values.rating > 5) return;
        reviewLock.current = true;
        setReviewing(true);
        setReviewFeedback(null);
        try {
            const result = await submitReview(orderId, {
                rating: values.rating,
                comment: values.comment?.trim() || null,
                damageReported: values.damageReported === true,
            });
            if (!alive.current) return;
            if (result?.orderId !== orderId || typeof result?.reviewId !== 'string' || !result.reviewId.trim()) {
                setReviewFeedback({ type: 'warning', text: 'The server returned an unexpected review confirmation. Your review may have been received; check before submitting again.' });
                return;
            }
            setReviewId(result.reviewId);
            setReviewFeedback({ type: 'success', text: `Review submitted. Reference: ${result.reviewId}` });
        } catch {
            if (alive.current) setReviewFeedback({ type: 'error', text: 'Unable to submit your review. Your entries have been kept. Please try again.' });
        } finally {
            reviewLock.current = false;
            if (alive.current) setReviewing(false);
        }
    }

    return (
        <Space orientation="vertical" size="large" style={{ width: '100%' }}>
            <Space wrap>
                <Link to="/orders"><Button>Back to orders</Button></Link>
                {orderId && <Link to={`/tracking/${encodeURIComponent(orderId)}`}><Button>Track order</Button></Link>}
            </Space>
            <Typography.Title level={2} style={{ margin: 0 }}>Order details</Typography.Title>
            <Typography.Text style={{ overflowWrap: 'anywhere' }}>Order #{orderId || 'Not provided'}</Typography.Text>
            {loading ? <Card><Skeleton active paragraph={{ rows: 4 }} /></Card> : loadError ? (
                <Alert type="error" showIcon title={loadError}
                    action={<Button onClick={() => setAttempt((value) => value + 1)}>Retry</Button>} />
            ) : !order ? (
                <Empty description="No order details are available.">
                    <Button onClick={() => setAttempt((value) => value + 1)}>Retry</Button>
                </Empty>
            ) : (
                <>
                    <Card title="Order summary">
                        <Descriptions column={{ xs: 1, sm: 2 }} items={[
                            { key: 'status', label: 'Status', children: status ? <StatusBadge status={status} /> : 'Not available' },
                            { key: 'created', label: 'Created', children: createdAt && !Number.isNaN(createdAt.getTime()) ? createdAt.toLocaleString() : 'Not provided' },
                            { key: 'description', label: 'Package', children: textOrMissing(order?.packageDescription) },
                            { key: 'cost', label: 'Estimated cost', children: typeof order?.estimatedCost === 'number' && Number.isFinite(order.estimatedCost) ? order.estimatedCost.toFixed(2) : 'Not provided' },
                        ]} />
                    </Card>
                    <Card title="Confirm receipt">
                        <Space orientation="vertical" style={{ width: '100%' }}>
                            <Typography.Paragraph style={{ margin: 0 }}>{receiptHint}</Typography.Paragraph>
                            <Popconfirm title="Have you received your package?" description="Confirm only once the package is in your possession."
                                disabled={!canConfirm} onConfirm={handleReceipt} okText="Confirm receipt" cancelText="Not yet">
                                <Button type="primary" disabled={!canConfirm} loading={confirming}>
                                    {status === 'DELIVERED' ? 'Delivered' : 'Confirm receipt'}
                                </Button>
                            </Popconfirm>
                            {receiptFeedback && <Alert role="status" showIcon type={receiptFeedback.type} title={receiptFeedback.text} />}
                        </Space>
                    </Card>
                    <Card title="Delivery review">
                        {status !== 'DELIVERED' && <Typography.Paragraph>Reviews are available after delivery.</Typography.Paragraph>}
                        <Form layout="vertical" initialValues={{ damageReported: false }} onFinish={handleReview}
                            disabled={!canReview} requiredMark="optional">
                            <Form.Item name="rating" label="Rating" rules={[{ required: true, message: 'Choose a rating from 1 to 5.' },
                                { type: 'integer', min: 1, max: 5, message: 'Choose a whole-number rating from 1 to 5.' }]}>
                                <Rate aria-label="Delivery rating" />
                            </Form.Item>
                            <Form.Item name="comment" label="Comment"><Input.TextArea rows={4} placeholder="Tell us about your delivery" /></Form.Item>
                            <Form.Item name="damageReported" valuePropName="checked"><Checkbox>My package was damaged</Checkbox></Form.Item>
                            <Button type="primary" htmlType="submit" disabled={!canReview} loading={reviewing}>
                                {reviewId ? 'Review submitted' : 'Submit review'}
                            </Button>
                        </Form>
                        {reviewFeedback && <Alert role="status" style={{ marginTop: 16 }} showIcon type={reviewFeedback.type} title={reviewFeedback.text} />}
                    </Card>
                </>
            )}
        </Space>
    );
}
