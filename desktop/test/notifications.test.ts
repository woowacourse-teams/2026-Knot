import { beforeEach, describe, expect, it, vi } from "vitest";
import { logMock } from "./helpers/logMock";

const appMock = {
  on: vi.fn(),
  setBadgeCount: vi.fn(() => true),
  dock: { bounce: vi.fn() },
};
const notificationInstances: Array<{ options: unknown; on: ReturnType<typeof vi.fn>; show: ReturnType<typeof vi.fn> }> = [];
const notificationSupported = vi.fn(() => true);

function NotificationMock(this: (typeof notificationInstances)[number], options: unknown): void {
  this.options = options;
  this.on = vi.fn();
  this.show = vi.fn();
  notificationInstances.push(this);
}
(NotificationMock as unknown as { isSupported: () => boolean }).isSupported = () => notificationSupported();

vi.mock("electron", () => ({ app: appMock, Notification: NotificationMock }));
vi.mock("electron-log/main", () => ({ default: logMock }));

const {
  attachSyncWatcher,
  buildSyncNotification,
  createDockBadge,
  createSyncWatcher,
  isNotificationInput,
  parseImportRunStatus,
  parseImportStartRequest,
  parseImportStatusRequest,
  showDesktopNotification,
} = await import("../src/main/notifications");

const API = "https://dev-api.knoted.kr";

describe("요청 URL 파서", () => {
  it("상태 조회 GET /api/v1/imports/{id}에서 실행 ID를 뽑는다", () => {
    expect(parseImportStatusRequest(`${API}/api/v1/imports/17`, API)).toBe(17);
    expect(parseImportStatusRequest(`${API}/api/v1/imports/17?x=1`, API)).toBe(17);
    expect(parseImportStatusRequest(`${API}/api/v1/imports/17/retry`, API)).toBeNull();
    expect(parseImportStatusRequest(`${API}/api/v1/imports/abc`, API)).toBeNull();
    expect(parseImportStatusRequest(`https://evil.example/api/v1/imports/17`, API)).toBeNull();
  });

  it("시작 POST /api/v1/workspaces/{id}/imports에서 워크스페이스 ID를 뽑는다", () => {
    expect(parseImportStartRequest(`${API}/api/v1/workspaces/3/imports`, API)).toBe("3");
    expect(parseImportStartRequest(`${API}/api/v1/workspaces/3/imports/1`, API)).toBeNull();
    expect(parseImportStartRequest(`${API}/api/v1/workspaces/x/imports`, API)).toBeNull();
  });
});

describe("parseImportRunStatus", () => {
  it("웹 DTO와 같은 모양만 받는다", () => {
    expect(
      parseImportRunStatus({
        id: 1,
        status: "COMPLETED",
        totalPageCount: 21,
        processedPageCount: 21,
        failureReason: null,
        createdAt: "x",
      }),
    ).toEqual({ id: 1, status: "COMPLETED", totalPageCount: 21, processedPageCount: 21, failureReason: null });
    expect(parseImportRunStatus({ id: 1, status: "DONE", totalPageCount: null, processedPageCount: 0, failureReason: null })).toBeNull();
    expect(parseImportRunStatus(null)).toBeNull();
  });
});

describe("buildSyncNotification", () => {
  it("완료·실패 문구와 채팅 딥링크를 만든다", () => {
    expect(
      buildSyncNotification({ kind: "completed", importRunId: 1, workspaceId: "3", processedPageCount: 21, failureReason: null }),
    ).toEqual({
      title: "Notion 동기화 완료",
      body: "문서 21개를 가져왔어요. 이제 질문할 수 있어요.",
      link: { type: "chat", workspaceId: "3" },
    });
    expect(
      buildSyncNotification({ kind: "failed", importRunId: 1, workspaceId: null, processedPageCount: 0, failureReason: "Notion 권한 없음" }),
    ).toEqual({ title: "Notion 동기화 실패", body: "Notion 권한 없음", link: null });
  });
});

describe("isNotificationInput", () => {
  it("제목·본문 문자열과 계약 모양의 링크만 통과시킨다", () => {
    expect(isNotificationInput({ title: "t", body: "b" })).toBe(true);
    expect(isNotificationInput({ title: "t", body: "b", link: { type: "invite", token: "x" } })).toBe(true);
    expect(isNotificationInput({ title: "", body: "b" })).toBe(false);
    expect(isNotificationInput({ title: "t", body: "b", link: { type: "auth" } })).toBe(false);
    expect(isNotificationInput({ title: "t".repeat(201), body: "b" })).toBe(false);
    expect(isNotificationInput("t")).toBe(false);
  });
});

interface FakeTimer {
  callback: () => void;
  delayMs: number;
  cancelled: boolean;
}

function createHarness(responses: Array<{ status: number; body?: unknown } | Error>) {
  const timers: FakeTimer[] = [];
  const fetchMock = vi.fn(async () => {
    const next = responses.shift();
    if (next === undefined) throw new Error("응답 준비 없음");
    if (next instanceof Error) throw next;
    return {
      ok: next.status >= 200 && next.status < 300,
      status: next.status,
      json: async () => next.body,
    } as Response;
  });
  const onNotice = vi.fn();
  let clock = 10_000;
  const watcher = createSyncWatcher({
    apiOrigin: API,
    readToken: () => "jwt",
    fetch: fetchMock as unknown as typeof fetch,
    schedule: (callback, delayMs) => {
      const timer = { callback, delayMs, cancelled: false };
      timers.push(timer);
      return timer;
    },
    cancel: (handle) => {
      (handle as FakeTimer).cancelled = true;
    },
    now: () => clock,
    onNotice,
  });
  async function runTimers(): Promise<void> {
    while (timers.length > 0) {
      const timer = timers.shift();
      if (timer === undefined || timer.cancelled) continue;
      timer.callback();
      await new Promise((resolve) => setImmediate(resolve));
    }
  }
  return {
    watcher,
    fetchMock,
    onNotice,
    timers,
    runTimers,
    advance(ms: number) {
      clock += ms;
    },
  };
}

const running = { id: 5, status: "RUNNING", totalPageCount: 10, processedPageCount: 3, failureReason: null };
const completed = { id: 5, status: "COMPLETED", totalPageCount: 10, processedPageCount: 10, failureReason: null };

describe("createSyncWatcher", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("SPA의 상태 조회를 보면 직접 폴링을 시작하고, 끝나면 알린다", async () => {
    const h = createHarness([{ status: 200, body: running }, { status: 200, body: completed }]);

    h.watcher.observe({ method: "POST", url: `${API}/api/v1/workspaces/3/imports`, statusCode: 202 });
    h.watcher.observe({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });
    h.watcher.observe({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });

    expect(h.watcher.trackedRunIds()).toEqual([5]);
    await h.runTimers();

    expect(h.fetchMock).toHaveBeenCalledTimes(2);
    const [url, init] = h.fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${API}/api/v1/imports/5`);
    expect(init.headers).toMatchObject({ Authorization: "Bearer jwt" });
    expect(h.onNotice).toHaveBeenCalledWith({
      kind: "completed",
      importRunId: 5,
      workspaceId: "3",
      processedPageCount: 10,
      failureReason: null,
    });
    expect(h.watcher.trackedRunIds()).toEqual([]);
  });

  it("처음 봤을 때 이미 끝난 실행은 알리지 않는다(SPA가 이미 보여 줬다)", async () => {
    const h = createHarness([{ status: 200, body: completed }]);

    h.watcher.observe({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });
    await h.runTimers();

    expect(h.onNotice).not.toHaveBeenCalled();
    expect(h.watcher.trackedRunIds()).toEqual([]);
  });

  it("시작 요청이 60초보다 오래됐으면 워크스페이스를 짝짓지 않는다", async () => {
    const h = createHarness([{ status: 200, body: running }, { status: 200, body: { ...completed, status: "FAILED", failureReason: "x" } }]);

    h.watcher.observe({ method: "POST", url: `${API}/api/v1/workspaces/3/imports`, statusCode: 409 });
    h.advance(60_001);
    h.watcher.observe({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });
    await h.runTimers();

    expect(h.onNotice).toHaveBeenCalledWith(expect.objectContaining({ kind: "failed", workspaceId: null, failureReason: "x" }));
  });

  it("401·403·404면 추적을 그만둔다", async () => {
    const h = createHarness([{ status: 401 }]);

    h.watcher.observe({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });
    await h.runTimers();

    expect(h.watcher.trackedRunIds()).toEqual([]);
    expect(h.onNotice).not.toHaveBeenCalled();
  });

  it("일시 오류는 다시 시도하고 연속 5회면 그만둔다", async () => {
    const h = createHarness([new Error("net"), { status: 200, body: running }, new Error("a"), new Error("b"), new Error("c"), new Error("d"), new Error("e")]);

    h.watcher.observe({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });
    await h.runTimers();

    expect(h.fetchMock).toHaveBeenCalledTimes(7);
    expect(h.watcher.trackedRunIds()).toEqual([]);
    expect(h.onNotice).not.toHaveBeenCalled();
  });

  it("토큰이 없으면 서버를 부르지 않는다", async () => {
    const timers: FakeTimer[] = [];
    const fetchMock = vi.fn();
    const watcher = createSyncWatcher({
      apiOrigin: API,
      readToken: () => null,
      fetch: fetchMock as unknown as typeof fetch,
      schedule: (callback, delayMs) => {
        timers.push({ callback, delayMs, cancelled: false });
        return null;
      },
      cancel: () => undefined,
      onNotice: vi.fn(),
    });

    watcher.observe({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });
    timers[0]?.callback();
    await new Promise((resolve) => setImmediate(resolve));

    expect(fetchMock).not.toHaveBeenCalled();
    expect(watcher.trackedRunIds()).toEqual([]);
  });

  it("30분이 지나면 추적을 그만둔다", async () => {
    const h = createHarness([{ status: 200, body: running }]);

    h.watcher.observe({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });
    h.timers[0]?.callback();
    await new Promise((resolve) => setImmediate(resolve));
    h.advance(30 * 60_000 + 1);
    h.timers[1]?.callback();
    await new Promise((resolve) => setImmediate(resolve));

    expect(h.fetchMock).toHaveBeenCalledTimes(1);
    expect(h.watcher.trackedRunIds()).toEqual([]);
  });

  it("stop은 예약된 폴링을 취소한다", () => {
    const h = createHarness([]);
    h.watcher.observe({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });

    h.watcher.stop();

    expect(h.timers[0]?.cancelled).toBe(true);
    expect(h.watcher.trackedRunIds()).toEqual([]);
  });

  it("로그에 토큰·문서 내용은 남지 않는다", async () => {
    const h = createHarness([{ status: 200, body: running }, { status: 200, body: completed }]);
    h.watcher.observe({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });
    await h.runTimers();

    const logged = JSON.stringify([...logMock.info.mock.calls, ...logMock.warn.mock.calls]);
    expect(logged).not.toContain("jwt");
  });
});

describe("attachSyncWatcher", () => {
  it("webRequest.onCompleted에 API 오리진의 두 경로만 걸고 URL·상태 코드를 넘긴다", () => {
    const onCompleted = vi.fn();
    const watcher = { observe: vi.fn(), trackedRunIds: vi.fn(() => []), stop: vi.fn() };

    attachSyncWatcher({ webRequest: { onCompleted } } as never, watcher, API);

    expect(onCompleted.mock.calls[0]?.[0]).toEqual({
      urls: [`${API}/api/v1/imports/*`, `${API}/api/v1/workspaces/*/imports`],
    });
    const listener = onCompleted.mock.calls[0]?.[1] as (details: unknown) => void;
    listener({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200, responseHeaders: { secret: "x" } });
    expect(watcher.observe).toHaveBeenCalledWith({ method: "GET", url: `${API}/api/v1/imports/5`, statusCode: 200 });
  });
});

describe("showDesktopNotification", () => {
  beforeEach(() => {
    notificationInstances.length = 0;
    notificationSupported.mockReturnValue(true);
  });

  it("알림을 띄우고 클릭하면 링크를 넘긴다", () => {
    const onClick = vi.fn();
    const link = { type: "chat", workspaceId: "3" } as const;

    expect(showDesktopNotification({ title: "t", body: "b", link }, onClick)).toBe(true);

    const [notification] = notificationInstances;
    expect(notification?.options).toEqual({ title: "t", body: "b" });
    expect(notification?.show).toHaveBeenCalled();
    const click = notification?.on.mock.calls.find(([name]) => name === "click")?.[1] as () => void;
    click();
    expect(onClick).toHaveBeenCalledWith(link);
  });

  it("지원하지 않는 환경이면 false", () => {
    notificationSupported.mockReturnValue(false);
    expect(showDesktopNotification({ title: "t", body: "b", link: null }, vi.fn())).toBe(false);
    expect(notificationInstances).toHaveLength(0);
  });
});

describe("createDockBadge", () => {
  it("완료 건수를 올리고 창이 포커스를 받으면 0으로 돌린다", () => {
    appMock.on.mockClear();
    const badge = createDockBadge();

    badge.bump();
    badge.bump();
    expect(badge.count()).toBe(2);
    expect(appMock.setBadgeCount).toHaveBeenLastCalledWith(2);

    const focus = appMock.on.mock.calls.find(([name]) => name === "browser-window-focus")?.[1] as () => void;
    focus();
    expect(badge.count()).toBe(0);
    expect(appMock.setBadgeCount).toHaveBeenLastCalledWith(0);
  });
});
