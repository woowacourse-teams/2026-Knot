import {
  HTTP_ERROR_TYPE,
  type HttpErrorType,
  isHttpError,
} from "@api/httpClient/error";
import useDocumentQuery from "@api/queries/useDocumentQuery";
import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import { isUnauthorizedError } from "@utils/isUnauthorizedError";
import { useEffect } from "react";

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
 * 문서 보기 섹션이 그릴 상태를 정해요.
 *
 * - `notFound`: 주소의 id가 정수가 아니거나(요청하지 않음) 400 · 403 · 404.
 *   공통 "잘못된 요청" 화면(ERR-R2)이 생기기 전까지 쓰는 임시 안내예요.
 * - `error`: 네트워크 · 5xx. 다시 시도로 다시 불러와요. 공통 오류 화면(ERR-R3 · R4)이 생기기 전까지 임시예요.
 * - `loading`: 불러오는 중. 401이면 로그인 화면으로 보내는 동안에도 이 상태로 둬요.
 * - `ready`: 문서를 받았어요.
 */
export const useDocumentViewer = ({
  workspaceId,
  documentId,
}: UseDocumentViewerParams) => {
  const query = useDocumentQuery({ workspaceId, documentId });
  const { navigateToLogin } = useNavigateToLogin();

  const isUnauthorized = isUnauthorizedError(query.error);

  useEffect(() => {
    if (isUnauthorized) navigateToLogin({ replace: true });
  }, [isUnauthorized, navigateToLogin]);

  // 요청하지 않은(enabled: false) 쿼리는 계속 isPending이라 불러오는 중보다 먼저 확인해요
  const isValidId =
    Number.isInteger(workspaceId) && Number.isInteger(documentId);

  if (!isValidId || isInvalidRequestError(query.error)) {
    return { status: "notFound" } as const;
  }

  // 다시 불러오기만 실패한 경우에도 이미 받은 문서는 계속 보여줘요
  if (query.data !== undefined) {
    return { status: "ready", document: query.data } as const;
  }

  if (query.isError && !isUnauthorized) {
    return {
      status: "error",
      retry: () => {
        query.refetch();
      },
    } as const;
  }

  return { status: "loading" } as const;
};
