interface GetEntryWorkspaceIdParams {
  /** 마지막으로 본 워크스페이스 ID. 본 적이 없으면 `null` */
  lastViewedWorkspaceId: number | null;
  /** 내가 속한 워크스페이스 목록 */
  workspaces: { id: number }[];
}

/**
 * 로그인한 회원이 처음 도착할 워크스페이스를 고릅니다.
 *
 * 마지막으로 본 워크스페이스를 우선하되, 그 사이 워크스페이스에서 나갔을 수 있으므로
 * 목록에 남아 있는지 확인해요. 없으면 첫 워크스페이스로 보냅니다.
 * 확인하지 않으면 더 이상 속하지 않은 워크스페이스로 보내 접근이 막혀요.
 *
 * 속한 워크스페이스가 하나도 없으면 보낼 곳이 없다는 뜻의 `undefined`를 돌려줍니다.
 * 그때는 워크스페이스를 만들거나 참여하는 화면으로 보내야 해요.
 *
 * @example
 * getEntryWorkspaceId({ lastViewedWorkspaceId: 2, workspaces: [{ id: 1 }, { id: 2 }] }); // 2
 */
export const getEntryWorkspaceId = ({
  lastViewedWorkspaceId,
  workspaces,
}: GetEntryWorkspaceIdParams) => {
  const lastViewed = workspaces.find(({ id }) => id === lastViewedWorkspaceId);

  return (lastViewed ?? workspaces[0])?.id;
};
