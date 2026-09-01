import React from "react";
import { createRoot } from "react-dom/client";
import { ThemeProvider } from "@emotion/react";

import App from "./App";
import { GlobalStyle, theme } from "./shared/provider/themeProvider";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./shared/provider/queryClient";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";

/**
 * API_MOCKING이 켜져 있을 때만 msw 워커를 띄워요.
 * 정적 import하면 msw가 프로덕션 번들에 들어가므로 플래그 안에서 동적 import해요.
 * 핸들러 없는 요청은 msw 기본값(warn)대로 실제 서버로 통과시켜요.
 */
const enableApiMocking = async () => {
  if (process.env.API_MOCKING !== "true") return;

  const { mockWorker } = await import("@api/mock/browser");
  await mockWorker.start();
};

const renderApp = () => {
  createRoot(document.getElementById("root") as HTMLElement).render(
    <React.StrictMode>
      <ThemeProvider theme={theme}>
        <GlobalStyle />
        <QueryClientProvider client={queryClient}>
          <App />
          <ReactQueryDevtools initialIsOpen={false} />
        </QueryClientProvider>
      </ThemeProvider>
    </React.StrictMode>,
  );
};

enableApiMocking()
  .then(renderApp)
  .catch((error: unknown) => {
    // 개발 전용 경로라 워커 시작 실패를 삼키지 않고 렌더하지 않은 채 콘솔로 드러내요
    console.error(
      "[MSW] mock 워커를 시작하지 못해 앱을 렌더하지 않았어요.",
      error,
    );
  });
