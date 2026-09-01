import { httpClient } from "@api/httpClient";

/** 닉네임 설정 API 경로 */
export const NICKNAME_API_PATH = "/api/v1/auth/nickname";

/**
 * 닉네임 설정 요청 본문
 *
 * @property nickname - 사용자가 정한 닉네임. 비어 있으면 안 되고 최대 20자
 */
export interface PostNicknameApiRequest {
  nickname: string;
}

/**
 * GitHub 로그인을 마친 신규 사용자의 닉네임을 등록해 회원가입을 완료합니다.
 *
 * 로그인 직후 받은 `KNOT_NICKNAME_TOKEN` 쿠키로 본인을 증명하므로 별도 인자가 없어요.
 * 성공하면 서버가 `__Host-KNOT_ACCESS_TOKEN`을 발급하고 온보딩 토큰을 만료시킵니다.
 * 응답 본문이 없어(204) 돌려줄 값도 없습니다.
 *
 * CSRF 토큰은 `httpClient`의 인터셉터가 붙이므로 여기서 다루지 않아요.
 *
 * 실패는 상태 코드로 구분해요.
 * `400` 닉네임 형식 오류 · `401` 온보딩 토큰 없음/만료 · `403` CSRF 토큰 문제
 * (403은 인터셉터가 토큰을 새로 받아 한 번 다시 보낸 뒤에도 실패한 경우예요)
 *
 * @param request - 등록할 닉네임
 * @example
 * await postNicknameApi({ nickname: "동성" });
 */
export const postNicknameApi = async (request: PostNicknameApiRequest) => {
  await httpClient({
    method: "post",
    url: NICKNAME_API_PATH,
    data: request,
  });
};
