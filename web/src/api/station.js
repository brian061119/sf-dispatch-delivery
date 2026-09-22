import { http } from '../lib/http';
// 配送站 — shown on the map in wizard step 1.
// Contract: GET /api/stations (response body TBD — confirm with StationRepository owner).
export async function getStations() {
    const { data } = await http.get('/stations');
    return data;
}
