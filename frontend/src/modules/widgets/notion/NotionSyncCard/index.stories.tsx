import { NOTION_IMPORT_STATUS_API_PATH } from "@api/fetch/api/v1/imports/[importRunId]";
import { NOTION_IMPORTS_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/imports";
import { NOTION_CONNECTION_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionConnection";
import { notionConnectionResponse } from "@api/mock/responses/notionConnection";
import {
  notionImportStartResponse,
  notionImportStatusResponse,
} from "@api/mock/responses/notionImport";
import { PATH_ROUTE, getRouterPath } from "@routes/PATH_ROUTE";
import type { Meta, StoryObj } from "@storybook/react-webpack5";
import { delay, http, HttpResponse } from "msw";
import { MemoryRouter, Route, Routes } from "react-router";
import { userEvent, within } from "storybook/test";

import NotionSyncCard from ".";

const WORKSPACE_ID = 1;
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const CONNECTION_URL = `*${NOTION_CONNECTION_API_PATH(WORKSPACE_ID)}`;
const START_URL = `*${NOTION_IMPORTS_API_PATH(WORKSPACE_ID)}`;
const STATUS_URL = `*${NOTION_IMPORT_STATUS_API_PATH(notionImportStartResponse.id)}`;

/** 연결 상태 조회가 이 상태로 응답하게 해요. */
const respondConnectionStatus = (status: "NOT_CONNECTED" | "REAUTH_REQUIRED") =>
  http.get(CONNECTION_URL, () =>
    HttpResponse.json({ ...notionConnectionResponse, status }),
  );

/** 「지금 동기화」를 눌러요. */
const clickSync = async (canvasElement: HTMLElement) => {
  const canvas = within(canvasElement);

  await userEvent.click(
    await canvas.findByRole("button", { name: "지금 동기화" }),
  );
};

// 문서 페이지는 스토리를 한 화면에 함께 그리는데 msw는 화면에 하나뿐이라, 마지막 스토리의 응답이 모든 스토리에 적용돼요.
// 응답을 바꾸는 스토리만 따로 그려 각자의 응답을 받게 해요
const ISOLATED_DOCS = { story: { inline: false, iframeHeight: 240 } };

/**
 * 워크스페이스 홈의 Notion 동기화 카드예요. 노션 연결 상태를 알려 주고, 노션 문서를 knot로 다시 가져오게 해요.
 *
 * **동작 규칙**
 * - 노션 연결 상태(연결 안 됨·연결됨·다시 연결 필요)를 안내 문구로 보여 줘요. 상태를 받아 오기 전에는 안내를 비워 두고, 받아 오지 못하면 확인하지 못했다고 알려요.
 * - 다시 연결이 필요한 경우는 연결을 승인한 멤버가 더 이상 워크스페이스 소유자가 아닐 때예요. 소유자가 다시 연결해야 해요.
 * - 「지금 동기화」를 누르면 가져오기를 시작하고, 끝날 때까지 버튼이 로딩으로 잠겨요.
 * - 다 가져오면 새로 들어온 문서 수와 비활성 「완료」 버튼으로, 실패하면 실패 이유로 바뀌어요. 두 경우 모두 2초 뒤 연결 상태 안내로 돌아와요.
 * - 서버가 실패 이유를 주지 못하면(시작 요청 자체가 실패) 잠시 후 다시 시도하도록 안내해요.
 * - 「완료」는 누를 수 없지만 강조색 글자·아이콘으로 방금 끝난 일을 알려요.
 *
 * 결과 스토리는 버튼을 눌러 보여 주므로 2초 뒤 처음 상태로 돌아가요. 다시 보려면 「지금 동기화」를 눌러요.
 */
const meta = {
  title: "Notion/NotionSyncCard",
  component: NotionSyncCard,
  parameters: {
    layout: "centered",
    design: [
      {
        name: "Card/NotionImport status=기본",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10086",
      },
      {
        name: "홈 화면/노션 연동 완료",
        type: "figma",
        url: "https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10101",
      },
    ],
  },
  decorators: [
    (Story) => (
      <MemoryRouter initialEntries={[HOME_PATH]}>
        <Routes>
          <Route path={PATH_ROUTE.WORKSPACE_HOME} element={<Story />} />
        </Routes>
      </MemoryRouter>
    ),
  ],
} satisfies Meta<typeof NotionSyncCard>;

export default meta;

type Story = StoryObj<typeof meta>;

/** 노션이 연결된 워크스페이스의 홈에서 처음 보이는 모습이에요. */
export const Default: Story = {};

/** 노션을 연결한 적이 없거나 연결이 끊긴 워크스페이스예요. 예: 워크스페이스를 만들 때 연결을 건너뛴 경우 */
export const NotConnected: Story = {
  parameters: {
    docs: ISOLATED_DOCS,
    msw: {
      handlers: { connection: respondConnectionStatus("NOT_CONNECTED") },
    },
  },
};

/** 노션을 연결한 멤버가 소유자에서 내려와 소유자가 다시 연결해야 하는 워크스페이스예요. */
export const ReauthRequired: Story = {
  parameters: {
    docs: ISOLATED_DOCS,
    msw: {
      handlers: { connection: respondConnectionStatus("REAUTH_REQUIRED") },
    },
  },
};

/** 연결 상태를 받아 오지 못했을 때예요. */
export const ConnectionError: Story = {
  parameters: {
    docs: ISOLATED_DOCS,
    msw: {
      handlers: {
        connection: http.get(
          CONNECTION_URL,
          () => new HttpResponse(null, { status: 500 }),
        ),
      },
    },
  },
};

/** 「지금 동기화」를 눌러 노션 문서를 가져오는 중이에요. 버튼이 로딩으로 잠겨요. */
export const Syncing: Story = {
  parameters: {
    docs: ISOLATED_DOCS,
    msw: {
      handlers: {
        startImport: http.post(START_URL, async () => {
          await delay("infinite");
          return HttpResponse.json(notionImportStartResponse, { status: 202 });
        }),
      },
    },
  },
  play: async ({ canvasElement }) => {
    await clickSync(canvasElement);
  },
};

/** 동기화를 마쳤을 때예요. 새로 들어온 문서 수를 알려 주고 2초 뒤 돌아와요. */
export const Synced: Story = {
  play: async ({ canvasElement }) => {
    await clickSync(canvasElement);
  },
};

/** 가져오는 도중 실패했을 때예요. 서버가 준 실패 이유를 보여 주고 2초 뒤 돌아와요. */
export const SyncFailed: Story = {
  parameters: {
    docs: ISOLATED_DOCS,
    msw: {
      handlers: {
        importStatus: http.get(STATUS_URL, () =>
          HttpResponse.json({
            ...notionImportStatusResponse,
            status: "FAILED",
            failureReason: "Notion 문서를 가져오지 못했습니다",
          }),
        ),
      },
    },
  },
  play: async ({ canvasElement }) => {
    await clickSync(canvasElement);
  },
};

/** 가져오기를 시작조차 못 했을 때예요. 예: 서버 오류. 다시 시도하도록 안내하고 2초 뒤 돌아와요. */
export const SyncStartFailed: Story = {
  parameters: {
    docs: ISOLATED_DOCS,
    msw: {
      handlers: {
        startImport: http.post(
          START_URL,
          () => new HttpResponse(null, { status: 500 }),
        ),
      },
    },
  },
  play: async ({ canvasElement }) => {
    await clickSync(canvasElement);
  },
};
