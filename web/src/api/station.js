import { http } from '../lib/http';
import * as mock from './mock';
// Delivery stations — shown on the map in wizard step 1.
// Contract: GET /api/stations — CONFIRMED 2026-09-26 (no auth required).
// Response items are Station (see types/api.js): stationId / name / latitude /
// longitude / maxCapacity ... NOT the old guess of { lat, lng }.
export async function getStations() {
    if (import.meta.env.VITE_MOCK === '1') return mock.getStations();
    const { data } = await http.get('/stations');
    return data;
}
