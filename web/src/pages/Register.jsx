import { LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../store/auth';
// Owner: Ziyuan Xu (认证). Wireframe: UI线框图/02_Register.svg
export default function Register() {
    const signup = useAuth((s) => s.signup);
    const nav = useNavigate();
    const { message } = App.useApp();
    async function onFinish(v) {
        try {
            await signup(v.username, v.password, v.email);
            nav('/dashboard');
        }
        catch {
            message.error('Sign up failed — username may be taken');
        }
    }
    return (<Card title="Create account" style={{ maxWidth: 400, margin: '48px auto' }}>
      <Form layout="vertical" onFinish={onFinish}>
        <Form.Item name="username" rules={[{ required: true, message: 'Username required' }]}>
          <Input prefix={<UserOutlined />} placeholder="Username"/>
        </Form.Item>
        <Form.Item name="email" rules={[{ type: 'email', message: 'Invalid email' }]}>
          <Input prefix={<MailOutlined />} placeholder="Email (optional)"/>
        </Form.Item>
        <Form.Item name="password" rules={[{ required: true, min: 6, message: 'At least 6 characters' }]}>
          <Input.Password prefix={<LockOutlined />} placeholder="Password"/>
        </Form.Item>
        <Form.Item name="confirm" dependencies={['password']} rules={[
            { required: true, message: 'Please confirm password' },
            ({ getFieldValue }) => ({
                validator: (_, value) => !value || getFieldValue('password') === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('Passwords do not match')),
            }),
        ]}>
          <Input.Password prefix={<LockOutlined />} placeholder="Confirm password"/>
        </Form.Item>
        <Button type="primary" htmlType="submit" block>
          Register
        </Button>
      </Form>
      <div style={{ marginTop: 12, textAlign: 'center' }}>
        Already have an account? <Link to="/login">Log in →</Link>
      </div>
    </Card>);
}
