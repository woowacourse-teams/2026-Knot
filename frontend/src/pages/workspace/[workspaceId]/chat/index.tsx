/**
 * 탐색(채팅) 화면 (`/workspace/:workspaceId/chat`, `/workspace/:workspaceId/chat/:sessionId`)
 *
 * 워크스페이스에 쌓인 기록을 대화로 찾는 화면이다.
 * `sessionId` 없이 들어오면 입력 전(빈 결과) 상태로 시작하고,
 * 대화가 시작되면 해당 세션(`/chat/:sessionId`)으로 이어진다.
 * 사이드바 오픈, 채팅 세션 목록도 별도 라우트가 아니라 이 화면 위의 상태 변형이다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10065 탐색 결과/입력 전
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10068 탐색 결과
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10207 탐색 결과/사이드바 오픈
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10217 탐색 결과/채팅 세션 목록
 */
export default function ChatPage() {
  return (
    <div>
      <h1>Chat Page</h1>
    </div>
  );
}
