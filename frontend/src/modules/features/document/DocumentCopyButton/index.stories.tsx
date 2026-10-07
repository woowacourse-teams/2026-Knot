import { documentDetailsResponse } from "@api/mock/responses/document";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { MemoryRouter, Route, Routes } from "react-router";
import { spyOn, userEvent, within } from "storybook/test";

import DocumentCopyButton from ".";

const WORKSPACE_ID = 1;
const [documentResponse] = documentDetailsResponse;
const DOCUMENT_PATH = getRouterPath({
  routeKey: "DOCUMENT",
  params: {
    workspaceId: String(WORKSPACE_ID),
    documentId: String(documentResponse.id),
  },
});

/** 복사 결과만 보이도록 클립보드를 대신해요. 스토리북 화면은 클립보드 쓰기 권한이 없을 수 있어서예요. */
const fakeClipboard = () => {
  const clipboardSpy = spyOn(
    navigator.clipboard,
    "writeText",
  ).mockResolvedValue();

  return () => clipboardSpy.mockRestore();
};

/**
 * 문서를 마크다운으로 클립보드에 복사하는 버튼이에요. 문서 보기의 오른쪽 위, 경로 아래 줄에 놓여요.
 *
 * **동작 규칙**
 * - 누르면 `# 제목`과 본문을 마크다운으로 복사해요. 본문에는 제목이 없어서 제목을 맨 앞에 붙여요.
 * - 복사하면 버튼이 3초 동안 「복사됨」으로 바뀌었다가 「복사」로 돌아와요. 토스트는 띄우지 않아요.
 *   클립보드에 쓰지 못하면 화면 변화 없이 넘어가요.
 * - 문서를 아직 받지 못했으면 아무것도 그리지 않아요.
 */
const meta = {
  title: "Document/DocumentCopyButton",
  component: DocumentCopyButton,
  parameters: {
    layout: "centered",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36058",
    },
  },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[DOCUMENT_PATH]}>
        <Routes>
          <Route path={PATH_ROUTE.DOCUMENT} element={<Story />} />
        </Routes>
      </MemoryRouter>
    ),
  ],
  args: { documentId: documentResponse.id },
} satisfies Meta<typeof DocumentCopyButton>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 문서 보기 오른쪽 위의 「복사」 버튼이에요. 누르면 `# 제목`과 본문을 마크다운으로 복사해요. */
export const Default: Story = {};

/** 「복사」를 눌러 문서를 복사한 직후예요. 3초 동안 「복사됨」으로 바뀌었다가 「복사」로 돌아와요. */
export const Copied: Story = {
  beforeEach: fakeClipboard,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // 문서를 받기 전에는 버튼을 그리지 않아 버튼이 보일 때까지 기다려요
    await userEvent.click(await canvas.findByRole("button", { name: "복사" }));
  },
};
