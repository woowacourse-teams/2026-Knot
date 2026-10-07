import { isHttpError } from "@api/httpClient/error";
import { QueryClient } from "@tanstack/react-query";

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 4xx 오류에 대해서는 재시도 하지 않음
      retry: (failureCount, error) => {
        if (isHttpError(error) && error.isClientError) return false;
        return failureCount < 2;
      },
    },
  },
});
