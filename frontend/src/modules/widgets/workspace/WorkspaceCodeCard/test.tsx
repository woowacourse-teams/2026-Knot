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
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import WorkspaceCodeCard from ".";

const expected = new GetInvitationPreviewResponseDto(invitationPreviewResponse);
const VALID_CODE = new PostWorkspaceInvitationResponseDto(
  workspaceInvitationResponse,
).code;
const INVALID_CODE = "X35D3@";
const JOIN_PATH = `/workspace/${expected.workspaceId}/join`;
const ELSEWHERE_PATH = "/elsewhere";
const SUCCESS_MESSAGE = "확인됐어요. 곧 다음 단계로 이동해요.";
const NOT_FOUND_MESSAGE = "올바르지 않은 코드예요. 다시 확인해 주세요.";
const TOO_MANY_REQUESTS_MESSAGE =
  "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.";
const UNKNOWN_MESSAGE = "코드를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.";
const SUCCESS_DISPLAY_MS = 1500;
// 경로 파라미터가 있어 fetch 상수 대신 mock 핸들러와 같은 패턴을 적어요
const PREVIEW_PATH_PATTERN = "*/api/v1/invitations/:tokenOrCode";

const renderCard = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.WORKSPACE_CODE, element: <WorkspaceCodeCard /> },
      { path: PATH_ROUTE.WORKSPACE_JOIN, element: <p>입장 확인</p> },
      { path: ELSEWHERE_PATH, element: <p>다른 화면</p> },
    ],
    { initialEntries: [PATH_ROUTE.WORKSPACE_CODE] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  const input = screen.getByRole("textbox", { name: "참여 코드" });

  return { router, input };
};

const typeCode = (input: HTMLElement, value: string) => {
  fireEvent.change(input, { target: { value } });
};

const overridePreviewStatus = (status: number) => {
  mockServer.use(
    http.get(PREVIEW_PATH_PATTERN, () => new HttpResponse(null, { status })),
  );
};

/** 유효한 코드만 통과시켜 에러를 고친 뒤 다시 채웠을 때의 재검증을 확인해요 */
const overridePreviewByCode = () => {
  mockServer.use(
    http.get(PREVIEW_PATH_PATTERN, ({ params }) =>
      params.tokenOrCode === VALID_CODE
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

const waitForSuccess = () => screen.findByText(SUCCESS_MESSAGE);

/**
 * 가짜 타이머를 비동기로 진행해요. 응답 전달(msw)과 쿼리 알림(`setTimeout(0)`)이 그 사이 실제 이벤트 루프로 흘러요.
 * `findBy`·`waitFor`는 가짜 `setInterval`을 기다리다 멈추므로 가짜 타이머 아래에서는 쓰지 않아요.
 */
const advanceTimers = async (ms: number) => {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
};

/** 시계를 움직이지 않고 응답이 화면에 반영될 때까지 이벤트 루프만 돌려요 */
const flushUntilSuccess = async () => {
  for (let round = 0; round < 100; round += 1) {
    if (screen.queryByText(SUCCESS_MESSAGE) !== null) return;
    await advanceTimers(0);
  }

  throw new Error("성공 메시지가 나타나지 않았어요");
};

const waitRealTime = async (ms: number) => {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, ms));
  });
};

const navigateAway = async (router: ReturnType<typeof createMemoryRouter>) => {
  await act(async () => {
    await router.navigate(ELSEWHERE_PATH);
  });
};

describe("WorkspaceCodeCard", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("6자를 채우면 미리보기를 조회하는 동안 입력을 잠그고 스피너를 보여준다", async () => {
    holdPreviewResponse("infinite");
    const { input } = renderCard();

    typeCode(input, VALID_CODE);

    expect(await screen.findByRole("textbox", { busy: true })).toBe(input);
    expect(input).toHaveAttribute("readonly");
    expect(
      input.parentElement?.querySelector('[aria-hidden="true"]'),
    ).toBeInTheDocument();
  });

  it("미리보기에 성공하면 성공 메시지와 체크 표시를 보여주고 입력은 잠근 채 둔다", async () => {
    const { router, input } = renderCard();

    typeCode(input, VALID_CODE);

    expect(await waitForSuccess()).toBeInTheDocument();
    expect(input.parentElement?.querySelector("svg")).toBeInTheDocument();
    expect(input).toHaveAttribute("readonly");
    expect(input).toHaveAttribute("aria-busy", "false");
    expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE_CODE);
  });

  it("성공 메시지를 1.5초 동안 보여준 뒤 응답의 workspaceId로 입장 확인 화면에 이동하며 코드와 이름을 state로 넘긴다", async () => {
    vi.useFakeTimers();
    const { router, input } = renderCard();

    typeCode(input, VALID_CODE);
    await flushUntilSuccess();
    await advanceTimers(SUCCESS_DISPLAY_MS - 1);

    expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE_CODE);

    await advanceTimers(1);

    expect(router.state.location.pathname).toBe(JOIN_PATH);
    expect(router.state.location.state).toEqual({
      credential: VALID_CODE,
      workspaceName: expected.workspaceName,
    });
  });

  it("404면 올바르지 않은 코드 문구를 보여주고 입력 잠금을 푼다", async () => {
    overridePreviewStatus(404);
    const { router, input } = renderCard();

    typeCode(input, INVALID_CODE);

    expect(await screen.findByText(NOT_FOUND_MESSAGE)).toBeInTheDocument();
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).not.toHaveAttribute("readonly");
    expect(input).toHaveAttribute("aria-busy", "false");
    expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE_CODE);
  });

  it("429면 요청이 너무 많다는 문구를 보여준다", async () => {
    overridePreviewStatus(429);
    const { input } = renderCard();

    typeCode(input, VALID_CODE);

    expect(
      await screen.findByText(TOO_MANY_REQUESTS_MESSAGE),
    ).toBeInTheDocument();
    expect(input).not.toHaveAttribute("readonly");
  });

  it("그 외 실패면 확인하지 못했다는 문구를 보여준다", async () => {
    overridePreviewStatus(500);
    const { input } = renderCard();

    typeCode(input, VALID_CODE);

    expect(await screen.findByText(UNKNOWN_MESSAGE)).toBeInTheDocument();
    expect(input).not.toHaveAttribute("readonly");
  });

  it("에러 상태에서 값을 고치면 에러가 사라진다", async () => {
    overridePreviewStatus(404);
    const { input } = renderCard();

    typeCode(input, INVALID_CODE);
    await screen.findByText(NOT_FOUND_MESSAGE);
    typeCode(input, "X35D3");

    expect(screen.queryByText(NOT_FOUND_MESSAGE)).not.toBeInTheDocument();
    expect(input).toHaveAttribute("aria-invalid", "false");
  });

  it("에러를 고쳐 다시 6자를 채우면 재검증한다", async () => {
    overridePreviewByCode();
    const { input } = renderCard();

    typeCode(input, INVALID_CODE);
    await screen.findByText(NOT_FOUND_MESSAGE);
    typeCode(input, "X35D3");
    typeCode(input, VALID_CODE);

    expect(await waitForSuccess()).toBeInTheDocument();
    expect(screen.queryByText(NOT_FOUND_MESSAGE)).not.toBeInTheDocument();
  });

  it("7자째 입력은 막힌다", () => {
    holdPreviewResponse("infinite");
    const { input } = renderCard();

    typeCode(input, `${VALID_CODE}7`);

    expect(input).toHaveAttribute("maxlength", "6");
    expect(input).toHaveValue(VALID_CODE);
  });

  it("소문자를 입력하면 대문자로 표시한다", () => {
    const { input } = renderCard();

    typeCode(input, "x35d");

    expect(input).toHaveValue("X35D");
  });

  it("성공 메시지를 보여주는 동안 페이지를 벗어나면 이동을 실행하지 않는다", async () => {
    vi.useFakeTimers();
    const { router, input } = renderCard();

    typeCode(input, VALID_CODE);
    await flushUntilSuccess();
    await navigateAway(router);
    await advanceTimers(SUCCESS_DISPLAY_MS);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it("조회 중 페이지를 벗어나면 이동을 실행하지 않는다", async () => {
    holdPreviewResponse(100);
    const { router, input } = renderCard();

    typeCode(input, VALID_CODE);
    await navigateAway(router);
    await waitRealTime(300);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });
});
