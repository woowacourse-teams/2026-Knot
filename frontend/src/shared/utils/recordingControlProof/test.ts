import { afterEach, describe, expect, it } from "vitest";

import {
  clearRecordingStartProof,
  getRecordingControlProof,
  getRecordingStartProof,
} from ".";

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

// 서버 RecordingStartRequest의 controlToken 검증식(32바이트 패딩 없는 Base64URL)
const CONTROL_TOKEN_PATTERN = /^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$/;

describe("recordingControlProof", () => {
  afterEach(() => sessionStorage.clear());

  it("시작 증명은 UUID 두 개와 서버 형식의 제어 증명으로 만든다", () => {
    const proof = getRecordingStartProof();

    expect(proof.requestId).toMatch(UUID_PATTERN);
    expect(proof.tabId).toMatch(UUID_PATTERN);
    expect(proof.controlToken).toMatch(CONTROL_TOKEN_PATTERN);
  });

  it("시작이 끝나기 전에는 같은 시작 증명을 다시 돌려준다", () => {
    expect(getRecordingStartProof()).toEqual(getRecordingStartProof());
  });

  it("시작 증명은 sessionStorage에 보관해 새로고침 뒤에도 같은 값을 읽는다", () => {
    const proof = getRecordingStartProof();
    const stored = JSON.stringify(sessionStorage);

    expect(stored).toContain(proof.requestId);
    expect(stored).toContain(proof.tabId);
    expect(stored).toContain(proof.controlToken);
  });

  it("제어 증명은 시작 때 만든 탭 ID와 제어 증명을 돌려준다", () => {
    const { tabId, controlToken } = getRecordingStartProof();

    expect(getRecordingControlProof()).toEqual({ tabId, controlToken });
  });

  it("시작 전에는 제어 증명이 없다", () => {
    expect(getRecordingControlProof()).toBeNull();
  });

  it("시작 증명을 지우면 새 요청 ID와 제어 증명을 만들고 탭 ID는 유지한다", () => {
    const before = getRecordingStartProof();

    clearRecordingStartProof();
    const after = getRecordingStartProof();

    expect(getRecordingControlProof()).toEqual({
      tabId: after.tabId,
      controlToken: after.controlToken,
    });
    expect(after.tabId).toBe(before.tabId);
    expect(after.requestId).not.toBe(before.requestId);
    expect(after.controlToken).not.toBe(before.controlToken);
  });
});
