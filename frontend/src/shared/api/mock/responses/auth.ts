import type { CsrfTokenResponse, MeResponse } from "@api/mock/types/auth";

export const meResponse = {
  memberId: 1,
  nickname: "노티드",
  profileImageUrl: "https://avatars.githubusercontent.com/u/583231?v=4",
} satisfies MeResponse;

export const csrfTokenResponse = {
  token: "mock-csrf-token",
} satisfies CsrfTokenResponse;
