import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        slack: {
          aubergine: "#3F0E40",
          aubergineDark: "#350D36",
          aubergineHover: "#4A154B",
          rail: "#2B0B2D",
          textOnPurple: "#D1C4DC",
          mention: "#1264A3",
          highlight: "#1164A3",
          accent: "#007A5A",
          unread: "#CD2553",
          divider: "#522653",
          bg: "#F8F8F8",
        },
      },
      fontFamily: {
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
        mono: ["var(--font-mono)", "monospace"],
      },
    },
  },
  plugins: [],
};
export default config;
