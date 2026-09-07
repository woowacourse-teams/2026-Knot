import { describe, expect, it, vi, beforeEach } from "vitest";
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const userDataDir = mkdtempSync(join(tmpdir(), "knot-token-store-"));

const safeStorage = {
  isEncryptionAvailable: vi.fn(() => true),
  // 실제 암호화 대신 뒤집기만 한다. 파일에 평문이 그대로 남지 않는지 확인할 수 있다
  encryptString: vi.fn((value: string) => Buffer.from(value).reverse()),
  decryptString: vi.fn((buffer: Buffer) => Buffer.from(buffer).reverse().toString()),
};

vi.mock("electron", () => ({
  app: { getPath: () => userDataDir },
  safeStorage,
}));

vi.mock("electron-log/main", () => ({
  default: {
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    transports: { file: { getFile: () => ({ path: "" }) }, console: {} },
  },
}));

const { clearToken, readToken, writeToken } = await import("../src/main/tokenStore");

const tokenFilePath = join(userDataDir, "auth.bin");

beforeEach(() => {
  safeStorage.isEncryptionAvailable.mockReturnValue(true);
  clearToken();
});

describe("암호화를 쓸 수 있을 때", () => {
  it("저장한 토큰을 그대로 다시 읽는다", () => {
    writeToken("jwt-token");

    expect(readToken()).toBe("jwt-token");
  });

  it("파일에 평문을 남기지 않는다", () => {
    writeToken("jwt-token");

    expect(safeStorage.encryptString).toHaveBeenCalledWith("jwt-token");
    expect(readFileSync(tokenFilePath).toString("utf8")).not.toContain("jwt-token");
  });

  it("저장한 적이 없으면 null을 돌려준다", () => {
    expect(readToken()).toBeNull();
  });

  it("지우면 파일까지 없앤다", () => {
    writeToken("jwt-token");

    clearToken();

    expect(existsSync(tokenFilePath)).toBe(false);
    expect(readToken()).toBeNull();
  });

  it("복호화에 실패하면 로그인하지 않은 것으로 다룬다", () => {
    writeFileSync(tokenFilePath, Buffer.from("깨진 파일"));
    safeStorage.decryptString.mockImplementationOnce(() => {
      throw new Error("복호화 실패");
    });

    expect(readToken()).toBeNull();
  });
});

describe("암호화를 쓸 수 없을 때", () => {
  beforeEach(() => {
    safeStorage.isEncryptionAvailable.mockReturnValue(false);
  });

  it("파일을 만들지 않고 메모리로만 들고 있는다", () => {
    writeToken("jwt-token");

    expect(existsSync(tokenFilePath)).toBe(false);
    expect(readToken()).toBe("jwt-token");
  });

  it("지우면 메모리에서도 사라진다", () => {
    writeToken("jwt-token");

    clearToken();

    expect(readToken()).toBeNull();
  });
});
