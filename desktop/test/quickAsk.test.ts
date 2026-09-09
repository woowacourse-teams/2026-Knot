import { beforeEach, describe, expect, it, vi } from "vitest";
import { mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { logMock } from "./helpers/logMock";

const appMock = { on: vi.fn() };

interface FakeBrowserWindow {
  options: Record<string, unknown>;
  handlers: Map<string, (...args: unknown[]) => void>;
  webContents: { on: ReturnType<typeof vi.fn>; loadURL?: never };
  loadURL: ReturnType<typeof vi.fn>;
  show: ReturnType<typeof vi.fn>;
  hide: ReturnType<typeof vi.fn>;
  focus: ReturnType<typeof vi.fn>;
  isVisible: ReturnType<typeof vi.fn>;
  isFocused: ReturnType<typeof vi.fn>;
  on: (name: string, handler: (...args: unknown[]) => void) => void;
}

const windows: FakeBrowserWindow[] = [];

function BrowserWindowMock(this: FakeBrowserWindow, options: Record<string, unknown>): void {
  this.options = options;
  this.handlers = new Map();
  this.webContents = { on: vi.fn() };
  this.loadURL = vi.fn(() => Promise.resolve());
  this.show = vi.fn();
  this.hide = vi.fn();
  this.focus = vi.fn();
  this.isVisible = vi.fn(() => true);
  this.isFocused = vi.fn(() => true);
  this.on = (name, handler) => {
    this.handlers.set(name, handler);
  };
  windows.push(this);
}

vi.mock("electron", () => ({
  app: appMock,
  BrowserWindow: BrowserWindowMock,
  session: { fromPartition: vi.fn(() => ({ partition: "persist:knot" })) },
}));
vi.mock("electron-log/main", () => ({ default: logMock }));

const {
  createLastWorkspaceStore,
  createQuickAskWindow,
  extractWorkspaceId,
  quickAskUrl,
  trackLastWorkspace,
} = await import("../src/main/quickAsk");
const { resolveEnvironment } = await import("../src/shared/env");

const env = resolveEnvironment("dev");

describe("extractWorkspaceId", () => {
  it("웹 오리진의 /workspace/:id 경로에서 ID를 뽑는다", () => {
    expect(extractWorkspaceId("https://dev.knoted.kr/workspace/12", env.webOrigin)).toBe("12");
    expect(extractWorkspaceId("https://dev.knoted.kr/workspace/12/chat/3?x=1", env.webOrigin)).toBe("12");
  });

  it("워크스페이스 경로가 아니거나 다른 오리진이면 null", () => {
    expect(extractWorkspaceId("https://dev.knoted.kr/workspace", env.webOrigin)).toBeNull();
    expect(extractWorkspaceId("https://dev.knoted.kr/workspace/create", env.webOrigin)).toBeNull();
    expect(extractWorkspaceId("https://dev.knoted.kr/invite/abc", env.webOrigin)).toBeNull();
    expect(extractWorkspaceId("https://evil.example/workspace/12", env.webOrigin)).toBeNull();
    expect(extractWorkspaceId("not a url", env.webOrigin)).toBeNull();
  });
});

describe("quickAskUrl", () => {
  it("워크스페이스를 알면 채팅, 모르면 목록을 연다", () => {
    expect(quickAskUrl(env.webOrigin, "5")).toBe("https://dev.knoted.kr/workspace/5/chat");
    expect(quickAskUrl(env.webOrigin, null)).toBe("https://dev.knoted.kr/workspace");
  });
});

describe("createLastWorkspaceStore", () => {
  it("저장한 값을 파일에서 다시 읽는다", () => {
    const dir = mkdtempSync(join(tmpdir(), "knot-last-ws-"));
    createLastWorkspaceStore(dir).write("42");

    expect(JSON.parse(readFileSync(join(dir, "last-workspace.json"), "utf8"))).toEqual({ workspaceId: "42" });
    expect(createLastWorkspaceStore(dir).read()).toBe("42");
  });

  it("파일이 없거나 깨졌으면 null", () => {
    const dir = mkdtempSync(join(tmpdir(), "knot-last-ws-"));
    expect(createLastWorkspaceStore(dir).read()).toBeNull();

    writeFileSync(join(dir, "last-workspace.json"), "{bad json");
    expect(createLastWorkspaceStore(dir).read()).toBeNull();

    writeFileSync(join(dir, "last-workspace.json"), JSON.stringify({ workspaceId: "../x" }));
    expect(createLastWorkspaceStore(dir).read()).toBeNull();
  });
});

describe("trackLastWorkspace", () => {
  it("창의 이동과 SPA 라우터 이동에서 워크스페이스를 기억한다", () => {
    const store = { read: vi.fn(() => null), write: vi.fn() };
    const listeners = new Map<string, (...args: unknown[]) => void>();
    const contents = { on: vi.fn((name: string, handler: (...args: unknown[]) => void) => listeners.set(name, handler)) };

    trackLastWorkspace(contents as never, env, store);
    listeners.get("did-navigate")?.(undefined, "https://dev.knoted.kr/workspace/3");
    listeners.get("did-navigate-in-page")?.(undefined, "https://dev.knoted.kr/workspace/4/chat", true);
    listeners.get("did-navigate-in-page")?.(undefined, "https://dev.knoted.kr/workspace/5", false);
    listeners.get("did-navigate")?.(undefined, "https://dev.knoted.kr/login");

    expect(store.write.mock.calls).toEqual([["3"], ["4"]]);
  });
});

describe("createQuickAskWindow", () => {
  beforeEach(() => {
    windows.length = 0;
    vi.clearAllMocks();
  });

  it("보안 webPreferences를 메인 창과 같게 두고 마지막 워크스페이스의 채팅을 연다", () => {
    const store = { read: vi.fn(() => "9"), write: vi.fn() };
    const quickAsk = createQuickAskWindow(env, store);

    quickAsk.show();

    const [window] = windows;
    expect(window).toBeDefined();
    expect(window?.options["webPreferences"]).toMatchObject({
      sandbox: true,
      contextIsolation: true,
      nodeIntegration: false,
      webviewTag: false,
    });
    expect(window?.options["alwaysOnTop"]).toBe(true);
    expect(window?.loadURL).toHaveBeenCalledWith("https://dev.knoted.kr/workspace/9/chat");
    expect(window?.show).toHaveBeenCalled();
  });

  it("같은 워크스페이스면 다시 로드하지 않고, 바뀌면 새로 로드한다", () => {
    let current: string | null = "9";
    const store = { read: vi.fn(() => current), write: vi.fn() };
    const quickAsk = createQuickAskWindow(env, store);

    quickAsk.show();
    quickAsk.show();
    current = "10";
    quickAsk.show();

    expect(windows[0]?.loadURL.mock.calls).toEqual([
      ["https://dev.knoted.kr/workspace/9/chat"],
      ["https://dev.knoted.kr/workspace/10/chat"],
    ]);
  });

  it("toggle은 보이는 창을 숨기고, 숨은 창을 다시 띄운다", () => {
    const store = { read: vi.fn(() => null), write: vi.fn() };
    const quickAsk = createQuickAskWindow(env, store);

    quickAsk.toggle();
    const [window] = windows;
    expect(window?.loadURL).toHaveBeenCalledWith("https://dev.knoted.kr/workspace");
    expect(window?.show).toHaveBeenCalledTimes(1);

    quickAsk.toggle();
    expect(window?.hide).toHaveBeenCalledTimes(1);

    window?.isVisible.mockReturnValue(false);
    quickAsk.toggle();
    expect(window?.show).toHaveBeenCalledTimes(2);
  });

  it("닫기는 숨기기이고 앱 종료 때만 실제로 닫힌다", () => {
    const store = { read: vi.fn(() => null), write: vi.fn() };
    createQuickAskWindow(env, store).show();
    const [window] = windows;
    const close = window?.handlers.get("close");
    const event = { preventDefault: vi.fn() };

    close?.(event);
    expect(event.preventDefault).toHaveBeenCalledTimes(1);
    expect(window?.hide).toHaveBeenCalledTimes(1);

    const beforeQuit = appMock.on.mock.calls.find(([name]) => name === "before-quit")?.[1] as () => void;
    beforeQuit();
    close?.(event);
    expect(event.preventDefault).toHaveBeenCalledTimes(1);
  });
});
