import { GetMeResponseDto, type GetMeResponseRaw } from "@api/dto/auth";
import { httpClient } from "@api/httpClient";

export const AUTH_ME_API_PATH = "/api/v1/auth/me";

/**
 * @description 로그인한 회원의 정보를 조회합니다
 * @returns 회원 ID·닉네임·프로필 이미지 URL
 * @example
 * const { nickname } = await getMeApi();
 */
export const getMeApi = async () => {
  const response = await httpClient<GetMeResponseRaw>({
    method: "get",
    url: AUTH_ME_API_PATH,
  });

  return new GetMeResponseDto(response.data);
};
