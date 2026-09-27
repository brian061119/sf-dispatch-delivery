// Owner: Zihang Cao (orders — list). Wireframe: wireframes/09_OrderHistory.svg
import {
    Button,
    Card,
    Empty,
    Pagination,
    Segmented,
    Space,
    Spin,
    Typography,
    message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { OrderCard } from '../components/OrderCard';
import { useOrders } from '../store/orders';

const { Title, Text } = Typography;
const PAGE_SIZE = 5;

const FILTERS = [
    { label: 'All', value: 'ALL' },
    { label: 'Active', value: 'ACTIVE' },
    { label: 'Delivered', value: 'DELIVERED' },
    { label: 'Cancelled', value: 'CANCELLED' },
];

export default function OrderHistory() {
    const navigate = useNavigate();
    const orders = useOrders((state) => state.list);
    const loading = useOrders((state) => state.loading);
    const refreshOrders = useOrders((state) => state.refresh);
    const [filter, setFilter] = useState('ALL');
    const [page, setPage] = useState(1);

    useEffect(() => {
        refreshOrders().catch(() => {
            message.error('Unable to load your order history.');
        });
    }, [refreshOrders]);

    const filteredOrders = useMemo(() => {
        if (filter === 'ACTIVE') {
            return orders.filter((order) => ['PENDING', 'IN_TRANSIT'].includes(order.status));
        }
        if (filter === 'ALL') {
            return orders;
        }
        return orders.filter((order) => order.status === filter);
    }, [filter, orders]);

    const visibleOrders = useMemo(() => {
        const start = (page - 1) * PAGE_SIZE;
        return filteredOrders.slice(start, start + PAGE_SIZE);
    }, [filteredOrders, page]);

    const changeFilter = (nextFilter) => {
        setFilter(nextFilter);
        setPage(1);
    };

    return (
        <Space direction="vertical" size={20} style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
                <div>
                    <Title level={2} style={{ marginBottom: 4 }}>Your orders</Title>
                    <Text type="secondary">Review past deliveries and open any order for full details.</Text>
                </div>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/order/new')}>
                    Create a new delivery
                </Button>
            </div>

            <Card>
                <Segmented
                    options={FILTERS}
                    value={filter}
                    onChange={changeFilter}
                    style={{ marginBottom: 20 }}
                />

                {loading ? (
                    <div style={{ padding: 40, textAlign: 'center' }}><Spin /></div>
                ) : visibleOrders.length === 0 ? (
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description={`No ${filter === 'ALL' ? '' : filter.toLowerCase()} orders found`}
                    >
                        <Button type="primary" onClick={() => navigate('/order/new')}>Create a delivery</Button>
                    </Empty>
                ) : (
                    <Space direction="vertical" size={12} style={{ width: '100%' }}>
                        {visibleOrders.map((order) => (
                            <div key={order.orderId}>
                                <OrderCard order={order} />
                                <Button
                                    type="link"
                                    style={{ paddingLeft: 0 }}
                                    onClick={() => navigate(`/order/${order.orderId}`)}
                                >
                                    View order details
                                </Button>
                            </div>
                        ))}
                    </Space>
                )}

                {filteredOrders.length > PAGE_SIZE && (
                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 20 }}>
                        <Pagination
                            current={page}
                            pageSize={PAGE_SIZE}
                            total={filteredOrders.length}
                            showSizeChanger={false}
                            onChange={setPage}
                        />
                    </div>
                )}
            </Card>
        </Space>
    );
}
