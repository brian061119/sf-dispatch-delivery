import { useState } from "react";
import { Alert, Button, Form, Input, Typography } from "antd";
import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { Link, useNavigate } from "react-router-dom";
import { AuthShell } from "../components/AuthShell";
import { apiErrorMessage } from "../lib/http";
import { useAuth } from "../store/auth";

const { Title, Text } = Typography;

export default function Login() {
  const navigate = useNavigate();
  const login = useAuth((state) => state.login);

  const [error, setError] = useState(null);

  const handleSubmit = async (values) => {
    setError(null);

    try {
      await login(values.username, values.password);
      navigate(useAuth.getState().role === "ADMIN" ? "/admin" : "/dashboard", { replace: true });
    } catch (err) {
      console.error("Login failed:", err);

      setError(
        apiErrorMessage(err, "Unable to log in. Please check your username and password.")
      );
    }
  };

  return (
    <AuthShell>
      <div style={{ textAlign: "center", marginBottom: 28 }}>
        <Title level={3} style={{ marginBottom: 8 }}>
          Log in
        </Title>
        <Text type="secondary">Robot / Drone delivery — SF Bay Area</Text>
      </div>

      {error && (
        <Alert
          type="error"
          message={error}
          showIcon
          style={{ marginBottom: 20 }}
        />
      )}

      <Form layout="vertical" onFinish={handleSubmit} requiredMark={false}>
        <Form.Item
          label="Username"
          name="username"
          rules={[
            { required: true, message: "Please enter your username" },
          ]}
        >
          <Input
            prefix={<UserOutlined />}
            placeholder="Your username"
            size="large"
          />
        </Form.Item>

        <Form.Item
          label="Password"
          name="password"
          rules={[
            { required: true, message: "Please enter your password" },
          ]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            placeholder="Enter your password"
            size="large"
          />
        </Form.Item>

        <Form.Item shouldUpdate>
          {({ isFieldsTouched, getFieldsError }) => {
            const hasErrors = getFieldsError().some(
              ({ errors }) => errors.length > 0
            );

            return (
              <Button
                type="primary"
                htmlType="submit"
                size="large"
                block
                disabled={!isFieldsTouched(true) || hasErrors}
                style={{ marginTop: 6 }}
              >
                Log in
              </Button>
            );
          }}
        </Form.Item>
      </Form>

      <div style={{ textAlign: "center", marginTop: 32 }}>
        <Text type="secondary">
          Don't have an account? <Link to="/register">Sign Up</Link>
        </Text>
      </div>
    </AuthShell>
  );
}
