import { meResponse, nicknameResponse } from "@api/mock/responses/auth";
import { mockServer } from "@api/mock/server";
import { beforeEach, describe, expect, it } from "vitest";

import {
  devAuthHandlers,
  devNotionOAuthHandlers,
  MOCK_ACCESS_TOKEN,
  MOCK_ONBOARDING_TOKEN,
} from ".";

// 절대 URL이어야 Node fetch가 보낼 수 있어요. 핸들러는 오리진 와일드카드라 아무 오리진이나 맞아요
const ME_URL = "http://localhost:3000/api/v1/auth/me";
const NICKNAME_URL = "http://localhost:3000/api/v1/auth/nickname";
const NOTION_OAUTH_URL =
  "http://localhost:3000/api/v1/workspaces/7/notion-oauth-authorizations";

const authorization = (token?: string): Record<string, string> =>
  token === undefined ? {} : { authorization: `Bearer ${token}` };

const requestMe = (token?: string) =>
  fetch(ME_URL, { headers: authorization(token) });

const requestNickname = (token?: string) =>
  fetch(NICKNAME_URL, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...authorization(token),
    },
    body: JSON.stringify({ nickname: "노티드" }),
  });

// 개발 브라우저(browser.ts)에서만 기본 핸들러를 덮는 핸들러라 여기서도 use로 등록해 검증해요
beforeEach(() => mockServer.use(...devAuthHandlers, ...devNotionOAuthHandlers));

describe("개발 브라우저 전용 인증 핸들러", () => {
  describe("GET /api/v1/auth/me", () => {
    it("Authorization 헤더가 없으면 401을 돌려준다", async () => {
      const response = await requestMe();

      expect(response.status).toBe(401);
    });

    it("액세스 토큰이 있으면 meResponse를 돌려준다", async () => {
      const response = await requestMe(MOCK_ACCESS_TOKEN);

      expect(response.status).toBe(200);
      await expect(response.json()).resolves.toEqual(meResponse);
    });

    it("온보딩 토큰만 있으면 아직 회원이 아니므로 401을 돌려준다", async () => {
      const response = await requestMe(MOCK_ONBOARDING_TOKEN);

      expect(response.status).toBe(401);
    });
  });

  describe("POST /api/v1/auth/nickname", () => {
    it("Authorization 헤더가 없으면 401을 돌려준다", async () => {
      const response = await requestNickname();

      expect(response.status).toBe(401);
    });

    it("온보딩 토큰이 있으면 액세스 토큰을 본문으로 돌려준다", async () => {
      const response = await requestNickname(MOCK_ONBOARDING_TOKEN);

      expect(response.status).toBe(200);
      await expect(response.json()).resolves.toEqual(nicknameResponse);
    });

    it("이미 발급된 액세스 토큰으로는 가입을 마칠 수 없다", async () => {
      const response = await requestNickname(MOCK_ACCESS_TOKEN);

      expect(response.status).toBe(401);
    });
  });
});

describe("개발 브라우저 전용 Notion OAuth 핸들러", () => {
  describe("POST /api/v1/workspaces/:workspaceId/notion-oauth-authorizations", () => {
    it("실제 Notion 대신 같은 오리진의 연동 결과 화면 URL을 돌려준다", async () => {
      const response = await fetch(NOTION_OAUTH_URL, { method: "POST" });

      expect(response.status).toBe(201);
      await expect(response.json()).resolves.toEqual({
        authorizationUrl: "/workspace/7/notion-connection?result=connected",
      });
    });
  });
});
