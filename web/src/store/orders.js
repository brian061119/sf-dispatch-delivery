import { create } from 'zustand';
import { getOrders } from '../api/order';
export const useOrders = create((set) => ({
    list: [],
    loading: false,
    async refresh() {
        set({ loading: true });
        try {
            const res = await getOrders(); // contract: GET /api/orders -> { orders: [...] }
            set({ list: res.orders });
        }
        finally {
            set({ loading: false });
        }
    },
}));
