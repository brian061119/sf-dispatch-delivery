const mockOrders = [
  { id: 'DD-20260921-001', date: '09-21 15:32', packageName: 'Textbooks', weight: '2.5kg', vehicle: 'Robot', status: 'IN_TRANSIT', amount: 12.5 },
  { id: 'DD-20260918-014', date: '09-18 10:05', packageName: 'Keyboard', weight: '1.2kg', vehicle: 'Drone', status: 'DELIVERED', amount: 15 },
  { id: 'DD-20260915-007', date: '09-15 14:20', packageName: 'Glass vase', weight: '0.8kg', vehicle: 'Robot', status: 'DELIVERED', amount: 9.8 },
  { id: 'DD-20260912-003', date: '09-12 09:10', packageName: 'Documents', weight: '0.3kg', vehicle: 'Drone', status: 'CANCELLED', amount: 0 },
];

function toDisplayOrder(order) {
  return {
    id: order.orderNumber ?? order.orderId ?? order.id,
    date: order.createdAt ? new Date(order.createdAt).toLocaleString('en-US', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }) : order.date,
    packageName: order.packageDescription ?? order.packageName ?? 'Package',
    weight: order.weightKg ? `${order.weightKg}kg` : order.weight ?? '—',
    vehicle: order.vehicleType === 'ROBOT' ? 'Robot' : order.vehicleType === 'DRONE' ? 'Drone' : order.vehicle ?? '—',
    status: order.status,
    amount: Number(order.estimatedCost ?? order.amount ?? 0),
  };
}

export async function getOrders() {
  const token = localStorage.getItem('token');
  try {
    const response = await fetch('/api/orders', {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!response.ok) throw new Error('Could not load orders');
    const payload = await response.json();
    return (payload.orders ?? payload).map(toDisplayOrder);
  } catch {
    return mockOrders;
  }
}
