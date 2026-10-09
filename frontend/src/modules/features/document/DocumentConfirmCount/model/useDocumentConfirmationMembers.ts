import useDocumentConfirmationsQuery from "@api/queries/useDocumentConfirmationsQuery";

interface UseDocumentConfirmationMembersParams {
  workspaceId: number;
  documentId: number;
}

/**
 * 확인한 사람 팝오버가 그릴 목록과 조회 상태.
 *
 * 목록은 팝오버를 열 때가 아니라 확인 수가 그려질 때 조회해요.
 * 포인터를 올리면 바로 열리는 팝오버라, 그때 조회하면 처음의 기다림이 눈에 띄어요.
 * 팝오버를 다시 열어도 다시 조회하지 않아요. 앞선 조회가 실패했을 때만 `retryIfFailed`로 다시 조회해요.
 *
 * 확인하지 않은 채 워크스페이스를 나간 사람(EXCLUDED)은 목록에 넣지 않아요.
 * 서버 정렬에 기대지 않고, 확인한 사람과 아직 확인하지 않은 사람을 여기서 나눠요(CONF-R5).
 */
export const useDocumentConfirmationMembers = ({
  workspaceId,
  documentId,
}: UseDocumentConfirmationMembersParams) => {
  const {
    data: confirmations,
    isError,
    isFetching,
    refetch,
  } = useDocumentConfirmationsQuery({ workspaceId, documentId });
  const items = confirmations?.items ?? [];

  const getStatus = () => {
    // 다시 불러오기만 실패한 경우에도 이미 받은 목록은 계속 보여줘요
    if (confirmations !== undefined) return "ready" as const;
    // 실패한 뒤 다시 불러오는 동안에도 isError는 true라, 불러오는 중이 아닌지 함께 봐요
    if (isError && !isFetching) return "failed" as const;

    return "loading" as const;
  };

  const status = getStatus();

  /** 앞선 조회가 실패한 채로 남아 있으면 다시 조회해요 */
  const retryIfFailed = () => {
    if (status === "failed") refetch();
  };

  return {
    status,
    confirmedMembers: items.filter(({ state }) => state === "CONFIRMED"),
    pendingMembers: items.filter(({ state }) => state === "PENDING"),
    retryIfFailed,
  };
};
