import WorkspaceCodeCard from "@widgets/workspace/WorkspaceCodeCard";

/**
 * 초대 코드 입력 화면 (`/workspace/code`)
 *
 * 팀에서 전달받은 참여 코드를 입력해 워크스페이스를 찾는 화면이다.
 * 입력 전 / 로딩 / 입력 에러(만료·오타 등 유효하지 않은 코드) 상태를 한 화면에서 다룬다.
 * 코드가 유효하면 워크스페이스 입장 확인(`/workspace/:workspaceId/join`)으로 이동한다.
 *
 * 초대 링크(`?code=` 쿼리 등)로 들어오는 진입은 이 화면의 범위가 아니며,
 * 링크 형식과 함께 링크 입장 페이지 Issue(#192 하위)에서 다룬다.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 담당하고, 이 페이지는 카드를 놓기만 한다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10168 초대 코드 입력
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10172 초대 코드 입력/입력 에러
 */
export default function WorkspaceCodePage() {
  return <WorkspaceCodeCard />;
}
