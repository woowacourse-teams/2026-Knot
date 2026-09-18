import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceJoinErrorNotice from ".";

const TITLE = "초대장을 열 수 없어요";
const DESCRIPTION_LINES = [
  "만료되었거나 잘못된 링크예요.",
  "초대한 분께 다시 요청해 보세요.",
];

const renderNotice = () => {
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.JOIN_ERROR, element: <WorkspaceJoinErrorNotice /> },
      { path: PATH_ROUTE.WORKSPACE_CODE, element: <p>초대 코드 입력</p> },
    ],
    { initialEntries: [PATH_ROUTE.JOIN_ERROR] },
  );

  render(
    <ThemeProvider theme={theme}>
      <RouterProvider router={router} />
    </ThemeProvider>,
  );

  return { router };
};

describe("WorkspaceJoinErrorNotice", () => {
  it("제목과 안내 문구 두 줄을 보여준다", () => {
    renderNotice();

    expect(screen.getByRole("heading", { name: TITLE })).toBeInTheDocument();
    DESCRIPTION_LINES.forEach((line) => {
      expect(screen.getByText(line)).toBeInTheDocument();
    });
  });

  it("초대 코드 직접 입력하기를 누르면 초대 코드 입력 화면으로 이동한다", () => {
    const { router } = renderNotice();

    fireEvent.click(
      screen.getByRole("button", { name: "초대 코드 직접 입력하기" }),
    );

    expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE_CODE);
  });
});
