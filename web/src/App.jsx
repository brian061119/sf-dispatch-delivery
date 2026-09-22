import { Layout } from 'antd';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AppHeader } from './components/AppHeader';
import { isAuthed } from './lib/auth';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import OrderDetail from './pages/OrderDetail';
import OrderHistory from './pages/OrderHistory';
import OrderWizard from './pages/OrderWizard';
import Register from './pages/Register';
import Tracking from './pages/Tracking';
function RequireAuth({ children }) {
    // TEMP while Login/Register are placeholders: let every route render so all
    // page URLs stay directly accessible. Restore the isAuthed() check (and the
    // redirect to /login) when Ziyuan's real Login lands.
    return <>{children}</>;
}
export default function App() {
    return (<Layout style={{ minHeight: '100vh' }}>
      {isAuthed() && <AppHeader />}
      <Layout.Content style={{ padding: 24, maxWidth: 1100, margin: '0 auto', width: '100%' }}>
        <Routes>
          <Route path="/login" element={<Login />}/>
          <Route path="/register" element={<Register />}/>
          <Route path="/dashboard" element={<RequireAuth><Dashboard /></RequireAuth>}/>
          <Route path="/orders" element={<RequireAuth><OrderHistory /></RequireAuth>}/>
          <Route path="/order/new" element={<RequireAuth><OrderWizard /></RequireAuth>}/>
          <Route path="/order/:orderId" element={<RequireAuth><OrderDetail /></RequireAuth>}/>
          <Route path="/tracking/:orderId" element={<RequireAuth><Tracking /></RequireAuth>}/>
          <Route path="*" element={<Navigate to="/dashboard" replace/>}/>
        </Routes>
      </Layout.Content>
    </Layout>);
}
