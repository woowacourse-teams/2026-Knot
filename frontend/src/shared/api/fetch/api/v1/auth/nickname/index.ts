import { httpClient } from "@api/httpClient";

export const AUTH_NICKNAME_API_PATH = "/api/v1/auth/nickname";

export interface PostNicknameApiRequest {
  /** 최대 20자 */
  nickname: string;
}

/**
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
