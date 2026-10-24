import designTokens from "@konselyavisa/design-tokens/tailwind-preset";

/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ["class"],
  presets: [designTokens],
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  plugins: [],
};
