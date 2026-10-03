import { ThemeProvider } from "@emotion/react";
import type { Preview } from "@storybook/react-webpack5";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { setupWorker } from "msw/browser";
import { mswLoader } from "msw-storybook-addon/csf3";
import { useState, type ReactNode } from "react";

import { handlers } from "../src/shared/api/mock/handlers";
import { GlobalStyle, theme } from "../src/shared/provider/themeProvider";

interface StoryQueryClientProviderProps {
  children: ReactNode;
}

/**
 * 스토리마다 새 QueryClient를 만들어 캐시가 다른 스토리로 새지 않게 해요.
 * 에러 상태가 재시도 없이 바로 보이도록 retry를 꺼요.
 */
function StoryQueryClientProvider({ children }: StoryQueryClientProviderProps) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: false },
          mutations: { retry: false },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const preview: Preview = {
  // 모든 컴포넌트에 설명과 props 표가 있는 문서 페이지를 만들어요
  tags: ["autodocs"],
  parameters: {
    options: {
      // 공통 컴포넌트를 맨 위에 두고, 도메인은 이름순으로 보여 줘요
      storySort: { order: ["Shared", ["*", "Layout"], "*"] },
    },
  },
  // vitest(server.ts)와 같은 기본 핸들러(항상 로그인된 상태)를 초기 핸들러로 깔아요.
  // 초기 핸들러는 스토리 사이 초기화에도 남고, 스토리의 parameters.msw.handlers가 그 앞에서 덮어요
  loaders: [
    mswLoader(async () => {
      const worker = setupWorker(...handlers);
      await worker.start({ quiet: true, onUnhandledRequest: "bypass" });
      return worker;
    }),
  ],
  // 앱의 진입점(src/index.tsx)과 같은 테마·전역 스타일·쿼리 프로바이더를 깔아요
  decorators: [
    (Story, { id }) => (
      <StoryQueryClientProvider key={id}>
        <ThemeProvider theme={theme}>
          <GlobalStyle />
          <Story />
        </ThemeProvider>
      </StoryQueryClientProvider>
    ),
  ],
};

export default preview;
