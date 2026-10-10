import { logRequestError } from "@utils/logRequestError";
import { useEffect } from "react";

/** 현재 녹음 조회 실패를 콘솔에 남겨요. 실패 안내는 기획 논의 전이라 화면에는 빈 상태만 보여 줘요. */
export const useLogCurrentRecordingError = (error: unknown) => {
  useEffect(() => {
    if (error) logRequestError("현재 녹음 조회", error);
  }, [error]);
};
