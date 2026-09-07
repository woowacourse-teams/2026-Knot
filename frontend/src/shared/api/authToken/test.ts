import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  clearAccessToken,
  clearOnboardingToken,
  getAccessToken,
  getOnboardingToken,
  receiveLoginTokens,
  setAccessToken,
} from ".";

const ACCESS_TOKEN_STORAGE_KEY = "knot.accessToken";
const ONBOARDING_TOKEN_STORAGE_KEY = "knot.onboardingToken";

/** 데스크톱 셸이 넣어 주는 저장소를 흉내내요. 웹에서는 이 값이 없습니다 */
const stubDesktopAuth = () => {
  let stored: string | null = null;

  const auth = {
    getToken: vi.fn(() => Promise.resolve(stored)),
    setToken: vi.fn((token: string) => {
      stored = token;
      return Promise.resolve();
    }),
    clearToken: vi.fn(() => {
      stored = null;
      return Promise.resolve();
    }),
  };

  window.knotDesktop = {
    version: "0.1.0",
    platform: "darwin",
    env: "local",
    openExternal: vi.fn(() => Promise.resolve()),
    onDeepLink: vi.fn(() => () => undefined),
    getPendingDeepLink: vi.fn(() => Promise.resolve(null)),
    auth,
  };

  return auth;
};

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  window.history.replaceState(null, "", "/");
});

afterEach(() => {
  delete window.knotDesktop;
});

describe("액세스 토큰", () => {
  it("브라우저에서는 localStorage에 저장하고 꺼낸다", async () => {
    await setAccessToken("jwt-token");

    expect(window.localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)).toBe(
      "jwt-token",
    );
    await expect(getAccessToken()).resolves.toBe("jwt-token");
  });

  it("저장한 적이 없으면 null을 돌려준다", async () => {
    await expect(getAccessToken()).resolves.toBeNull();
  });

  it("로그아웃하면 저장소에서 지운다", async () => {
    await setAccessToken("jwt-token");

    await clearAccessToken();

    expect(window.localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)).toBeNull();
    await expect(getAccessToken()).resolves.toBeNull();
  });

  it("데스크톱 셸에서는 localStorage 대신 셸이 준 저장소를 쓴다", async () => {
    const desktopAuth = stubDesktopAuth();

    await setAccessToken("desktop-token");

    expect(desktopAuth.setToken).toHaveBeenCalledWith("desktop-token");
    expect(window.localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)).toBeNull();
    await expect(getAccessToken()).resolves.toBe("desktop-token");
  });

  it("데스크톱 셸에서 로그아웃하면 셸 저장소를 비운다", async () => {
    const desktopAuth = stubDesktopAuth();
    await setAccessToken("desktop-token");

    await clearAccessToken();

    expect(desktopAuth.clearToken).toHaveBeenCalled();
    await expect(getAccessToken()).resolves.toBeNull();
  });
});

describe("로그인 리다이렉트 프래그먼트", () => {
  it("액세스 토큰을 저장하고 주소창에서 지운다", async () => {
    window.history.replaceState(
      null,
      "",
      "/?next=home#access_token=jwt-token&token_type=Bearer&expires_in=3600",
    );

    await receiveLoginTokens();

    await expect(getAccessToken()).resolves.toBe("jwt-token");
    expect(window.location.hash).toBe("");
    expect(window.location.search).toBe("?next=home");
  });

  it("신규 가입자의 온보딩 토큰은 액세스 토큰과 다른 자리에 둔다", async () => {
    window.history.replaceState(
      null,
      "",
      "/onboarding#onboarding_token=onboarding-token&expires_in=600",
    );

    await receiveLoginTokens();

    expect(getOnboardingToken()).toBe("onboarding-token");
    expect(window.sessionStorage.getItem(ONBOARDING_TOKEN_STORAGE_KEY)).toBe(
      "onboarding-token",
    );
    await expect(getAccessToken()).resolves.toBeNull();
    expect(window.location.hash).toBe("");
  });

  it("토큰이 없는 프래그먼트는 건드리지 않는다", async () => {
    window.history.replaceState(null, "", "/#section");

    await receiveLoginTokens();

    expect(window.location.hash).toBe("#section");
  });
});

describe("온보딩 토큰", () => {
  it("가입을 마치면 지운다", async () => {
    window.history.replaceState(
      null,
      "",
      "/onboarding#onboarding_token=onboarding-token&expires_in=600",
    );
    await receiveLoginTokens();

    clearOnboardingToken();

    expect(getOnboardingToken()).toBeNull();
  });
});
