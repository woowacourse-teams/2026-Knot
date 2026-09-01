import { getMeApi } from "@api/fetch/api/v1/auth/me";
import { authKeys } from "@api/queryKey/auth";
import { useQuery } from "@tanstack/react-query";

/**
 * 로그인한 회원 정보를 조회합니다.
 *
 * 인증 쿠키는 `httpOnly`라 자바스크립트가 읽을 수 없어요. 그래서 로그인했는지 여부도
 * 이 조회의 성공·실패로 판단합니다. 로그인하지 않았으면 401이 오고 `error`에 담겨요.
 * 401은 `queryClient`가 재시도하지 않습니다.
 */
const useMeQuery = () => {
  return useQuery({
    queryKey: authKeys.me(),
    queryFn: getMeApi,
  });
};

export default useMeQuery;
