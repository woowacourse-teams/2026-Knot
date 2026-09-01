import type { PostNicknameRequestDto } from "@api/dto/auth";
import { httpClient } from "@api/httpClient";

export const AUTH_NICKNAME_API_PATH = "/api/v1/auth/nickname";

/**
 * @description 첫 로그인 뒤 닉네임을 정해 가입을 마칩니다. 성공 시 응답 본문은 없어요
 * @param body - 닉네임 설정 요청 본문
 * @example
 * await completeNicknameApi(new PostNicknameRequestDto({ nickname: "노티드" }));
 */
export const completeNicknameApi = async (body: PostNicknameRequestDto) => {
  await httpClient({
    method: "post",
    url: AUTH_NICKNAME_API_PATH,
    data: body,
  });
};
