// Owner: Zihang Cao (orders — list) + AI card shell. Wireframe: wireframes/03_Dashboard.svg
import {
    Alert,
    Button,
    Card,
    Col,
    Empty,
    Row,
    Space,
    Spin,
    Typography,
    message,
} from 'antd';
import { PlusOutlined, RocketOutlined } from '@ant-design/icons';
import { useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { OrderCard } from '../components/OrderCard';
import { useAuth } from '../store/auth';
import { useOrders } from '../store/orders';

const { Title, Text } = Typography;

export default function Dashboard() {
    const navigate = useNavigate();
    const username = useAuth((state) => state.username);
    const orders = useOrders((state) => state.list);
    const loading = useOrders((state) => state.loading);
    const refreshOrders = useOrders((state) => state.refresh);

    useEffect(() => {
        refreshOrders().catch(() => {
            message.error('Unable to load your orders. Please try again.');
        });
    }, [refreshOrders]);

    const activeOrders = useMemo(
        () => orders.filter((order) => ['PENDING', 'IN_TRANSIT'].includes(order.status)),
        [orders],
    );

    return (
        <Space direction="vertical" size={24} style={{ width: '100%' }}>
            <div>
                <Title level={2} style={{ marginBottom: 4 }}>
                    Welcome back{username ? `, ${username}` : ''}.
                </Title>
                <Text type="secondary">
                    Create a delivery, compare autonomous delivery options, and follow every order in one place.
                </Text>
            </div>

            <Row gutter={[24, 24]}>
                <Col xs={24} lg={15}>
                    <Card
                        title={<Space><RocketOutlined />Start a delivery</Space>}
                        extra={
                            <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/order/new')}>
                                New delivery
                            </Button>
                        }
                    >
                        <Text type="secondary">
                            Enter pickup and dropoff locations, package details, and delivery priority. The backend then ranks available robot and drone options by route, capacity, estimated time, and price.
                        </Text>
                        <Button
                            type="primary"
                            icon={<PlusOutlined />}
                            style={{ marginTop: 16 }}
                            onClick={() => navigate('/order/new')}
                        >
                            Create a delivery
                        </Button>
                    </Card>
                </Col>

                <Col xs={24} lg={9}>
                    <Card title="Delivery overview">
                        <Row gutter={16}>
                            <Col span={12}>
                                <Text type="secondary">Active</Text>
                                <Title level={3} style={{ margin: 0 }}>{activeOrders.length}</Title>
                            </Col>
                            <Col span={12}>
                                <Text type="secondary">All orders</Text>
                                <Title level={3} style={{ margin: 0 }}>{orders.length}</Title>
                            </Col>
                        </Row>
                        <Button type="link" style={{ paddingLeft: 0, marginTop: 12 }} onClick={() => navigate('/orders')}>
                            View order history
                        </Button>
                    </Card>
                </Col>
            </Row>

            <Card
                title="Active deliveries"
                extra={<Button type="link" onClick={() => navigate('/orders')}>View all</Button>}
            >
                {loading ? (
                    <div style={{ padding: 36, textAlign: 'center' }}><Spin /></div>
                ) : activeOrders.length === 0 ? (
                    <Empty
                        description="No active deliveries"
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                    >
                        <Button type="primary" onClick={() => navigate('/order/new')}>Create a delivery</Button>
                    </Empty>
                ) : (
                    <Row gutter={[16, 16]}>
                        {activeOrders.slice(0, 3).map((order) => (
                            <Col xs={24} md={12} xl={8} key={order.orderId}>
                                <OrderCard order={order} mini />
                            </Col>
                        ))}
                    </Row>
                )}
            </Card>

            <Alert
                showIcon
                type="info"
                message="Delivery recommendation"
                description="Robot and drone options are ranked by the backend using route, capacity, estimated time, and cost. The frontend displays the result; it does not calculate or change the score."
            />
        </Space>
    );
}
