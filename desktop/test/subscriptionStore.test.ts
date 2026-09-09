import { describe, expect, it } from "vitest";
import { createSubscriptionStore, parseCredentials } from "../src/main/llm/subscriptionStore";
import type { SecretStore } from "../src/main/secretStore";

function memoryStore(): SecretStore & { value: string | null } {
  return {
    value: null,
    read() {
      return this.value;
    },
    write(value) {
      this.value = value;
    },
    clear() {
      this.value = null;
    },
  };
}

const CREDENTIALS = {
  accessToken: "sk-ant-oat01-a",
  refreshToken: "sk-ant-ort01-r",
  expiresAt: "2026-09-09T20:00:00.000Z",
};

describe("createSubscriptionStore", () => {
  it("저장한 자격증명을 그대로 읽는다", () => {
    const secret = memoryStore();
    const store = createSubscriptionStore(secret);

    store.write(CREDENTIALS);

    expect(store.read()).toEqual(CREDENTIALS);
    expect(secret.value).toContain('"refreshToken"');
  });

  it("없으면 null이고, 지우면 다시 null이다", () => {
    const store = createSubscriptionStore(memoryStore());
    expect(store.read()).toBeNull();
    store.write(CREDENTIALS);
    store.clear();
    expect(store.read()).toBeNull();
  });
});

describe("parseCredentials", () => {
  it.each([
    ["JSON 아님", "not json"],
    ["객체 아님", '"x"'],
    ["accessToken 없음", JSON.stringify({ refreshToken: "r", expiresAt: CREDENTIALS.expiresAt })],
    ["refreshToken 빈 문자열", JSON.stringify({ ...CREDENTIALS, refreshToken: "" })],
    ["expiresAt 날짜 아님", JSON.stringify({ ...CREDENTIALS, expiresAt: "언젠가" })],
  ])("깨진 값은 null이다: %s", (_label, raw) => {
    expect(parseCredentials(raw)).toBeNull();
  });
});
