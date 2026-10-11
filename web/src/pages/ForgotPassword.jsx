import { useState } from "react";
import { Alert, Button, Form, Input, Typography } from "antd";
import { MailOutlined } from "@ant-design/icons";
import { Link } from "react-router-dom";
import { forgotPassword } from "../api/auth";
import { AuthShell } from "../components/AuthShell";
import { apiErrorMessage } from "../lib/http";

const { Title, Text } = Typography;

// Step 1 of the reset flow. The backend answers the same way whether or not the
// account exists. There's no email service: the link is in the backend log, or
// shown here when the backend runs with DEMO_SHOW_RESET_LINK=true.
export default function ForgotPassword() {
  const [sent, setSent] = useState(null); // { message, resetLink? }
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  async function handleSubmit({ identifier }) {
    setBusy(true);
    setError(null);
    try {
      setSent(await forgotPassword(identifier.trim()));
    } catch (err) {
      setError(apiErrorMessage(err, "Could not send a reset link. Please try again."));
    } finally {
      setBusy(false);
    }
  }

  // The link points at this app; open it as an in-app route.
  const resetPath = sent?.resetLink ? sent.resetLink.replace(/^https?:\/\/[^/]+/, "") : null;

  return (
    <AuthShell>
      <div style={{ textAlign: "center", marginBottom: 28 }}>
        <Title level={3} style={{ marginBottom: 8 }}>Forgot your password?</Title>
        <Text type="secondary">Enter your username or email and we'll send you a reset link.</Text>
      </div>

      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 20 }} />}

      {sent ? (
        <>
          <Alert type="success" showIcon message="Check for your reset link" description={sent.message} style={{ marginBottom: 16 }} />
          {resetPath && (
            <Alert
              type="info"
              showIcon
              message="Demo mode: no email service"
              description={
                <>
                  <div style={{ marginBottom: 8 }}>Here is the link that would have been emailed:</div>
                  <Text copyable={{ text: sent.resetLink }} style={{ wordBreak: "break-all" }}>{sent.resetLink}</Text>
                  <div style={{ marginTop: 12 }}>
                    <Link to={resetPath}><Button type="primary">Open reset link</Button></Link>
                  </div>
                </>
              }
              style={{ marginBottom: 16 }}
            />
          )}
          <Button block onClick={() => setSent(null)}>Send another link</Button>
        </>
      ) : (
        <Form layout="vertical" onFinish={handleSubmit} requiredMark={false}>
          <Form.Item
            label="Username or email"
            name="identifier"
            rules={[{ required: true, whitespace: true, message: "Please enter your username or email" }]}
          >
            <Input prefix={<MailOutlined />} placeholder="e.g. normal_user or you@example.com" size="large" autoFocus />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={busy} style={{ marginTop: 6 }}>
            Send reset link
          </Button>
        </Form>
      )}

      <div style={{ textAlign: "center", marginTop: 32 }}>
        <Text type="secondary">Remembered it? <Link to="/login">Back to log in</Link></Text>
      </div>
    </AuthShell>
  );
}
