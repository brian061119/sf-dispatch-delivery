import { useEffect, useState } from 'react';
import { getOrders } from './orders';

const statusMeta = {
  IN_TRANSIT: { label: 'In transit', className: 'status-transit' },
  DELIVERED: { label: 'Delivered', className: 'status-delivered' },
  CANCELLED: { label: 'Cancelled', className: 'status-cancelled' },
  PENDING: { label: 'Pending', className: 'status-pending' },
};

function Vehicle({ type }) {
  return <span className="vehicle">{type === 'Robot' ? '🤖' : '🚁'} {type}</span>;
}

export default function App() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getOrders().then((items) => setOrders(items)).finally(() => setLoading(false));
  }, []);

  function logout() {
    localStorage.removeItem('token');
    window.location.assign('/login');
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="/">Dispatch &amp; Delivery</a>
        <button className="logout" type="button" onClick={logout}>LOG OUT</button>
      </header>

      <main className="page-content">
        <h1>My Orders</h1>
        <section className="orders-card" aria-label="Order history">
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Tracking No.</th><th>Date</th><th>Package</th><th>Vehicle</th><th>Status</th><th>Amount</th><th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {loading && <tr><td className="message" colSpan="7">Loading orders…</td></tr>}
                {!loading && orders.length === 0 && <tr><td className="message" colSpan="7">No orders yet.</td></tr>}
                {!loading && orders.map((order) => {
                  const meta = statusMeta[order.status] ?? statusMeta.PENDING;
                  const isCancelled = order.status === 'CANCELLED';
                  return <tr key={order.id} className={isCancelled ? 'cancelled-row' : ''}>
                    <td>{order.id}</td>
                    <td>{order.date}</td>
                    <td>{order.packageName} · {order.weight}</td>
                    <td><Vehicle type={order.vehicle} /></td>
                    <td><span className={`status ${meta.className}`}>{meta.label}</span></td>
                    <td>${order.amount.toFixed(2)}</td>
                    <td>{order.status === 'IN_TRANSIT' ? <a href={`/orders/${order.id}/tracking`}>Track <span aria-hidden="true">→</span></a> : order.status === 'DELIVERED' ? <a href={`/orders/${order.id}/review`}>Feedback</a> : <span className="muted">—</span>}</td>
                  </tr>;
                })}
              </tbody>
            </table>
          </div>
          <nav className="pagination" aria-label="Pagination">
            <button className="active" type="button" aria-current="page">1</button>
            <button type="button">2</button><button type="button">3</button><span>…</span>
          </nav>
        </section>
      </main>
    </div>
  );
}
