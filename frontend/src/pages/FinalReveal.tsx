import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { DiamondRing } from '../components/DiamondRing';
import { GlowText } from '../components/GlowText';
import { useGameStore } from '../stores/gameStore';

export const FinalReveal: React.FC = () => {
    const [showConfetti, setShowConfetti] = useState(false);
    const [crying, setCrying] = useState(false);
    const submitAnswer = useGameStore(state => state.submitAnswer);

    const handleYes = async () => {
        setShowConfetti(true);
        await submitAnswer(5, { answer: 'yes' });
    };

    const handleCry = () => {
        setCrying(true);
        setTimeout(handleYes, 3000);
    };

    return (
        <div className="min-h-screen relative flex flex-col items-center justify-center bg-background-primary p-6 overflow-hidden">
            {/* Spotlight Effect */}
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,transparent_0%,black_80%)] z-10" />

            <AnimatePresence>
                {!showConfetti ? (
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0, scale: 1.1 }}
                        transition={{ duration: 2 }}
                        className="relative z-20 flex flex-col items-center gap-12"
                    >
                        <DiamondRing />

                        <div className="text-center space-y-8">
                            <h2 className="text-4xl md:text-7xl font-serif text-text-primary leading-tight">
                                Will you <GlowText>marry me?</GlowText>
                            </h2>

                            <div className="flex flex-col md:flex-row gap-6 justify-center items-center">
                                <button
                                    onClick={handleYes}
                                    className="px-12 py-4 rounded-full border border-accent-gold text-accent-gold hover:bg-accent-gold hover:text-background-primary transition-all duration-500 font-medium tracking-widest uppercase text-sm"
                                >
                                    Yes, forever
                                </button>
                                <button
                                    onClick={handleCry}
                                    disabled={crying}
                                    className="px-8 py-4 rounded-full border border-border-subtle text-text-secondary hover:border-accent-rose hover:text-accent-rose transition-all duration-500 font-light tracking-widest text-sm"
                                >
                                    {crying ? "Take your time..." : "Let me cry first"}
                                </button>
                            </div>
                        </div>
                    </motion.div>
                ) : (
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        className="relative z-30 text-center max-w-2xl"
                    >
                        <h1 className="text-5xl md:text-8xl font-serif text-accent-gold mb-8">Yes.</h1>
                        <p className="romantic-text text-2xl md:text-4xl text-text-primary italic leading-relaxed">
                            "To the end of the world, and every lifetime thereafter."
                        </p>
                        {/* Particle implementation would go here or a simple CSS animation */}
                        <div className="mt-12 opacity-50 text-sm tracking-[0.3em] uppercase">forever mine</div>
                    </motion.div>
                )}
            </AnimatePresence>

            {/* Background Particles/Confetti */}
            <AnimatePresence>
                {showConfetti && (
                    <div className="absolute inset-0 z-40 pointer-events-none">
                        {Array.from({ length: 50 }).map((_, i) => (
                            <motion.div
                                key={i}
                                className="absolute w-1 h-1 bg-accent-gold rounded-full"
                                initial={{
                                    x: '50vw',
                                    y: '50vh',
                                    scale: 0
                                }}
                                animate={{
                                    x: `${Math.random() * 100}vw`,
                                    y: `${Math.random() * 100}vh`,
                                    scale: Math.random() * 2 + 1,
                                    opacity: [0, 1, 0]
                                }}
                                transition={{
                                    duration: Math.random() * 3 + 2,
                                    ease: "easeOut"
                                }}
                            />
                        ))}
                    </div>
                )}
            </AnimatePresence>
        </div>
    );
};
