import {
  GetInvitationPreviewResponseDto,
  PostWorkspaceInvitationResponseDto,
} from "@api/dto/workspaceInvitation";
import {
  invitationPreviewResponse,
  workspaceInvitationResponse,
} from "@api/mock/responses/workspaceInvitation";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceInviteLinkGate from ".";

const expected = new GetInvitationPreviewResponseDto(invitationPreviewResponse);
const VALID_TOKEN = new PostWorkspaceInvitationResponseDto(
  workspaceInvitationResponse,
).linkToken;
const JOIN_PATH = `/workspace/${expected.workspaceId}/join`;
const ELSEWHERE_PATH = "/elsewhere";
const STATUS_TEXT = "초대 링크를 확인하고 있어요";
// 경로 파라미터가 있어 fetch 상수 대신 mock 핸들러와 같은 패턴을 적어요
const PREVIEW_PATH_PATTERN = "*/api/v1/invitations/:tokenOrCode";

const renderGate = (token: string) => {
  const invitePath = getRouterPath({ routeKey: "INVITE", params: { token } });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: ELSEWHERE_PATH, element: <p>다른 화면</p> },
      { path: PATH_ROUTE.INVITE, element: <WorkspaceInviteLinkGate /> },
      { path: PATH_ROUTE.WORKSPACE_JOIN, element: <p>입장 확인</p> },
      { path: PATH_ROUTE.JOIN_ERROR, element: <p>초대 링크 오류</p> },
    ],
    { initialEntries: [ELSEWHERE_PATH, invitePath], initialIndex: 1 },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  return { router, invitePath };
};

const overridePreviewStatus = (status: number) => {
  mockServer.use(
    http.get(PREVIEW_PATH_PATTERN, () => new HttpResponse(null, { status })),
  );
};

/** 발급된 linkToken 원문만 통과시켜 게이트가 토큰을 정규화하지 않는지 확인해요 */
const overridePreviewByToken = () => {
  mockServer.use(
    http.get(PREVIEW_PATH_PATTERN, ({ params }) =>
      params.tokenOrCode === VALID_TOKEN
        ? HttpResponse.json(invitationPreviewResponse)
        : new HttpResponse(null, { status: 404 }),
    ),
  );
};

const holdPreviewResponse = (ms: number | "infinite") => {
  mockServer.use(
    http.get(PREVIEW_PATH_PATTERN, async () => {
      await delay(ms);
      return HttpResponse.json(invitationPreviewResponse);
    }),
  );
};

const waitForPath = (
  router: ReturnType<typeof createMemoryRouter>,
  pathname: string,
) =>
  waitFor(() => {
    expect(router.state.location.pathname).toBe(pathname);
  });

const waitRealTime = async (ms: number) => {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, ms));
  });
};

const goBack = async (router: ReturnType<typeof createMemoryRouter>) => {
  await act(async () => {
    await router.navigate(-1);
  });
};

describe("WorkspaceInviteLinkGate", () => {
  it("판정 중에는 스피너와 확인 중 안내를 보여준다", () => {
    holdPreviewResponse("infinite");
    const { router, invitePath } = renderGate(VALID_TOKEN);

    const status = screen.getByRole("status");

    expect(status).toHaveTextContent(STATUS_TEXT);
    expect(status.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(invitePath);
  });

  it("미리보기에 성공하면 응답의 workspaceId로 입장 확인 화면에 이동하며 토큰과 이름을 state로 넘긴다", async () => {
    const { router } = renderGate(VALID_TOKEN);

    await waitForPath(router, JOIN_PATH);

    expect(router.state.location.state).toEqual({
      credential: VALID_TOKEN,
      workspaceName: expected.workspaceName,
    });
  });

  it("통과 이동은 replace라 뒤로 가기 때 진입 라우트로 돌아오지 않는다", async () => {
    const { router } = renderGate(VALID_TOKEN);

    await waitForPath(router, JOIN_PATH);
    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it.each([404, 500])(
    "미리보기가 %i면 초대 링크 오류 화면에 replace 이동한다",
    async (status) => {
      overridePreviewStatus(status);
      const { router } = renderGate(VALID_TOKEN);

      await waitForPath(router, PATH_ROUTE.JOIN_ERROR);
      await goBack(router);

      expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
    },
  );

  it("대소문자만 다른 토큰은 정규화하지 않고 그대로 보내 실패로 판정한다", async () => {
    overridePreviewByToken();
    const lowerCasedToken = VALID_TOKEN.toLowerCase();
    expect(lowerCasedToken).not.toBe(VALID_TOKEN);

    const { router } = renderGate(lowerCasedToken);

    await waitForPath(router, PATH_ROUTE.JOIN_ERROR);
  });

  it("판정 중 페이지를 벗어나면 이동을 실행하지 않는다", async () => {
    holdPreviewResponse(100);
    const { router } = renderGate(VALID_TOKEN);

    await act(async () => {
      await router.navigate(ELSEWHERE_PATH);
    });
    await waitRealTime(300);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });
});
