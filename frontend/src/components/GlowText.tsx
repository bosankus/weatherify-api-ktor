import React from 'react';
import { motion } from 'framer-motion';

interface GlowTextProps {
    children: React.ReactNode;
    className?: string;
}

export const GlowText: React.FC<GlowTextProps> = ({ children, className = '' }) => {
    return (
        <motion.span
            className={`text-accent-gold ${className}`}
            animate={{
                textShadow: [
                    '0 0 10px rgba(201, 169, 110, 0.3)',
                    '0 0 20px rgba(201, 169, 110, 0.5)',
                    '0 0 10px rgba(201, 169, 110, 0.3)',
                ],
            }}
            transition={{
                duration: 3,
                repeat: Infinity,
                ease: "easeInOut"
            }}
        >
            {children}
        </motion.span>
    );
};
