import { DOCUMENT_MY_CONFIRMATION_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]/confirmations/me";
import { documentDetailsResponse } from "@api/mock/responses/document";
import { resetDocumentMockState } from "@api/mock/state/document";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";
import { userEvent, within } from "storybook/test";

import DocumentConfirmButton from ".";

const WORKSPACE_ID = 1;
const [documentResponse] = documentDetailsResponse;
const DOCUMENT_PATH = getRouterPath({
  routeKey: "DOCUMENT",
  params: {
    workspaceId: String(WORKSPACE_ID),
    documentId: String(documentResponse.id),
  },
});
const MY_CONFIRMATION_REQUEST = `*${DOCUMENT_MY_CONFIRMATION_API_PATH(WORKSPACE_ID, documentResponse.id)}`;

/**
 * 문서를 확인했다고 기록하는 「문서를 확인했어요」 버튼이에요. 문서 보기에서 본문 아래 가운데에 놓여요.
 * 아직 확인하지 않은 확인 대상에게만 보여요.
 *
 * **동작 규칙**
 * - 내 상태가 이미 확인했거나 확인 대상이 아니면 그려지지 않아요. 문서를 받기 전에도 그려지지 않아요.
 * - 누르면 응답이 올 때까지 버튼에 스피너가 돌고 다시 누를 수 없어요.
 * - 확인이 기록되면 버튼이 사라지고, 같은 화면의 확인 수가 하나 늘어요.
 *   녹음 직후 확인 화면처럼 「확인했어요」로 남지 않고 사라져요.
 * - 누른 뒤 확인 대상이 아니라는 응답을 받아도 문서를 다시 불러와 버튼이 사라져요.
 * - 그 밖의 이유로 실패하면 버튼이 남아 다시 누를 수 있어요.
 * - 로그인이 풀렸으면 로그인 화면으로 보내요.
 */
const meta = {
  title: "Document/DocumentConfirmButton",
  component: DocumentConfirmButton,
  parameters: {
    layout: "centered",
    design: {
      type: "figma",
      url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36058",
    },
  },
  // 확인을 누르면 mock에 남아 다시 열었을 때 버튼이 보이지 않으므로, 열 때마다 처음 상태로 되돌려요
  loaders: [
    () => {
      resetDocumentMockState();
    },
  ],
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
} satisfies Meta<typeof DocumentConfirmButton>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 아직 확인하지 않은 확인 대상에게만 보이는 「문서를 확인했어요」 버튼이에요. 누르면 버튼이 사라져요. */
export const Default: Story = {};

/** 「문서를 확인했어요」를 누르고 응답을 기다리는 동안이에요. 버튼에 스피너가 돌고 다시 누를 수 없어요. */
export const Confirming: Story = {
  parameters: {
    // 문서 페이지에서는 스토리를 한 화면에 함께 그려 응답이 섞이므로, 이 스토리는 따로 그려요
    docs: { story: { inline: false, iframeHeight: 120 } },
    msw: {
      handlers: {
        myConfirmation: http.put(MY_CONFIRMATION_REQUEST, async () => {
          // 응답을 끝내 보내지 않아, 누른 뒤 기다리는 모습으로 머물러요
          await delay("infinite");
          return new HttpResponse(null);
        }),
      },
    },
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);

    // 문서를 받기 전에는 버튼을 그리지 않아 버튼이 보일 때까지 기다려요
    await userEvent.click(
      await canvas.findByRole("button", { name: "문서를 확인했어요" }),
    );
  },
};
