import type { ToastControls } from "..";

/**
 * 현재 V2 MVP 토스트의 종류와 문구. show(TOAST_MESSAGES.COPY_FAILED)처럼 사용해요.
 *
 * 2026-10-07 확인: Notion 공통 UI 규칙 및 Figma 에러 목록·성공 화면.
 * https://app.notion.com/p/3ddb435175228144bce5d8837695c062
 * https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2069-27113
 */
export const TOAST_MESSAGES = {
  /** 전체 문서 생성 성공 시. Figma 홈/문서 정리 완료 (1943:6015) */
  DOCUMENT_CREATED: {
    variant: "success",
    message: "문서가 만들어졌어요",
  },
  /** 같은 녹음의 문서를 본인이 모두 확인했을 때. Figma 1785:3439 */
  ALL_DOCUMENTS_CONFIRMED: {
    variant: "success",
    message: "문서를 모두 확인했어요",
  },
  /** 워크스페이스 전환 완료 후. Figma 1942:11998 */
  WORKSPACE_CHANGED: {
    variant: "success",
    message: "워크스페이스를 옮겼어요",
  },
  /** 연결 끊김으로 종료된 녹음의 업로드 완료·문서 처리 시작을 확인한 뒤 사용해요. */
  RECORDING_CONNECTION_LOST: {
    variant: "caution",
    message: "연결이 끊겨 녹음이 끝났어요. 여기까지 문서로 정리하고 있어요",
  },
  RECORDING_TIME_LIMIT_APPROACHING: {
    variant: "caution",
    message:
      "최대 녹음 시간까지 15분 남았어요. 시간이 되면 녹음을 끝내고 문서로 정리해요",
  },
  /** 로그인 화면으로 이동하며 navigateWithToast로 전달해요. */
  SESSION_EXPIRED: {
    variant: "caution",
    message: "로그인이 만료됐어요. 다시 로그인해 주세요.",
  },
  /** 기기 문제로 종료된 녹음의 저장·문서 처리 시작이 확인된 경우에 사용해요. */
  RECORDING_INTERRUPTED: {
    variant: "caution",
    message: "녹음을 이어갈 수 없어 여기까지 저장했어요. 문서로 정리하고 있어요",
  },
  /** 정리 중 화면 밖에서 결과를 알릴 때. 정리 중 화면에서는 결과 화면을 보여줘요. */
  NO_DOCUMENT_CONTENT: {
    variant: "caution",
    message: "문서로 만들 내용이 없었어요",
  },
  /** 질문 접수 전 실패 시. 답변 생성 실패는 답변 자리에서 안내해요. */
  QUESTION_SEND_FAILED: {
    variant: "error",
    message: "질문을 보내지 못했어요. 잠시 후 다시 시도해 주세요.",
  },
  COPY_FAILED: {
    variant: "error",
    message: "복사하지 못했어요. 다시 시도해 주세요.",
  },
  WORKSPACE_ACCESS_DENIED: {
    variant: "error",
    message: "이 워크스페이스에 더 이상 들어갈 수 없어요.",
  },
} as const satisfies Record<string, Parameters<ToastControls["show"]>[0]>;
