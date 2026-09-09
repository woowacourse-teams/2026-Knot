/**
 * Notion 동기화 완료 알림(폴링) + Dock 배지 + preload `notifications.show` (기획서 7절 P2·4.4, 로드맵 A10).
 *
 * 흐름: SPA가 `POST /api/v1/workspaces/{id}/imports`로 동기화를 시작하고 `GET /api/v1/imports/{id}`를
 * 폴링한다. main은 `session.webRequest.onCompleted`로 그 요청의 **URL과 상태 코드만** 본다(본문·헤더는
 * 보지 않는다). 그렇게 알게 된 실행 ID를 main이 Bearer로 직접 폴링하고, 사용자가 창을 닫거나 다른
 * 화면으로 가서 SPA 폴링이 끊긴 뒤에도 끝(COMPLETED·FAILED)까지 따라간다. 끝나면 OS 알림과
 * Dock 배지를 올리고, 알림을 누르면 그 워크스페이스의 채팅으로 간다(`{type: "chat"}` 딥링크 재사용).
 *
 * 착수 가정(로드맵 4.1절 A10):
 * - 시작 요청(202·409)에서 본 워크스페이스 ID를 60초 안에 처음 본 상태 조회 ID와 짝지어 알림의
 *   목적지로 쓴다. 짝이 없으면 알림은 뜨되 누르면 창만 앞으로 온다.
 * - main이 처음 조회했을 때 이미 끝나 있던 실행은 알리지 않는다(SPA가 화면에 이미 보여 줬다).
 * - 폴링 간격 3초, 실행 하나를 최대 30분까지만 본다. 401·403·404나 연속 5회 오류면 그만둔다.
 * - 배지는 완료 건수이며 앱 창이 포커스를 받으면 0으로 돌아간다. 창이 포커스 상태면 배지를 올리지 않는다.
 * - 알림 본문에 문서 제목·질문·토큰은 넣지 않는다. 로그에는 실행 ID·상태만 남긴다.
 */

import { Notification, app } from "electron";
import type { Session } from "electron";
import type { KnotDeepLink } from "../shared/api";
import { toOrigin } from "../shared/env";
import { isKnotDeepLink } from "./deepLink";
import { logger } from "./logging";

export const IMPORT_POLL_INTERVAL_MS = 3_000;
export const IMPORT_POLL_TIMEOUT_MS = 10_000;
export const IMPORT_WATCH_MAX_MS = 30 * 60_000;
export const IMPORT_PAIRING_WINDOW_MS = 60_000;
export const IMPORT_MAX_CONSECUTIVE_ERRORS = 5;

const ID_PATTERN = /^[1-9]\d{0,17}$/;
const MAX_NOTIFICATION_TEXT = 200;

export type ImportStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";

/** `GET /api/v1/imports/{id}` 응답 중 알림에 필요한 필드(웹 DTO `GetNotionImportStatusResponseRaw`와 같다) */
export interface ImportRunStatus {
  id: number;
  status: ImportStatus;
  totalPageCount: number | null;
  processedPageCount: number;
  failureReason: string | null;
}

/** `GET /api/v1/imports/{id}` URL이면 실행 ID */
export function parseImportStatusRequest(rawUrl: string, apiOrigin: string): number | null {
  const segments = apiPathSegments(rawUrl, apiOrigin);
  if (segments === null || segments.length !== 4) return null;
  const [api, v1, imports, id] = segments;
  if (api !== "api" || v1 !== "v1" || imports !== "imports" || id === undefined || !ID_PATTERN.test(id)) return null;
  return Number(id);
}

/** `POST /api/v1/workspaces/{id}/imports` URL이면 워크스페이스 ID */
export function parseImportStartRequest(rawUrl: string, apiOrigin: string): string | null {
  const segments = apiPathSegments(rawUrl, apiOrigin);
  if (segments === null || segments.length !== 5) return null;
  const [api, v1, workspaces, id, imports] = segments;
  if (api !== "api" || v1 !== "v1" || workspaces !== "workspaces" || imports !== "imports") return null;
  if (id === undefined || !ID_PATTERN.test(id)) return null;
  return id;
}

function apiPathSegments(rawUrl: string, apiOrigin: string): string[] | null {
  if (toOrigin(rawUrl) !== apiOrigin) return null;
  try {
    return new URL(rawUrl).pathname.split("/").filter((segment) => segment.length > 0);
  } catch {
    return null;
  }
}

export function parseImportRunStatus(json: unknown): ImportRunStatus | null {
  if (typeof json !== "object" || json === null) return null;
  const { id, status, totalPageCount, processedPageCount, failureReason } = json as Record<string, unknown>;
  if (typeof id !== "number") return null;
  if (status !== "PENDING" && status !== "RUNNING" && status !== "COMPLETED" && status !== "FAILED") return null;
  if (totalPageCount !== null && typeof totalPageCount !== "number") return null;
  if (typeof processedPageCount !== "number") return null;
  if (failureReason !== null && typeof failureReason !== "string") return null;
  return { id, status, totalPageCount, processedPageCount, failureReason };
}

export interface SyncNotice {
  kind: "completed" | "failed";
  importRunId: number;
  /** 시작 요청과 짝지어진 워크스페이스. 모르면 null */
  workspaceId: string | null;
  processedPageCount: number;
  failureReason: string | null;
}

export interface DesktopNotificationInput {
  title: string;
  body: string;
  link: KnotDeepLink | null;
}

/** 알림 문구. 문서 제목·본문은 넣지 않는다 */
export function buildSyncNotification(notice: SyncNotice): DesktopNotificationInput {
  const link: KnotDeepLink | null = notice.workspaceId === null ? null : { type: "chat", workspaceId: notice.workspaceId };
  if (notice.kind === "completed") {
    return {
      title: "Notion 동기화 완료",
      body: `문서 ${notice.processedPageCount}개를 가져왔어요. 이제 질문할 수 있어요.`,
      link,
    };
  }
  return {
    title: "Notion 동기화 실패",
    body: notice.failureReason ?? "동기화를 마치지 못했어요. 앱에서 다시 시도해 주세요.",
    link,
  };
}

/** preload `notifications.show` 입력 검증(기획서 4.4). renderer가 준 값은 믿지 않는다 */
export function isNotificationInput(value: unknown): value is { title: string; body: string; link?: KnotDeepLink } {
  if (typeof value !== "object" || value === null) return false;
  const { title, body, link } = value as Record<string, unknown>;
  if (typeof title !== "string" || title.length === 0 || title.length > MAX_NOTIFICATION_TEXT) return false;
  if (typeof body !== "string" || body.length > MAX_NOTIFICATION_TEXT) return false;
  if (link !== undefined && !isKnotDeepLink(link)) return false;
  return true;
}

export interface ObservedRequest {
  method: string;
  url: string;
  statusCode: number;
}

export interface SyncWatcherOptions {
  apiOrigin: string;
  readToken: () => string | null;
  onNotice: (notice: SyncNotice) => void;
  fetch?: typeof fetch;
  /** 테스트용. 기본은 `setTimeout` */
  schedule?: (callback: () => void, delayMs: number) => unknown;
  cancel?: (handle: unknown) => void;
  now?: () => number;
  pollIntervalMs?: number;
  maxWatchMs?: number;
  pairingWindowMs?: number;
}

export interface SyncWatcher {
  /** `webRequest.onCompleted`가 본 요청 하나 */
  observe(request: ObservedRequest): void;
  trackedRunIds(): number[];
  stop(): void;
}

interface TrackedRun {
  workspaceId: string | null;
  startedAt: number;
  sawInProgress: boolean;
  consecutiveErrors: number;
  handle: unknown;
}

export function createSyncWatcher(options: SyncWatcherOptions): SyncWatcher {
  const doFetch = options.fetch ?? fetch;
  const schedule = options.schedule ?? ((callback, delayMs) => setTimeout(callback, delayMs));
  const cancel = options.cancel ?? ((handle) => clearTimeout(handle as ReturnType<typeof setTimeout>));
  const now = options.now ?? Date.now;
  const pollIntervalMs = options.pollIntervalMs ?? IMPORT_POLL_INTERVAL_MS;
  const maxWatchMs = options.maxWatchMs ?? IMPORT_WATCH_MAX_MS;
  const pairingWindowMs = options.pairingWindowMs ?? IMPORT_PAIRING_WINDOW_MS;

  const tracked = new Map<number, TrackedRun>();
  let lastStart: { workspaceId: string; at: number } | null = null;
  let stopped = false;

  function drop(importRunId: number, reason: string): void {
    const run = tracked.get(importRunId);
    if (run === undefined) return;
    tracked.delete(importRunId);
    if (run.handle !== null) cancel(run.handle);
    logger.info("[knot] 동기화 추적 종료", { importRunId, reason });
  }

  async function poll(importRunId: number): Promise<void> {
    const run = tracked.get(importRunId);
    if (run === undefined || stopped) return;
    run.handle = null;

    if (now() - run.startedAt > maxWatchMs) {
      drop(importRunId, "max-watch");
      return;
    }
    const token = options.readToken();
    if (token === null) {
      drop(importRunId, "no-token");
      return;
    }

    let status: ImportRunStatus | null = null;
    try {
      const response = await doFetch(`${options.apiOrigin}/api/v1/imports/${importRunId}`, {
        method: "GET",
        headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
        signal: AbortSignal.timeout(IMPORT_POLL_TIMEOUT_MS),
      });
      if (response.status === 401 || response.status === 403 || response.status === 404) {
        drop(importRunId, `http-${response.status}`);
        return;
      }
      if (response.ok) status = parseImportRunStatus(await response.json());
    } catch (error) {
      logger.warn("[knot] 동기화 상태 조회 실패", { importRunId, reason: errorName(error) });
    }
    if (tracked.get(importRunId) !== run) return;

    if (status === null) {
      run.consecutiveErrors += 1;
      if (run.consecutiveErrors >= IMPORT_MAX_CONSECUTIVE_ERRORS) {
        drop(importRunId, "errors");
        return;
      }
      run.handle = schedule(() => void poll(importRunId), pollIntervalMs);
      return;
    }
    run.consecutiveErrors = 0;

    if (status.status === "PENDING" || status.status === "RUNNING") {
      run.sawInProgress = true;
      run.handle = schedule(() => void poll(importRunId), pollIntervalMs);
      return;
    }

    const notice: SyncNotice = {
      kind: status.status === "COMPLETED" ? "completed" : "failed",
      importRunId,
      workspaceId: run.workspaceId,
      processedPageCount: status.processedPageCount,
      failureReason: status.failureReason,
    };
    drop(importRunId, status.status);
    if (run.sawInProgress) {
      logger.info("[knot] 동기화 알림", { importRunId, status: status.status });
      options.onNotice(notice);
    }
  }

  return {
    observe(request) {
      if (stopped) return;
      const method = request.method.toUpperCase();

      if (method === "POST" && (request.statusCode === 202 || request.statusCode === 409)) {
        const workspaceId = parseImportStartRequest(request.url, options.apiOrigin);
        if (workspaceId !== null) lastStart = { workspaceId, at: now() };
        return;
      }

      if (method !== "GET" || request.statusCode !== 200) return;
      const importRunId = parseImportStatusRequest(request.url, options.apiOrigin);
      if (importRunId === null || tracked.has(importRunId)) return;

      const paired = lastStart !== null && now() - lastStart.at <= pairingWindowMs ? lastStart.workspaceId : null;
      lastStart = null;
      const run: TrackedRun = {
        workspaceId: paired,
        startedAt: now(),
        sawInProgress: false,
        consecutiveErrors: 0,
        handle: null,
      };
      tracked.set(importRunId, run);
      logger.info("[knot] 동기화 추적 시작", { importRunId, hasWorkspace: paired !== null });
      run.handle = schedule(() => void poll(importRunId), 0);
    },
    trackedRunIds() {
      return [...tracked.keys()];
    },
    stop() {
      stopped = true;
      for (const importRunId of [...tracked.keys()]) drop(importRunId, "stop");
    },
  };
}

/** 웹 세션의 API 요청 완료 이벤트를 감시자에 잇는다. URL·상태 코드만 넘긴다 */
export function attachSyncWatcher(webSession: Session, watcher: SyncWatcher, apiOrigin: string): void {
  webSession.webRequest.onCompleted(
    { urls: [`${apiOrigin}/api/v1/imports/*`, `${apiOrigin}/api/v1/workspaces/*/imports`] },
    (details) => {
      watcher.observe({ method: details.method, url: details.url, statusCode: details.statusCode });
    },
  );
}

/** OS 알림. 지원하지 않는 환경이면 false */
export function showDesktopNotification(
  input: DesktopNotificationInput,
  onClick: (link: KnotDeepLink | null) => void,
): boolean {
  if (!Notification.isSupported()) {
    logger.warn("[knot] 이 환경은 알림을 지원하지 않는다");
    return false;
  }
  const notification = new Notification({ title: input.title, body: input.body });
  notification.on("click", () => {
    onClick(input.link);
  });
  notification.show();
  return true;
}

export interface DockBadge {
  bump(): void;
  clear(): void;
  count(): number;
}

/** Dock(macOS)·작업 표시줄(Windows·Linux Unity) 배지. 창이 포커스를 받으면 0으로 */
export function createDockBadge(): DockBadge {
  let count = 0;
  const apply = (): void => {
    app.setBadgeCount(count);
  };
  app.on("browser-window-focus", () => {
    if (count === 0) return;
    count = 0;
    apply();
  });
  return {
    bump() {
      count += 1;
      apply();
      if (process.platform === "darwin") app.dock?.bounce("informational");
    },
    clear() {
      count = 0;
      apply();
    },
    count() {
      return count;
    },
  };
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "UnknownError";
}
