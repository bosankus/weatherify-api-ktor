import React from 'react';
import { motion } from 'framer-motion';

export const DiamondRing: React.FC = () => {
    return (
        <div className="relative w-64 h-64 flex items-center justify-center">
            {/* Halo Glow */}
            <motion.div
                className="absolute w-48 h-48 rounded-full"
                style={{
                    background: 'radial-gradient(circle, rgba(201, 169, 110, 0.2) 0%, transparent 70%)',
                }}
                animate={{
                    scale: [1, 1.2, 1],
                    opacity: [0.3, 0.6, 0.3],
                }}
                transition={{
                    duration: 4,
                    repeat: Infinity,
                    ease: "easeInOut"
                }}
            />

            <svg viewBox="0 0 100 100" className="w-full h-full drop-shadow-glow">
                {/* Ring Band */}
                <circle
                    cx="50"
                    cy="65"
                    r="25"
                    fill="none"
                    stroke="url(#goldGradient)"
                    strokeWidth="4"
                />

                {/* Diamond Base (Setting) */}
                <path
                    d="M40 40 L60 40 L55 50 L45 50 Z"
                    fill="#E8D5A8"
                />

                {/* The Diamond */}
                <motion.path
                    d="M50 20 L65 35 L50 50 L35 35 Z"
                    fill="url(#diamondGradient)"
                    animate={{
                        filter: [
                            'brightness(1) contrast(1)',
                            'brightness(1.5) contrast(1.2)',
                            'brightness(1) contrast(1)',
                        ],
                    }}
                    transition={{
                        duration: 2,
                        repeat: Infinity,
                        ease: "easeInOut"
                    }}
                />

                {/* Sparkles */}
                {[0, 45, 90, 135, 180, 225, 270, 315].map((angle, i) => (
                    <motion.circle
                        key={i}
                        cx={50 + Math.cos(angle * Math.PI / 180) * 15}
                        cy={35 + Math.sin(angle * Math.PI / 180) * 15}
                        r="1"
                        fill="white"
                        animate={{
                            opacity: [0, 1, 0],
                            scale: [0, 1.5, 0],
                        }}
                        transition={{
                            duration: 1.5,
                            delay: i * 0.2,
                            repeat: Infinity,
                        }}
                    />
                ))}

                <defs>
                    <linearGradient id="goldGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stopColor="#C9A96E" />
                        <stop offset="50%" stopColor="#E8D5A8" />
                        <stop offset="100%" stopColor="#C9A96E" />
                    </linearGradient>
                    <radialGradient id="diamondGradient">
                        <stop offset="0%" stopColor="#FFFFFF" />
                        <stop offset="50%" stopColor="#F5F0EB" />
                        <stop offset="100%" stopColor="#C9A96E" />
                    </radialGradient>
                </defs>
            </svg>
        </div>
    );
};
