import React from "react";
import { createRoot } from "react-dom/client";
import { ThemeProvider } from "@emotion/react";

import App from "./App";
import { GlobalStyle, theme } from "./shared/provider/themeProvider";

createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <ThemeProvider theme={theme}>
      <GlobalStyle />
      <App />
    </ThemeProvider>
  </React.StrictMode>,
);
