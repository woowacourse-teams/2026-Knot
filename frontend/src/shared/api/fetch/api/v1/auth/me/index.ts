import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 로그인한 회원 정보 조회 API 경로
 */
export const AUTH_ME_API_PATH = "/api/v1/auth/me";

/**
 * @public
 * @category Types
 * @interface GetMeApiResponse
 * @description 로그인한 회원 정보 조회 응답 타입
 * @property {number} memberId - 회원 ID
 * @property {string} nickname - 닉네임
 * @property {string} profileImageUrl - 프로필 이미지 URL
 */
export interface GetMeApiResponse {
  memberId: number;
  nickname: string;
  profileImageUrl: string;
}

/**
 * @public
 * @category Auth
 * @description 로그인한 회원의 정보를 조회합니다
 * @returns 회원 ID·닉네임·프로필 이미지 URL
 * @example
 * const me = await getMeApi();
 * console.log(me.nickname);
 */
export const getMeApi = async () => {
  const response = await httpClient<GetMeApiResponse>({
    method: "get",
    url: AUTH_ME_API_PATH,
  });

  return response.data;
};
