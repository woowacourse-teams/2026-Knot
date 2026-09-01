import { httpClient } from "@api/httpClient";

export const AUTH_ME_API_PATH = "/api/v1/auth/me";

interface GetMeApiResponse {
  memberId: number;
  nickname: string;
  profileImageUrl: string;
}

/**
 * @description 로그인한 회원의 정보를 조회합니다
 * @returns 회원 ID·닉네임·프로필 이미지 URL
 * @example
 * const { nickname } = await getMeApi();
 */
export const getMeApi = async () => {
  const response = await httpClient<GetMeApiResponse>({
    method: "get",
    url: AUTH_ME_API_PATH,
  });

  return response.data;
};
