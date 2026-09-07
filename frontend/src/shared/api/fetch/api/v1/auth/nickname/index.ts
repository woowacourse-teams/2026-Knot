import {
  PostNicknameResponseDto,
  type PostNicknameRequestDto,
  type PostNicknameResponseRaw,
} from "@api/dto/auth";
import { getOnboardingToken } from "@api/authToken";
import { httpClient } from "@api/httpClient";

export const AUTH_NICKNAME_API_PATH = "/api/v1/auth/nickname";

/**
 * @description GitHub 로그인을 마친 신규 사용자의 닉네임을 등록해 회원가입을 완료합니다. 로그인 직후 받은 온보딩 토큰으로 본인을 증명하므로 `Authorization` 헤더에 액세스 토큰이 아니라 그 토큰을 담아 보내요. 성공하면 서버가 액세스 토큰을 응답 본문으로 돌려줍니다. 실패는 `400` 닉네임 형식 오류 · `401` 온보딩 토큰 없음/만료로 구분해요
 * @param body - 닉네임 설정 요청 본문
 * @returns 발급된 액세스 토큰과 만료까지 남은 초
 * @example
 * const { accessToken } = await completeNicknameApi(new PostNicknameRequestDto({ nickname: "노티드" }));
 */
export const completeNicknameApi = async (body: PostNicknameRequestDto) => {
  const response = await httpClient<PostNicknameResponseRaw>({
    method: "post",
    url: AUTH_NICKNAME_API_PATH,
    data: body,
    headers: { Authorization: `Bearer ${getOnboardingToken() ?? ""}` },
  });

  return new PostNicknameResponseDto(response.data);
};
