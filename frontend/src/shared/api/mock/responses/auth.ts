import type { MeResponse, NicknameResponse } from "@api/mock/types/auth";

export const meResponse = {
  memberId: 1,
  nickname: "노티드",
  profileImageUrl: "https://avatars.githubusercontent.com/u/583231?v=4",
} satisfies MeResponse;

const ACCESS_TOKEN_EXPIRES_IN_SECONDS = 3600;

export const nicknameResponse = {
  // 개발 서버의 mock 로그인 토큰과 같은 값이라야 가입 직후 요청이 인증돼요(handlers/dev)
  accessToken: "mock-access-token",
  tokenType: "Bearer",
  expiresIn: ACCESS_TOKEN_EXPIRES_IN_SECONDS,
} satisfies NicknameResponse;
