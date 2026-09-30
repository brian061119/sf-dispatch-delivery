import { create } from 'zustand';
import { sendChatMessage } from '../api/ai';
// AI assistant conversation state. The drawer is app-global (mounted in App).
// messages: [{ role: 'user'|'assistant', content, cards?, prefill? }]
export const useChat = create((set, get) => ({
    open: false,
    busy: false,
    messages: [],
    toggle: () => set((s) => ({ open: !s.open })),
    async send(text) {
        const content = (text ?? '').trim();
        if (!content || get().busy) return;
        const history = get().messages.map(({ role, content: c }) => ({ role, content: c }));
        set((s) => ({ busy: true, messages: [...s.messages, { role: 'user', content }] }));
        try {
            const res = await sendChatMessage({ message: content, history });
            set((s) => ({
                busy: false,
                messages: [...s.messages, { role: 'assistant', content: res.reply ?? '…', cards: res.cards ?? [], prefill: res.prefill }],
            }));
        } catch {
            set((s) => ({
                busy: false,
                messages: [...s.messages, { role: 'assistant', content: 'Sorry — the assistant is unavailable right now. You can keep using the app normally.', cards: [] }],
            }));
        }
    },
    clear: () => set({ messages: [] }),
}));
