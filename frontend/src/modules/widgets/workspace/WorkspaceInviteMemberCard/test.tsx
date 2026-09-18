import { PostWorkspaceInvitationResponseDto } from "@api/dto/workspaceInvitation";
import { WORKSPACE_INVITATIONS_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations";
import { workspaceInvitationResponse } from "@api/mock/responses/workspaceInvitation";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import WorkspaceInviteMemberCard from ".";

const expected = new PostWorkspaceInvitationResponseDto(
  workspaceInvitationResponse,
);
const WORKSPACE_ID = 1;
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const DISPLAY_INVITE_LINK = `/invite/${expected.linkToken}`;
const COPIED_DURATION_MS = 2000;

const writeText = vi.fn<(text: string) => Promise<void>>();

const renderCard = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      {
        path: PATH_ROUTE.WORKSPACE_HOME,
        element: <WorkspaceInviteMemberCard />,
      },
    ],
    { initialEntries: [HOME_PATH] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  const linkInput = screen.getByRole("textbox", { name: "초대 링크" });
  const copyLinkButton = screen.getByRole("button", { name: "복사" });
  const copyCodeButton = screen.getByRole("button", { name: "초대 코드 복사" });

  return { linkInput, copyLinkButton, copyCodeButton };
};

const waitForInvitation = () => screen.findByDisplayValue(DISPLAY_INVITE_LINK);

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

describe("WorkspaceInviteMemberCard", () => {
  beforeEach(() => {
    writeText.mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText },
      configurable: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    writeText.mockReset();
    Reflect.deleteProperty(navigator, "clipboard");
  });

  it("초대를 불러오는 동안에는 링크와 코드 복사를 막는다", () => {
    holdInvitationResponse();
    const { linkInput, copyLinkButton, copyCodeButton } = renderCard();

    expect(linkInput).toHaveValue("");
    expect(copyLinkButton).toBeDisabled();
    expect(copyLinkButton).toHaveAttribute("aria-busy", "true");
    expect(copyCodeButton).toBeDisabled();
  });

  it("초대 응답의 링크를 읽기 전용으로 보여준다", async () => {
    const { linkInput, copyLinkButton, copyCodeButton } = renderCard();

    expect(await waitForInvitation()).toBeInTheDocument();
    expect(linkInput).toHaveAttribute("readonly");
    expect(copyLinkButton).toBeEnabled();
    expect(copyCodeButton).toBeEnabled();
  });

  it("복사를 누르면 전체 링크를 복사하고 2초 동안 복사됨을 보여준다", async () => {
    const { copyLinkButton } = renderCard();
    await waitForInvitation();
    vi.useFakeTimers();

    await click(copyLinkButton);

    expect(writeText).toHaveBeenCalledWith(
      `${window.location.origin}${DISPLAY_INVITE_LINK}`,
    );
    expect(screen.getByRole("button", { name: "복사됨" })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "복사" }),
    ).not.toBeInTheDocument();

    await advanceTimers(COPIED_DURATION_MS - 1);

    expect(screen.getByRole("button", { name: "복사됨" })).toBeInTheDocument();

    await advanceTimers(1);

    expect(screen.getByRole("button", { name: "복사" })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "복사됨" }),
    ).not.toBeInTheDocument();
  });

  it("초대 코드 복사를 누르면 응답의 코드를 복사하고 2초 동안 복사됨으로 바꾼다", async () => {
    const { copyCodeButton } = renderCard();
    await waitForInvitation();
    vi.useFakeTimers();

    await click(copyCodeButton);

    expect(writeText).toHaveBeenCalledWith(expected.code);
    expect(copyCodeButton).toHaveTextContent("복사됨");
    expect(screen.getByRole("button", { name: "복사" })).toBeInTheDocument();

    await advanceTimers(COPIED_DURATION_MS);

    expect(copyCodeButton).toHaveTextContent("초대 코드 복사");
  });

  it("클립보드에 쓰지 못하면 화면이 바뀌지 않는다", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    writeText.mockRejectedValue(new Error("denied"));
    const { copyLinkButton, copyCodeButton } = renderCard();
    await waitForInvitation();

    await click(copyLinkButton);
    await click(copyCodeButton);

    expect(copyLinkButton).toHaveTextContent("복사");
    expect(copyLinkButton).not.toHaveTextContent("복사됨");
    expect(copyCodeButton).toHaveTextContent("초대 코드 복사");
  });
});
