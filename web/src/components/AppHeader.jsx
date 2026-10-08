import { Button, Layout, Space, Tag, Typography } from "antd";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { BRAND_NAME } from "../lib/brand";
import { useAuth } from "../store/auth";
// Top bar has guest, customer and admin variants.
// Admin sees only the console, account and logout.
// Other variants:
//   guest  — public pages (/track incl. ?code=): brand + Log in button
//   authed — brand + Dashboard + Orders + [+ Create a new delivery]
//            (+ Admin for ADMIN role) + user (+ role badge) + Log out
// On the auth pages themselves the button cross-links: /login shows
// "Sign up", /register shows "Log in".
export function AppHeader() {
  const { token, username, role, logout } = useAuth();
  const nav = useNavigate();
  const { pathname } = useLocation();
  const barStyle = {
    display: "flex",
    alignItems: "center",
    gap: 24,
    background: "#fff",
    borderBottom: "1px solid #f0f0f0",
    paddingInline: 24,
    // Sticky so the nav follows on the tall public pages (track/auth).
    position: "sticky",
    top: 0,
    zIndex: 100,
  };
  const brandLinkStyle = {
    backgroundImage: "linear-gradient(135deg, #1677ff 0%, #722ed1 100%)",
    WebkitBackgroundClip: "text",
    backgroundClip: "text",
    WebkitTextFillColor: "transparent",
  };
  if (!token) {
    const onAuthPage = pathname === "/login" || pathname === "/register";
    return (
      <Layout.Header style={barStyle}>
        <Typography.Title level={4} style={{ margin: 0 }}>
          <Link to="/track" style={brandLinkStyle}>
            {BRAND_NAME}
          </Link>
        </Typography.Title>

        <div style={{ flex: 1 }} />

        {!onAuthPage && (
          <Button type="primary" onClick={() => nav("/login")}>
            Log in
          </Button>
        )}
      </Layout.Header>
    );
  }
  return (
    <Layout.Header style={barStyle}>
      <Typography.Title level={4} style={{ margin: 0 }}>
        <Link to={role === "ADMIN" ? "/admin" : "/dashboard"} style={brandLinkStyle}>
          {BRAND_NAME}
        </Link>
      </Typography.Title>
      <Space size="large">
        {role === "ADMIN" ? (
          <Link to="/admin">Admin console</Link>
        ) : (
          <>
            <Link to="/dashboard">Dashboard</Link>
            <Link to="/orders">Orders</Link>
            <Link to="/vip" style={{ color: role === "VIP" ? "#d48806" : undefined, fontWeight: role === "VIP" ? "bold" : "normal" }}>
              {role === "VIP" ? "👑 VIP Center" : "💎 Upgrade to VIP"}
            </Link>
          </>
        )}
      </Space>
      <div style={{ flex: 1 }} />
      <Space>
        {role !== "ADMIN" && <Button type="primary" onClick={() => nav("/order/new")}>
          + Create a new delivery
        </Button>}
        <span>{username ?? "dev-user"}</span>
        {role === "VIP" && (
          <Link to="/vip">
            <Tag color="gold" style={{ cursor: "pointer", fontWeight: "bold" }}>👑 VIP</Tag>
          </Link>
        )}
        {role === "ADMIN" && <Tag color="geekblue">ADMIN</Tag>}
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
