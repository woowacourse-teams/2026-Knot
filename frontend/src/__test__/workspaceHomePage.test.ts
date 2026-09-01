import { GetMeResponseDto } from "@api/dto/auth";
import {
  GetWorkspaceResponseDto,
  GetWorkspacesResponseDto,
} from "@api/dto/workspace";
import { PostWorkspaceInvitationResponseDto } from "@api/dto/workspaceInvitation";
import { meResponse } from "@api/mock/responses/auth";
import {
  workspaceDetailResponse,
  workspacesResponse,
} from "@api/mock/responses/workspace";
import { workspaceInvitationResponse } from "@api/mock/responses/workspaceInvitation";
import { expect, test, type Page } from "@playwright/test";

/**
 * 워크스페이스 홈 화면(`/workspace/:workspaceId`) E2E.
 *
 * 홈 진입, 사이드바 열고 닫기, 폴더 트리 펼침·접힘, 초대 링크·코드 복사, Notion 동기화,
 * 하단 Dock으로 탐색 이동까지 홈 화면 위에서 사용자가 밟는 플로우를 페이지 단위로 확인해요.
 * 회원·워크스페이스·초대는 dev 서버의 msw mock 응답(`API_MOCKING`)에서 오므로 기대값도 같은 응답을
 * DTO로 변환해 가져와요. 동기화와 사이드바 트리는 API가 아직 없어 위젯의 임시 상수예요.
 */

const expectedMe = new GetMeResponseDto(meResponse);
const expectedWorkspace = new GetWorkspaceResponseDto(workspaceDetailResponse);
const expectedInvitation = new PostWorkspaceInvitationResponseDto(
  workspaceInvitationResponse,
);

// mock 워크스페이스 조회는 id와 무관하게 같은 응답이라 목록 첫 워크스페이스의 id로 들어가요
const WORKSPACE_ID = new GetWorkspacesResponseDto(workspacesResponse)
  .workspaces[0].id;
const HOME_PATH = `/workspace/${WORKSPACE_ID}`;
const CHAT_PATH = `${HOME_PATH}/chat`;

const GREETING = `반가워요, ${expectedMe.nickname} 님`;
const INVITE_CODE = expectedInvitation.code;
const DISPLAY_INVITE_LINK = `/invite/${expectedInvitation.linkToken}`;

// TODO(동기화 API Issue 미정): Notion 동기화 API 연결 후 응답으로 교체
const LAST_SYNCED_AT_LABEL = "어제 오후 3:12에 동기화";
const SYNCED_DOCUMENT_COUNT = 8;

const getSidebarToggle = (page: Page) =>
  page.getByRole("button", { name: "사이드바" });

const getSidebar = (page: Page) =>
  page.getByRole("complementary", { name: "워크스페이스 사이드바" });

interface GetFolderRowParams {
  page: Page;
  name: string;
}

/** 폴더 행의 접근성 이름은 `이름 + 문서 수`라 앞부분만 맞춰요. */
const getFolderRow = ({ page, name }: GetFolderRowParams) =>
  getSidebar(page).getByRole("button", { name: new RegExp(`^${name}`) });

const getDock = (page: Page) =>
  page.getByRole("navigation", { name: "주요 화면 이동" });

interface GetCardParams {
  page: Page;
  title: string;
}

/** 카드 `section`에는 landmark 이름이 없어 제목으로 카드를 찾아요. */
const getCard = ({ page, title }: GetCardParams) =>
  page
    .locator("section")
    .filter({ has: page.getByRole("heading", { name: title }) });

const readClipboard = (page: Page) =>
  page.evaluate(() => navigator.clipboard.readText());

// 클립보드에 쓴 값을 실제로 읽어 확인하기 위해 권한을 미리 허용해요
test.use({ permissions: ["clipboard-read", "clipboard-write"] });

test.beforeEach(async ({ page }) => {
  await page.goto(HOME_PATH);
});

test.describe("홈 진입", () => {
  test("인사말, 카드 2개, 하단 Dock을 보여주고 사이드바는 닫혀 있다", async ({
    page,
  }) => {
    await expect(page.getByRole("heading", { name: GREETING })).toBeVisible();

    const notionCard = getCard({ page, title: "Notion 동기화" });
    await expect(notionCard.getByText(LAST_SYNCED_AT_LABEL)).toBeVisible();
    await expect(
      notionCard.getByRole("button", { name: "지금 동기화" }),
    ).toBeEnabled();

    const inviteCard = getCard({ page, title: "팀원 초대" });
    await expect(
      inviteCard.getByRole("textbox", { name: "초대 링크" }),
    ).toHaveValue(DISPLAY_INVITE_LINK);
    await expect(
      inviteCard.getByRole("button", { name: "복사", exact: true }),
    ).toBeVisible();
    await expect(
      inviteCard.getByRole("button", { name: "초대 코드 복사" }),
    ).toBeVisible();

    const dock = getDock(page);
    await expect(dock.getByRole("button", { name: "홈" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    await expect(
      dock.getByRole("button", { name: /^탐색/ }),
    ).not.toHaveAttribute("aria-current");

    await expect(getSidebarToggle(page)).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    await expect(getSidebar(page)).toBeHidden();
  });

  test("홈에서 Dock의 홈을 눌러도 화면이 바뀌지 않는다", async ({ page }) => {
    await getDock(page).getByRole("button", { name: "홈" }).click();

    await expect(page).toHaveURL(HOME_PATH);
    await expect(page.getByRole("heading", { name: GREETING })).toBeVisible();
  });
});

test.describe("사이드바", () => {
  test("GNB 버튼으로 열면 워크스페이스 정보와 임시 트리를 보여주고, 다시 누르면 닫힌다", async ({
    page,
  }) => {
    const toggle = getSidebarToggle(page);
    const sidebar = getSidebar(page);

    await toggle.click();

    await expect(toggle).toHaveAttribute("aria-expanded", "true");
    await expect(sidebar).toBeVisible();
    await expect(sidebar.getByText(expectedWorkspace.name)).toBeVisible();
    await expect(sidebar.getByText("폴더", { exact: true })).toBeVisible();

    await expect(getFolderRow({ page, name: "제품" })).toContainText("24");
    await expect(getFolderRow({ page, name: "로드맵" })).toContainText("7");
    await expect(sidebar.getByText("2026 H2 로드맵")).toBeVisible();
    await expect(getFolderRow({ page, name: "스펙" })).toContainText("5");
    await expect(getFolderRow({ page, name: "리서치" })).toContainText("18");
    await expect(getFolderRow({ page, name: "회의록" })).toContainText("41");
    await expect(getFolderRow({ page, name: "초안" })).toContainText("6");

    await expect(sidebar.getByText("지금 동기화")).toBeVisible();
    await expect(sidebar.getByText("2분 전")).toBeVisible();

    await toggle.click();

    await expect(toggle).toHaveAttribute("aria-expanded", "false");
    await expect(sidebar).toBeHidden();
  });

  test("처음에는 제품과 로드맵만 펼쳐져 있고, 폴더를 누르면 접히고 다시 누르면 펼쳐진다", async ({
    page,
  }) => {
    await getSidebarToggle(page).click();

    const productRow = getFolderRow({ page, name: "제품" });
    const roadmapRow = getFolderRow({ page, name: "로드맵" });
    const roadmapFile = getSidebar(page).getByText("2026 H2 로드맵");

    await expect(productRow).toHaveAttribute("aria-expanded", "true");
    await expect(roadmapRow).toHaveAttribute("aria-expanded", "true");
    await expect(getFolderRow({ page, name: "스펙" })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    await expect(getFolderRow({ page, name: "리서치" })).toHaveAttribute(
      "aria-expanded",
      "false",
    );

    await productRow.click();

    await expect(productRow).toHaveAttribute("aria-expanded", "false");
    await expect(roadmapRow).toBeHidden();
    await expect(roadmapFile).toBeHidden();
    await expect(getFolderRow({ page, name: "스펙" })).toBeHidden();
    await expect(getFolderRow({ page, name: "리서치" })).toBeVisible();

    await productRow.click();

    await expect(productRow).toHaveAttribute("aria-expanded", "true");
    await expect(roadmapRow).toBeVisible();
    await expect(roadmapFile).toBeVisible();
  });

  test("중간 폴더를 접어도 형제 폴더는 그대로 보인다", async ({ page }) => {
    await getSidebarToggle(page).click();

    const roadmapRow = getFolderRow({ page, name: "로드맵" });

    await roadmapRow.click();

    await expect(roadmapRow).toHaveAttribute("aria-expanded", "false");
    await expect(getSidebar(page).getByText("2026 H2 로드맵")).toBeHidden();
    await expect(getFolderRow({ page, name: "스펙" })).toBeVisible();
  });

  test("사이드바를 닫았다 다시 열어도 접어 둔 폴더는 접힌 채로 남는다", async ({
    page,
  }) => {
    const toggle = getSidebarToggle(page);

    await toggle.click();
    await getFolderRow({ page, name: "제품" }).click();
    await toggle.click();
    await expect(getSidebar(page)).toBeHidden();

    await toggle.click();

    await expect(getFolderRow({ page, name: "제품" })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    await expect(getFolderRow({ page, name: "로드맵" })).toBeHidden();
  });
});

test.describe("팀원 초대 카드", () => {
  test("복사를 누르면 전체 초대 링크가 클립보드에 담기고 복사됨을 잠시 보여준 뒤 돌아온다", async ({
    page,
  }) => {
    const inviteCard = getCard({ page, title: "팀원 초대" });
    const copyLinkButton = inviteCard.getByRole("button", {
      name: "복사",
      exact: true,
    });

    await copyLinkButton.click();

    await expect(
      inviteCard.getByRole("button", { name: "복사됨", exact: true }),
    ).toBeVisible();
    await expect(copyLinkButton).toBeHidden();
    const origin = await page.evaluate(() => window.location.origin);
    expect(await readClipboard(page)).toBe(`${origin}${DISPLAY_INVITE_LINK}`);

    // 2초 뒤 원래 `복사`로 돌아와요
    await expect(copyLinkButton).toBeVisible();
    await expect(
      inviteCard.getByRole("button", { name: "복사됨", exact: true }),
    ).toBeHidden();
  });

  test("초대 코드 복사를 누르면 6자 코드가 클립보드에 담기고 글자가 복사됨으로 바뀌었다 돌아온다", async ({
    page,
  }) => {
    const inviteCard = getCard({ page, title: "팀원 초대" });
    const copyCodeButton = inviteCard.getByRole("button", {
      name: "초대 코드 복사",
    });

    await copyCodeButton.click();

    await expect(
      inviteCard.getByRole("button", { name: "복사됨", exact: true }),
    ).toBeVisible();
    // 링크 쪽 `복사` 버튼은 그대로예요
    await expect(
      inviteCard.getByRole("button", { name: "복사", exact: true }),
    ).toBeVisible();
    expect(await readClipboard(page)).toBe(INVITE_CODE);

    // 2초 뒤 원래 `초대 코드 복사`로 돌아와요
    await expect(copyCodeButton).toBeVisible();
  });
});

test.describe("Notion 동기화 카드", () => {
  test("지금 동기화를 누르면 로딩을 거쳐 새로 들어온 문서 수와 비활성 완료 버튼으로 바뀐다", async ({
    page,
  }) => {
    const notionCard = getCard({ page, title: "Notion 동기화" });
    const syncButton = notionCard.getByRole("button", { name: "지금 동기화" });

    await syncButton.click();

    await expect(syncButton).toHaveAttribute("aria-busy", "true");
    await expect(syncButton).toBeDisabled();

    await expect(
      notionCard.getByText(`문서 ${SYNCED_DOCUMENT_COUNT}개가 새로 들어왔어요`),
    ).toBeVisible();
    await expect(
      notionCard.getByRole("button", { name: "완료" }),
    ).toBeDisabled();
    await expect(syncButton).toBeHidden();
    await expect(notionCard.getByText(LAST_SYNCED_AT_LABEL)).toBeHidden();
  });
});

test.describe("하단 Dock", () => {
  test("탐색을 누르면 탐색 화면으로 이동하고, 뒤로 가기로 홈에 돌아온다", async ({
    page,
  }) => {
    await getDock(page).getByRole("button", { name: /^탐색/ }).click();

    await expect(page).toHaveURL(CHAT_PATH);
    await expect(
      page.getByRole("textbox", { name: "무엇이든 요청하세요" }),
    ).toBeVisible();

    await page.goBack();

    await expect(page).toHaveURL(HOME_PATH);
    await expect(page.getByRole("heading", { name: GREETING })).toBeVisible();
    await expect(
      getDock(page).getByRole("button", { name: "홈" }),
    ).toHaveAttribute("aria-current", "page");
  });

  test("열어 둔 사이드바는 탐색 화면으로 넘어가도 열린 채로 유지된다", async ({
    page,
  }) => {
    await getSidebarToggle(page).click();
    await expect(getSidebar(page)).toBeVisible();

    await getDock(page).getByRole("button", { name: /^탐색/ }).click();

    await expect(page).toHaveURL(CHAT_PATH);
    await expect(getSidebar(page)).toBeVisible();
    await expect(getSidebarToggle(page)).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    await expect(
      getSidebar(page).getByText(expectedWorkspace.name),
    ).toBeVisible();
  });
});
