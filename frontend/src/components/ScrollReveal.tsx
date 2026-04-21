import React, { useRef } from 'react';
import { motion, useInView } from 'framer-motion';

interface ScrollRevealProps {
    children: React.ReactNode;
    delay?: number;
    duration?: number;
    y?: number;
    className?: string;
}

export const ScrollReveal: React.FC<ScrollRevealProps> = ({
    children,
    delay = 0,
    duration = 0.8,
    y = 30,
    className = ''
}) => {
    const ref = useRef(null);
    const isInView = useInView(ref, { once: true, amount: 0.3 });

    return (
        <motion.div
            ref={ref}
            initial={{ opacity: 0, y }}
            animate={isInView ? { opacity: 1, y: 0 } : { opacity: 0, y }}
            transition={{ duration, delay, ease: "easeOut" }}
            className={className}
        >
            {children}
        </motion.div>
    );
};
