import axios from "axios";

import { WORKSPACE_SUBMIT_ERROR_MESSAGE } from "../constants/workspaceName";

/**
 * 워크스페이스 생성 실패를 사용자에게 보여줄 문구로 바꿉니다.
 *
 * 서버가 응답 본문을 주지 않아 상태 코드로만 구분해요.
 * 401은 여기서 다루지 않습니다. `isUnauthorizedError`(`@utils/isUnauthorizedError`)로 먼저 걸러 로그인으로 보내세요.
 */
export const getSubmitErrorMessage = (error: unknown) => {
  if (!axios.isAxiosError(error)) {
    return WORKSPACE_SUBMIT_ERROR_MESSAGE.unknown;
  }

  switch (error.response?.status) {
    case 400:
      return WORKSPACE_SUBMIT_ERROR_MESSAGE.invalid;
    case 403:
      return WORKSPACE_SUBMIT_ERROR_MESSAGE.forbidden;
    default:
      return WORKSPACE_SUBMIT_ERROR_MESSAGE.unknown;
  }
};
