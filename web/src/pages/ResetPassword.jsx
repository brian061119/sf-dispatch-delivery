import { useState } from "react";
import { Alert, Button, Form, Input, Result, Typography } from "antd";
import { LockOutlined } from "@ant-design/icons";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { resetPassword } from "../api/auth";
import { AuthShell } from "../components/AuthShell";
import { apiErrorMessage } from "../lib/http";

const { Title, Text } = Typography;

// Step 2 of the reset flow, opened from the link: /reset-password?token=...
// Same password rules as sign-up (at least 6 characters, typed twice).
export default function ResetPassword() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const [done, setDone] = useState(false);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit({ password }) {
    setBusy(true);
    setError(null);
    try {
      await resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(apiErrorMessage(err, "Could not reset your password. Please try again."));
    } finally {
      setBusy(false);
    }
  }

  if (!token) {
    return (
      <AuthShell>
        <Result
          status="warning"
          title="This reset link is incomplete"
          subTitle="Open the full link from your reset message, or request a new one."
          extra={<Link to="/forgot-password"><Button type="primary">Request a new link</Button></Link>}
        />
      </AuthShell>
    );
  }

  if (done) {
    return (
      <AuthShell>
        <Result
          status="success"
          title="Password changed"
          subTitle="You can now log in with your new password."
          extra={<Button type="primary" size="large" onClick={() => navigate("/login", { replace: true })}>Log in</Button>}
        />
      </AuthShell>
    );
  }

  return (
    <AuthShell>
      <div style={{ textAlign: "center", marginBottom: 28 }}>
        <Title level={3} style={{ marginBottom: 8 }}>Choose a new password</Title>
        <Text type="secondary">The link works once and expires 30 minutes after it was sent.</Text>
      </div>

      {error && (
        <Alert
          type="error"
          showIcon
          message={error}
          action={/expired|invalid/i.test(error) ? <Link to="/forgot-password">New link</Link> : null}
          style={{ marginBottom: 20 }}
        />
      )}

      <Form layout="vertical" onFinish={handleSubmit} requiredMark={false}>
        <Form.Item
          label="New password"
          name="password"
          rules={[
            { required: true, message: "Please enter a new password" },
            { min: 6, message: "Password must be at least 6 characters" },
          ]}
        >
          <Input.Password prefix={<LockOutlined />} placeholder="At least 6 characters" size="large" autoFocus />
        </Form.Item>
        <Form.Item
          label="Confirm new password"
          name="confirmPassword"
          dependencies={["password"]}
          rules={[
            { required: true, message: "Please confirm your new password" },
            ({ getFieldValue }) => ({
              validator(_, value) {
                return !value || getFieldValue("password") === value
                  ? Promise.resolve()
                  : Promise.reject(new Error("Passwords do not match"));
              },
            }),
          ]}
        >
          <Input.Password prefix={<LockOutlined />} placeholder="Type it again" size="large" />
        </Form.Item>
        <Button type="primary" htmlType="submit" size="large" block loading={busy} style={{ marginTop: 6 }}>
          Reset password
        </Button>
      </Form>

      <div style={{ textAlign: "center", marginTop: 32 }}>
        <Text type="secondary"><Link to="/login">Back to log in</Link></Text>
      </div>
    </AuthShell>
  );
}
