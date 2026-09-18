import { GetMeResponseDto } from "@api/dto/auth";
import { AUTH_ME_API_PATH } from "@api/fetch/api/v1/auth/me";
import { meResponse } from "@api/mock/responses/auth";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import MemberGreeting from ".";

const expected = new GetMeResponseDto(meResponse);

const renderGreeting = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <MemberGreeting />
      </QueryClientProvider>
    </ThemeProvider>,
  );
};

const holdMeResponse = () => {
  mockServer.use(
    http.get(`*${AUTH_ME_API_PATH}`, async () => {
      await delay("infinite");
      return HttpResponse.json(meResponse);
    }),
  );
};

describe("MemberGreeting", () => {
  it("회원 정보 응답의 닉네임으로 인사한다", async () => {
    renderGreeting();

    expect(
      await screen.findByRole("heading", {
        name: `반가워요, ${expected.nickname} 님`,
      }),
    ).toBeInTheDocument();
  });

  it("응답 전에는 닉네임 없이 인사말만 보여준다", () => {
    holdMeResponse();
    renderGreeting();

    expect(
      screen.getByRole("heading", { name: "반가워요" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/님$/)).not.toBeInTheDocument();
  });
});
