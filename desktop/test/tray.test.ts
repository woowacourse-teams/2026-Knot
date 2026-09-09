import { beforeEach, describe, expect, it, vi } from "vitest";
import { logMock } from "./helpers/logMock";

const trayInstances: Array<{ image: unknown; setToolTip: ReturnType<typeof vi.fn>; setContextMenu: ReturnType<typeof vi.fn>; on: ReturnType<typeof vi.fn> }> = [];
const nativeImageMock = { isEmpty: vi.fn(() => false), setTemplateImage: vi.fn() };
const globalShortcutMock = { register: vi.fn(() => true), unregisterAll: vi.fn() };

function TrayMock(this: (typeof trayInstances)[number], image: unknown): void {
  this.image = image;
  this.setToolTip = vi.fn();
  this.setContextMenu = vi.fn();
  this.on = vi.fn();
  trayInstances.push(this);
}

vi.mock("electron", () => ({
  Tray: TrayMock,
  Menu: { buildFromTemplate: vi.fn((template: unknown) => ({ template })) },
  globalShortcut: globalShortcutMock,
  nativeImage: { createFromPath: vi.fn(() => nativeImageMock) },
}));
vi.mock("electron-log/main", () => ({ default: logMock }));

const { buildTrayMenuTemplate, createTray, registerQuickAskShortcut } = await import("../src/main/tray");

const actions = {
  onOpen: vi.fn(),
  onQuickAsk: vi.fn(),
  onQuit: vi.fn(),
  quickAskAccelerator: "CommandOrControl+Shift+K",
};

beforeEach(() => {
  trayInstances.length = 0;
  vi.clearAllMocks();
  nativeImageMock.isEmpty.mockReturnValue(false);
  globalShortcutMock.register.mockReturnValue(true);
});

describe("buildTrayMenuTemplate", () => {
  it("열기·퀵 질문·종료만 둔다", () => {
    const template = buildTrayMenuTemplate(actions);

    expect(template.map((item) => item.label ?? item.type)).toEqual(["Knot 열기", "퀵 질문", "separator", "종료"]);
    expect(template[1]?.accelerator).toBe("CommandOrControl+Shift+K");
    template[0]?.click?.(undefined as never, undefined, undefined as never);
    template[1]?.click?.(undefined as never, undefined, undefined as never);
    template[3]?.click?.(undefined as never, undefined, undefined as never);
    expect(actions.onOpen).toHaveBeenCalledTimes(1);
    expect(actions.onQuickAsk).toHaveBeenCalledTimes(1);
    expect(actions.onQuit).toHaveBeenCalledTimes(1);
  });
});

describe("createTray", () => {
  it("템플릿 이미지로 트레이를 만들고 툴팁·메뉴를 단다", () => {
    createTray("/resources/tray/knotTemplate.png", actions);

    expect(nativeImageMock.setTemplateImage).toHaveBeenCalledWith(true);
    expect(trayInstances[0]?.setToolTip).toHaveBeenCalledWith("Knot");
    expect(trayInstances[0]?.setContextMenu).toHaveBeenCalled();
  });

  it("아이콘 파일이 없어도 트레이는 만든다(경고만)", () => {
    nativeImageMock.isEmpty.mockReturnValue(true);

    createTray("/missing.png", actions);

    expect(trayInstances).toHaveLength(1);
    expect(logMock.warn).toHaveBeenCalled();
  });
});

describe("registerQuickAskShortcut", () => {
  it("등록 성공이면 true", () => {
    const handler = vi.fn();
    expect(registerQuickAskShortcut("CommandOrControl+Shift+K", handler)).toBe(true);
    expect(globalShortcutMock.register).toHaveBeenCalledWith("CommandOrControl+Shift+K", handler);
  });

  it("다른 앱이 잡고 있거나 예외가 나면 false이고 던지지 않는다", () => {
    globalShortcutMock.register.mockReturnValueOnce(false);
    expect(registerQuickAskShortcut("CommandOrControl+Shift+K", vi.fn())).toBe(false);

    globalShortcutMock.register.mockImplementationOnce(() => {
      throw new Error("invalid accelerator");
    });
    expect(registerQuickAskShortcut("Bad", vi.fn())).toBe(false);
  });
});
