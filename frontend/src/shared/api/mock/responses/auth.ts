import type { GetCsrfTokenApiResponse } from "@api/fetch/api/v1/auth/csrf";
import type { GetMeApiResponse } from "@api/fetch/api/v1/auth/me";

export const meResponse = {
  memberId: 1,
  nickname: "노티드",
  profileImageUrl: "https://avatars.githubusercontent.com/u/583231?v=4",
} satisfies GetMeApiResponse;

export const csrfTokenResponse = {
  token: "mock-csrf-token",
} satisfies GetCsrfTokenApiResponse;
