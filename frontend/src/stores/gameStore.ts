import { create } from 'zustand';

interface GameState {
    chapter: number;
    completedChapters: number[];
    startedAt: string | null;
    loading: boolean;
    error: string | null;
    partnerName: string;
    totalChapters: number;
}

interface GameActions {
    fetchState: () => Promise<void>;
    fetchConfig: () => Promise<void>;
    startGame: (password: string) => Promise<boolean>;
    submitAnswer: (chapter: number, payload: any) => Promise<boolean>;
}

export const useGameStore = create<GameState & GameActions>((set) => ({
    chapter: 0,
    completedChapters: [],
    startedAt: null,
    loading: false,
    error: null,
    partnerName: 'Love',
    totalChapters: 5,

    fetchConfig: async () => {
        try {
            const res = await fetch('/api/game/config');
            if (res.ok) {
                const data = await res.json();
                set({ partnerName: data.partnerName, totalChapters: data.chapters });
            }
        } catch (err) {
            console.error('Failed to fetch config', err);
        }
    },

    fetchState: async () => {
        set({ loading: true });
        try {
            const res = await fetch('/api/game/state');
            if (res.ok) {
                const data = await res.json();
                set({
                    chapter: data.chapter,
                    completedChapters: data.completed,
                    startedAt: data.startedAt,
                    error: null
                });
            } else {
                // Not started yet or session expired
                set({ chapter: 0 });
            }
        } catch (err) {
            set({ error: 'Failed to connect to server' });
        } finally {
            set({ loading: false });
        }
    },

    startGame: async (password: string) => {
        set({ loading: true });
        try {
            const res = await fetch('/api/game/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password })
            });
            const data = await res.json();
            if (data.success) {
                set({ chapter: data.chapter, error: null });
                return true;
            } else {
                set({ error: data.message || 'Access denied' });
                return false;
            }
        } catch (err) {
            set({ error: 'Connection error' });
            return false;
        } finally {
            set({ loading: false });
        }
    },

    submitAnswer: async (chapter: number, payload: any) => {
        try {
            const res = await fetch('/api/game/answer', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ chapter, ...payload })
            });
            const data = await res.json();
            if (data.correct) {
                set((state) => ({
                    chapter: data.nextChapter,
                    completedChapters: [...state.completedChapters, chapter]
                }));
                return true;
            }
            return false;
        } catch (err) {
            console.error('Failed to submit answer', err);
            return false;
        }
    },
}));
