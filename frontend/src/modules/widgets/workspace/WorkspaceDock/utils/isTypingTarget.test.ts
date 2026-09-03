import { describe, expect, it } from "vitest";

import isTypingTarget from "./isTypingTarget";

const createElement = (tagName: string, isEditable = false) => {
  const element = document.createElement(tagName);

  if (isEditable) element.setAttribute("contenteditable", "true");

  return element;
};

describe("isTypingTarget", () => {
  it.each(["input", "textarea", "select"])(
    "%s는 적고 있는 곳이다",
    (tagName) => {
      expect(isTypingTarget(createElement(tagName))).toBe(true);
    },
  );

  it("contenteditable 요소도 적고 있는 곳이다", () => {
    expect(isTypingTarget(createElement("div", true))).toBe(true);
  });

  it("일반 요소는 적고 있는 곳이 아니다", () => {
    expect(isTypingTarget(createElement("div"))).toBe(false);
    expect(isTypingTarget(createElement("button"))).toBe(false);
  });

  it("요소가 아니면 적고 있는 곳이 아니다", () => {
    expect(isTypingTarget(null)).toBe(false);
    expect(isTypingTarget(document)).toBe(false);
  });
});
