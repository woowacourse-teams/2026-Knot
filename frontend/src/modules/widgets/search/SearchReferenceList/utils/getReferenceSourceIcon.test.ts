import { describe, it, expect } from "vitest";
import Notion from "@/assets/icons/notion.svg";
import { getReferenceSourceIcon } from "./getReferenceSourceIcon";
import type { ReferenceSource } from "../types/searchReference";

describe("getReferenceSourceIcon", () => {
  it("referenceSource가 notion이면 notion 아이콘 컴포넌트를 반환한다", () => {
    expect(getReferenceSourceIcon("notion")).toBe(Notion);
  });

  it("referenceSource를 받지 못하면 notion 아이콘 컴포넌트를 반환한다", () => {
    expect(getReferenceSourceIcon()).toBe(Notion);
  });

  it("등록되지 않은 referenceSource를 받으면 notion 아이콘 컴포넌트를 반환한다", () => {
    const unregisteredSource = "slack" as ReferenceSource;

    expect(getReferenceSourceIcon(unregisteredSource)).toBe(Notion);
  });
});
