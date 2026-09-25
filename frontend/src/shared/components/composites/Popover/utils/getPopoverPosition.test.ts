import { describe, expect, it } from "vitest";

import { getPopoverPosition } from "./getPopoverPosition";

const viewport = { width: 1440, height: 900 };
const popoverSize = { width: 200, height: 120 };

describe("getPopoverPosition", () => {
  it("bottom-end면 트리거 8px 아래에 오른쪽 끝을 맞춰 띄운다", () => {
    const triggerRect = { top: 100, right: 1100, bottom: 120, left: 1000 };

    expect(
      getPopoverPosition({
        triggerRect,
        popoverSize,
        viewport,
        placement: "bottom-end",
      }),
    ).toEqual({ top: 128, left: 900 });
  });

  it("bottom-start면 트리거의 왼쪽 끝에 맞춘다", () => {
    const triggerRect = { top: 100, right: 1100, bottom: 120, left: 1000 };

    expect(
      getPopoverPosition({
        triggerRect,
        popoverSize,
        viewport,
        placement: "bottom-start",
      }),
    ).toEqual({ top: 128, left: 1000 });
  });

  it("아래에 띄워도 화면 아래 끝까지 16px이 남으면 뒤집지 않는다", () => {
    // 아래 공간 144 = 간격 8 + 높이 120 + 16. 카드 아래 끝은 884로 화면 끝(900)에서 16px
    const triggerRect = { top: 736, right: 1100, bottom: 756, left: 1000 };

    expect(
      getPopoverPosition({
        triggerRect,
        popoverSize,
        viewport,
        placement: "bottom-end",
      }),
    ).toEqual({ top: 764, left: 900 });
  });

  it("화면 아래 끝까지 16px이 남지 않으면 트리거 8px 위로 뒤집는다", () => {
    // 아래 공간 143 = 144보다 1px 모자람
    const triggerRect = { top: 737, right: 1100, bottom: 757, left: 1000 };

    expect(
      getPopoverPosition({
        triggerRect,
        popoverSize,
        viewport,
        placement: "bottom-end",
      }),
    ).toEqual({ top: 609, left: 900 });
  });

  it("아래 공간이 모자라도 위쪽이 더 좁으면 뒤집지 않는다", () => {
    // 높이 500: 아래 공간 480은 기준 524보다 작지만, 위쪽 400은 그보다 더 좁음
    const tallPopoverSize = { width: 200, height: 500 };
    const triggerRect = { top: 400, right: 1100, bottom: 420, left: 1000 };

    expect(
      getPopoverPosition({
        triggerRect,
        popoverSize: tallPopoverSize,
        viewport,
        placement: "bottom-end",
      }),
    ).toEqual({ top: 428, left: 900 });
  });

  it("오른쪽 가장자리를 넘으면 정렬은 두고 16px 안쪽으로 민다", () => {
    const triggerRect = { top: 100, right: 1400, bottom: 120, left: 1300 };

    expect(
      getPopoverPosition({
        triggerRect,
        popoverSize,
        viewport,
        placement: "bottom-start",
      }),
    ).toEqual({ top: 128, left: 1224 });
  });

  it("왼쪽 가장자리를 넘으면 16px 안쪽으로 민다", () => {
    const triggerRect = { top: 100, right: 150, bottom: 120, left: 50 };

    expect(
      getPopoverPosition({
        triggerRect,
        popoverSize,
        viewport,
        placement: "bottom-end",
      }),
    ).toEqual({ top: 128, left: 16 });
  });

  it("가장자리에서 딱 16px이면 그대로 둔다", () => {
    const triggerRect = { top: 100, right: 216, bottom: 120, left: 116 };

    expect(
      getPopoverPosition({
        triggerRect,
        popoverSize,
        viewport,
        placement: "bottom-end",
      }),
    ).toEqual({ top: 128, left: 16 });
  });

  it("화면이 팝오버보다 좁으면 왼쪽 16px에 붙인다", () => {
    const narrowViewport = { width: 200, height: 900 };
    const triggerRect = { top: 100, right: 150, bottom: 120, left: 50 };

    expect(
      getPopoverPosition({
        triggerRect,
        popoverSize,
        viewport: narrowViewport,
        placement: "bottom-end",
      }),
    ).toEqual({ top: 128, left: 16 });
  });
});
