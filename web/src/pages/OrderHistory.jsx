import { Button, Table, Typography } from 'antd';
import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { StatusBadge } from '../components/StatusBadge';
import { useOrders } from '../store/orders';
// Owner: Zihang Cao (order list). Wireframe: wireframes/09_OrderHistory.svg
// Columns follow the contract list item: orderId/status/createdAt/packageDescription/estimatedCost.
export default function OrderHistory() {
    const { list, loading, refresh } = useOrders();
    useEffect(() => {
        refresh();
    }, [refresh]);
    const columns = [
        { title: 'Order', dataIndex: 'orderId', key: 'orderId' },
        { title: 'Item', dataIndex: 'packageDescription', key: 'packageDescription' },
        { title: 'Status', dataIndex: 'status', key: 'status', render: (s) => <StatusBadge status={s} /> },
        { title: 'Cost', dataIndex: 'estimatedCost', key: 'estimatedCost', render: (p) => `$${Number(p).toFixed(2)}` },
        {
            title: 'Created',
            dataIndex: 'createdAt',
            key: 'createdAt',
            render: (e) => (e ? new Date(e).toLocaleString() : '—'),
        },
        {
            title: '',
            key: 'action',
            render: (_, r) => (
                <Link to={`/tracking/${r.orderId}`}>
                    <Button size="small" type="link">
                        Track
                    </Button>
                </Link>
            ),
        },
    ];
    return (
        <div>
            <Typography.Title level={3}>Order history</Typography.Title>
            <Table rowKey="orderId" loading={loading} columns={columns} dataSource={list} pagination={{ pageSize: 10 }} />
        </div>
    );
}
