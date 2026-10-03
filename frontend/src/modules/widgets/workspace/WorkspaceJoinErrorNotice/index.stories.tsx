import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter, Route, Routes } from "react-router";

import WorkspaceJoinErrorNotice from ".";

/**
 * 만료되었거나 잘못된 초대 링크로 들어온 사용자에게 이유를 알리는 안내예요. 초대 링크 오류 화면(`/join-error`)에 놓여요.
 *
 * **동작 규칙**
 * - 카드 없이 그림·제목·설명만 보여 줘요.
 * - 「초대 코드 직접 입력하기」를 누르면 초대 코드 입력 화면(`/workspace/code`)으로 이어져요.
 * - 로고와 화면 가운데 배치는 화면 레이아웃이 맡아요. 이 안내는 로고 아래 내용만 그려요.
 *
 * **디자인 원본**: [올바르지 않은 초대 링크 접근](https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10148)
 */
const meta = {
  title: "Workspace/WorkspaceJoinErrorNotice",
  component: WorkspaceJoinErrorNotice,
  parameters: { layout: "centered" },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[PATH_ROUTE.JOIN_ERROR]}>
        <Routes>
          <Route path={PATH_ROUTE.JOIN_ERROR} element={<Story />} />
          <Route
            path="*"
            element={<p>초대 코드 입력 화면으로 이동했어요.</p>}
          />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof WorkspaceJoinErrorNotice>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 만료되었거나 잘못된 초대 링크를 열었을 때예요. */
export const Default: Story = {};
