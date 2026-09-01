import axios from "axios";

import { NOTION_CONNECT_ERROR_MESSAGE } from "../constants/notionConnect";

/**
 * Notion 연결 시작 실패를 사용자에게 보여줄 문구로 바꿉니다.
 *
 * 서버가 응답 본문을 주지 않아 상태 코드로만 구분해요.
 * 401·404는 여기서 다루지 않습니다. 문구 대신 로그인·워크스페이스 선택 화면으로 보내야 해서
 * 쓰는 쪽(`useNotionConnect`)이 먼저 걸러요.
 */
export const getConnectErrorMessage = (error: unknown) => {
  if (!axios.isAxiosError(error)) {
    return NOTION_CONNECT_ERROR_MESSAGE.unknown;
  }

  return error.response?.status === 403
    ? NOTION_CONNECT_ERROR_MESSAGE.forbidden
    : NOTION_CONNECT_ERROR_MESSAGE.unknown;
};
