import React from 'react';
import { motion } from 'framer-motion';

interface ProgressDotsProps {
    current: number;
    total: number;
}

export const ProgressDots: React.FC<ProgressDotsProps> = ({ current, total }) => {
    return (
        <div className="fixed bottom-12 left-1/2 -translate-x-1/2 flex gap-4 z-50">
            {Array.from({ length: total }).map((_, i) => (
                <motion.div
                    key={i}
                    className={`h-2 rounded-full border border-accent-gold ${i + 1 <= current ? 'bg-accent-gold' : 'bg-transparent'
                        }`}
                    animate={{
                        width: i + 1 === current ? 24 : 8,
                        opacity: i + 1 === current ? 1 : 0.5,
                    }}
                    transition={{ duration: 0.4 }}
                />
            ))}
        </div>
    );
};
