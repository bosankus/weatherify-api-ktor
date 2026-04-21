/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                background: {
                    primary: '#0A0A0A',
                    secondary: '#111111',
                    card: '#1A1A1A',
                },
                text: {
                    primary: '#F5F0EB',
                    secondary: '#8A8478',
                },
                accent: {
                    gold: '#C9A96E',
                    'gold-light': '#E8D5A8',
                    rose: '#B76E79',
                },
            },
            fontFamily: {
                serif: ['"Playfair Display"', 'serif'],
                sans: ['Inter', 'system-ui', 'sans-serif'],
                quotes: ['"Cormorant Garamond"', 'serif'],
            },
            boxShadow: {
                glow: '0 0 20px rgba(201, 169, 110, 0.15)',
            }
        },
    },
    plugins: [],
}
