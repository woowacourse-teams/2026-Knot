export const theme = {
  primary: "#3C3B39",

  neutral: {
    0: "#FFFFFF",
    50: "#FAF9F7",
    100: "#F4F2EE",
    200: "#E9E6E1",
    300: "#D8D4CD",
    400: "#B0ABA2",
    500: "#8C8880",
    600: "#6B6862",
    700: "#55524D",
    800: "#3C3B39",
    900: "#1F1E1C",
  },

  sub: {
    accent: {
      100: "#E5F0F4",
      200: "#A8CEDB",
      500: "#2E85A3",
    },
    warning: {
      50: "#FAEFEE",
      600: "#BF3B2C",
      800: "#8C2A1F",
    },
  },
} as const;

export type AppTheme = typeof theme;
