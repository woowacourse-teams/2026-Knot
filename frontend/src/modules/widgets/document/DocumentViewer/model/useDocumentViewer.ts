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

/** 문서가 없어졌거나 볼 수 없게 된 실패. 경로 형식 오류(400) · 워크스페이스 멤버 아님(403) · 문서 없음(404) */
const UNAVAILABLE_TYPES: HttpErrorType[] = [
  HTTP_ERROR_TYPE.badRequest,
  HTTP_ERROR_TYPE.forbidden,
  HTTP_ERROR_TYPE.notFound,
];

const isUnavailableError = (error: unknown) =>
  isHttpError(error) && UNAVAILABLE_TYPES.includes(error.type);

/**
 * 문서 보기 섹션이 그릴 상태를 정해요. 주소의 id는 위젯이 읽은 자리에서 확인하므로 여기서는 정수만 받아요.
 *
 * - `error`: 문서를 보여 주지 못해요. 다시 시도로 문서만 다시 불러와요.
 *   400 · 403 · 404는 문서가 없어졌거나 볼 수 없게 된 것이라, 이미 받은 문서가 있어도 이 상태예요.
 *   네트워크 · 5xx는 받은 문서가 없을 때만 이 상태예요.
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

  // 화면을 새로고침하지 않고 문서만 다시 조회해요. 새로고침하면 브라우저 안에만 있는 진행 중인 녹음이 사라져요
  const retry = () => {
    refetch();
  };

  if (isUnavailableError(error)) {
    return { status: "error", retry } as const;
  }

  // 다시 불러오기만 실패한 경우에도 이미 받은 문서는 계속 보여줘요
  if (documentDetail !== undefined) {
    return { status: "ready", document: documentDetail } as const;
  }

  if (isError && !isUnauthorized) {
    return { status: "error", retry } as const;
  }

  return { status: "loading" } as const;
};
