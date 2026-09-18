/**
 * 입장 확인 화면(`/workspace/:workspaceId/join`)이 라우터 state로 받는 값.
 *
 * 참여 API는 workspaceId가 아니라 credential만 받으므로(ADR 244), 미리보기를 통과한
 * 코드·토큰을 주소에 노출하지 않고 state로 넘겨요. `useNavigateToWorkspaceJoin`이 쓰고
 * `useWorkspaceJoinState`가 읽어요. 새로고침·직접 진입처럼 state가 없으면 입장 확인 화면은 선택 화면으로 돌려보내요.
 */
export interface WorkspaceJoinState {
  /** 미리보기를 통과한 6자 초대 코드 또는 링크 토큰 원문 */
  credential: string;
  /** 미리보기 응답의 워크스페이스 이름. 입장 확인 카드 제목에 보여줘요 */
  workspaceName: string;
}
