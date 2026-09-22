import { http } from '../lib/http';
import * as mock from './mock';
// 配送站 — shown on the map in wizard step 1.
// Contract: GET /api/stations (response body TBD — confirm with StationRepository owner).
export async function getStations() {
    if (import.meta.env.VITE_MOCK === '1') return mock.getStations();
    const { data } = await http.get('/stations');
    return data;
}
