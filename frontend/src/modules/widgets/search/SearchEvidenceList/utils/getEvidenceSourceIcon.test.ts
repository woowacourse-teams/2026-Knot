import { describe, it, expect } from "vitest";
import {
  EVIDENCE_SOURCE_ICON,
  getEvidenceSourceIcon,
} from "./getEvidenceSourceIcon";
import type { EvidenceSource } from "../types/searchEvidence";

describe("getEvidenceSourceIcon", () => {
  it("evidenceSource가 notion이면 notion 아이콘 컴포넌트를 반환한다", () => {
    expect(getEvidenceSourceIcon("notion")).toBe(EVIDENCE_SOURCE_ICON.notion);
  });

  it("evidenceSource를 받지 못하면 notion 아이콘 컴포넌트를 반환한다", () => {
    expect(getEvidenceSourceIcon()).toBe(EVIDENCE_SOURCE_ICON.notion);
  });

  it("등록되지 않은 evidenceSource를 받으면 notion 아이콘 컴포넌트를 반환한다", () => {
    const unregisteredSource = "slack" as EvidenceSource;

    expect(getEvidenceSourceIcon(unregisteredSource)).toBe(
      EVIDENCE_SOURCE_ICON.notion,
    );
  });
});
