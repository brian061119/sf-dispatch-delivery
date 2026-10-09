import { Layout } from "antd";
import { Navigate, Route, Routes } from "react-router-dom";
import { AiAssistant } from "./components/AiAssistant";
import { AppHeader } from "./components/AppHeader";
import { useAuth } from "./store/auth";
import { getRole, isAuthed } from "./lib/auth";
import AdminDashboard from "./pages/AdminDashboard";
import AdminUserDetail from "./pages/AdminUserDetail";
import Dashboard from "./pages/Dashboard";
import ForgotPassword from "./pages/ForgotPassword";
import GuestTrack from "./pages/GuestTrack";
import Login from "./pages/Login";
import OrderDetail from "./pages/OrderDetail";
import OrderHistory from "./pages/OrderHistory";
import OrderWizard from "./pages/OrderWizard";
import Register from "./pages/Register";
import ResetPassword from "./pages/ResetPassword";
import VipMembership from "./pages/VipMembership";
function RequireCustomer({ children }) {
  // social-ai style: the frontend trusts token PRESENCE (any string counts) —
  // the backend is the one that validates it on real API calls.
  // PUBLIC routes (never guarded): /login, /register, /track (incl. ?code=)
  if (!isAuthed()) {
    return <Navigate to="/login" replace />;
  }
  if (getRole() === "ADMIN") return <Navigate to="/admin" replace />;
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
  const { token, role } = useAuth();
  const isAdmin = !!token && role === "ADMIN";
  const home = token ? (isAdmin ? "/admin" : "/dashboard") : "/track";
  return (
    <Layout style={{ minHeight: "100vh", background: "#f4f6f8" }}>
      <AppHeader />
      <Layout.Content
        style={{ padding: 24, maxWidth: 1100, margin: "0 auto", width: "100%" }}
      >
        <Routes>
          <Route path="/login" element={isAdmin ? <Navigate to="/admin" replace /> : <Login />} />
          <Route path="/register" element={isAdmin ? <Navigate to="/admin" replace /> : <Register />} />
          {/* Public: forgot / reset password (the reset link carries a one-time token). */}
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/track" element={isAdmin ? <Navigate to="/admin" replace /> : <GuestTrack />} />
          <Route
            path="/dashboard"
            element={
              <RequireCustomer>
                <Dashboard />
              </RequireCustomer>
            }
          />
          <Route
            path="/orders"
            element={
              <RequireCustomer>
                <OrderHistory />
              </RequireCustomer>
            }
          />
          <Route
            path="/order/new"
            element={
              <RequireCustomer>
                <OrderWizard />
              </RequireCustomer>
            }
          />
          <Route
            path="/order/:orderId"
            element={
              <RequireCustomer>
                <OrderDetail />
              </RequireCustomer>
            }
          />
          <Route
            path="/vip"
            element={
              <RequireCustomer>
                <VipMembership />
              </RequireCustomer>
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
          <Route
            path="/admin/users/:userId"
            element={
              <RequireAdmin>
                <AdminUserDetail />
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
              <Navigate to={home} replace />
            }
          />
        </Routes>
      </Layout.Content>
      {!isAdmin && <AiAssistant />}
    </Layout>
  );
}
