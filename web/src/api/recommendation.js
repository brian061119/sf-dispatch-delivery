import { http } from '../lib/http';
// Owner: Zihang Cao (推荐). Contract: POST /api/recommendations.
// Request:  { pickup, dropoff, package, priority }   (see types/api.js)
// Response: { candidates: Candidate[] } — isFastest/isCheapest computed by backend.
export async function getRecommendations(body) {
    const { data } = await http.post('/recommendations', body);
    return data;
}
