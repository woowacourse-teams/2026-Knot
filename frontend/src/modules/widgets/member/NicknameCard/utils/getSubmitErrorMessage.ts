import { HTTP_ERROR_TYPE, isHttpError } from "@api/httpClient/error";

import { NICKNAME_SUBMIT_ERROR_MESSAGE } from "../constants/nickname";

/**
 * 닉네임 등록 실패를 사용자에게 보여줄 문구로 바꿉니다.
 *
 * 서버가 응답 본문을 주지 않아 실패 이유(`HTTP_ERROR_TYPE`)로만 구분해요.
 * 401은 여기서 다루지 않습니다. `isUnauthorizedError`(`@utils/isUnauthorizedError`)로 먼저 걸러 로그인으로 보내세요.
 */
export const getSubmitErrorMessage = (error: unknown) => {
  if (!isHttpError(error)) return NICKNAME_SUBMIT_ERROR_MESSAGE.unknown;

  switch (error.type) {
    case HTTP_ERROR_TYPE.badRequest:
      return NICKNAME_SUBMIT_ERROR_MESSAGE.invalid;
    case HTTP_ERROR_TYPE.forbidden:
      return NICKNAME_SUBMIT_ERROR_MESSAGE.forbidden;
    default:
      return NICKNAME_SUBMIT_ERROR_MESSAGE.unknown;
  }
};
