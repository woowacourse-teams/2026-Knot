import { describe, expect, it } from "vitest";
import { createDeviceSessionStore, parseSession } from "../src/main/auth/deviceSessionStore";
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

const SESSION = {
  refreshToken: "rt",
  sessionId: "7",
  deviceName: "my-mac",
  accessTokenExpiresAt: "2026-09-09T10:00:00.000Z",
};

describe("createDeviceSessionStore", () => {
  it("저장한 세션을 그대로 읽는다", () => {
    const secret = memoryStore();
    const store = createDeviceSessionStore(secret);

    store.write(SESSION);

    expect(store.read()).toEqual(SESSION);
    expect(secret.value).toContain('"refreshToken"');
  });

  it("없으면 null이고, 지우면 다시 null이다", () => {
    const store = createDeviceSessionStore(memoryStore());
    expect(store.read()).toBeNull();

    store.write(SESSION);
    store.clear();

    expect(store.read()).toBeNull();
  });

  it("깨진 값은 null로 다룬다", () => {
    const secret = memoryStore();
    secret.value = "{not json";
    expect(createDeviceSessionStore(secret).read()).toBeNull();
  });
});

describe("parseSession", () => {
  it("필수 필드가 빠지면 null이다", () => {
    expect(parseSession(JSON.stringify({ ...SESSION, refreshToken: "" }))).toBeNull();
    expect(parseSession(JSON.stringify({ ...SESSION, sessionId: 7 }))).toBeNull();
    expect(parseSession(JSON.stringify({ refreshToken: "rt" }))).toBeNull();
    expect(parseSession("null")).toBeNull();
  });
});
