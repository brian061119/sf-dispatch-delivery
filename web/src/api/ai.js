import { http } from '../lib/http';
import * as mock from './mock';
// AI natural-language ordering (P1). ⚠️ NOT in api-contract.md — frontend-proposed endpoint,
// needs a backend owner before it counts. Backend may be a real LLM or a regex
// stub for the demo — the frontend only cares about this signature. Always degrade gracefully.
export async function parseOrderText(text) {
    if (import.meta.env.VITE_MOCK === '1') return mock.parseOrderText(text);
    const { data } = await http.post('/ai/parse', { text }, { timeout: 60000 });
    return data;
}
// AI assistant (contract-external POST /api/ai/chat — proposed 2026-09-30).
// Read-only intents: track-by-code, price quotes, status explanations,
// order-draft prefill. Writes are never executed by the model — the reply may
// carry a `prefill` draft, but the user still walks the wizard and pays.
// Request:  { message, history: [{ role: 'user'|'assistant', content }] }
// Response: { reply, cards?: Array<{type:'order'|'quote'|'prefill', ...}>, prefill?: {...} }
export async function sendChatMessage(body) {
    if (import.meta.env.VITE_MOCK === '1') return mock.sendChatMessage(body);
    const { data } = await http.post('/ai/chat', body, { timeout: 60000 });
    return data;
}
