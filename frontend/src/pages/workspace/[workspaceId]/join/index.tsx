/**
 * 워크스페이스 입장 확인 화면 (`/workspace/:workspaceId/join`)
 *
 * 초대 코드를 입력했거나 초대 링크를 타고 들어온 사용자에게 어느 워크스페이스에
 * 합류하는지 확인시키는 화면이다.
 * 수락하면 워크스페이스 홈(`/workspace/:workspaceId`)으로 이동하고,
 * 링크가 만료·손상된 경우에는 초대 링크 오류(`/join-error`)로 빠진다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10176 초대 코드 입력/워크스페이스 입장
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10180 초대 링크로 워크스페이스 입장
 */
export default function WorkspaceJoinPage() {
  return (
    <div>
      <h1>Workspace Join Page</h1>
    </div>
  );
}
