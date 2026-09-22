import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/auth';
// Owner: Ziyuan Xu (auth). Wireframe: wireframes/01_Login.svg
export default function Login() {
    const login = useAuth((s) => s.login);
    const nav = useNavigate();
    const { message } = App.useApp();
    async function onFinish(v) {
        try {
            await login(v.username, v.password);
            nav('/dashboard');
        }
        catch {
            message.error('Login failed — check username/password');
        }
    }
    return (<Card title="Log in" style={{ maxWidth: 400, margin: '48px auto' }}>
      <Form layout="vertical" onFinish={onFinish}>
        <Form.Item name="username" rules={[{ required: true, message: 'Username required' }]}>
          <Input prefix={<UserOutlined />} placeholder="Username"/>
        </Form.Item>
        <Form.Item name="password" rules={[{ required: true, message: 'Password required' }]}>
          <Input.Password prefix={<LockOutlined />} placeholder="Password"/>
        </Form.Item>
        <Button type="primary" htmlType="submit" block>
          Log in
        </Button>
      </Form>
      <div style={{ marginTop: 12, textAlign: 'center' }}>
        New here? <Link to="/register">Register →</Link>
      </div>
    </Card>);
}
