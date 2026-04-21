import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useGameStore } from '../stores/gameStore';
import { GlowText } from '../components/GlowText';

export const GatePage: React.FC = () => {
    const [password, setPassword] = useState('');
    const [isError, setIsError] = useState(false);
    const [attempts, setAttempts] = useState(0);
    const startGame = useGameStore(state => state.startGame);
    const loading = useGameStore(state => state.loading);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        const success = await startGame(password);
        if (!success) {
            setIsError(true);
            setAttempts(prev => prev + 1);
            // Error stays visible for 4 seconds for readability of hints
            setTimeout(() => setIsError(false), 4000);
        }
    };

    const getHint = () => {
        if (attempts === 1) return "Thats sad, you did not get it right, try again.";
        if (attempts === 2) return "which word irritates me sometimes?";
        if (attempts >= 3) return "Are.. amay ki bole dakish tui?";
        return "That's not the key to this world...";
    };

    return (
        <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-background-primary overflow-hidden">
            <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ duration: 2 }}
                className="text-center space-y-12"
            >
                <motion.h1
                    className="text-4xl md:text-6xl font-serif text-text-primary"
                    initial={{ y: 20 }}
                    animate={{ y: 0 }}
                >
                    <GlowText>This world was made for you.</GlowText>
                </motion.h1>

                <form onSubmit={handleSubmit} className="relative max-w-sm mx-auto">
                    <input
                        type="text"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        disabled={loading}
                        placeholder="Say the magic word..."
                        className={`w-full bg-transparent border-b-2 outline-none py-2 px-4 transition-colors text-center text-xl font-light tracking-widest ${isError ? 'border-red-500 text-red-500' : 'border-border-subtle focus:border-accent-gold'
                            }`}
                    />
                    <AnimatePresence>
                        {isError && (
                            <motion.p
                                initial={{ opacity: 0, y: 10 }}
                                animate={{ opacity: 1, y: 0 }}
                                exit={{ opacity: 0 }}
                                className="absolute top-full mt-4 w-full text-center text-red-500/80 text-sm font-light italic"
                            >
                                {getHint()}
                            </motion.p>
                        )}
                    </AnimatePresence>

                    <button
                        type="submit"
                        className="hidden"
                        disabled={loading || !password}
                    />
                </form>
            </motion.div>
        </div>
    );
};
