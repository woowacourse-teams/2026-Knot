import {
  HTTP_ERROR_TYPE,
  type HttpErrorType,
  isHttpError,
} from "@api/httpClient/error";
import useDocumentQuery from "@api/queries/useDocumentQuery";
import useRedirectToLoginOnUnauthorized from "@hooks/domain/auth/useRedirectToLoginOnUnauthorized";

interface UseDocumentViewerParams {
  workspaceId: number;
  documentId: number;
}

/** 다시 불러와도 결과가 같은 잘못된 요청. 경로 형식 오류(400) · 워크스페이스 멤버 아님(403) · 문서 없음(404) */
const INVALID_REQUEST_TYPES: HttpErrorType[] = [
  HTTP_ERROR_TYPE.badRequest,
  HTTP_ERROR_TYPE.forbidden,
  HTTP_ERROR_TYPE.notFound,
];

const isInvalidRequestError = (error: unknown) =>
  isHttpError(error) && INVALID_REQUEST_TYPES.includes(error.type);

/**
 * 문서 보기 섹션이 그릴 상태를 정해요. 주소의 id는 위젯이 읽은 자리에서 확인하므로 여기서는 정수만 받아요.
 *
 * - `notFound`: 400 · 403 · 404. 다시 불러와도 결과가 같아 홈으로 보내요.
 *   공통 "잘못된 요청" 화면(ERR-R2)이나 이 상황의 시안이 생기기 전까지 쓰는 임시 안내예요.
 * - `error`: 네트워크 · 5xx. 다시 시도로 다시 불러와요. 공통 오류 화면(ERR-R3 · R4)이나 이 상황의 시안이 생기기 전까지 임시예요.
 * - `loading`: 불러오는 중. 401이면 로그인 화면으로 보내는 동안에도 이 상태로 둬요.
 * - `ready`: 문서를 받았어요.
 */
export const useDocumentViewer = ({
  workspaceId,
  documentId,
}: UseDocumentViewerParams) => {
  const {
    data: documentDetail,
    error,
    isError,
    refetch,
  } = useDocumentQuery({ workspaceId, documentId });
  const { isUnauthorized } = useRedirectToLoginOnUnauthorized({ error });

  if (isInvalidRequestError(error)) {
    return { status: "notFound" } as const;
  }

  // 다시 불러오기만 실패한 경우에도 이미 받은 문서는 계속 보여줘요
  if (documentDetail !== undefined) {
    return { status: "ready", document: documentDetail } as const;
  }

  if (isError && !isUnauthorized) {
    return {
      status: "error",
      // 화면을 새로고침하지 않고 문서만 다시 조회해요. 새로고침하면 브라우저 안에만 있는 진행 중인 녹음이 사라져요
      retry: () => {
        refetch();
      },
    } as const;
  }

  return { status: "loading" } as const;
};
