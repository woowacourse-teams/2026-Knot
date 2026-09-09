import { describe, expect, it } from "vitest";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  DEFAULT_WINDOW_STATE,
  MIN_WINDOW_HEIGHT,
  MIN_WINDOW_WIDTH,
  createWindowStateStore,
  resolveWindowState,
} from "../src/main/windowState";

const PRIMARY = { x: 0, y: 25, width: 1728, height: 1054 };
const SECOND = { x: 1728, y: 0, width: 2560, height: 1440 };

describe("resolveWindowState", () => {
  it("저장된 값이 없거나 깨졌으면 기본값이다", () => {
    expect(resolveWindowState(null, [PRIMARY])).toEqual(DEFAULT_WINDOW_STATE);
    expect(resolveWindowState("junk", [PRIMARY])).toEqual(DEFAULT_WINDOW_STATE);
    expect(resolveWindowState({ width: "wide" }, [PRIMARY])).toEqual(DEFAULT_WINDOW_STATE);
  });

  it("어느 디스플레이에 걸쳐 있는 위치는 그대로 되살린다", () => {
    const state = resolveWindowState({ x: 100, y: 80, width: 1200, height: 800, isMaximized: false }, [PRIMARY, SECOND]);
    expect(state).toEqual({ x: 100, y: 80, width: 1200, height: 800, isMaximized: false });
  });

  it("두 번째 모니터에 있던 창도 그 모니터가 아직 있으면 되살린다", () => {
    const state = resolveWindowState({ x: 2000, y: 100, width: 1200, height: 800 }, [PRIMARY, SECOND]);
    expect(state.x).toBe(2000);
  });

  it("사라진 모니터의 위치는 버리고 크기만 쓴다(화면 밖 방지)", () => {
    const state = resolveWindowState({ x: 2000, y: 100, width: 1200, height: 800 }, [PRIMARY]);
    expect(state).toEqual({ width: 1200, height: 800, isMaximized: false });
  });

  it("작업 영역과 겹치는 부분이 너무 작으면 위치를 버린다", () => {
    const state = resolveWindowState({ x: 1700, y: 1040, width: 1200, height: 800 }, [PRIMARY]);
    expect(state.x).toBeUndefined();
    expect(state.y).toBeUndefined();
  });

  it("최소 크기보다 작게 저장된 값은 최소 크기로 올린다", () => {
    const state = resolveWindowState({ width: 300, height: 200 }, [PRIMARY]);
    expect(state.width).toBe(MIN_WINDOW_WIDTH);
    expect(state.height).toBe(MIN_WINDOW_HEIGHT);
  });

  it("최대화 여부는 true일 때만 되살린다", () => {
    expect(resolveWindowState({ width: 1280, height: 832, isMaximized: true }, [PRIMARY]).isMaximized).toBe(true);
    expect(resolveWindowState({ width: 1280, height: 832, isMaximized: "yes" }, [PRIMARY]).isMaximized).toBe(false);
  });

  it("디스플레이 정보가 없으면 위치를 되살리지 않는다", () => {
    expect(resolveWindowState({ x: 1, y: 1, width: 1280, height: 832 }, []).x).toBeUndefined();
  });
});

describe("createWindowStateStore", () => {
  it("저장한 값을 다시 읽고, 파일이 없거나 깨졌으면 null이다", () => {
    const dir = mkdtempSync(join(tmpdir(), "knot-window-state-"));
    const store = createWindowStateStore(dir);

    expect(store.read()).toBeNull();

    store.write({ x: 10, y: 20, width: 1000, height: 700, isMaximized: false });
    expect(store.read()).toEqual({ x: 10, y: 20, width: 1000, height: 700, isMaximized: false });

    writeFileSync(store.filePath, "{broken");
    expect(store.read()).toBeNull();
  });
});
