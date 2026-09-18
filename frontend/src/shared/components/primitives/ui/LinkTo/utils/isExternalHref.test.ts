import { describe, it, expect } from "vitest";

import { isExternalHref } from "./isExternalHref";

describe("isExternalHref", () => {
  it("http, https 스킴을 가지면 외부 링크로 판단한다", () => {
    expect(isExternalHref("https://www.notion.so/page")).toBe(true);
    expect(isExternalHref("http://example.com")).toBe(true);
  });

  it("mailto, tel 같은 다른 스킴을 가져도 외부 링크로 판단한다", () => {
    expect(isExternalHref("mailto:knot@example.com")).toBe(true);
    expect(isExternalHref("tel:01012345678")).toBe(true);
  });

  it("프로토콜 상대 경로(//)도 외부 링크로 판단한다", () => {
    expect(isExternalHref("//example.com/page")).toBe(true);
  });

  it("슬래시로 시작하는 앱 내부 경로는 외부 링크가 아니다", () => {
    expect(isExternalHref("/workspace/1/chat")).toBe(false);
  });

  it("상대 경로, 쿼리, 해시는 외부 링크가 아니다", () => {
    expect(isExternalHref("chat/1")).toBe(false);
    expect(isExternalHref("?sort=relevance")).toBe(false);
    expect(isExternalHref("#section")).toBe(false);
  });

  it("앞뒤 공백이 있어도 스킴을 인식한다", () => {
    expect(isExternalHref("  https://example.com  ")).toBe(true);
  });
});
