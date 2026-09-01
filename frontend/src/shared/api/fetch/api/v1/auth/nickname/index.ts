import type { PostNicknameRequestDto } from "@api/dto/auth";
import { httpClient } from "@api/httpClient";

export const AUTH_NICKNAME_API_PATH = "/api/v1/auth/nickname";

/**
 * @description GitHub 로그인을 마친 신규 사용자의 닉네임을 등록해 회원가입을 완료합니다. 로그인 직후 받은 온보딩 토큰 쿠키로 본인을 증명하므로 별도 인자가 없고, 성공하면 서버가 접근 토큰 쿠키를 발급해요. 응답 본문은 없어요(204). 실패는 `400` 닉네임 형식 오류 · `401` 온보딩 토큰 없음/만료 · `403` CSRF 토큰 문제로 구분해요
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
