import { useMutation } from "@tanstack/react-query";

import { clearOnboardingToken, setAccessToken } from "@api/authToken";
import {
  PostNicknameRequestDto,
  type PostNicknameRequestInput,
} from "@api/dto/auth";
import { completeNicknameApi } from "@api/fetch/api/v1/auth/nickname";

/**
 * 닉네임을 등록해 회원가입을 완료하는 뮤테이션 훅.
 *
 * 서버가 응답 본문으로 액세스 토큰을 주므로 여기서 저장소에 넣습니다. 이 저장이 끝나야
 * 다음 요청부터 `Authorization` 헤더가 붙어요. 다 쓴 온보딩 토큰은 함께 버립니다.
 */
const usePostNicknameMutation = () => {
  return useMutation({
    mutationFn: async (input: PostNicknameRequestInput) => {
      const response = await completeNicknameApi(
        new PostNicknameRequestDto(input),
      );

      await setAccessToken(response.accessToken);
      clearOnboardingToken();

      return response;
    },
  });
};

export default usePostNicknameMutation;
