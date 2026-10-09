import useConfirmDocumentMutation from "@api/mutations/useConfirmDocumentMutation";
import useDocumentQuery from "@api/queries/useDocumentQuery";
import useNavigateToLogin from "@hooks/domain/auth/useNavigateToLogin";
import Button from "@primitives/ui/Button";
import { isUnauthorizedError } from "@utils/isUnauthorizedError";
import { useParams } from "react-router";

import CheckIcon from "@/assets/icons/check.svg";

interface DocumentConfirmButtonProps {
  /**
   * 확인할 문서의 ID.
   * 같은 녹음의 문서를 넘겨 보는 녹음 직후 확인 화면에서는 지금 보는 문서의 ID가 주소에 없을 수 있어요.
   * 그 화면에도 놓을 수 있게 주소에서 읽지 않고 받아요. 워크스페이스 ID는 워크스페이스 아래 모든 화면의 주소에 있어 주소에서 읽어요
   */
  documentId: number;
}

/**
 * 문서를 확인했다고 기록하는 버튼. 아직 확인하지 않은 확인 대상에게만 보여요(CONF-R6).
 *
 * 보일지는 문서 상세의 내 확인 상태로 정해요. 문서 보기 위젯과 같은 키로 조회해서 요청이 더 나가지 않아요.
 * 불러오는 중이거나, 이미 확인했거나, 확인 대상이 아니면 아무것도 그리지 않아요.
 *
 * 누르면 응답과 문서 다시 불러오기가 끝날 때까지 누를 수 없고, 다시 받은 내 상태가 바뀌면 사라져요.
 * 확인 대상이 아니라는 409도 같은 흐름으로 사라져요. 그 밖의 실패는 버튼을 남겨 다시 누를 수 있어요.
 */
export default function DocumentConfirmButton({
  documentId,
}: DocumentConfirmButtonProps) {
  const params = useParams();
  const workspaceId = Number(params.workspaceId);
  const { data: documentDetail } = useDocumentQuery({
    workspaceId,
    documentId,
  });
  const { mutate: confirmDocument, isPending } = useConfirmDocumentMutation();
  const { navigateToLogin } = useNavigateToLogin();

  if (documentDetail?.myConfirmationState !== "PENDING") return null;

  const handleConfirm = () => {
    confirmDocument(
      { workspaceId, documentId },
      {
        onError: (error) => {
          if (isUnauthorizedError(error)) navigateToLogin({ replace: true });
        },
      },
    );
  };

  return (
    <Button isLoading={isPending} onClick={handleConfirm}>
      <CheckIcon />
      문서를 확인했어요
    </Button>
  );
}
