import { Button, Layout, Space, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { BRAND_NAME } from '../lib/brand';
import { useAuth } from '../store/auth';
// Top bar shown once logged in (matches the head in the wireframes:
// logo + Dashboard + Orders + [+ Create a new delivery] + user).
export function AppHeader() {
    const { username, logout } = useAuth();
    const nav = useNavigate();
    return (<Layout.Header style={{
            display: 'flex',
            alignItems: 'center',
            gap: 24,
            background: '#fff',
            borderBottom: '1px solid #f0f0f0',
            paddingInline: 24,
        }}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {BRAND_NAME}
      </Typography.Title>
      <Space size="large">
        <Link to="/dashboard">Dashboard</Link>
        <Link to="/orders">Orders</Link>
      </Space>
      <div style={{ flex: 1 }}/>
      <Space>
        <Button type="primary" onClick={() => nav('/order/new')}>
          + Create a new delivery
        </Button>
        <span>{username}</span>
        <Button onClick={() => {
            logout();
            nav('/login');
        }}>
          Log out
        </Button>
      </Space>
    </Layout.Header>);
}
