import { Button, Layout, Space, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { BRAND_NAME } from '../lib/brand';
import { useAuth } from '../store/auth';
// Top bar (matches the wireframe head). TWO variants driven by the auth token:
//   guest  — public pages (/track, /tracking/:id): brand + [Log in]
//   authed — brand + Dashboard + Orders + [+ Create a new delivery] + user + Log out
// Rendered on every route (App.jsx); guests must still see a header with a login entry.
export function AppHeader() {
    const { token, username, logout } = useAuth();
    const nav = useNavigate();
    const barStyle = {
        display: 'flex',
        alignItems: 'center',
        gap: 24,
        background: '#fff',
        borderBottom: '1px solid #f0f0f0',
        paddingInline: 24,
    };
    if (!token) {
        return (<Layout.Header style={barStyle}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        <Link to="/track" style={{ color: 'inherit' }}>{BRAND_NAME}</Link>
      </Typography.Title>
      <div style={{ flex: 1 }}/>
      <Button type="primary" onClick={() => nav('/login')}>
        Log in
      </Button>
    </Layout.Header>);
    }
    return (<Layout.Header style={barStyle}>
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
