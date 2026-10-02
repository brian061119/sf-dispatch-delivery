import { Card, Typography } from "antd";
import {
  EnvironmentOutlined,
  FieldTimeOutlined,
  RobotOutlined,
} from "@ant-design/icons";
import { BRAND_HERO_BG, BRAND_NAME, CARD_SHADOW, FULL_BLEED, HERO_BG_SIZE } from "../lib/brand";

const { Title, Text } = Typography;

// Shared split-screen shell for /login and /register (WeDelivery-style auth):
//   left  — brand gradient panel with the product pitch (hidden < 900px via
//           index.css .auth-brand, so phones get the form full-width)
//   right — light panel with the actual form, vertically centered
// minHeight assumes the 64px AppHeader above (antd Layout.Header default).
export function AuthShell({ children }) {
  const features = [
    {
      icon: <RobotOutlined style={{ fontSize: 20 }} />,
      title: "Autonomous fleet",
      desc: "Robots and drones dispatched from 3 SF stations.",
    },
    {
      icon: <EnvironmentOutlined style={{ fontSize: 20 }} />,
      title: "Live map tracking",
      desc: "Watch your delivery move in real time — no login needed to track.",
    },
    {
      icon: <FieldTimeOutlined style={{ fontSize: 20 }} />,
      title: "Smart pricing",
      desc: "Off-peak discounts, VIP rates and instant quotes.",
    },
  ];

  return (
    <div
      style={{
        ...FULL_BLEED,
        minHeight: "calc(100vh - 64px)",
        display: "flex",
        background: "#f4f6f8",
      }}
    >
      {/* Left brand panel — purely decorative, so aria-hidden */}
      <div
        className="auth-brand"
        aria-hidden
        style={{
          flex: "0 0 46%",
          maxWidth: 560,
          background: BRAND_HERO_BG,
          backgroundSize: HERO_BG_SIZE,
          color: "#fff",
          padding: "56px 44px",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          gap: 28,
        }}
      >
        <div>
          <Title level={2} style={{ color: "#fff", marginBottom: 8 }}>
            {BRAND_NAME}
          </Title>
          <Text style={{ color: "rgba(255,255,255,.88)", fontSize: 15 }}>
            Robot & drone delivery across San Francisco —
            <br />
            quote, order and track in under a minute.
          </Text>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
          {features.map(({ icon, title, desc }) => (
            <div key={title} style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
              <div
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: 10,
                  background: "rgba(255,255,255,.16)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  flexShrink: 0,
                }}
              >
                {icon}
              </div>
              <div>
                <div style={{ fontWeight: 500 }}>{title}</div>
                <div style={{ color: "rgba(255,255,255,.78)", fontSize: 13 }}>{desc}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Right form panel */}
      <div
        style={{
          flex: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: 24,
        }}
      >
        <Card
          style={{
            width: "100%",
            maxWidth: 400,
            borderRadius: 14,
            boxShadow: CARD_SHADOW,
          }}
          styles={{ body: { padding: "32px 32px 36px" } }}
        >
          {children}
        </Card>
      </div>
    </div>
  );
}
