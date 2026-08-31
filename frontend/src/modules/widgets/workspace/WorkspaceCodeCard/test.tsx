import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import WorkspaceCodeCard from ".";

const ERROR_MESSAGE = "올바르지 않은 코드예요. 다시 확인해 주세요.";
const ELSEWHERE_PATH = "/elsewhere";
// TODO(#243): 미리보기 API 연결 후 msw 응답으로 교체
const VALID_CODE = "000000";
const INVALID_CODE = "X35D3@";

const renderCard = () => {
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
      <RouterProvider router={router} />
    </ThemeProvider>,
  );

  const input = screen.getByRole("textbox", { name: "참여 코드" });

  return { router, input };
};

const typeCode = (input: HTMLElement, value: string) => {
  fireEvent.change(input, { target: { value } });
};

const finishVerification = async () => {
  await act(async () => {
    vi.runAllTimers();
  });
};

describe("WorkspaceCodeCard", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("6자를 채우면 입력을 잠그고 스피너를 보여준다", () => {
    const { input } = renderCard();

    typeCode(input, VALID_CODE);

    expect(input).toHaveAttribute("readonly");
    expect(input).toHaveAttribute("aria-busy", "true");
    expect(
      input.parentElement?.querySelector('[aria-hidden="true"]'),
    ).toBeInTheDocument();
  });

  it("지연 뒤 유효한 코드면 임시 workspaceId로 입장 확인 화면에 이동한다", async () => {
    const { router, input } = renderCard();

    typeCode(input, VALID_CODE);
    await finishVerification();

    expect(router.state.location.pathname).toBe("/workspace/temp/join");
  });

  it("지연 뒤 유효하지 않은 코드면 에러 문구를 보여주고 입력 잠금을 푼다", async () => {
    const { router, input } = renderCard();

    typeCode(input, INVALID_CODE);
    await finishVerification();

    expect(screen.getByText(ERROR_MESSAGE)).toBeInTheDocument();
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).not.toHaveAttribute("readonly");
    expect(input).toHaveAttribute("aria-busy", "false");
    expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE_CODE);
  });

  it("에러 상태에서 값을 고치면 에러가 사라진다", async () => {
    const { input } = renderCard();

    typeCode(input, INVALID_CODE);
    await finishVerification();
    typeCode(input, "X35D3");

    expect(screen.queryByText(ERROR_MESSAGE)).not.toBeInTheDocument();
    expect(input).toHaveAttribute("aria-invalid", "false");
  });

  it("에러를 고쳐 다시 6자를 채우면 재검증한다", async () => {
    const { router, input } = renderCard();

    typeCode(input, INVALID_CODE);
    await finishVerification();
    typeCode(input, "X35D3");
    typeCode(input, VALID_CODE);
    await finishVerification();

    expect(router.state.location.pathname).toBe("/workspace/temp/join");
  });

  it("7자째 입력은 막힌다", () => {
    const { input } = renderCard();

    typeCode(input, "X35D3S7");

    expect(input).toHaveAttribute("maxlength", "6");
    expect(input).toHaveValue("X35D3S");
  });

  it("소문자를 입력하면 대문자로 표시한다", () => {
    const { input } = renderCard();

    typeCode(input, "x35d");

    expect(input).toHaveValue("X35D");
  });

  it("로딩 중 페이지를 벗어나면 이동을 실행하지 않는다", async () => {
    const { router, input } = renderCard();

    typeCode(input, VALID_CODE);
    await act(async () => {
      await router.navigate(ELSEWHERE_PATH);
    });
    await finishVerification();

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });
});
