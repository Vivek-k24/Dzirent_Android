/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        brand: {
          50: "#f6f7f9",
          100: "#eceef2",
          200: "#d5d9e2",
          300: "#b0b8c8",
          400: "#8491aa",
          500: "#63718f",
          600: "#4e5975",
          700: "#3f475f",
          800: "#363c4f",
          900: "#181a20",
          950: "#0f1015",
          accent: "#2A64F6",
          accentDark: "#3B82F6",
        },
      },
      fontFamily: {
        sans: [
          "Roboto",
          "-apple-system",
          "BlinkMacSystemFont",
          "Segoe UI",
          "Helvetica",
          "Arial",
          "sans-serif",
        ],
      },
    },
  },
  plugins: [],
};
