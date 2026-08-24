import { css } from "@emotion/react";

const text = {
  title01: css`
    font-size: 2.5rem; /* 40px */
    font-weight: 500;
    line-height: 1.3;
    letter-spacing: -0.02em;
  `,
  title02: css`
    font-size: 2.5rem; /* 40px */
    font-weight: 700;
    line-height: 1.3;
    letter-spacing: -0.02em;
  `,

  heading01: css`
    font-size: 1.5rem; /* 24px */
    font-weight: 500;
    line-height: 1.5;
    letter-spacing: -0.02em;
  `,
  heading02: css`
    font-size: 1.625rem; /* 26px */
    font-weight: 600;
    line-height: 1.5;
    letter-spacing: -0.02em;
  `,

  body01: css`
    font-size: 1rem; /* 16px */
    font-weight: 400;
    line-height: 1.5;
    letter-spacing: 0;
  `,
  body02: css`
    font-size: 1.125rem; /* 18px */
    font-weight: 400;
    line-height: 1.5;
    letter-spacing: 0;
  `,

  label01: css`
    font-size: 1rem; /* 16px */
    font-weight: 600;
    line-height: 1.5;
    letter-spacing: 0;
  `,

  caption01: css`
    font-size: 0.75rem; /* 12px */
    font-weight: 400;
    line-height: 1.5;
    letter-spacing: 0;
  `,
  caption02: css`
    font-size: 0.875rem; /* 14px */
    font-weight: 400;
    line-height: 1.5;
    letter-spacing: 0;
  `,
} as const;

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

  text,

  shadow02:
    "0 6px 16px -4px rgba(31, 30, 28, 0.08), 0 1px 2px 0 rgba(31, 30, 28, 0.04)",
  shadow03:
    "0 8px 24px 0 rgba(31, 30, 28, 0.07), 0 2px 4px 0 rgba(31, 30, 28, 0.04)",
} as const;

export type AppTheme = typeof theme;
