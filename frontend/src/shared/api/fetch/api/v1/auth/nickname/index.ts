import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 닉네임 설정 완료 API 경로
 */
export const AUTH_NICKNAME_API_PATH = "/api/v1/auth/nickname";

/**
 * @public
 * @category Types
 * @interface PostNicknameApiRequest
 * @description 닉네임 설정 완료 요청 타입
 * @property {string} nickname - 닉네임 (최대 20자)
 */
export interface PostNicknameApiRequest {
  nickname: string;
}

/**
 * @public
 * @category Auth
 * @description 첫 로그인 뒤 닉네임을 정해 가입을 마칩니다. 성공 시 응답 본문은 없어요
 * @param body - 닉네임
 * @example
 * await completeNicknameApi({ nickname: "노티드" });
 */
export const completeNicknameApi = async (body: PostNicknameApiRequest) => {
  await httpClient({
    method: "post",
    url: AUTH_NICKNAME_API_PATH,
    data: body,
  });
};
