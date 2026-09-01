import axios from "axios";

import { NICKNAME_SUBMIT_ERROR_MESSAGE } from "../constants/nickname";

/** 온보딩 토큰이 없거나 유효하지 않은 상태. 문구 대신 로그인 화면으로 돌려보내야 해요. */
export const isUnauthorizedError = (error: unknown) =>
  axios.isAxiosError(error) && error.response?.status === 401;

/**
 * 닉네임 등록 실패를 사용자에게 보여줄 문구로 바꿉니다.
 *
 * 서버가 응답 본문을 주지 않아 상태 코드로만 구분해요.
 * 401은 여기서 다루지 않습니다. `isUnauthorizedError`로 먼저 걸러 로그인으로 보내세요.
 */
export const getSubmitErrorMessage = (error: unknown) => {
  if (!axios.isAxiosError(error)) return NICKNAME_SUBMIT_ERROR_MESSAGE.unknown;

  switch (error.response?.status) {
    case 400:
      return NICKNAME_SUBMIT_ERROR_MESSAGE.invalid;
    case 403:
      return NICKNAME_SUBMIT_ERROR_MESSAGE.forbidden;
    default:
      return NICKNAME_SUBMIT_ERROR_MESSAGE.unknown;
  }
};
