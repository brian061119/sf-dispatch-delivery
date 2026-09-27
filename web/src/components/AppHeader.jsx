import { Button, Layout, Space, Typography } from "antd";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { BRAND_NAME } from "../lib/brand";
import { useAuth } from "../store/auth";
// Top bar. TWO variants driven by the auth token:
//   guest  — public pages (/track, /tracking/:id): brand + auth entry button
//   authed — brand + Dashboard + Orders + [+ Create a new delivery] + user + Log out
// Rendered on every route (App.jsx). On the auth pages themselves the button
// cross-links (GitHub style): /login shows "Sign up", /register shows "Log in",
// so the header never offers the page you are already on.
export function AppHeader() {
  const { token, username, logout } = useAuth();
  const nav = useNavigate();
  const { pathname } = useLocation();
  const barStyle = {
    display: "flex",
    alignItems: "center",
    gap: 24,
    background: "#fff",
    borderBottom: "1px solid #f0f0f0",
    paddingInline: 24,
  };
  if (!token) {
    const onLoginPage = pathname === "/login";
    return (
      <Layout.Header style={barStyle}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          <Link to="/track" style={{ color: "inherit" }}>
            {BRAND_NAME}
          </Link>
        </Typography.Title>
        <div style={{ flex: 1 }} />
        <Button type="primary" onClick={() => nav(onLoginPage ? "/register" : "/login")}>
          {onLoginPage ? "Sign up" : "Log in"}
        </Button>
      </Layout.Header>
    );
  }
  return (
    <Layout.Header style={barStyle}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        <Link to="/dashboard" style={{ color: "inherit" }}>
          {BRAND_NAME}
        </Link>
      </Typography.Title>
      <Space size="large">
        <Link to="/dashboard">Dashboard</Link>
        <Link to="/orders">Orders</Link>
      </Space>
      <div style={{ flex: 1 }} />
      <Space>
        <Button type="primary" onClick={() => nav("/order/new")}>
          + Create a new delivery
        </Button>
        <span>{username ?? "dev-user"}</span>
        <Button
          onClick={() => {
            logout();
            nav("/login");
          }}
        >
          Log out
        </Button>
      </Space>
    </Layout.Header>
  );
}
