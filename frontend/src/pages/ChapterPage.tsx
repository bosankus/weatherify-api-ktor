import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useGameStore } from '../stores/gameStore';
import { GlowText } from '../components/GlowText';
import { ScrollReveal } from '../components/ScrollReveal';

export const ChapterPage: React.FC = () => {
    const chapter = useGameStore(state => state.chapter);
    const submitAnswer = useGameStore(state => state.submitAnswer);

    // Chapter 1: Reveal state
    const [revealed, setRevealed] = useState(false);

    // Chapter 2: Ghat choice
    const GHATS = ["Dashashwamedh", "Assi", "Manikarnika"];

    // Chapter 3: Diya count
    const [diyas, setDiyas] = useState(0);
    const [showHeart, setShowHeart] = useState(false);

    useEffect(() => {
        if (chapter === 3 && diyas >= 15 && !showHeart) {
            setTimeout(() => setShowHeart(true), 500);
            submitAnswer(3, { count: 15 });
        }
    }, [diyas, chapter, showHeart, submitAnswer]);

    const renderChapter = () => {
        switch (chapter) {
            case 1:
                return (
                    <div className="min-h-screen flex flex-col items-center justify-center text-center px-6">
                        <ScrollReveal>
                            <h2 className="text-sm uppercase tracking-[0.5em] text-accent-gold mb-6">Chapter One</h2>
                            <h1 className="text-5xl md:text-8xl font-serif mb-12 leading-tight">
                                Some cities don't just exist.<br /><GlowText>They remember.</GlowText>
                            </h1>
                        </ScrollReveal>

                        <ScrollReveal delay={0.4}>
                            <p className="romantic-text text-xl md:text-3xl max-w-2xl mx-auto italic opacity-80 mb-16">
                                Varanasi was the first witness to our story. Between the ancient stones and the eternal river,
                                we found something that the world had forgotten...
                            </p>
                        </ScrollReveal>

                        <ScrollReveal delay={0.8}>
                            <div className="relative group cursor-pointer" onClick={() => {
                                setRevealed(true);
                                submitAnswer(1, { action: 'revealed' });
                            }}>
                                <AnimatePresence mode="wait">
                                    {!revealed ? (
                                        <motion.div
                                            key="mask"
                                            exit={{ opacity: 0, scale: 1.1 }}
                                            className="border border-accent-gold/30 px-12 py-6 rounded-full text-accent-gold/60 uppercase tracking-[0.3em] text-xs hover:border-accent-gold hover:text-accent-gold transition-all"
                                        >
                                            Touch to unveil the memory
                                        </motion.div>
                                    ) : (
                                        <motion.div
                                            key="content"
                                            initial={{ opacity: 0, y: 10 }}
                                            animate={{ opacity: 1, y: 0 }}
                                            className="text-2xl font-serif text-accent-gold-light italic"
                                        >
                                            "The first time you laughed at my worst joke, I knew."
                                        </motion.div>
                                    )}
                                </AnimatePresence>
                            </div>
                        </ScrollReveal>
                    </div>
                );

            case 2:
                return (
                    <div className="min-h-screen flex flex-col items-center justify-center px-6 py-24">
                        <ScrollReveal>
                            <h2 className="text-sm uppercase tracking-[0.5em] text-accent-gold mb-6">Chapter Two</h2>
                            <h1 className="text-5xl md:text-8xl font-serif mb-12 leading-tight">
                                The City of <GlowText>Light</GlowText>
                            </h1>
                        </ScrollReveal>

                        <ScrollReveal delay={0.4}>
                            <div className="max-w-xl mx-auto mb-16 space-y-8">
                                <p className="romantic-text text-xl italic opacity-80 leading-relaxed">
                                    Where the Ganga holds every prayer ever whispered. We stood there, as the bells rang across the water,
                                    and you made a wish. Do you remember where we were?
                                </p>
                            </div>
                        </ScrollReveal>

                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 w-full max-w-4xl">
                            {GHATS.map((ghat, i) => (
                                <ScrollReveal key={ghat} delay={0.6 + i * 0.1}>
                                    <button
                                        onClick={() => submitAnswer(2, { answer: ghat.toLowerCase() })}
                                        className="w-full p-8 border border-border-subtle rounded-2xl hover:border-accent-gold hover:bg-accent-gold/5 transition-all group text-center"
                                    >
                                        <span className="block text-text-secondary group-hover:text-accent-gold uppercase tracking-[0.2em] text-xs mb-2">Ghat</span>
                                        <span className="text-xl font-serif text-text-primary group-hover:text-accent-gold-light">{ghat}</span>
                                    </button>
                                </ScrollReveal>
                            ))}
                        </div>
                    </div>
                );

            case 3:
                return (
                    <div className="min-h-screen flex flex-col items-center justify-center text-center px-6 relative" onClick={() => setDiyas(d => d + 1)}>
                        <div className="absolute inset-0 pointer-events-none overflow-hidden">
                            <AnimatePresence>
                                {Array.from({ length: diyas }).map((_, i) => (
                                    <motion.div
                                        key={i}
                                        initial={{ opacity: 0, scale: 0 }}
                                        animate={{
                                            opacity: 0.8,
                                            scale: 1,
                                            x: showHeart ? undefined : `${Math.random() * 100}vw`,
                                            y: showHeart ? undefined : `${Math.random() * 100}vh`
                                        }}
                                        className="absolute w-2 h-2 bg-accent-gold rounded-full blur-[2px] shadow-[0_0_10px_var(--accent-gold)]"
                                        style={showHeart ? {
                                            left: '50%',
                                            top: '50%',
                                            transform: `translate(-50%, -50%) rotate(${i * (360 / diyas)}deg) translateY(-100px)`
                                        } : {}}
                                    />
                                ))}
                            </AnimatePresence>
                        </div>

                        <ScrollReveal className="relative z-10">
                            <h2 className="text-sm uppercase tracking-[0.5em] text-accent-gold mb-6">Chapter Three</h2>
                            <h1 className="text-5xl md:text-8xl font-serif mb-12 leading-tight">
                                A Thousand <GlowText>Diyas</GlowText>
                            </h1>
                            <p className="romantic-text text-xl italic opacity-80 max-w-lg mx-auto">
                                Every light here is a wish. Tap the screen to light ours.
                            </p>
                            <div className="mt-8 text-accent-gold/40 font-mono tracking-widest uppercase text-xs">
                                {diyas} / 15
                            </div>
                        </ScrollReveal>

                        <AnimatePresence>
                            {showHeart && (
                                <motion.div
                                    initial={{ opacity: 0, scale: 0.9 }}
                                    animate={{ opacity: 1, scale: 1 }}
                                    className="mt-16 relative z-10"
                                >
                                    <p className="romantic-text text-3xl italic text-accent-gold-light">
                                        "My only wish has always been you."
                                    </p>
                                    <button
                                        onClick={(e) => { e.stopPropagation(); submitAnswer(3, { count: 15 }); }}
                                        className="mt-8 text-xs text-text-secondary uppercase tracking-[0.4em] hover:text-white transition-colors"
                                    >
                                        Continue
                                    </button>
                                </motion.div>
                            )}
                        </AnimatePresence>
                    </div>
                );

            case 4:
                return (
                    <div className="min-h-screen flex flex-col items-center justify-center text-center px-6">
                        <ScrollReveal>
                            <h2 className="text-sm uppercase tracking-[0.5em] text-accent-gold mb-6">Chapter Four</h2>
                            <h1 className="text-5xl md:text-8xl font-serif mb-12 leading-tight">
                                The <GlowText>Question</GlowText>
                            </h1>
                        </ScrollReveal>

                        <div className="max-w-2xl mx-auto space-y-12">
                            <p className="romantic-text text-2xl md:text-4xl italic leading-relaxed">
                                I've carried this question across every lifetime this city has seen...
                            </p>

                            <ScrollReveal delay={1.5}>
                                <motion.button
                                    whileHover={{ scale: 1.05 }}
                                    whileTap={{ scale: 0.95 }}
                                    onClick={() => submitAnswer(4, { action: 'ready' })}
                                    className="px-16 py-6 bg-accent-gold text-background-primary rounded-full font-serif text-xl tracking-widest shadow-glow hover:bg-accent-gold-light transition-all duration-500"
                                >
                                    Turn around
                                </motion.button>
                            </ScrollReveal>
                        </div>
                    </div>
                );

            default:
                return null;
        }
    };

    return (
        <div className="bg-background-primary min-h-screen text-text-primary selection:bg-accent-gold selection:text-background-primary">
            <AnimatePresence mode="wait">
                <motion.div
                    key={chapter}
                    initial={{ opacity: 0, scale: 0.98 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 1.02 }}
                    transition={{ duration: 0.6 }}
                >
                    {renderChapter()}
                </motion.div>
            </AnimatePresence>
        </div>
    );
};
