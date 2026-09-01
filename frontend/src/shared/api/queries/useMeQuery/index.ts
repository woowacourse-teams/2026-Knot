import { getMeApi } from "@api/fetch/api/v1/auth/me";
import { authKeys } from "@api/queryKey/auth";
import { useQuery } from "@tanstack/react-query";

/**
 * 로그인한 회원의 정보(닉네임·프로필 이미지 URL)를 조회하는 쿼리 훅.
 *
 * 홈 인사말처럼 로그인한 회원 자신을 보여주는 곳에서 써요. 인자가 없어 키도 `me` 하나예요.
 */
const useMeQuery = () => {
  return useQuery({
    queryKey: authKeys.me(),
    queryFn: getMeApi,
  });
};

export default useMeQuery;
