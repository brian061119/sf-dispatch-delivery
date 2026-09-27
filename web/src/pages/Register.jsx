import { useState } from "react";
import { Alert, Button, Card, Form, Input, Typography } from "antd";
import { LockOutlined, MailOutlined, UserOutlined } from "@ant-design/icons";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../store/auth";
import { BRAND_NAME } from "../lib/brand";

const { Title, Text } = Typography;

export default function Register() {
  const navigate = useNavigate();
  const signup = useAuth((state) => state.signup);

  const [error, setError] = useState(null);

  const handleSubmit = async (values) => {
    setError(null);

    try {
      await signup(values.username, values.password, values.email);

      navigate("/dashboard");
    } catch (err) {
      console.error("Registration failed:", err);

      setError(
        err?.response?.data?.message ||
          "Unable to create your account. Please try again."
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
              Sign Up
            </Title>

            <Text type="secondary">Create your account to start delivery</Text>
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
                  message: "Please enter a username",
                },
                {
                  min: 3,
                  message: "Username must be at least 3 characters",
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
              label="Email"
              name="email"
              rules={[
                {
                  required: true,
                  message: "Please enter your email",
                },
                {
                  type: "email",
                  message: "Please enter a valid email address",
                },
              ]}
            >
              <Input
                prefix={<MailOutlined />}
                placeholder="you@example.com"
                size="large"
              />
            </Form.Item>

            <Form.Item
              label="Password"
              name="password"
              rules={[
                {
                  required: true,
                  message: "Please enter a password",
                },
                {
                  min: 6,
                  message: "Password must be at least 6 characters",
                },
              ]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="Create a password"
                size="large"
              />
            </Form.Item>

            <Form.Item
              label="Confirm password"
              name="confirmPassword"
              dependencies={["password"]}
              rules={[
                {
                  required: true,
                  message: "Please confirm your password",
                },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue("password") === value) {
                      return Promise.resolve();
                    }

                    return Promise.reject(new Error("Passwords do not match"));
                  },
                }),
              ]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="Confirm your password"
                size="large"
              />
            </Form.Item>

            <Button type="primary" htmlType="submit" size="large" block>
              Sign Up
            </Button>
          </Form>

          <div
            style={{
              textAlign: "center",
              marginTop: 28,
            }}
          >
            <Text type="secondary">
              Already have an account? <Link to="/login">Log in</Link>
            </Text>
          </div>
        </Card>
      </div>
    </div>
  );
}
