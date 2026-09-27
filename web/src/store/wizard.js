import { create } from 'zustand';
// Order-wizard draft, shared by the 4 steps of /order/new. Shapes follow the
// contract: pickup/dropoff = ContractAddress, pkg = ContractPackage,
// candidates/selected = Candidate from POST /api/recommendations.
// prefill() merges a partial draft (used by the AI one-sentence order on
// Dashboard): AI drafts, wizard verifies.
export const useWizard = create((set) => ({
    pickup: undefined,
    dropoff: undefined,
    pkg: undefined,
    priority: 'STANDARD',
    candidates: [],
    selected: undefined,
    setAddress: (pickup, dropoff) => set({ pickup, dropoff }),
    setPackage: (pkg, priority) => set({ pkg, ...(priority ? { priority } : {}) }),
    setCandidates: (candidates) => set({ candidates }),
    select: (selected) => set({ selected }),
    prefill: (draft) => set((s) => ({ ...s, ...draft })),
    reset: () => set({ pickup: undefined, dropoff: undefined, pkg: undefined, priority: 'STANDARD', candidates: [], selected: undefined }),
}));
