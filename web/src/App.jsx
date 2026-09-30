import { Layout } from "antd";
import { Navigate, Route, Routes } from "react-router-dom";
import { AiAssistant } from "./components/AiAssistant";
import { AppHeader } from "./components/AppHeader";
import { getRole, isAuthed } from "./lib/auth";
import AdminDashboard from "./pages/AdminDashboard";
import Dashboard from "./pages/Dashboard";
import GuestTrack from "./pages/GuestTrack";
import Login from "./pages/Login";
import OrderDetail from "./pages/OrderDetail";
import OrderHistory from "./pages/OrderHistory";
import OrderWizard from "./pages/OrderWizard";
import Register from "./pages/Register";
function RequireAuth({ children }) {
  // social-ai style: the frontend trusts token PRESENCE (any string counts) —
  // the backend is the one that validates it on real API calls.
  // PUBLIC routes (never guarded): /login, /register, /track (incl. ?code=)
  if (!isAuthed()) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}
function RequireAdmin({ children }) {
  // Frontend gate is a hint only — the backend re-checks the role on
  // GET /api/admin/dashboard and returns 403 for non-admins.
  if (!isAuthed()) return <Navigate to="/login" replace />;
  if (getRole() !== "ADMIN") return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}
export default function App() {
  return (
    <Layout style={{ minHeight: "100vh", background: "#eef2f6" }}>
      <AppHeader />
      <Layout.Content
        style={{ padding: 24, maxWidth: 1100, margin: "0 auto", width: "100%" }}
      >
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/track" element={<GuestTrack />} />
          <Route
            path="/dashboard"
            element={
              <RequireAuth>
                <Dashboard />
              </RequireAuth>
            }
          />
          <Route
            path="/orders"
            element={
              <RequireAuth>
                <OrderHistory />
              </RequireAuth>
            }
          />
          <Route
            path="/order/new"
            element={
              <RequireAuth>
                <OrderWizard />
              </RequireAuth>
            }
          />
          <Route
            path="/order/:orderId"
            element={
              <RequireAuth>
                <OrderDetail />
              </RequireAuth>
            }
          />
          <Route
            path="/admin"
            element={
              <RequireAdmin>
                <AdminDashboard />
              </RequireAdmin>
            }
          />
          {/* Canonical and ONLY tracking link: /track?code=<trackingCode>
              (search box at /track), public, rendered by GuestTrack. */}
          {/* Landing page is auth-aware (WeDelivery/FedEx model): guests land on the
              public tracking lookup, logged-in users land on their dashboard. */}
          <Route
            path="*"
            element={
              <Navigate to={isAuthed() ? "/dashboard" : "/track"} replace />
            }
          />
        </Routes>
      </Layout.Content>
      <AiAssistant />
    </Layout>
  );
}
