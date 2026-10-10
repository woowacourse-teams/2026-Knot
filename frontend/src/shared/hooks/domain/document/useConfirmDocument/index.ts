import useConfirmDocumentMutation from "@api/mutations/useConfirmDocumentMutation";
import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import { isUnauthorizedError } from "@utils/isUnauthorizedError";

interface UseConfirmDocumentParams {
  workspaceId: number;
  documentId: number;
}

/**
 * 문서를 확인했다고 기록하는 동작과 그 진행 상태를 주는 도메인 훅.
 *
 * 요청이 끝나면 뮤테이션 훅이 문서를 다시 불러와요. `isConfirming`은 그 다시 불러오기까지 끝나야 `false`가 돼요.
 * 확인 대상이 아니라는 409도 같은 흐름으로 문서를 다시 불러와요. 그 밖의 실패는 아무것도 바꾸지 않아 다시 누를 수 있어요.
 * 로그인이 풀렸으면(401) 로그인 화면으로 보내요.
 */
const useConfirmDocument = ({
  workspaceId,
  documentId,
}: UseConfirmDocumentParams) => {
  const { mutate: requestConfirmation, isPending: isConfirming } =
    useConfirmDocumentMutation();
  const { navigateToLogin } = useNavigateToLogin();

  const confirmDocument = () => {
    requestConfirmation(
      { workspaceId, documentId },
      {
        onError: (error) => {
          if (isUnauthorizedError(error)) navigateToLogin({ replace: true });
        },
      },
    );
  };

  return { confirmDocument, isConfirming };
};

export default useConfirmDocument;
