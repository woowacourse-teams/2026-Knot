import { describe, expect, it, vi } from "vitest";
import { logMock } from "./helpers/logMock";

vi.mock("electron", () => ({ WebContentsView: class {}, shell: { openExternal: vi.fn() } }));
vi.mock("electron-log/main", () => ({ default: logMock }));

const { LOGIN_HEADER_HEIGHT, resolveLoginViewBounds } = await import("../src/main/auth/loginView");

// 2026-09-10 Q68 재개정. 로그인 화면은 창이 아니라 메인 창 안 뷰다. 뷰는 content 영역에서
// 상단 헤더 띠만 남기고 덮으며, 그 띠에 SPA가 제목과 "취소"를 그린다(기획서 5.2).
describe("resolveLoginViewBounds", () => {
  it("헤더 띠만 남기고 content 영역을 덮는다", () => {
    expect(resolveLoginViewBounds({ width: 1200, height: 800 })).toEqual({
      x: 0,
      y: LOGIN_HEADER_HEIGHT,
      width: 1200,
      height: 800 - LOGIN_HEADER_HEIGHT,
    });
  });

  it("창이 헤더보다 작아도 음수 크기를 만들지 않는다", () => {
    expect(resolveLoginViewBounds({ width: 0, height: 10 })).toEqual({
      x: 0,
      y: LOGIN_HEADER_HEIGHT,
      width: 0,
      height: 0,
    });
  });
});
