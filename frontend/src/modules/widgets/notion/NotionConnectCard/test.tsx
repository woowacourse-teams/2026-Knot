import { PostNotionOAuthAuthorizationResponseDto } from "@api/dto/notionConnection";
import { NOTION_OAUTH_AUTHORIZATIONS_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionOauthAuthorizations";
import { notionOAuthAuthorizationResponse } from "@api/mock/responses/notionConnection";
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
import { afterEach, describe, expect, it, vi } from "vitest";

import NotionConnectCard from ".";

const expected = new PostNotionOAuthAuthorizationResponseDto(
  notionOAuthAuthorizationResponse,
);
const WORKSPACE_ID = 1;
const NOTION_CONNECTION_PATH = getRouterPath({
  routeKey: "WORKSPACE_NOTION_CONNECTION",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const ELSEWHERE_PATH = "/elsewhere";
const FORBIDDEN_ERROR_MESSAGE =
  "워크스페이스 소유자만 노션을 연결할 수 있어요.";
const UNKNOWN_ERROR_MESSAGE =
  "노션 연결을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.";

const renderCard = (search = "") => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const router = createMemoryRouter(
    [
      { path: ELSEWHERE_PATH, element: <p>다른 화면</p> },
      {
        path: PATH_ROUTE.WORKSPACE_NOTION_CONNECTION,
        element: <NotionConnectCard />,
      },
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>워크스페이스 홈</p> },
      { path: PATH_ROUTE.WORKSPACE, element: <p>워크스페이스 선택</p> },
      { path: PATH_ROUTE.LOGIN, element: <p>로그인</p> },
    ],
    { initialEntries: [ELSEWHERE_PATH, `${NOTION_CONNECTION_PATH}${search}`] },
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

const getConnectButton = () =>
  screen.getByRole("button", { name: "노션 연결하기" });
const getGoHomeButton = () =>
  screen.getByRole("button", { name: "워크스페이스로 이동" });
const getRetryButton = () => screen.getByRole("button", { name: "다시 시도" });

const overrideStartStatus = (status: number) => {
  mockServer.use(
    http.post(
      `*${NOTION_OAUTH_AUTHORIZATIONS_API_PATH(WORKSPACE_ID)}`,
      () => new HttpResponse(null, { status }),
    ),
  );
};

const holdStartResponse = () => {
  mockServer.use(
    http.post(
      `*${NOTION_OAUTH_AUTHORIZATIONS_API_PATH(WORKSPACE_ID)}`,
      async () => {
        await delay("infinite");
        return HttpResponse.json(notionOAuthAuthorizationResponse, {
          status: 201,
        });
      },
    ),
  );
};

const goBack = async (router: ReturnType<typeof createMemoryRouter>) => {
  await act(async () => {
    await router.navigate(-1);
  });
};

describe("NotionConnectCard", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("제목·안내 문구와 연결·이동 버튼을 보여준다", () => {
    renderCard();

    expect(
      screen.getByRole("heading", { name: "노션 기록 이어가기" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/노션에 쌓아둔 기록들을.*knot로 옮겨올 수 있어요\./),
    ).toBeInTheDocument();
    expect(getConnectButton()).toBeEnabled();
    expect(getGoHomeButton()).toBeEnabled();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("노션 연결하기를 누르면 요청 중 버튼이 로딩 상태로 잠긴다", async () => {
    holdStartResponse();
    renderCard();

    fireEvent.click(getConnectButton());

    await waitFor(() => {
      expect(getConnectButton()).toHaveAttribute("aria-busy", "true");
    });
    expect(getConnectButton()).toBeDisabled();
  });

  it("연결 시작에 성공하면 응답의 authorizationUrl로 페이지를 이동시키고 버튼은 로딩을 유지한다", async () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, assign });
    renderCard();

    fireEvent.click(getConnectButton());

    await waitFor(() => {
      expect(assign).toHaveBeenCalledWith(expected.authorizationUrl);
    });
    expect(assign).toHaveBeenCalledTimes(1);
    expect(getConnectButton()).toHaveAttribute("aria-busy", "true");
  });

  it("워크스페이스로 이동을 누르면 워크스페이스 홈으로 이동한다", () => {
    const { router } = renderCard();

    fireEvent.click(getGoHomeButton());

    expect(router.state.location.pathname).toBe(HOME_PATH);
  });

  it("401이면 로그인 화면으로 replace 이동한다", async () => {
    overrideStartStatus(401);
    const { router } = renderCard();

    fireEvent.click(getConnectButton());

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.LOGIN);
    });

    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it("404면 워크스페이스 선택 화면으로 replace 이동한다", async () => {
    overrideStartStatus(404);
    const { router } = renderCard();

    fireEvent.click(getConnectButton());

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE);
    });

    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it("403이면 소유자만 연결할 수 있다는 문구를 보여주고 이동하지 않는다", async () => {
    overrideStartStatus(403);
    const { router } = renderCard();

    fireEvent.click(getConnectButton());

    expect(await screen.findByRole("alert")).toHaveTextContent(
      FORBIDDEN_ERROR_MESSAGE,
    );
    expect(getConnectButton()).toBeEnabled();
    expect(router.state.location.pathname).toBe(NOTION_CONNECTION_PATH);
  });

  it("그 외 실패면 잠시 후 다시 시도 문구를 보여준다", async () => {
    overrideStartStatus(500);
    renderCard();

    fireEvent.click(getConnectButton());

    expect(await screen.findByRole("alert")).toHaveTextContent(
      UNKNOWN_ERROR_MESSAGE,
    );
  });

  it("?result=connected로 돌아오면 워크스페이스 홈으로 replace 이동한다", async () => {
    const { router } = renderCard("?result=connected");

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(HOME_PATH);
    });

    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it("?result=failed로 돌아오면 실패 화면을 보여주고 하단에 워크스페이스로 이동 버튼을 둔다", () => {
    const { router } = renderCard("?result=failed");

    expect(
      screen.getByRole("heading", { name: "노션 연결에 실패했어요" }),
    ).toBeInTheDocument();
    expect(getRetryButton()).toBeEnabled();
    expect(getGoHomeButton()).toBeEnabled();
    expect(
      screen.queryByRole("button", { name: "노션 연결하기" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(router.state.location.pathname).toBe(NOTION_CONNECTION_PATH);
  });

  it("실패 화면에서 다시 시도를 누르면 연결 시작 응답의 authorizationUrl로 페이지를 이동시킨다", async () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, assign });
    renderCard("?result=failed");

    fireEvent.click(getRetryButton());

    await waitFor(() => {
      expect(assign).toHaveBeenCalledWith(expected.authorizationUrl);
    });
    expect(assign).toHaveBeenCalledTimes(1);
  });

  it("실패 화면에서 다시 시도가 403이면 소유자 안내 문구를 보여준다", async () => {
    overrideStartStatus(403);
    renderCard("?result=failed");

    fireEvent.click(getRetryButton());

    expect(await screen.findByRole("alert")).toHaveTextContent(
      FORBIDDEN_ERROR_MESSAGE,
    );
  });

  it("실패 화면에서 워크스페이스로 이동을 누르면 워크스페이스 홈으로 이동한다", () => {
    const { router } = renderCard("?result=failed");

    fireEvent.click(getGoHomeButton());

    expect(router.state.location.pathname).toBe(HOME_PATH);
  });
});
