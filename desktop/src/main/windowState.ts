/**
 * 창 상태 복원 (기획서 7절 P1 "창 상태 복원", 로드맵 `A2`).
 *
 * `userData/window-state.json`에 `{x, y, width, height, isMaximized}`를 두고 다음 실행에 되살린다.
 * 저장된 위치가 지금 연결된 어떤 디스플레이의 작업 영역에도 충분히 걸치지 않으면(모니터를 뺀 경우)
 * 위치는 버리고 크기만 쓴다 — 창이 화면 밖에 뜨는 것을 막는다. 값이 깨졌으면 기본값이다.
 * 순수 함수(`resolveWindowState`)는 Electron을 import 하지 않아 vitest로 검증한다.
 */

import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

export const WINDOW_STATE_FILE_NAME = "window-state.json";

/** 창의 최소 크기. `BrowserWindow`의 `minWidth`·`minHeight`와 같다 */
export const MIN_WINDOW_WIDTH = 960;
export const MIN_WINDOW_HEIGHT = 600;

/** 저장된 위치를 되살리려면 작업 영역과 가로·세로로 이만큼은 겹쳐야 한다 */
export const MIN_VISIBLE_PX = 64;

export interface WindowState {
  x?: number;
  y?: number;
  width: number;
  height: number;
  isMaximized: boolean;
}

/** 디스플레이 작업 영역(`Display.workArea`) */
export interface DisplayArea {
  x: number;
  y: number;
  width: number;
  height: number;
}

export const DEFAULT_WINDOW_STATE: WindowState = { width: 1280, height: 832, isMaximized: false };

function isInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value);
}

function overlapsEnough(state: { x: number; y: number; width: number; height: number }, display: DisplayArea): boolean {
  const overlapWidth = Math.min(state.x + state.width, display.x + display.width) - Math.max(state.x, display.x);
  const overlapHeight = Math.min(state.y + state.height, display.y + display.height) - Math.max(state.y, display.y);
  return overlapWidth >= MIN_VISIBLE_PX && overlapHeight >= MIN_VISIBLE_PX;
}

/**
 * 저장된 값(파싱 전 unknown)을 지금 디스플레이 구성에 맞는 창 상태로 바꾼다.
 *
 * @param displays 연결된 디스플레이의 작업 영역. 비어 있으면 위치를 되살리지 않는다
 */
export function resolveWindowState(
  saved: unknown,
  displays: readonly DisplayArea[],
  defaults: WindowState = DEFAULT_WINDOW_STATE,
): WindowState {
  if (typeof saved !== "object" || saved === null) return { ...defaults };
  const { x, y, width, height, isMaximized } = saved as Record<string, unknown>;

  const resolved: WindowState = {
    width: isInteger(width) ? Math.max(width, MIN_WINDOW_WIDTH) : defaults.width,
    height: isInteger(height) ? Math.max(height, MIN_WINDOW_HEIGHT) : defaults.height,
    isMaximized: isMaximized === true,
  };

  if (isInteger(x) && isInteger(y)) {
    const candidate = { x, y, width: resolved.width, height: resolved.height };
    if (displays.some((display) => overlapsEnough(candidate, display))) {
      resolved.x = x;
      resolved.y = y;
    }
  }
  return resolved;
}

export interface WindowStateStore {
  read(): unknown;
  write(state: WindowState): void;
  readonly filePath: string;
}

export function createWindowStateStore(userDataDir: string): WindowStateStore {
  const filePath = join(userDataDir, WINDOW_STATE_FILE_NAME);
  return {
    filePath,
    read() {
      try {
        return JSON.parse(readFileSync(filePath, "utf8")) as unknown;
      } catch {
        return null;
      }
    },
    write(state) {
      try {
        mkdirSync(userDataDir, { recursive: true });
        writeFileSync(filePath, JSON.stringify(state, null, 2) + "\n");
      } catch {
        // 저장 실패는 다음 실행에 기본 크기로 뜨는 것뿐이다
      }
    },
  };
}
