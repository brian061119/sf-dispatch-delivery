import { useState } from "react";
import { Alert, Button, Card, Form, Input, Typography } from "antd";
import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../store/auth";
import { BRAND_NAME } from "../lib/brand";

const { Title, Text } = Typography;

export default function Login() {
  const navigate = useNavigate();
  const login = useAuth((state) => state.login);

  const [error, setError] = useState(null);

  const handleSubmit = async (values) => {
    setError(null);

    try {
      await login(values.username, values.password);
      navigate("/dashboard");
    } catch (err) {
      console.error("Login failed:", err);

      setError(
        err?.response?.data?.message ||
          "Unable to log in. Please check your username and password."
      );
    }
  };

  return (
    <div
      style={{
        minHeight: "calc(100vh - 48px)",
        display: "flex",
        flexDirection: "column",
      }}
    >
      {/* Main light-gray area */}
      <div
        style={{
          flex: 1,
          background: "#f4f6f8",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: 24,
        }}
      >
        <Card
          style={{
            width: "100%",
            maxWidth: 380,
            borderRadius: 10,
          }}
          styles={{
            body: {
              padding: "30px 30px 34px",
            },
          }}
        >
          <div
            style={{
              textAlign: "center",
              marginBottom: 30,
            }}
          >
            <Title
              level={3}
              style={{
                marginBottom: 8,
              }}
            >
              Log in
            </Title>

            <Text type="secondary">Robot / Drone delivery — SF Bay Area</Text>
          </div>

          {error && (
            <Alert
              type="error"
              message={error}
              showIcon
              style={{
                marginBottom: 20,
              }}
            />
          )}

          <Form layout="vertical" onFinish={handleSubmit} requiredMark={false}>
            <Form.Item
              label="Username"
              name="username"
              rules={[
                {
                  required: true,
                  message: "Please enter your username",
                },
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
                {
                  required: true,
                  message: "Please enter your password",
                },
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
                    style={{
                      marginTop: 6,
                    }}
                  >
                    Log in
                  </Button>
                );
              }}
            </Form.Item>
          </Form>

          <div
            style={{
              textAlign: "center",
              marginTop: 34,
            }}
          >
            <Text type="secondary">
              Don't have an account? <Link to="/register">Sign Up</Link>
            </Text>
          </div>
        </Card>
      </div>
    </div>
  );
}
