import { GetWorkspacesResponseDto } from "@api/dto/workspace";
import { workspacesResponse } from "@api/mock/responses/workspace";
import { expect, test, type Page } from "@playwright/test";

import type { AgentBridgeStatus } from "@/shared/types/desktop";

/**
 * CLI 에이전트 연결 화면(`/agent-connection`) E2E.
 *
 * 이 화면은 데스크톱 셸이 preload로 넣는 `window.knotDesktop.agent`가 있어야 뜻이 있어요.
 * 브라우저 E2E에서는 셸이 없으므로 `addInitScript`로 같은 자리에 스텁을 심어 셸 안에서
 * 보이는 화면을 재현하고, 스텁이 없는 경우에는 데스크톱 안내만 보이는지 확인해요.
 * 회원·워크스페이스는 dev 서버의 msw mock 응답(`API_MOCKING`)에서 와요.
 */

const AGENT_CONNECTION_PATH = "/agent-connection";
const WORKSPACE_ID = new GetWorkspacesResponseDto(workspacesResponse)
  .workspaces[0].id;
const HOME_PATH = `/workspace/${WORKSPACE_ID}`;

const RUNNING_STATUS: AgentBridgeStatus = {
  running: true,
  port: 47871,
  url: "http://127.0.0.1:47871/mcp",
  error: null,
  tokenIssuedAt: "2026-09-09T00:41:00+09:00",
  lastToolCallAt: null,
  skillPath: "/Applications/Knot.app/Contents/Resources/skills/knot/SKILL.md",
};

interface AgentCallRecord {
  method: string;
  argument?: string | number;
}

/** 셸의 preload가 페이지 스크립트보다 먼저 `window.knotDesktop`을 넣는 것을 재현해요 */
const installDesktopStub = (page: Page, initialStatus: AgentBridgeStatus) =>
  page.addInitScript((status) => {
    let currentStatus = status;
    const calls: AgentCallRecord[] = [];

    Object.assign(window, { __knotAgentCalls: calls });
    window.knotDesktop = {
      version: "0.1.0",
      platform: "darwin",
      env: "local",
      openExternal: () => Promise.resolve(),
      onDeepLink: () => () => undefined,
      getPendingDeepLink: () => Promise.resolve(null),
      agent: {
        getStatus: () => Promise.resolve(currentStatus),
        copyRegistration: (target) => {
          calls.push({ method: "copyRegistration", argument: target });
          return Promise.resolve({ preview: `${target} <연결 토큰>` });
        },
        rotateToken: () => {
          calls.push({ method: "rotateToken" });
          currentStatus = {
            ...currentStatus,
            tokenIssuedAt: "2026-09-09T01:00:00+09:00",
          };
          return Promise.resolve();
        },
        setPort: (port) => {
          calls.push({ method: "setPort", argument: port });
          currentStatus = {
            ...currentStatus,
            port,
            url: `http://127.0.0.1:${port}/mcp`,
          };
          return Promise.resolve();
        },
      },
    };
  }, initialStatus);

const readAgentCalls = (page: Page) =>
  page.evaluate(
    () =>
      (window as unknown as { __knotAgentCalls: AgentCallRecord[] })
        .__knotAgentCalls,
  );

const getRegistrationGroup = (page: Page, title: string) =>
  page.getByRole("group", { name: title, exact: true });

test.describe("데스크톱 셸 안에서", () => {
  test.beforeEach(async ({ page }) => {
    await installDesktopStub(page, RUNNING_STATUS);
    await page.goto(AGENT_CONNECTION_PATH);
  });

  test("MCP 서버 상태와 세 CLI·스킬의 복사 항목, 서버 설정을 보여 준다", async ({
    page,
  }) => {
    await expect(
      page.getByRole("heading", { name: "CLI 에이전트 연결" }),
    ).toBeVisible();
    await expect(page.getByText("연결 준비됨")).toBeVisible();
    await expect(page.getByText(RUNNING_STATUS.url)).toBeVisible();
    await expect(page.getByText("아직 없어요")).toBeVisible();

    for (const title of [
      "Claude Code",
      "Codex CLI",
      "Gemini CLI",
      "Knot 스킬",
    ]) {
      await expect(
        getRegistrationGroup(page, title).getByRole("button", {
          name: "복사",
          exact: true,
        }),
      ).toBeVisible();
    }

    await expect(page.getByRole("textbox", { name: "포트" })).toHaveValue(
      String(RUNNING_STATUS.port),
    );
    await expect(
      page.getByRole("button", { name: "연결 토큰 재발급" }),
    ).toBeVisible();
    await expect(
      page.getByText(/사용자의 CLI 에이전트와 그 에이전트가 쓰는 모델 제공자/),
    ).toBeVisible();
  });

  test("복사를 누르면 셸에 대상을 넘기고 미리보기와 복사됨을 보여 준다", async ({
    page,
  }) => {
    const claudeGroup = getRegistrationGroup(page, "Claude Code");

    await claudeGroup
      .getByRole("button", { name: "복사", exact: true })
      .click();

    await expect(
      claudeGroup.getByRole("button", { name: "복사됨" }),
    ).toBeVisible();
    await expect(
      claudeGroup.getByText("claude-code <연결 토큰>"),
    ).toBeVisible();
    expect(await readAgentCalls(page)).toContainEqual({
      method: "copyRegistration",
      argument: "claude-code",
    });
  });

  test("포트를 바꾸면 셸에 넘기고 새 주소를 보여 준다", async ({ page }) => {
    await page.getByRole("textbox", { name: "포트" }).fill("48000");
    await page.getByRole("button", { name: "포트 변경" }).click();

    await expect(page.getByText("http://127.0.0.1:48000/mcp")).toBeVisible();
    expect(await readAgentCalls(page)).toContainEqual({
      method: "setPort",
      argument: 48000,
    });
  });

  test("범위 밖 포트는 셸을 부르지 않고 안내 문구를 보여 준다", async ({
    page,
  }) => {
    await page.getByRole("textbox", { name: "포트" }).fill("80");
    await page.getByRole("button", { name: "포트 변경" }).click();

    await expect(
      page.getByText("1024~65535 사이의 정수를 입력해 주세요."),
    ).toBeVisible();
    expect(
      (await readAgentCalls(page)).some((call) => call.method === "setPort"),
    ).toBe(false);
  });

  test("토큰 재발급은 확인을 거쳐 실행하고 발급 시각을 새로 읽는다", async ({
    page,
  }) => {
    await page.getByRole("button", { name: "연결 토큰 재발급" }).click();
    expect(
      (await readAgentCalls(page)).some(
        (call) => call.method === "rotateToken",
      ),
    ).toBe(false);

    await page
      .getByRole("group", { name: "연결 토큰 재발급 확인" })
      .getByRole("button", { name: "재발급" })
      .click();

    await expect(page.getByText("2026.09.09 01:00")).toBeVisible();
    expect(await readAgentCalls(page)).toContainEqual({
      method: "rotateToken",
    });
  });

  test("프로필 메뉴의 CLI 에이전트 연결 항목으로 이 화면에 들어온다", async ({
    page,
  }) => {
    await page.goto(HOME_PATH);

    await page.getByRole("button", { name: "내 계정 메뉴" }).click();
    await page.getByRole("menuitem", { name: "CLI 에이전트 연결" }).click();

    await expect(page).toHaveURL(AGENT_CONNECTION_PATH);
    await expect(
      page.getByRole("heading", { name: "CLI 에이전트 연결" }),
    ).toBeVisible();
  });
});

test.describe("브라우저에서", () => {
  test("데스크톱 앱에서 이어가라는 안내만 보여 주고, 프로필 메뉴에 연결 항목이 없다", async ({
    page,
  }) => {
    await page.goto(AGENT_CONNECTION_PATH);

    await expect(
      page.getByRole("heading", { name: "데스크톱 앱에서 이어가요" }),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "CLI 에이전트 연결" }),
    ).toBeHidden();

    await page.goto(HOME_PATH);
    await page.getByRole("button", { name: "내 계정 메뉴" }).click();

    await expect(
      page.getByRole("menuitem", { name: "로그아웃" }),
    ).toBeVisible();
    await expect(
      page.getByRole("menuitem", { name: "CLI 에이전트 연결" }),
    ).toBeHidden();
  });
});
