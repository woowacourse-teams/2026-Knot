import axios from "axios";

import { INVITE_CODE_ERROR_MESSAGE } from "../constants/inviteCode";

/**
 * 초대 코드 미리보기 실패를 사용자에게 보여줄 문구로 바꿉니다.
 *
 * 서버가 응답 본문을 주지 않아 상태 코드로만 구분해요.
 * 404·429 외의 실패는 코드가 틀렸다고 단정할 수 없어 확인하지 못했다는 문구로 묶어요.
 */
export const getPreviewErrorMessage = (error: unknown) => {
  if (!axios.isAxiosError(error)) return INVITE_CODE_ERROR_MESSAGE.unknown;

  switch (error.response?.status) {
    case 404:
      return INVITE_CODE_ERROR_MESSAGE.notFound;
    case 429:
      return INVITE_CODE_ERROR_MESSAGE.tooManyRequests;
    default:
      return INVITE_CODE_ERROR_MESSAGE.unknown;
  }
};
