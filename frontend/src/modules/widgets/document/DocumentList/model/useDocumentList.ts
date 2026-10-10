import {
  HTTP_ERROR_TYPE,
  type HttpErrorType,
  isHttpError,
} from "@api/httpClient/error";
import useDocumentsQuery from "@api/queries/useDocumentsQuery";
import useWorkspaceAccessGuard from "@hooks/domain/workspace/useWorkspaceAccessGuard";
import { groupDocumentsByTopic } from "@utils/groupDocumentsByTopic";

interface UseDocumentListParams {
  workspaceId: number;
}

/** `useWorkspaceAccessGuard`가 다른 화면으로 보내는 실패. 로그인 풀림(401) · 워크스페이스 멤버 아님(403) · 없는 워크스페이스(404) */
const LEAVING_TYPES: HttpErrorType[] = [
  HTTP_ERROR_TYPE.unauthorized,
  HTTP_ERROR_TYPE.forbidden,
  HTTP_ERROR_TYPE.notFound,
];

const isLeavingError = (error: unknown) =>
  isHttpError(error) && LEAVING_TYPES.includes(error.type);

/**
 * 문서 목록 섹션이 그릴 상태를 정해요. 주소에서 읽은 id를 확인하지 않고 그대로 조회하고, 잘못된 id는 서버 응답(400)으로 알아요.
 *
 * - `loading`: 불러오는 중. 401 · 403 · 404로 다른 화면에 보내는 동안에도 이 상태로 둬요.
 * - `failed`: 문서를 받지 못했어요. 이어 받는 도중에 실패한 경우도 받은 일부를 쓰지 않고 이 상태예요.
 *   `retry`는 처음부터 다시 받아요.
 * - `empty`: 문서가 하나도 없어요.
 * - `ready`: 문서를 받아 폴더별로 묶었어요. `folders`에 폴더가 있어요.
 *
 * 쓰는 쪽이 구조분해해서 받을 수 있게, 어느 상태든 같은 이름의 값을 모두 돌려줘요. 받은 문서가 없으면 `folders`는 빈 배열이에요.
 */
export const useDocumentList = ({ workspaceId }: UseDocumentListParams) => {
  const {
    data: documentList,
    error,
    isError,
    refetch,
  } = useDocumentsQuery({ workspaceId });

  // 로그인이 풀렸으면 로그인 화면으로, 워크스페이스에 들어갈 수 없으면 선택 화면으로 보내요
  useWorkspaceAccessGuard({ error });

  const folders =
    documentList === undefined
      ? []
      : groupDocumentsByTopic({
          topics: documentList.topics,
          documents: documentList.items,
        });

  const getStatus = () => {
    // 다시 불러오기만 실패한 경우에도 이미 받은 문서는 계속 보여줘요
    if (documentList !== undefined) {
      return folders.length === 0 ? ("empty" as const) : ("ready" as const);
    }

    if (isError && !isLeavingError(error)) return "failed" as const;

    return "loading" as const;
  };

  // 화면을 새로고침하지 않고 목록만 다시 조회해요. 새로고침하면 브라우저 안에만 있는 진행 중인 녹음이 사라져요
  const retry = () => {
    refetch();
  };

  return { status: getStatus(), folders, retry };
};
