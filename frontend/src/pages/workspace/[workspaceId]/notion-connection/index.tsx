import NotionConnectCard from "@widgets/notion/NotionConnectCard";

/**
 * 노션 연동 화면 (`/workspace/:workspaceId/notion-connection`)
 *
 * 워크스페이스 생성 플로우의 마지막 단계로, 기존 노션 기록을 knot으로 가져올지 묻는 화면이다.
 * 연동하거나 건너뛰면 워크스페이스 홈(`/workspace/:workspaceId`)으로 이동한다.
 * Notion OAuth를 마친 서버는 `?result=connected|failed`를 붙여 이 화면으로 되돌려보낸다.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 담당하고, 이 페이지는 카드를 놓기만 한다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1946 새 워크스페이스 생성/노션에서 가져오기
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10203 노션 연동
 */
export default function WorkspaceNotionConnectionPage() {
  return <NotionConnectCard />;
}
