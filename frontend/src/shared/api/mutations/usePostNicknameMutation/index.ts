import { completeNicknameApi } from "@api/fetch/api/v1/auth/nickname";
import { useMutation } from "@tanstack/react-query";

/**
 * 닉네임을 등록해 회원가입을 완료하는 뮤테이션 훅.
 *
 * 성공하면 서버가 접근 토큰 쿠키를 발급하므로, 이후 요청은 별도 처리 없이 인증됩니다.
 */
const usePostNicknameMutation = () => {
  return useMutation({
    mutationFn: completeNicknameApi,
  });
};

export default usePostNicknameMutation;
