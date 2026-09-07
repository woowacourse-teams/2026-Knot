import { receiveLoginTokens } from "@api/authToken";
import React from "react";
import { createRoot } from "react-dom/client";
import { ThemeProvider } from "@emotion/react";

import App from "./App";
import { GlobalStyle, theme } from "./shared/provider/themeProvider";
import { QueryClientProvider } from "@tanstack/react-query";
import { queryClient } from "./shared/provider/queryClient";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";

// 정적 import하면 msw가 프로덕션 번들에 들어가므로 플래그 안에서 동적 import해요
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

// 로그인 리다이렉트로 받은 토큰을 그리기 전에 저장해야 첫 요청부터 Authorization 헤더가 붙어요
enableApiMocking()
  .then(receiveLoginTokens)
  .then(renderApp)
  .catch((error: unknown) => {
    // 준비 단계 실패를 삼키지 않고 콘솔로 드러내요. 여기서 막히면 화면이 아예 그려지지 않아요
    console.error("앱을 시작하기 전 준비 단계에서 실패했어요.", error);
  });
