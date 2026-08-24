import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./shared/provider/queryClient";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";

createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </React.StrictMode>,
);
