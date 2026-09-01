import styled from "@emotion/styled";
import { WorkspaceSidebarProvider } from "@provider/context/workspaceSidebarContext";
import WorkspaceGnb from "@widgets/workspace/WorkspaceGnb";
import WorkspaceSidebar from "@widgets/workspace/WorkspaceSidebar";
import { Outlet } from "react-router";

/**
 * GNB 레이아웃
 *
 * 워크스페이스 입장 후의 내부 페이지(홈, 노션 연동, 탐색)가 공유한다.
 * 왼쪽에 사이드바, 오른쪽에 GNB와 화면 콘텐츠를 두며, 사이드바가 열리면 GNB·본문이 오른쪽으로 밀린다.
 * 사이드바 열림/닫힘 상태는 이 레이아웃이 감싼 프로바이더가 가지므로 하위 라우트를 오가도 유지된다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10077 홈 화면
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10207 탐색 결과/사이드바 오픈
 */
export default function WorkspaceLayout() {
  return (
    <WorkspaceSidebarProvider>
      <Container>
        <WorkspaceSidebar />
        <Content>
          <WorkspaceGnb />
          <Main>
            <Outlet />
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
