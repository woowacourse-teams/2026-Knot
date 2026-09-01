import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceJoinCard from ".";

const WORKSPACE_ID = "ws-1";
// TODO(#243): 미리보기 API 연결 후 msw 응답으로 교체
const TEMP_WORKSPACE_NAME = "노티드의 워크스페이스";
const DESCRIPTION = "초대를 수락하면 함께 기록할 수 있어요";

const renderCard = () => {
  const joinPath = getRouterPath({
    routeKey: "WORKSPACE_JOIN",
    params: { workspaceId: WORKSPACE_ID },
  });
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.WORKSPACE_JOIN, element: <WorkspaceJoinCard /> },
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>워크스페이스 홈</p> },
    ],
    { initialEntries: [joinPath] },
  );

  render(
    <ThemeProvider theme={theme}>
      <RouterProvider router={router} />
    </ThemeProvider>,
  );

  return { router };
};

describe("WorkspaceJoinCard", () => {
  it("임시 워크스페이스 이름과 안내 문구를 보여준다", () => {
    renderCard();

    expect(
      screen.getByRole("heading", { name: TEMP_WORKSPACE_NAME }),
    ).toBeInTheDocument();
    expect(screen.getByText(DESCRIPTION)).toBeInTheDocument();
  });

  it("참여할게요를 누르면 현재 workspaceId의 워크스페이스 홈으로 이동한다", () => {
    const { router } = renderCard();

    fireEvent.click(screen.getByRole("button", { name: "참여할게요" }));

    expect(router.state.location.pathname).toBe(`/workspace/${WORKSPACE_ID}`);
  });
});
