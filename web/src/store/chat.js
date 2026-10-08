import { create } from 'zustand';
import { apiErrorMessage } from '../lib/http';
import { sendChatMessage } from '../api/ai';
// AI assistant conversation state. The drawer is app-global (mounted in App).
// messages: [{ role: 'user'|'assistant', content, cards?, prefill? }]
export const useChat = create((set, get) => ({
    generation: 0,
    open: false,
    busy: false,
    messages: [],
    toggle: () => set((s) => ({ open: !s.open })),
    async send(text) {
        const content = (text ?? '').trim();
        if (!content || get().busy) return;
        const generation = get().generation;
        const history = get().messages.slice(-20).map(({ role, content: c }) => ({ role, content: c }));
        set((s) => ({ busy: true, messages: [...s.messages, { role: 'user', content }] }));
        try {
            const res = await sendChatMessage({ message: content, history });
            if (get().generation !== generation) return;
            set((s) => ({
                busy: false,
                messages: [...s.messages, { role: 'assistant', content: res.reply ?? '…', cards: res.cards ?? [], prefill: res.prefill }],
            }));
        } catch (err) {
            if (get().generation !== generation) return;
            set((s) => ({
                busy: false,
                messages: [...s.messages, { role: 'assistant', content: apiErrorMessage(err, 'The assistant is unavailable. Please try again later.'), cards: [] }],
            }));
        }
    },
    clear: () => set((s) => ({ messages: [], busy: false, open: false, generation: s.generation + 1 })),
}));
