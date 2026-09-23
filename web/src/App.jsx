import { Layout } from "antd";
import { Navigate, Route, Routes } from "react-router-dom";
import { AppHeader } from "./components/AppHeader";
import { isAuthed } from "./lib/auth";
import Dashboard from "./pages/Dashboard";
import GuestTrack from "./pages/GuestTrack";
import Login from "./pages/Login";
import OrderDetail from "./pages/OrderDetail";
import OrderHistory from "./pages/OrderHistory";
import OrderWizard from "./pages/OrderWizard";
import Register from "./pages/Register";
import Tracking from "./pages/Tracking";
function RequireAuth({ children }) {
  // social-ai style: the frontend trusts token PRESENCE (any string counts) —
  // the backend is the one that validates it on real API calls. During the demo
  // phase (Login/Register still stubs) you can "log in" manually: DevTools →
  // Application → Local Storage → set  token = anything  → refresh.
  // PUBLIC routes (never guarded): /login, /register, /track, /tracking/:orderId
  // (guest order lookup — the order number itself is the credential).
  if (!isAuthed()) {
    return <Navigate to="/login" replace />;
  }

  return isAuthed() ? <>{children}</> : <Navigate to="/login" replace />;
}
export default function App() {
  return (
    <Layout style={{ minHeight: "100vh" }}>
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
          <Route path="/tracking/:orderId" element={<Tracking />} />
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
    </Layout>
  );
}
