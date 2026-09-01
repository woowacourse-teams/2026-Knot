import { PostWorkspaceInvitationResponseDto } from "@api/dto/workspaceInvitation";
import { WORKSPACE_INVITATIONS_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations";
import { workspaceInvitationResponse } from "@api/mock/responses/workspaceInvitation";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import WorkspaceInviteCard from ".";

const expected = new PostWorkspaceInvitationResponseDto(
  workspaceInvitationResponse,
);
const WORKSPACE_ID = 1;
const NOTION_CONNECTION_PATH = getRouterPath({
  routeKey: "WORKSPACE_NOTION_CONNECTION",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const DISPLAY_INVITE_LINK = `/invite/${expected.linkToken}`;
const ELSEWHERE_PATH = "/elsewhere";
const COPIED_DURATION_MS = 2000;

const writeText = vi.fn<(text: string) => Promise<void>>();

const renderCard = (workspaceId = String(WORKSPACE_ID)) => {
  const invitePath = getRouterPath({
    routeKey: "WORKSPACE_INVITE",
    params: { workspaceId },
  });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: ELSEWHERE_PATH, element: <p>다른 화면</p> },
      { path: PATH_ROUTE.WORKSPACE_INVITE, element: <WorkspaceInviteCard /> },
      {
        path: PATH_ROUTE.WORKSPACE_NOTION_CONNECTION,
        element: <p>노션 연동</p>,
      },
      { path: PATH_ROUTE.WORKSPACE, element: <p>워크스페이스 선택</p> },
      { path: PATH_ROUTE.LOGIN, element: <p>로그인</p> },
    ],
    { initialEntries: [ELSEWHERE_PATH, invitePath] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  return { router };
};

const getCodeButton = () => screen.getByRole("button", { name: /^참여 코드/ });
const getCopyLinkButton = () => screen.getByRole("button", { name: "복사" });
const getLinkInput = () => screen.getByRole("textbox", { name: "초대 링크" });

const waitForInvitation = () => screen.findByText(expected.code);

const overrideInvitationStatus = (status: number) => {
  mockServer.use(
    http.post(
      `*${WORKSPACE_INVITATIONS_API_PATH(WORKSPACE_ID)}`,
      () => new HttpResponse(null, { status }),
    ),
  );
};

const holdInvitationResponse = () => {
  mockServer.use(
    http.post(`*${WORKSPACE_INVITATIONS_API_PATH(WORKSPACE_ID)}`, async () => {
      await delay("infinite");
      return HttpResponse.json(workspaceInvitationResponse);
    }),
  );
};

const click = async (element: HTMLElement) => {
  await act(async () => {
    fireEvent.click(element);
  });
};

const advanceTimers = async (ms: number) => {
  await act(async () => {
    vi.advanceTimersByTime(ms);
  });
};

const goBack = async (router: ReturnType<typeof createMemoryRouter>) => {
  await act(async () => {
    await router.navigate(-1);
  });
};

describe("WorkspaceInviteCard", () => {
  beforeEach(() => {
    writeText.mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText },
      configurable: true,
    });
    vi.spyOn(window, "alert").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    writeText.mockReset();
    Reflect.deleteProperty(navigator, "clipboard");
  });

  it("초대를 불러오는 동안에는 코드와 링크 복사를 막는다", () => {
    holdInvitationResponse();
    renderCard();

    expect(getCodeButton()).toBeDisabled();
    expect(getCodeButton()).toHaveAttribute("aria-busy", "true");
    expect(getCopyLinkButton()).toBeDisabled();
    expect(getCopyLinkButton()).toHaveAttribute("aria-busy", "true");
    expect(getLinkInput()).toHaveValue("");
  });

  it("workspaceId가 정수가 아니면 초대를 요청하지 않고 복사를 막는다", async () => {
    const requestedUrls: string[] = [];
    const listener = ({ request }: { request: Request }) => {
      requestedUrls.push(request.url);
    };
    mockServer.events.on("request:start", listener);

    renderCard("abc");
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
    mockServer.events.removeListener("request:start", listener);

    expect(requestedUrls).not.toContainEqual(
      expect.stringContaining("/invitations"),
    );
    expect(getCodeButton()).toBeDisabled();
    expect(getCopyLinkButton()).toBeDisabled();
  });

  it("초대 응답의 참여 코드와 초대 링크를 보여준다", async () => {
    renderCard();

    expect(await waitForInvitation()).toBeInTheDocument();
    expect(getCodeButton()).toBeEnabled();
    expect(getLinkInput()).toHaveValue(DISPLAY_INVITE_LINK);
    expect(getLinkInput()).toHaveAttribute("readonly");
    expect(getCopyLinkButton()).toBeEnabled();
  });

  it("참여 코드를 누르면 응답의 코드를 복사한다", async () => {
    renderCard();
    await waitForInvitation();

    await click(getCodeButton());

    expect(writeText).toHaveBeenCalledWith(expected.code);
  });

  it("복사를 누르면 전체 링크를 복사하고 2초 동안 복사됨을 보여준다", async () => {
    renderCard();
    await waitForInvitation();
    vi.useFakeTimers();

    await click(getCopyLinkButton());

    expect(writeText).toHaveBeenCalledWith(
      `${window.location.origin}${DISPLAY_INVITE_LINK}`,
    );
    expect(screen.getByRole("button", { name: "복사됨" })).toBeInTheDocument();

    await advanceTimers(COPIED_DURATION_MS - 1);

    expect(screen.getByRole("button", { name: "복사됨" })).toBeInTheDocument();

    await advanceTimers(1);

    expect(getCopyLinkButton()).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "복사됨" }),
    ).not.toBeInTheDocument();
  });

  it("다음을 누르면 노션 연동 화면으로 이동한다", async () => {
    const { router } = renderCard();
    await waitForInvitation();

    await click(screen.getByRole("button", { name: "다음" }));

    expect(router.state.location.pathname).toBe(NOTION_CONNECTION_PATH);
  });

  it("초대 조회가 401이면 로그인 화면으로 replace 이동한다", async () => {
    overrideInvitationStatus(401);
    const { router } = renderCard();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.LOGIN);
    });

    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it.each([403, 404])(
    "초대 조회가 %i면 워크스페이스 선택 화면으로 replace 이동한다",
    async (status) => {
      overrideInvitationStatus(status);
      const { router } = renderCard();

      await waitFor(() => {
        expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE);
      });

      await goBack(router);

      expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
    },
  );
});
