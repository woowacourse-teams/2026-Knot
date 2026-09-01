import {
  createContext,
  useContext,
  useState,
  type PropsWithChildren,
} from "react";

interface WorkspaceSidebarContextValue {
  isSidebarOpen: boolean;
  toggleSidebar: () => void;
}

/** GNB 토글 버튼의 `aria-controls`와 사이드바의 `id`를 잇는 값이에요. */
export const WORKSPACE_SIDEBAR_ID = "workspace-sidebar";

const WorkspaceSidebarContext =
  createContext<WorkspaceSidebarContextValue | null>(null);

/**
 * 워크스페이스 사이드바의 열림/닫힘 상태를 GNB와 사이드바 위젯이 함께 쓰게 하는 프로바이더.
 *
 * `WorkspaceLayout`이 감싸므로 홈·탐색·노션 연동 라우트 사이를 이동해도 상태가 유지돼요.
 * 처음에는 닫혀 있습니다.
 */
export const WorkspaceSidebarProvider = ({ children }: PropsWithChildren) => {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  const toggleSidebar = () => setIsSidebarOpen((prev) => !prev);

  return (
    <WorkspaceSidebarContext.Provider value={{ isSidebarOpen, toggleSidebar }}>
      {children}
    </WorkspaceSidebarContext.Provider>
  );
};

export const useWorkspaceSidebar = () => {
  const context = useContext(WorkspaceSidebarContext);

  if (context === null) {
    throw new Error(
      "useWorkspaceSidebar는 WorkspaceSidebarProvider 안에서만 쓸 수 있어요.",
    );
  }

  return context;
};
