import { describe, expect, it } from "vitest";
import { UPDATE_INTERVAL, UPDATE_REPO, resolveUpdatePolicy } from "../src/main/updatePolicy";

describe("resolveUpdatePolicy", () => {
  it("패키징된 prod macOS·Windows 빌드에서만 켠다", () => {
    for (const platform of ["darwin", "win32"]) {
      expect(resolveUpdatePolicy({ isPackaged: true, platform, envName: "prod" })).toEqual({
        enabled: true,
        repo: UPDATE_REPO,
        updateInterval: UPDATE_INTERVAL,
      });
    }
  });

  it("미패키징 빌드에서는 끈다", () => {
    const policy = resolveUpdatePolicy({ isPackaged: false, platform: "darwin", envName: "prod" });
    expect(policy.enabled).toBe(false);
  });

  it("Linux에서는 끈다(내장 autoUpdater 미지원)", () => {
    const policy = resolveUpdatePolicy({ isPackaged: true, platform: "linux", envName: "prod" });
    expect(policy.enabled).toBe(false);
    expect(policy.enabled === false && policy.reason).toContain("linux");
  });

  it("dev·local 빌드는 같은 저장소의 릴리스로 덮어쓰이면 안 되므로 끈다", () => {
    for (const envName of ["dev", "local"] as const) {
      const policy = resolveUpdatePolicy({ isPackaged: true, platform: "darwin", envName });
      expect(policy.enabled).toBe(false);
    }
  });

  it("저장소 슬러그는 owner/repo 형식이다", () => {
    expect(UPDATE_REPO).toMatch(/^[\w.-]+\/[\w.-]+$/);
  });
});
