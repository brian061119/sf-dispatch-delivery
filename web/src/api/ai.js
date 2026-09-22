import { http } from '../lib/http';
import * as mock from './mock';
// AI 自然语言下单 (P1). ⚠️ NOT in api-contract.md — frontend-proposed endpoint,
// needs a backend owner before it counts. Backend may be a real LLM or a regex
// stub for the demo — the frontend only cares about this signature. Always degrade gracefully.
export async function parseOrderText(text) {
    if (import.meta.env.VITE_MOCK === '1') return mock.parseOrderText(text);
    const { data } = await http.post('/ai/parse', { text });
    return data;
}
