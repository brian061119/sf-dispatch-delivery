import { http } from '../lib/http';
import * as mock from './mock';
// Owner: Zihang Cao (recommendations). Contract: POST /api/recommendations.
// Request:  { pickup, dropoff, package, priority }   (see types/api.js)
// Response: { candidates: Candidate[] } — isFastest/isCheapest computed by backend.
export async function getRecommendations(body) {
    if (import.meta.env.VITE_MOCK === '1') return mock.getRecommendations(body);
    const { data } = await http.post('/recommendations', body);
    return data;
}
