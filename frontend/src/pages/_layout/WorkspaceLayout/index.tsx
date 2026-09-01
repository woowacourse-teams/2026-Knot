import styled from "@emotion/styled";
import useWorkspaceEntry from "@hooks/domain/workspace/useWorkspaceEntry";
import LoadingIndicator from "@primitives/ui/LoadingIndicator";
import { WorkspaceSidebarProvider } from "@provider/context/workspaceSidebarContext";
import WorkspaceGnb from "@widgets/workspace/WorkspaceGnb";
import WorkspaceSidebar from "@widgets/workspace/WorkspaceSidebar";
import { Outlet, useParams } from "react-router";

/**
 * GNB 레이아웃
 *
 * 워크스페이스 입장 후의 내부 페이지(홈, 탐색)가 공유한다.
 * 왼쪽에 사이드바, 오른쪽에 GNB와 화면 콘텐츠를 두며, 사이드바가 열리면 GNB·본문이 오른쪽으로 밀린다.
 * 사이드바 열림/닫힘 상태는 이 레이아웃이 감싼 프로바이더가 가지므로 하위 라우트를 오가도 유지된다.
 *
 * 들어갈 수 있는 워크스페이스인지도 이 레이아웃 범위에서 한 번만 판정한다(`useWorkspaceEntry`).
 * 워크스페이스 조회에 성공하기 전에는 본문 대신 스피너를 두고, 401은 로그인으로, 403·404는 선택 화면으로 보낸다.
 * 판정 규칙은 훅이 가지고 이 레이아웃은 배치만 맡는다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10077 홈 화면
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10207 탐색 결과/사이드바 오픈
 */
export default function WorkspaceLayout() {
  const { workspaceId } = useParams();
  const { isReady } = useWorkspaceEntry({ workspaceId: Number(workspaceId) });

  return (
    <WorkspaceSidebarProvider>
      <Container>
        <WorkspaceSidebar />
        <Content>
          <WorkspaceGnb />
          <Main aria-busy={!isReady}>
            {isReady ? (
              <Outlet />
            ) : (
              <LoadingFallback label="워크스페이스를 불러오고 있어요" />
            )}
          </Main>
        </Content>
      </Container>
    </WorkspaceSidebarProvider>
  );
}

const Container = styled.div`
  display: flex;
  height: 100%;
  background-color: ${({ theme }) => theme.neutral[50]};
`;

const Content = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
`;

const Main = styled.main`
  flex: 1;
  min-height: 0;
  overflow-y: auto;
`;

/** 본문 자리를 그대로 채워 스피너가 화면 가운데에 놓이도록 해요. */
const LoadingFallback = styled(LoadingIndicator)`
  height: 100%;
  color: ${({ theme }) => theme.neutral[800]};
`;
